plugins {
    java
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.shilov.ecommerce"
version = "0.0.1-SNAPSHOT"
description = "ecommerce-platform"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.5")
    }
}

dependencies {
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.slf4j:slf4j-api")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}