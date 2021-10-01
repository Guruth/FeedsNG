plugins {
    idea

    id("org.springframework.boot") version "2.5.4"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"


    val kotlinCompilerVersion = "1.5.30"
    kotlin("jvm") version kotlinCompilerVersion
    kotlin("kapt") version kotlinCompilerVersion
    kotlin("plugin.spring") version kotlinCompilerVersion
    kotlin("plugin.serialization") version kotlinCompilerVersion
}


group = "sh.weller"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Coroutines
    val kotlinCoroutinesVersion = "1.5.2"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$kotlinCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinCoroutinesVersion")

    implementation("io.projectreactor.tools:blockhound:1.0.6.RELEASE")

    // General Spring & Web
    kapt("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.session:spring-session-core")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.2")

    // UI
    implementation("org.springframework.boot:spring-boot-starter-mustache")
    implementation("org.webjars:webjars-locator:0.41")
    implementation("org.webjars.npm:bulma:0.9.2")
    implementation("org.webjars.npm:fortawesome__fontawesome-free:5.15.4")

    // RSS
    val romeToolsVersion = "1.16.0"
    implementation("com.rometools:rome:$romeToolsVersion")
    implementation("com.rometools:rome-opml:$romeToolsVersion")

    // Database
    implementation("org.springframework:spring-r2dbc")
    implementation("io.r2dbc:r2dbc-pool")
    implementation("io.r2dbc:r2dbc-h2")
    implementation("com.h2database:h2")
    implementation("io.r2dbc:r2dbc-postgresql")

    runtimeOnly("org.springframework:spring-jdbc")
    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")

    // Misc
    implementation("io.github.microutils:kotlin-logging:2.0.11")

    // Dev Dependencies
    // developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-starter-actuator")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // testImplementation("org.springframework.security:spring-security-test")

    testImplementation(kotlin("test-common"))
    testImplementation(kotlin("test-annotations-common"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    val testContainersVersion = "1.16.0"
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinCoroutinesVersion")

    val striktVersion = "0.32.0"
    testImplementation("io.strikt:strikt-core:$striktVersion")
    testImplementation("io.strikt:strikt-jvm:$striktVersion")
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