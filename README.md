# AlmostPerWorlds

A minimal, Folia-safe fork of [PerWorlds](https://github.com/TheNextLvl-net/per-worlds) that does
exactly one thing: keeps each configured **world group** on its own inventory, ender chest, XP,
food/health, and (optionally) game mode - so worlds managed by
[Worlds](https://github.com/TheNextLvl-net/worlds) (or anything else) don't share a single
inventory across the whole server.

## What's ported vs. what isn't

**Ported (player-scoped, safe on Folia):**
- Inventory, armor, off-hand
- Ender chest
- XP / level
- Food level, saturation, exhaustion
- Health
- Potion effects
- Game mode - two independent, stackable options:
  - `sync-game-mode` in `config.yml` (off by default): remembers each player's *own* last game
    mode per group and restores it when they come back. Nothing is set the first time a player
    enters a group.
  - `/almostperworlds gamemode <group> <mode>`: force-sets *every* player to a fixed game mode
    every time they enter that group (e.g. always Creative in your test world), overriding
    whatever `sync-game-mode` would have restored. This is what you want for "world A is always
    survival, world B is always creative."

**Deliberately dropped (world-scoped, unsafe on Folia / not needed since "Worlds" owns this):**
- Time, weather, difficulty/hardcore, game rule, and world border syncing across a group's worlds
- Chat/tablist per-group behavior
- Multiverse-Inventories / MyWorlds importers
- The `net.thenextlvl.*` command framework, i18n bundle, metrics, and version checker the
  original plugin depended on - replaced with a plain `CommandExecutor`/`TabCompleter` and no
  external runtime dependencies besides the Paper API itself, so this builds against nothing but
  `paperweight.paperDevBundle(...)`.

The dropped features all required reading or writing a **second world's** state synchronously
from inside an event fired on a different world/region - that's the part that isn't safe on
Folia's per-region threading model (and is exactly what the original author's commented-out
`// foliaSupported = true` line was flagging). Everything kept here only ever touches the single
player who triggered the event, dispatched through `Bukkit.getAsyncScheduler()` for file I/O and
`player.getScheduler()` to apply the result - the standard Folia-safe pattern for entity-scoped
work. It also runs fine on regular (non-Folia) Paper.

## Commands

```
/almostperworlds create <name>
/almostperworlds delete <name>
/almostperworlds addworld <group> <world>
/almostperworlds removeworld <group> <world>
/almostperworlds list
/almostperworlds info <group>
/almostperworlds gamemode <group> <survival|creative|adventure|spectator|clear>
```
(alias: `/apw`, permission: `almostperworlds.command.group`)

Worlds not assigned to any group all share one implicit "default" bucket - same as vanilla,
so nothing is lost for worlds you never group.

## Building

```
./gradlew shadowJar
```

The output jar will be at `build/libs/almost-per-worlds-<version>-all.jar`.

**Note:** this was written and reviewed against the same Paper/Folia scheduler APIs
(`Bukkit.getAsyncScheduler()`, `Entity#getScheduler()`) already used elsewhere in the source
project it was ported from, but it has **not** been compiled or run in this environment (no
network/Gradle access here) - please build and smoke-test it against your own server jar before
relying on it, and open an issue with the exact compiler error if anything doesn't line up with
your Paper API version.

## Install

Requires [Worlds](https://github.com/TheNextLvl-net/worlds) (or your own world manager) to
actually create the worlds - AlmostPerWorlds only groups worlds that already exist and are
loaded when you run `addworld`.
