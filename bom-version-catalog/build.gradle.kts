plugins {
    kotlin("jvm") version "1.5.10"
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.15.0"
}

group = "com.faendir.gradle"
version = "1.0.1"

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
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

gradlePlugin {
    plugins {
        create("BomVersionCatalogPlugin") {
            id = "com.faendir.gradle.bom-version-catalog"
            displayName = "Bom to version catalog plugin"
            description = "Allows to import boms as version catalogs"
            implementationClass = "com.faendir.gradle.BomVersionCatalogPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/F43nd1r/bomVersionCatalog"
    vcsUrl = "https://github.com/F43nd1r/bomVersionCatalog"
    tags = listOf("bom", "version-catalog")
}



project.configurations.detachedConfiguration()
