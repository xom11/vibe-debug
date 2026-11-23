import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val isFdroidBuild = providers.gradleProperty("fdroidBuild").orNull?.toBoolean() == true
val keystoreProps = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (!isFdroidBuild && propsFile.isFile) {
        propsFile.inputStream().use { load(it) }
    }
}
val releaseStorePath = if (isFdroidBuild) null else {
    keystoreProps.getProperty("storeFile") ?: System.getenv("SIGNING_STORE_FILE")
}
val releaseStorePassword = if (isFdroidBuild) null else {
    keystoreProps.getProperty("storePassword") ?: System.getenv("SIGNING_STORE_PASSWORD")
}
val releaseKeyAlias = if (isFdroidBuild) null else {
    keystoreProps.getProperty("keyAlias") ?: System.getenv("SIGNING_KEY_ALIAS")
}
val releaseKeyPassword = if (isFdroidBuild) null else {
    keystoreProps.getProperty("keyPassword") ?: System.getenv("SIGNING_KEY_PASSWORD")
}
val releaseStoreFile = releaseStorePath?.takeIf { it.isNotBlank() }?.let { path ->
    val candidate = File(path)
    if (candidate.isAbsolute) candidate else rootProject.file(path)
}
val hasReleaseSigning = releaseStoreFile?.isFile == true &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "io.github.xom11.vibedebug"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.xom11.vibedebug"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "2.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo {
                include = false
            }
            if (hasReleaseSigning) {
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperty(
                    "robolectric.dependency.repo.url",
                    System.getProperty(
                        "robolectric.dependency.repo.url",
                        "https://maven-central.storage-download.googleapis.com/maven2"
                    )
                )
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
