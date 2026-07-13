plugins {
    kotlin("jvm") version "2.1.20"
    id("com.google.protobuf") version "0.9.4"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

val ktorVersion = "3.1.3"
val divanVersion = "32.57.0"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    // Ktor client (Open-Meteo)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    // DivKit JSON-builder (divan)
    implementation("com.yandex.div:kotlin-json-builder:$divanVersion")

    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")

    // Protobuf runtime
    implementation("com.google.protobuf:protobuf-kotlin:4.29.3")
    implementation("com.google.protobuf:protobuf-java-util:4.29.3")

    // Test
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(kotlin("test"))
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") { }
                register("kotlin") { }
            }
        }
    }
}

application {
    mainClass.set("workshop.ApplicationKt")
}

tasks.test {
    useJUnit()
    systemProperty("weather.source", "mock")
}
