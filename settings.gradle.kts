plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "AiChallenge"

// JVM модуль (существующий)
include(":jvm")

// Android модули
include(":app")
include(":chatAI")

project(":jvm").projectDir = file("jvm")
project(":app").projectDir = file("android/app")
project(":chatAI").projectDir = file("android/chatAI")
