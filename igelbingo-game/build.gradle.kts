plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.2"
}

group = "de.igelbingo"
version = "1.0.5"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.12.2")

    implementation("org.yaml:snakeyaml:2.3")
}

tasks {
    shadowJar {
        archiveFileName.set("igelbingo-purpur-plugin.jar")
        archiveClassifier.set("")
        relocate("org.yaml.snakeyaml", "de.igelbingo.libs.snakeyaml")
    }

    build {
        dependsOn(shadowJar)
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }
}
