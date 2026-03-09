plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":gateway-core"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.bundles.koog)
    implementation(libs.koog.agents.mcp)
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.kotest)
}
