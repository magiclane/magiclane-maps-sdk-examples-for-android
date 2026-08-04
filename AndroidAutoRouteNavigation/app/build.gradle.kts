plugins {
    id("com.magiclane.sdk.examples.gradle.application")
}

android {
    namespace = "com.magiclane.sdk.examples.androidautoroutenavigation"

    compileSdk = shared.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        applicationId = "com.magiclane.sdk.examples.androidautoroutenavigation"

        // Android Auto is only compatible with phones running Android 6.0 (API level 23) or higher
        minSdk = 23
        targetSdk = shared.versions.targetSdkVersion.get().toInt()
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(shared.magiclane.maps.kotlin)

    implementation(shared.androidx.core.ktx)
    implementation(shared.androidx.car)
    implementation(shared.androidx.appcompat)
    implementation(shared.androidx.constraintlayout)
    implementation(shared.material)

    testImplementation(shared.junit)
    androidTestImplementation(project(":build-testing"))
    androidTestImplementation(shared.androidx.junit)
    androidTestImplementation(shared.androidx.espresso.core)
    androidTestImplementation(shared.androidx.test.rules)
    androidTestImplementation(shared.androidx.test.runner)
}
