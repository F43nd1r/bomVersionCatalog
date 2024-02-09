import com.faendir.gradle.createWithBomSupport

pluginManagement {
    includeBuild("../bom-version-catalog")
}
plugins {
    id("com.faendir.gradle.bom-version-catalog")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        createWithBomSupport("libs") {
            fromBomAlias("jacksonBom")
            // fromBom("org.springframework.boot:spring-boot-dependencies:2.5.0")
            fromBomAlias("springBom")
        }
    }
}