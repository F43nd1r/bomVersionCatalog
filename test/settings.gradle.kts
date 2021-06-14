import com.faendir.gradle.createFromBom

buildscript {
    repositories {
        mavenCentral()
        mavenLocal()
    }
    dependencies {
        classpath("com.faendir.gradle:bom-version-catalog:1.0-SNAPSHOT")
    }
}
apply(plugin = "com.faendir.gradle.bom-version-catalog")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        createFromBom("spring", "org.springframework.boot:spring-boot-dependencies:2.5.0")
    }
}