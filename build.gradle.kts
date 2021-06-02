plugins {
    idea

    id("org.springframework.boot") version "2.5.0"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"

    kotlin("jvm") version "1.5.10"
    kotlin("kapt") version "1.5.10"
    kotlin("plugin.spring") version "1.5.10"
    kotlin("plugin.serialization") version "1.5.10"
}

group = "sh.weller"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    kapt("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-logging")

    implementation("io.netty:netty-tcnative-boringssl-static")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.projectreactor.tools:blockhound:1.0.6.RELEASE")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.5.0")

    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions") // Magic Speedup

    implementation("com.rometools:rome:1.15.0")
    implementation("com.rometools:rome-opml:1.15.0")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.springframework:spring-r2dbc")
    implementation("io.r2dbc:r2dbc-pool")
    implementation("io.r2dbc:r2dbc-h2")
    implementation("com.h2database:h2")
    implementation("io.r2dbc:r2dbc-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
//    testImplementation("org.springframework.security:spring-security-test")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")

    testImplementation("org.testcontainers:testcontainers:1.15.3")
    testImplementation("org.testcontainers:postgresql:1.15.3")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.0")

    testImplementation("io.strikt:strikt-core:0.31.0")
    testImplementation("io.strikt:strikt-jvm:0.31.0")
}

tasks {
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
            jvmTarget = "11"
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "11"
        }
    }
    test {

        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}