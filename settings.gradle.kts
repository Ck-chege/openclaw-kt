rootProject.name = "openclaw-kt"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

// Core modules
include(":protocol")
include(":gateway-core")
include(":agent-engine")
include(":session-store")

// Channel modules
include(":channels:channel-api")
include(":channels:telegram")
include(":channels:discord")
include(":channels:slack")
include(":channels:webchat")

// Tool modules
include(":tools:tools-api")
include(":tools:exec")
include(":tools:filesystem")
include(":tools:web")

// Feature modules
include(":skills")
include(":plugins")
include(":cron")
include(":orchestrator")
include(":memory")
include(":cli")

// Application
include(":app")
