import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
    id("de.eldoria.plugin-yml.paper") version "0.9.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "dev.xeaf.almostperworlds"
version = project.property("version") as String

java.disableAutoTargetJvm()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.compileJava {
    options.release.set(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.2.build.+")
}

tasks.shadowJar {
    archiveBaseName.set("almost-per-worlds")
}

paper {
    name = "AlmostPerWorlds"
    main = "dev.xeaf.almostperworlds.AlmostPerWorlds"
    apiVersion = "26.1.2"
    description = "Minimal Folia-safe per-world-group inventory separation"
    load = BukkitPluginDescription.PluginLoadOrder.STARTUP
    authors = listOf("xeaf")
    foliaSupported = true

    permissions {
        register("almostperworlds.admin") { children = listOf("almostperworlds.command.group") }
        register("almostperworlds.command.group") { description = "Allows access to group management commands" }
    }

    // "Worlds" (https://github.com/TheNextLvl-net/worlds) is Folia-supported and owns world
    // creation/management. We only need it (if present) to have already created the worlds
    // referenced in groups.yml by the time we load - we don't call any of its API.
    serverDependencies {
        register("Worlds") {
            load = PaperPluginDescription.RelativeLoadOrder.AFTER
            required = false
        }
    }
}
