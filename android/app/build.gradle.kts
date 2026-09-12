import java.util.Properties

plugins {
    id("com.android.application")
}

// 署名鍵は環境変数で渡す（CI では GitHub Secrets、ローカルでは ~/.android/linkshare-release.env を source する）。
// 渡されなければデバッグ鍵で署名する。デバッグ鍵は端末ごとに異なるので、配布用には必ず環境変数を設定する。
val keystoreFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.isFile && it.length() > 0 }

android {
    namespace = "io.github.shimajiro4892.linkshare"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.shimajiro4892.linkshare"
        minSdk = 26
        targetSdk = 36
        // GitHub Actions では実行番号を versionCode にして、常に上書きインストールできるようにする。
        versionCode = (System.getenv("APK_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.$versionCode"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
