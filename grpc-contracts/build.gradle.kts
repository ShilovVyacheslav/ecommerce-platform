import com.google.protobuf.gradle.id

plugins {
    id("java-library")
    id("com.google.protobuf") version "0.10.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.shilov.ecommerce"
version = "0.0.1-SNAPSHOT"
description = "grpc-contracts"

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
        mavenBom("org.springframework.grpc:spring-grpc-dependencies:0.12.0")
    }
}

dependencies {
    api("io.grpc:grpc-stub")
    api("io.grpc:grpc-protobuf")
    implementation("com.google.protobuf:protobuf-java")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val grpcVersion = "1.74.0"
val protocVersion = "4.31.1"

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protocVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc") {
                    option("@generated=omit")
                }
            }
        }
    }
}
