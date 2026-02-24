plugins {
    kotlin("jvm")
}

group = "org.iamsso"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
}