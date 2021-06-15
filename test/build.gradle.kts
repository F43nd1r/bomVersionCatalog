plugins {
    java
}

group = "com.faendir.gradle"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    implementation(platform(libs.springBom))
    implementation(platform(libs.orgSpringframeworkBoot.springBootDependencies))
    implementation(libs.comQuerydsl.querydslJpa)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}