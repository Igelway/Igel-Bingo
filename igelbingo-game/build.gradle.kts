plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.2"
}

group = "de.igelbingo"
version = "1.0.9"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    compileOnly("org.popcraft:chunky-common:1.3.38")
    implementation("org.yaml:snakeyaml:2.4")
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
        options.release = 25
    }
}
