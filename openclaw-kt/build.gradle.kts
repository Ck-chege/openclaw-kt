plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.shadow) apply false
}

allprojects {
    group = "com.kclaw"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    configurations.all {
        resolutionStrategy {
            // Koog 0.6.3 pulls kotlinx-datetime 0.7.1 which moves Instant/Clock to kotlin.time,
            // breaking Exposed's exposed-kotlin-datetime (expects kotlinx.datetime.Instant as a real class).
            // Force 0.6.2 until Exposed supports the 0.7.x typealias change.
            force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            force("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.2")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}
