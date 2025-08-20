plugins {
    id("java-library")
    id("maven-publish")
}

group = "org.figuramc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.figuramc:memory-tracker:1.0-SNAPSHOT")
    implementation("org.figuramc:figura-translations:1.0-SNAPSHOT")

    implementation("org.jetbrains:annotations:25.0.0")

    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-util:9.6")
}

java {
    withSourcesJar()
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}