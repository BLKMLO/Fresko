plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fresko.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fresko.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.7.1"
    }

    // Signature : clé fournie par la CI (secrets GitHub) si présente, sinon clé debug.
    val keystorePath = System.getenv("FRESKO_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("FRESKO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FRESKO_KEY_ALIAS")
                keyPassword = System.getenv("FRESKO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":feature:checker"))
    implementation(project(":sources:asura"))
    implementation(project(":sources:demonic"))
    implementation(project(":sources:natomanga"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Captures d'écran (Robolectric, rendu natif) : ./gradlew :app:testDebugUnitTest -Pscreenshots
// Images écrites dans app/build/screenshots/. Exclues par défaut (téléchargement lourd).
tasks.withType<Test>().configureEach {
    // Robolectric télécharge Android depuis ce miroir de Maven Central (Maven Central
    // lui-même répond 429 dans l'environnement de dev ; le miroir marche aussi en CI).
    systemProperty(
        "robolectric.dependency.repo.url",
        "https://maven-central.storage-download.googleapis.com/maven2",
    )
    if (project.hasProperty("screenshots")) {
        systemProperty("fresko.screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.path)
    } else {
        exclude("**/screenshots/**")
    }
}
