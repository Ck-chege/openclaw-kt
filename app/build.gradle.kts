plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("com.openclaw.MainKt")
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":gateway-core"))
    implementation(project(":agent-engine"))
    implementation(project(":session-store"))
    implementation(project(":channels:channel-api"))
    implementation(project(":channels:webchat"))
    implementation(project(":tools:tools-api"))
    implementation(project(":tools:exec"))
    implementation(project(":tools:filesystem"))
    implementation(project(":tools:web"))
    implementation(project(":tools:memory-tools"))
    implementation(project(":skills"))
    implementation(project(":plugins"))
    implementation(project(":cron"))
    implementation(project(":orchestrator"))
    implementation(project(":memory"))
    implementation(project(":cli"))

    implementation(libs.bundles.ktor.server)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.koog)
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.kotest)
}
