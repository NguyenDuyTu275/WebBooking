import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Same backend the Vercel site proxies to. Override with api.baseUrl in local.properties,
// e.g. api.baseUrl=http://10.0.2.2:8080 to reach a backend running on this PC from the emulator.
val defaultApiBaseUrl = "https://ript1307-03-2026-nhom2-kthp-production.up.railway.app"

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val apiBaseUrl: String = localProperties.getProperty("api.baseUrl", defaultApiBaseUrl).trimEnd('/')

// Web OAuth client the backend verifies Google ID tokens against (google.client-id); a client id is public.
val defaultGoogleWebClientId = "629111799618-99e15l0prujq2bqif30mtfr6eevlqk7s.apps.googleusercontent.com"
val googleWebClientId: String = localProperties.getProperty("google.webClientId", defaultGoogleWebClientId)

android {
    namespace = "com.nhom2.booking"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nhom2.booking"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl/\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The language can be switched inside the app, so every language must ship in the install.
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
