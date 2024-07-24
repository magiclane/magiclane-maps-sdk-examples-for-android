plugins {
    id("com.magiclane.examples.sdk.gradle.application")
}

android {
    namespace = "com.magiclane.sdk.examples.multiplesurfacesinfragment"

    compileSdk = shared.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        applicationId = "com.magiclane.sdk.examples.multiplesurfacesinfragment"

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
    implementation(shared.androidx.appcompat)
    implementation(shared.androidx.activity.ktx)
    implementation(shared.androidx.navigation.fragment.ktx)
    implementation(shared.androidx.navigation.ui.ktx)   
    implementation(shared.material)

    testImplementation(shared.junit)
    androidTestImplementation(shared.androidx.junit)
    androidTestImplementation(shared.androidx.espresso.core)
    androidTestImplementation(shared.androidx.espresso.idlingresource)
    androidTestImplementation(shared.androidx.test.rules)
    androidTestImplementation(shared.androidx.test.runner)
}
