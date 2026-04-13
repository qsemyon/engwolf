plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") // Оставляем KSP для Room
}

android {
    namespace = "com.example.engwolf"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.engwolf"
        minSdk = 36 // Рекомендую снизить до 26, если хочешь, чтобы работало не только на эмуляторе 2026 года
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Это заменяет старые compileOptions и фиксит ошибку JVM Target
    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")

    "ksp"("androidx.room:room-compiler:2.8.4")
}