# BOM Version Catalog

This plugin allows you to combine the type safety of version catalogs with existing BOMs by importing them into version catalogs.

## Usage

settings.gradle.kts

```kotlin
import com.faendir.gradle.createWithBomSupport
plugins {
    id("com.faendir.gradle.bom-version-catalog") version "<latest>"
}
dependencyResolutionManagement {
    repositories {
        mavenCentral() //or whichever repository holds your boms
    }
    versionCatalogs {
        createWithBomSupport("libs") {
            fromBom("com.vaadin:vaadin-bom:20.0.1") //either directly specify your bom
            fromBomAlias("springBootBom") //or use definition in toml
            version("mockito","3.9.1") //overrides both bom and toml
        }
    }
}
```

gradle/libs.versions.toml (overrides BOM)

```toml
[versions]
springBoot="2.5.1"
querydsl="5.0.0-SNAPSHOT"

[libraries]
springBootBom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "springBoot" }
```