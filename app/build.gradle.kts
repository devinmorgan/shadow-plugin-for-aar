import com.example.ShadowAarDependenciesPlugin
import com.example.ShadowAarDependenciesPluginExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
apply<ShadowAarDependenciesPlugin>()



android {
    namespace = "com.example.shadowaardeps"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.shadowaardeps"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// use this extension to configure an AAR that you need to
// shadow in a new namespace
extensions.getByType(ShadowAarDependenciesPluginExtension::class.java).apply {
    targetAar.set("com.datadoghq:dd-sdk-android-logs:2.2.0")
    targetPackageName.set("com.datadog")
    destinationPackageName.set("com.example.shadowaardeps.datadog")
    subDependencies.set(
        mutableListOf(
            "androidx.annotation:annotation:1.1.0",
            "androidx.collection:collection:1.1.0",
            "androidx.work:work-runtime:2.8.1",
            "com.google.code.gson:gson:2.10.1",
            "com.lyft.kronos:kronos-android:0.0.1-alpha11",
            "com.squareup.okhttp3:okhttp:4.11.0",
            "org.jetbrains.kotlin:kotlin-stdlib:1.8.10"
        )
    )
}

dependencies {
    // this dependency can coexist with the Shadowed
    // dd-sdk-android-logs:2.2.0 above. If it were not
    // shadowed, then the build would fail
    implementation("com.datadoghq:dd-sdk-android:1.19.2")

    // dependencies included by default
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

