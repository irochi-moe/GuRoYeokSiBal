plugins {
    java
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()
description = providers.gradleProperty("description").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

configurations.all {
    exclude(group = "org.bukkit", module = "bukkit")

    resolutionStrategy {
        force("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.github.LlmDl:Towny:0.100.4.0")
    compileOnly("com.github.TownyAdvanced:TownyChat:0.116")
}

tasks {
    processResources {
        val projectVersion = version
        val projectDescription = description
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(
                mapOf(
                    "version" to projectVersion,
                    "description" to projectDescription
                )
            )
        }
    }

    jar {
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
