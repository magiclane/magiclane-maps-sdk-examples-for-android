/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
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
                apply("kotlin-kapt")
                apply("com.magiclane.sdk.examples.gradle.detekt")
                apply("com.magiclane.sdk.examples.gradle.ktlint")
            }

            project.extensions.extraProperties["kapt.incremental.apt"] = "false"
            project.extensions.extraProperties["kapt.correctErrorTypes"] = "true"
            project.extensions.extraProperties["kapt.use.worker.api"] = "true"

            extensions.configure<ApplicationExtension> {
                defaultConfig {
                    versionCode = 1
                    versionName = "1.0"

                    val token = project.findProperty("GEM_TOKEN") as String?
                        ?: System.getenv("GEM_TOKEN")?.takeIf { it.isNotBlank() }
                        ?: ""

                    if (token.isEmpty()) {
                        tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("install") }.configureEach {
                            doFirst {
                                logger.warn(
                                    """
                                    |
                                    |------------------------------------------------------------------
                                    |No token set.
                                    |You can still test your apps, but a watermark will be displayed,
                                    |and all the online services including mapping, searching,
                                    |routing, etc. will slow down after a few minutes.
                                    |
                                    |Set it in one of these locations:
                                    |- Environment variable: GEM_TOKEN=your_token
                                    |- Global gradle.properties:
                                    |  * Linux/Mac: ~/.gradle/gradle.properties
                                    |  * Windows: %USERPROFILE%\.gradle\gradle.properties
                                    |- Project gradle.properties
                                    |------------------------------------------------------------------
                                    |
                                    """.trimMargin()
                                )
                            }
                        }
                    }

                    manifestPlaceholders["GEM_TOKEN"] = token

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                buildFeatures {
                    dataBinding = true
                }

                val generatedKotlinDir = File(project.layout.buildDirectory.asFile.get(), "generated/source/bindingAdapters/kotlin")
                val generatedResDir = File(project.layout.buildDirectory.asFile.get(), "generated/res/bindingAdapters")
                sourceSets.getByName("main").kotlin.srcDir(generatedKotlinDir)
                sourceSets.getByName("main").res.srcDir(generatedResDir)

                project.tasks.register("generateBindingAdapters") {
                    val kotlinOutputDir = generatedKotlinDir
                    val resOutputDir = generatedResDir
                    val namespace = project.extensions.getByType(ApplicationExtension::class.java).namespace
                        ?: "com.magiclane.sdk.examples"

                    outputs.dir(kotlinOutputDir)
                    outputs.dir(resOutputDir)
                    doLast {
                        val packageDir = File(kotlinOutputDir, namespace.replace('.', '/'))
                        packageDir.mkdirs()
                        File(packageDir, "BindingAdapters.kt").writeText(generateBindingAdaptersSource(namespace))

                        val valuesDir = File(resOutputDir, "values")
                        valuesDir.mkdirs()
                        File(valuesDir, "binding_adapters_attrs.xml").writeText(generateAttrsXml())
                    }
                }

                project.afterEvaluate {
                    tasks.matching {
                        it.name.startsWith("compile") ||
                        it.name.startsWith("kapt") ||
                        it.name.startsWith("generate") ||
                        it.name.startsWith("merge") ||
                        it.name.startsWith("process") ||
                        it.name.startsWith("map") ||
                        it.name.startsWith("extract") ||
                        it.name.startsWith("parse") ||
                        it.name.startsWith("dataBinding")
                    }.configureEach {
                        if (name != "generateBindingAdapters") {
                            dependsOn("generateBindingAdapters")
                        }
                    }
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
                        // libGEM.so is the release lib, libGEM_d.so the debug one - both
                        // must keep their symbols or native breakpoints in the SDK's JNI
                        // sources cannot bind.
                        keepDebugSymbols += "**/libGEM.so"
                        keepDebugSymbols += "**/libGEM_d.so"
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
                    excludeMavenSdkArtifacts(File(gemSDKPath))
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
                    excludeMavenSdkArtifacts(libsDir)
                }
            }
        }
    }

    // Exclude the Maven coordinates of the SDK artifacts supplied as local AARs.
    private fun Project.excludeMavenSdkArtifacts(aarDir: File) {
        configurations.forEach { it.exclude("com.magiclane", "maps-kotlin") }

        val aarNames = aarDir.listFiles()?.map { it.name }.orEmpty()
        if (aarNames.any { it.matches(Regex("MAGICLANE-MAPS-COMPOSE-\\d.*\\.aar")) }) {
            configurations.forEach { it.exclude("com.magiclane", "maps-compose") }
        }
        if (aarNames.any { it.matches(Regex("MAGICLANE-MAPS-COMPOSE-COMPONENTS-.*\\.aar")) }) {
            configurations.forEach { it.exclude("com.magiclane", "maps-compose-components") }
        }
    }

    private fun Project.configureGradleManagedDevices(commonExtension: CommonExtension<*, *, *, *, *, *>) {
        val deviceConfigs = listOf(
            DeviceConfig("Pixel 9", 36, "google")
        )

        commonExtension.testOptions {
            animationsDisabled = true
            unitTests {
                isIncludeAndroidResources = true
            }
            managedDevices {
                allDevices {
                    deviceConfigs.forEach { deviceConfig ->
                        register(deviceConfig.taskName, ManagedVirtualDevice::class.java) {
                            device = deviceConfig.device
                            apiLevel = deviceConfig.apiLevel
                            systemImageSource = deviceConfig.systemImageSource
                            pageAlignment = ManagedVirtualDevice.PageAlignment.FORCE_16KB_PAGES
                            require64Bit = true
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

    private fun generateBindingAdaptersSource(packageName: String): String = """
/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package $packageName

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMarginsRelative
import androidx.core.view.updatePadding
import androidx.databinding.BindingAdapter

private data class InitialPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)
private data class InitialMargins(val start: Int, val top: Int, val end: Int, val bottom: Int)

private fun View.initialPadding() = InitialPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)

private fun View.initialMargins(): InitialMargins {
    val lp = layoutParams as? MarginLayoutParams
    return InitialMargins(
        start = lp?.marginStart ?: 0,
        top = lp?.topMargin ?: 0,
        end = lp?.marginEnd ?: 0,
        bottom = lp?.bottomMargin ?: 0
    )
}

private fun View.parseDimension(value: String): Float {
    if (value.isBlank()) return 0f
    return try {
        when {
            value.startsWith("@dimen/") -> {
                val resName = value.substringAfter("@dimen/")
                val resId = resources.getIdentifier(resName, "dimen", context.packageName)
                if (resId != 0) resources.getDimensionPixelSize(resId).toFloat() else 0f
            }
            value.endsWith("dp") -> {
                val num = value.removeSuffix("dp").toFloat()
                num * resources.displayMetrics.density
            }
            value.endsWith("px") -> value.removeSuffix("px").toFloat()
            else -> value.toFloat()
        }
    } catch (_: Exception) {
        0f
    }
}

/**
 * Applies system window insets (navigation bar, status bar, display cutout) as padding.
 */
@BindingAdapter(
    value = [
        "paddingLeftWithSystemWindowInsets",
        "paddingTopWithSystemWindowInsets",
        "paddingRightWithSystemWindowInsets",
        "paddingBottomWithSystemWindowInsets",
    ],
    requireAll = false
)
fun addSystemWindowInsetToPadding(
    view: View,
    leftPadding: Any?,
    topPadding: Any?,
    rightPadding: Any?,
    bottomPadding: Any?
) {
    fun Any.toPixels(): Float? = when (this) {
        is Number -> toFloat()
        is String -> view.parseDimension(this)
        else -> null
    }

    val leftPx = leftPadding?.toPixels()
    val topPx = topPadding?.toPixels()
    val rightPx = rightPadding?.toPixels()
    val bottomPx = bottomPadding?.toPixels()

    val initial = view.initialPadding()

    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val sys = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )

        v.updatePadding(
            left = initial.left + (leftPx?.toInt() ?: 0) + if (leftPx != null) sys.left else 0,
            top = initial.top + (topPx?.toInt() ?: 0) + if (topPx != null) sys.top else 0,
            right = initial.right + (rightPx?.toInt() ?: 0) + if (rightPx != null) sys.right else 0,
            bottom = initial.bottom + (bottomPx?.toInt() ?: 0) + if (bottomPx != null) sys.bottom else 0
        )

        insets
    }

    view.requestApplyInsetsWhenAttached()
}

/**
 * Applies system window insets (navigation bar, status bar, display cutout) as margin.
 */
@BindingAdapter(
    value = [
        "marginLeftWithSystemWindowInsets",
        "marginTopWithSystemWindowInsets",
        "marginRightWithSystemWindowInsets",
        "marginBottomWithSystemWindowInsets",
    ],
    requireAll = false,
)
fun addSystemWindowInsetToMargin(
    view: View,
    leftMargin: Any?,
    topMargin: Any?,
    rightMargin: Any?,
    bottomMargin: Any?
) {
    fun Any.toPixels(): Float? = when (this) {
        is Number -> toFloat()
        is String -> view.parseDimension(this)
        else -> null
    }

    val leftPx = leftMargin?.toPixels()
    val topPx = topMargin?.toPixels()
    val rightPx = rightMargin?.toPixels()
    val bottomPx = bottomMargin?.toPixels()

    val initial = view.initialMargins()

    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val sys = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )

        v.updateLayoutParams<MarginLayoutParams> {
            updateMarginsRelative(
                start = initial.start + (leftPx?.toInt() ?: 0) + if (leftPx != null) sys.left else 0,
                top = initial.top + (topPx?.toInt() ?: 0) + if (topPx != null) sys.top else 0,
                end = initial.end + (rightPx?.toInt() ?: 0) + if (rightPx != null) sys.right else 0,
                bottom = initial.bottom + (bottomPx?.toInt() ?: 0) + if (bottomPx != null) sys.bottom else 0
            )
        }

        insets
    }

    view.requestApplyInsetsWhenAttached()
}

fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                v.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}
""".trimIndent()

    private fun generateAttrsXml(): String = """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <declare-styleable name="SystemWindowInsetsBindingAdapters">
        <attr name="paddingLeftWithSystemWindowInsets" format="string" />
        <attr name="paddingTopWithSystemWindowInsets" format="string" />
        <attr name="paddingRightWithSystemWindowInsets" format="string" />
        <attr name="paddingBottomWithSystemWindowInsets" format="string" />
        <attr name="marginLeftWithSystemWindowInsets" format="string" />
        <attr name="marginTopWithSystemWindowInsets" format="string" />
        <attr name="marginRightWithSystemWindowInsets" format="string" />
        <attr name="marginBottomWithSystemWindowInsets" format="string" />
    </declare-styleable>
</resources>
""".trimIndent()
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
