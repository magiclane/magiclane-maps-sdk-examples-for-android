// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    id("base")
}

tasks.register("buildAll") {
    group = "build"

    dependsOn(gradle.includedBuilds.filter { it.name != "build-support" }.map { it.task(":app:assemble") })
}

tasks.register("runUnitTestsAll") {
    group = "verification"

    dependsOn(gradle.includedBuilds.filter { it.name != "build-support" }.map { it.task(":app:testDebugUnitTest") })
}

tasks.register("checkAll") {
    group = "verification"

    dependsOn(gradle.includedBuilds.filter { it.name != "build-support" }.map { it.task(":app:check") })
}
