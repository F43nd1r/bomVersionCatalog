import com.faendir.gradle.createWithBomSupport

buildscript {
    repositories {
        mavenCentral()
        mavenLocal()
    }
    dependencies {
        classpath("com.faendir.gradle:bom-version-catalog:1.2.0")
    }
}
apply(plugin = "com.faendir.gradle.bom-version-catalog")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        createWithBomSupport("libs") {
            //fromBom("org.springframework.boot:spring-boot-dependencies:2.5.0")
            fromBomAlias("springBom")
        }
    }
}