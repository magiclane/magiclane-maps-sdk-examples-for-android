@file:Suppress("UnstableApiUsage")

import java.net.URL

// This will find your GEM_SDK_REGISTRY_TOKEN in ~/.gradle/gradle.properties
val GEM_SDK_REGISTRY_TOKEN: String? by settings

pluginManagement {
	includeBuild("../build-support")
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
    val gemSdkRegistryToken = GEM_SDK_REGISTRY_TOKEN
        ?: System.getenv("GEM_SDK_REGISTRY_TOKEN").takeIf { !it.isNullOrBlank() }
    if(gemSdkRegistryToken.isNullOrBlank()) {
        throw IllegalStateException(
            """
               ------------------------------------------------------------------
               'GEM_SDK_REGISTRY_TOKEN' must to be defined either in gradle 
               properties file or as an env. variable.
               Check ${URL("https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html")} 
               how to create a personal access token.
               ------------------------------------------------------------------
            """.trimIndent())
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://issuetracker.magiclane.com/api/v4/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Private-Token"
                value = gemSdkRegistryToken
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
    versionCatalogs {
        create("shared") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "RangeFinder"
include(":app")
