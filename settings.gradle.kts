pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        plugins {
            id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
            id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
            id("com.android.application") version "8.11.1" apply false
            id("com.android.library") version "8.11.1" apply false
            id("com.google.devtools.ksp") version "1.9.22-1.0.17"
            id("kotlin-kapt") version "2.2.0" apply false
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ugh"
include(":app")
