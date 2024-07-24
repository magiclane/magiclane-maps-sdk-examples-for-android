plugins {
    id("com.magiclane.examples.sdk.gradle.application")
}

android {
    namespace = "com.magiclane.sdk.examples.projection"

    compileSdk = shared.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        applicationId = "com.magiclane.sdk.examples.projection"

        minSdk = shared.versions.minSdkVersion.get().toInt()
        targetSdk = shared.versions.targetSdkVersion.get().toInt()
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(shared.magiclane.maps.kotlin)

    implementation(shared.androidx.core.ktx)
    implementation(shared.androidx.activity.ktx)
    implementation(shared.androidx.appcompat)
    implementation(shared.androidx.constraintlayout)
    implementation(shared.androidx.recyclerview)
    implementation(shared.androidx.espresso.idlingresource)
    implementation(shared.material)

    testImplementation(shared.junit)
    androidTestImplementation(shared.androidx.junit)
    androidTestImplementation(shared.androidx.espresso.core)
    androidTestImplementation(shared.androidx.test.rules)
    androidTestImplementation(shared.androidx.test.runner)
}
