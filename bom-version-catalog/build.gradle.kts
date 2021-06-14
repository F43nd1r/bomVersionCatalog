plugins {
    kotlin("jvm") version "1.5.10"
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.15.0"
}

group = "com.faendir.gradle"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.12.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.3")
    implementation("net.pearx.kasechange:kasechange:1.3.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

gradlePlugin {
    plugins {
        create("BomVersionCatalogPlugin") {
            id = "com.faendir.gradle.bom-version-catalog"
            implementationClass = "com.faendir.gradle.BomVersionCatalogPlugin"
        }
    }
}

project.configurations.detachedConfiguration()
