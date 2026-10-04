plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.longlifeio.fineprint"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.longlifeio.fineprint"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    // Reads dex files on-device. Apache 2.0; the only non-AndroidX runtime dependency.
    implementation(libs.smali.dexlib2) {
        // Declared by dexlib2 3.0.10 but never used by it: none of its classes reference Guava.
        exclude(group = "com.google.guava", module = "guava")
    }

    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (android.jar only has stubs).
    testImplementation(libs.org.json)
}

tasks.withType<Test>().configureEach {
    // The tests read the bundled signatures from disk; rerun them whenever the asset changes.
    inputs.file("src/main/assets/trackers.json").withPathSensitivity(PathSensitivity.RELATIVE)
}
