/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice
import com.android.build.api.dsl.CommonExtension
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.invoke

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import java.io.File
import java.util.Properties

class ApplicationModulePlugin : Plugin<Project> {

    override fun apply(target: Project) {

        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
                apply("com.magiclane.examples.sdk.gradle.detekt")
                apply("com.magiclane.examples.sdk.gradle.ktlint")
            }

            extensions.configure<ApplicationExtension> {
                defaultConfig {
                    versionCode = 1
                    versionName = "1.0"

					val token = System.getenv("GEM_TOKEN").takeIf { !it.isNullOrBlank() } ?: ""
					if(token.isEmpty()) {
                        logger.warn(
                            """
                               ------------------------------------------------------------------
                               No token set. 
                               You can still test your apps, but a watermark will be displayed, 
                               and all the online services including mapping, searching, 
                               routing, etc. will slow down after a few minutes.
                               ------------------------------------------------------------------
                            """.trimIndent())
                    }
                    manifestPlaceholders["GEM_TOKEN"] = token

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
                        )
                        // NOTE: Signing with the debug keys for now.
                        // Add your own signing config for the release build.
                        signingConfig = signingConfigs.getByName("debug")
                    }
                }

                packaging {
                    jniLibs {
                        keepDebugSymbols += "**/libGEM.so"
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }

                tasks.withType<KotlinCompile>().configureEach {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_11)
                    }
                }

                configureGradleManagedDevices(this)

                lint {
                    abortOnError = false
                    checkReleaseBuilds = true
                    checkAllWarnings = true
                    ignoreTestSources = true
                    warningsAsErrors = false
                    explainIssues = true
                    textReport = false
                    xmlReport = false
                    htmlReport = true
                    sarifReport = false
                }
            }

            val localPropFile = File(rootProject.projectDir, "../local.properties")
            if (localPropFile.exists()) {
                // Check if path to direct downloaded Maps SDK for Android exists
				val localProp = Properties()
				localPropFile.inputStream().use {
					localProp.load(it)
				}
				val gemSDKPath = localProp.getProperty("aarPath.dir", null)
				if (!gemSDKPath.isNullOrEmpty()) {
                    logger.warn(
                        """
                            ------------------------------------------------------------------
                            Using AAR ('${gemSDKPath}') dependency
                            ------------------------------------------------------------------
                        """.trimIndent())
					dependencies {
						add("implementation", fileTree(mapOf("dir" to gemSDKPath, "include" to listOf("*.jar", "*.aar"))))
					}
                    configurations.forEach { it.exclude("com.magiclane", "maps-kotlin") }
                }
            } else {
				// Check if direct downloaded Maps SDK for Android exists in libs folder
				val libsDir = File(rootProject.projectDir, "app/libs")
				val regex = Regex("MAGICLANE-(ADAS|MAPS)-SDK-.*\\.aar")
				val aarFile = libsDir.listFiles()?.find { it.name.matches(regex) }
				if (aarFile != null) {
                    logger.warn(
                        """
                            ------------------------------------------------------------------
                            Using AAR ('${aarFile.name}') dependency
                            ------------------------------------------------------------------
                        """.trimIndent())
                    dependencies {
                        add("implementation", fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
                    }
                    configurations.forEach { it.exclude("com.magiclane", "maps-kotlin") }
				}
			}
        }
    }

    private fun Project.configureGradleManagedDevices(commonExtension: CommonExtension<*, *, *, *, *, *>) {
        val deviceConfigs = listOf(
            DeviceConfig("Pixel 8", 34, "google")
        )

        commonExtension.testOptions {
            animationsDisabled = true
            unitTests {
                isIncludeAndroidResources = true
            }
            managedDevices {
                allDevices {
                    deviceConfigs.forEach { deviceConfig ->
                        maybeCreate(deviceConfig.taskName, ManagedVirtualDevice::class.java).apply {
                            device = deviceConfig.device
                            apiLevel = deviceConfig.apiLevel
                            systemImageSource = deviceConfig.systemImageSource
                        }
                    }
                }
                groups {
                    maybeCreate("ci").apply {
                        deviceConfigs.forEach { deviceConfig ->
                            targetDevices.add(allDevices[deviceConfig.taskName])
                        }
                    }
                }
            }
        }
    }   
}

private data class DeviceConfig(
    val device: String,
    val apiLevel: Int,
    val systemImageSource: String,
) {
    val taskName = buildString {
        append(device.lowercase().replace(" ", "_"))
        append("api")
        append(apiLevel.toString())
        append(systemImageSource.replace("-", ""))
    }
}
