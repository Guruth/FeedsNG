import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea

    id("org.springframework.boot") version "2.4.5"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"

    kotlin("jvm") version "1.5.0-RC"
    kotlin("kapt") version "1.5.0-RC"
    kotlin("plugin.spring") version "1.5.0"
    kotlin("plugin.serialization") version "1.5.0-RC"
}

group = "sh.weller"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    kapt("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springframework.boot:spring-boot-starter-webflux") {
        exclude("com.fasterxml.jackson.core")
        exclude("com.fasterxml.jackson.datatype")
        exclude("com.fasterxml.jackson.module")
    }
    // implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-logging")

    implementation("io.netty:netty-tcnative")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")

    implementation(kotlin("reflect"))
    // implementation(kotlin("stdlib-jdk8"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    implementation("com.rometools:rome:1.15.0")
    implementation("com.rometools:rome-opml:1.15.0")

    // developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("org.springframework:spring-r2dbc")
    implementation("io.r2dbc:r2dbc-pool")
    implementation("io.r2dbc:r2dbc-h2")
    implementation("com.h2database:h2")
    implementation("io.r2dbc:r2dbc-postgresql")

    // testImplementation("org.springframework.boot:spring-boot-starter-test")
    // testImplementation("org.springframework.security:spring-security-test")

    testImplementation(kotlin("test-common"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")

    testImplementation("org.testcontainers:testcontainers:1.15.3")
    testImplementation("org.testcontainers:postgresql:1.15.3")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")

    testImplementation("io.strikt:strikt-core:0.31.0")
    testImplementation("io.strikt:strikt-jvm:0.31.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
        jvmTarget = "11"
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}