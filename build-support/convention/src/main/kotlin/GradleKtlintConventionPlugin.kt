/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.gradle.api.artifacts.VersionCatalogsExtension

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
                 android.set(false)
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
