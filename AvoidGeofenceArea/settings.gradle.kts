@file:Suppress("UnstableApiUsage")

// This will find your gemSdkLocalMavenPath in ~/.gradle/gradle.properties
val gemSdkLocalMavenPath: String? by settings

includeBuild("../build-support")

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
    }
}
dependencyResolutionManagement {
    val localMavenPath = gemSdkLocalMavenPath
        ?: System.getenv("GEM_SDK_LOCAL_MAVEN_PATH").takeIf { !it.isNullOrBlank() }

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if(!localMavenPath.isNullOrBlank()) {
            maven { url = uri(localMavenPath) }
        } else {
            maven {
                url = uri("https://developer.magiclane.com/packages/android")
            }
        }
    }
    versionCatalogs {
        create("shared") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "AvoidGeofenceArea"
include(":app")
