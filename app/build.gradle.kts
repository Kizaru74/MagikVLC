plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.magikvlc.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.magikvlc.app"
        minSdk = 21
        targetSdk = 36
        versionCode = 8
        versionName = "1.5.0"

        buildConfigField("String", "GIT_SHA", "\"${gitSha()}\"")
        buildConfigField("String", "GIT_COUNT", "\"${gitCount()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firmado con la clave debug: solo para comparar rendimiento
            // release vs debug en el mismo dispositivo.
            signingConfig = signingConfigs.getByName("debug")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.videolan.android:libvlc-all:3.6.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation(kotlin("test"))
}

fun gitSha(): String = try {
    val proc = Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short", "HEAD"))
    proc.inputStream.bufferedReader().readText().trim()
} catch (_: Exception) { "unknown" }

fun gitCount(): String = try {
    val proc = Runtime.getRuntime().exec(arrayOf("git", "rev-list", "--count", "HEAD"))
    proc.inputStream.bufferedReader().readText().trim()
} catch (_: Exception) { "0" }
