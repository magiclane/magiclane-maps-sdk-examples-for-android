/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask

class GradleKtlintConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {

        with(target) {
            val libs = extensions.getByType<VersionCatalogsExtension>().named("shared")

            pluginManager.apply(libs.findPlugin("ktlint").get().get().pluginId)

            configure<KtlintExtension> {
                version.set(KTLINT_VERSION)
                debug.set(true)
                verbose.set(true)
                outputToConsole.set(true)
                coloredOutput.set(true)
                android.set(true)
                outputColorName.set("RED")
                ignoreFailures.set(true)
                enableExperimentalRules.set(false)

                reporters {
                    reporter(ReporterType.HTML)
                }
                filter {
                    exclude("**/generated/**")
                    include("**/kotlin/**")
                }
            }

            tasks.named("check") {
                dependsOn(":app:ktlintCheck")
            }

            tasks.withType<GenerateReportsTask> {
                reportsOutputDirectory.set(project.layout.projectDirectory.dir("${project.layout.buildDirectory.get()}/reports/ktlint"))
            }
        }
    }

    companion object {
        // The version of ktlint used by Ktlint Gradle
        // https://github.com/pinterest/ktlint/releases
        const val KTLINT_VERSION = "1.2.1"
    }
}
