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
            fromBomAlias("jacksonBom")
            fromBomAlias("springBom")
        }
    }
}