/* Top-level build file where you can add configuration options common to all sub-projects/modules. */

plugins {
    alias(shared.plugins.android.application) apply false
    alias(shared.plugins.kotlin.android) apply false
    alias(shared.plugins.detekt) apply false
    alias(shared.plugins.ktlint) apply false
    alias(shared.plugins.compose) apply false
}

rootProject.layout.buildDirectory = file("build")
subprojects {
    layout.buildDirectory = file("${rootProject.layout.buildDirectory.get()}/${name}")
}
