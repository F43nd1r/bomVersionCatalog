plugins {
    java
}

group = "com.faendir.gradle"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.comQuerydsl.querydslJpa)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}