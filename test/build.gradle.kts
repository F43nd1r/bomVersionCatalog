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
    implementation(libs.comFasterxmlJacksonDatatype.jacksonDatatypeJdk8)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}