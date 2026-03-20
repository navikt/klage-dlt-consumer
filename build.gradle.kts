import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val logstashVersion = "9.0"
val simpleSlackPosterVersion = "1.0.0"

plugins {
    val kotlinVersion = "2.3.20"
    id("org.springframework.boot") version "4.0.4"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    idea
}

apply(plugin = "io.spring.dependency-management")

// CVE GHSA-72hv-8253-57qq: jackson-core async parser DoS. Remove when Spring has updated.
extra["jackson-2-bom.version"] = "2.21.1"
extra["jackson-bom.version"] = "3.1.0"

java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

// Remove when Spring has updated.
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.lz4:lz4-java"))
            .using(module("at.yawk.lz4:lz4-java:1.10.4"))
            .because("CVE-2025-12183 and CVE-2025-66566: org.lz4:lz4-java is archived, new releases under at.yawk.lz4")
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")

    implementation("ch.qos.logback:logback-classic")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.springframework.kafka:spring-kafka")

    implementation("no.nav.slackposter:simple-slack-poster:$simpleSlackPosterVersion")
}

idea {
    module {
        isDownloadJavadoc = true
    }
}

java.sourceCompatibility = JavaVersion.VERSION_21

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    this.archiveFileName.set("app.jar")
}