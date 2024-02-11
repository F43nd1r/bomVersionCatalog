plugins {
    kotlin("jvm") version "1.9.22"
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("fr.brouillard.oss.gradle.jgitver") version "0.10.0-rc03"
}

group = "com.faendir.gradle"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.16.1"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("net.pearx.kasechange:kasechange:1.4.1")
}

val javaVersion: JavaVersion = JavaVersion.VERSION_1_8
val javaMajorVersion: String = javaVersion.majorVersion

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(javaMajorVersion))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "$javaVersion"
    }
}

gradlePlugin {
    website.set("https://github.com/F43nd1r/bomVersionCatalog")
    vcsUrl.set("https://github.com/F43nd1r/bomVersionCatalog")
    plugins {
        create("BomVersionCatalogPlugin") {
            id = "com.faendir.gradle.bom-version-catalog"
            displayName = "Bom to version catalog plugin"
            description = "Allows to import boms as version catalogs"
            implementationClass = "com.faendir.gradle.BomVersionCatalogPlugin"
            tags.set(listOf("bom", "version-catalog"))
        }
    }
}
