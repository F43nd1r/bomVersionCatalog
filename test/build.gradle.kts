plugins {
    java
}

group = "com.faendir.gradle"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    implementation(platform(spring.bom))
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}