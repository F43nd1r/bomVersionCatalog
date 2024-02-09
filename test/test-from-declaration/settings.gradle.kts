import com.faendir.gradle.createWithBomSupport

pluginManagement {
    includeBuild("../../bom-version-catalog")
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
            fromBom("org.springframework.boot:spring-boot-dependencies:2.5.1")
            fromBom("com.fasterxml.jackson:jackson-bom:2.16.1")
        }
    }
}