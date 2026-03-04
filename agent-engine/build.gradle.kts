plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":gateway-core"))
    implementation(project(":session-store"))
    implementation(project(":tools:tools-api"))
    implementation(project(":skills"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.bundles.koog)
    implementation(libs.bundles.koog.executors)

    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koog.agents.test)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}
