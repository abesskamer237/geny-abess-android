plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "net.payool.genyabess"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.payool.genyabess"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("GENY_KEYSTORE_PATH")
            val storePass = System.getenv("GENY_KEYSTORE_PASSWORD")
            val alias = System.getenv("GENY_KEY_ALIAS")
            val keyPass = System.getenv("GENY_KEY_PASSWORD")
            if (!storePath.isNullOrBlank() && !storePass.isNullOrBlank() &&
                !alias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            // R8 obfuscation and shrinking raise reverse-engineering cost, but cannot make an APK impossible to inspect.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val hasSigning = !System.getenv("GENY_KEYSTORE_PATH").isNullOrBlank() &&
                !System.getenv("GENY_KEYSTORE_PASSWORD").isNullOrBlank() &&
                !System.getenv("GENY_KEY_ALIAS").isNullOrBlank() &&
                !System.getenv("GENY_KEY_PASSWORD").isNullOrBlank()
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
