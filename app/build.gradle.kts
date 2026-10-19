plugins { id("com.android.application") }

android {
    namespace = "fr.damien.m832print"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.damien.m832print"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val releaseKeystore = System.getenv("M832_RELEASE_KEYSTORE")
    val releaseStorePassword = System.getenv("M832_RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("M832_RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("M832_RELEASE_KEY_PASSWORD")

    if (listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
