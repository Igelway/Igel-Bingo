plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.2"
}

group = "de.igelbingo"
version = "1.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    compileOnly("com.google.inject:guice:7.0.0")

    implementation("org.yaml:snakeyaml:2.3")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("org.yaml.snakeyaml", "de.igelbingo.libs.snakeyaml")
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    build {
        dependsOn(shadowJar)
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }
}
