plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
}

group = "de.igelbingo"
version = "1.0.0"

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

    implementation("org.yaml:snakeyaml:2.3")
    implementation("com.github.docker-java:docker-java-core:3.4.2")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.4.2")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("org.yaml.snakeyaml", "de.igelbingo.libs.snakeyaml")
        relocate("com.github.dockerjava", "de.igelbingo.libs.dockerjava")
        relocate("org.apache.commons", "de.igelbingo.libs.commons")
    }

    build {
        dependsOn(shadowJar)
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }
}
