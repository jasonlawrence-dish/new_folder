import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// --- Version bump discipline -------------------------------------------------
// versionCode/versionName are read from version.properties and MUST be bumped
// on every change/build (per project standing rule). Run ./gradlew bumpVersion
// or edit version.properties before building a release.
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}
val appVersionCode = (versionProps.getProperty("VERSION_CODE") ?: "1").toInt()
val appVersionName = versionProps.getProperty("VERSION_NAME") ?: "1.0.0"

// --- Signing -----------------------------------------------------------------
// Keystore credentials live in keystore.properties (git-ignored) and the
// enterprise password manager. NEVER commit the keystore or its passwords.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.dish.newfolderview"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dish.newfolderview"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}

// Convenience task: ./gradlew bumpVersion  (increments VERSION_CODE, keeps name)
tasks.register("bumpVersion") {
    doLast {
        val code = (versionProps.getProperty("VERSION_CODE") ?: "1").toInt() + 1
        versionProps.setProperty("VERSION_CODE", code.toString())
        versionPropsFile.outputStream().use { versionProps.store(it, "Auto-bumped by bumpVersion") }
        println("Bumped VERSION_CODE to $code (VERSION_NAME=$appVersionName)")
    }
}
