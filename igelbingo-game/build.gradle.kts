plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.2"
}

group = "de.igelbingo"
version = "1.1.10"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    compileOnly("com.github.retrooper:packetevents-spigot:2.12.2")
    implementation("org.yaml:snakeyaml:2.4")
}

tasks {
    shadowJar {
        archiveFileName.set("igelbingo-purpur-${project.version}.jar")
        archiveClassifier.set("")
        relocate("org.yaml.snakeyaml", "de.igelbingo.libs.snakeyaml")
    }

    build {
        dependsOn(shadowJar)
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 25
    }
}
