plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

// Исключаем задачу test из конфигурации до ее создания
tasks.matching { it.name == "test" }.configureEach {
    enabled = false
}

group = "com.example.aiadvent"
version = "1.0.0"

dependencies {
    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    
    // Kotlinx Serialization для JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Ktor для HTTP клиента
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    
    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.13")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    
    // Тестовые зависимости (минимальные, чтобы Gradle мог создать задачу test)
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.example.aiadvent.mcp.McpServerKt")
}

tasks.jar {
    archiveBaseName.set("mcp-server")
    archiveVersion.set("1.0.0")
    manifest {
        attributes["Main-Class"] = "com.example.aiadvent.mcp.McpServerKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

// Отключаем задачи тестирования
tasks.named("check") {
    enabled = false
}

