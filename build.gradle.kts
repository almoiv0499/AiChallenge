plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

val ktorVersion = "2.3.12"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    
    // Ktor Client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")
    
    // SQLite
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("org.example.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}

// Задача для запуска индексации документов
tasks.register<JavaExec>("runIndexDocs") {
    group = "application"
    description = "Индексирует документ docs/docs.md с эмбеддингами"
    mainClass.set("org.example.embedding.IndexDocumentMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Задача для тестирования поиска
tasks.register<JavaExec>("runSearchTest") {
    group = "application"
    description = "Тестирует поиск по индексу документов"
    mainClass.set("org.example.embedding.SearchTestMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Задача для тестирования Git MCP Server
tasks.register<JavaExec>("runGitMcpTest") {
    group = "application"
    description = "Тестирует Git MCP Server для Code Review Pipeline"
    mainClass.set("org.example.mcp.server.GitMcpServerTestKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Задача для тестирования Code Review Pipeline
tasks.register<JavaExec>("runCodeReviewTest") {
    group = "application"
    description = "Тестирует полный pipeline Code Review с LLM"
    mainClass.set("org.example.review.CodeReviewTestKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Задача для запуска AI Code Review в CI
tasks.register<JavaExec>("runCodeReview") {
    group = "ci"
    description = "Запускает AI Code Review для PR (используется в GitHub Actions)"
    mainClass.set("org.example.review.CodeReviewRunnerKt")
    classpath = sourceSets["main"].runtimeClasspath
    
    // Передаём аргументы из командной строки
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split(" ")
    }
}