plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":protocol"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)

    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.kotest)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}
