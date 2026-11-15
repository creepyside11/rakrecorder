plugins { id("com.android.application") }
android {
    namespace = "dev.rakrecorder"
    compileSdk = 35
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    defaultConfig { applicationId = "dev.rakrecorder"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1.0-alpha" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { testImplementation("junit:junit:4.13.2") }
