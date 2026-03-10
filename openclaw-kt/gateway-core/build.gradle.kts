plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":session-store"))
    implementation(project(":skills"))

    implementation(libs.bundles.ktor.server)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.typesafe.config)

    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.bundles.ktor.server.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.cio)
    testImplementation("io.ktor:ktor-client-websockets:${libs.versions.ktor.get()}")
}
