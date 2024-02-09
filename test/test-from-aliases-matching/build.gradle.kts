plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.comQuerydsl.querydslJpa)
    implementation(libs.comFasterxmlJacksonDatatype.jacksonDatatypeJdk8)
}