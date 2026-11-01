plugins {
    id("com.android.application")
}

android {
    namespace = "jp.hakusyaku.simkirikae"
    compileSdk = 37
    enableKotlin = false

    defaultConfig {
        applicationId = "jp.hakusyaku.simkirikae"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        // 毎回同じ鍵で署名して、上書きインストールできるようにする（鍵はリポジトリ内）
        create("fixed") {
            storeFile = file("../keystore/simkirikae.jks")
            storePassword = "simkirikae"
            keyAlias = "simkirikae"
            keyPassword = "simkirikae"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("fixed")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.10.0")
    compileOnly("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}
