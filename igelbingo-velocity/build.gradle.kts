plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.2"
}

group = "de.igelbingo"
version = "1.1.9"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    compileOnly("com.google.inject:guice:7.0.0")
    compileOnly("net.luckperms:api:5.4")

    implementation("org.yaml:snakeyaml:2.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    shadowJar {
        archiveFileName.set("igelbingo-velocity-${project.version}.jar")
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
        options.release = 25
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
}
