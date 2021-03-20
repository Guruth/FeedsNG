import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea

    id("org.springframework.boot") version "2.4.3"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"

    kotlin("jvm") version "1.5.0-M1"
    kotlin("plugin.spring") version "1.5.0-M1"
    kotlin("plugin.serialization") version "1.5.0-M1"
}

group = "sh.weller"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    implementation("com.rometools:rome:1.15.0")
    implementation("com.rometools:rome-opml:1.15.0")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("org.springframework:spring-r2dbc")
    implementation("io.r2dbc:r2dbc-h2")
    implementation("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    testImplementation(kotlin("test-common"))
    testImplementation(kotlin("test-annotations-common"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")

    testImplementation("io.strikt:strikt-core:0.29.0")
    testImplementation("io.strikt:strikt-jvm:0.29.0")
    testImplementation("io.strikt:strikt-spring:0.29.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
        jvmTarget = "11"

        useIR = true
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