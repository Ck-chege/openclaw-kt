plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":tools:tools-api"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.koog)

    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotlinx.coroutines.test)
}
