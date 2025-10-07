/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.api.artifacts.VersionCatalogsExtension

class DetektConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {

        with(target) {
			val libs = extensions.getByType<VersionCatalogsExtension>().named("shared")

            pluginManager.apply(libs.findPlugin("detekt").get().get().pluginId)

            tasks.withType<Detekt>().configureEach {
                jvmTarget = JavaVersion.VERSION_17.toString()
            }
            tasks.withType<DetektCreateBaselineTask>().configureEach {
                jvmTarget = JavaVersion.VERSION_17.toString()
            }
  
            extensions.getByType<DetektExtension>().apply {
                buildUponDefaultConfig = true
                allRules = false
                autoCorrect = false
                parallel = true
                ignoreFailures = true

                config.setFrom(rootProject.files("../detekt.yml"))
                source.from(files("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin", "build.gradle.kts"))
            }

            tasks.withType<Detekt>().configureEach {
                reports {
                    html.required.set(true)
                    html.outputLocation.set(file("${project.layout.buildDirectory.get()}/reports/detekt/${project.name}_report.html"))
                    xml.required.set(false)
                    txt.required.set(false)
                    sarif.required.set(false)
                    md.required.set(false)
                }
                basePath = rootDir.absolutePath
            }

            tasks.named("check") {
                dependsOn(":app:detekt")
            }

            dependencies.apply {
                add("detektPlugins", libs.findLibrary("detekt-formatting").get())
            }
        }
    }
}
