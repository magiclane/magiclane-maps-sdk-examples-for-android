plugins {
    id("com.magiclane.sdk.examples.gradle.application")
}

android {
    namespace = "com.magiclane.sdk.examples.routeterrainprofile"

    compileSdk = shared.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        applicationId = "com.magiclane.sdk.examples.routeterrainprofile"

        minSdk = shared.versions.minSdkVersion.get().toInt()
        targetSdk = shared.versions.targetSdkVersion.get().toInt()
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(shared.magiclane.maps.kotlin)

    implementation(shared.androidx.core.ktx)
    implementation(shared.androidx.appcompat)
    implementation(shared.androidx.espresso.idlingresource)
    implementation(shared.androidx.swiperefreshlayout)
    implementation(shared.material)
    implementation(shared.sortabletableview)

    implementation("third-party:MPChartLib")

    testImplementation(shared.junit)
    androidTestImplementation(project(":build-testing"))
    androidTestImplementation(shared.androidx.junit)
    androidTestImplementation(shared.androidx.espresso.core)
    androidTestImplementation(shared.androidx.test.rules)
    androidTestImplementation(shared.androidx.test.runner)
}
