plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":tools:tools-api"))
    implementation(project(":memory"))

    implementation(libs.bundles.koog)
    implementation(libs.bundles.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.0")
}
