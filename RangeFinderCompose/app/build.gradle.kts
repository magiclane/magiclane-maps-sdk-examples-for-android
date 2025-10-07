plugins {
    id("com.magiclane.examples.sdk.gradle.application")
    alias(shared.plugins.compose)
}

android {
    namespace = "com.magiclane.sdk.examples.rangefinder_compose"

    compileSdk = shared.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        applicationId = "com.magiclane.sdk.examples.rangefinder_compose"

        minSdk = shared.versions.minSdkVersion.get().toInt()
        targetSdk = shared.versions.targetSdkVersion.get().toInt()
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(shared.magiclane.maps.kotlin)

    val composeBom = platform(shared.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(shared.androidx.core.ktx)
    implementation(shared.androidx.lifecycle.runtime.ktx)

    implementation(shared.androidx.activity.compose)
    implementation(shared.androidx.compose.ui)
    implementation(shared.androidx.compose.ui.graphics)
    implementation(shared.androidx.compose.ui.tooling.preview)
    implementation(shared.androidx.compose.material3)
    implementation(shared.androidx.lifecycle.viewmodel.compose)

    testImplementation(shared.junit)
    androidTestImplementation(shared.androidx.junit)
    androidTestImplementation(shared.androidx.espresso.core)
    androidTestImplementation(platform(shared.androidx.compose.bom))
    androidTestImplementation(shared.androidx.compose.ui.test.junit4)
    debugImplementation(shared.androidx.compose.ui.tooling)
    debugImplementation(shared.androidx.compose.ui.test.manifest)
}
