plugins { id("com.android.application") }
android {
    namespace = "dev.rakrecorder"
    compileSdk = 35
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    defaultConfig { applicationId = "dev.rakrecorder"; minSdk = 29; targetSdk = 35; versionCode = 2; versionName = "0.2.0-alpha"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { testImplementation("junit:junit:4.13.2"); androidTestImplementation("androidx.test:runner:1.6.2"); androidTestImplementation("androidx.test.ext:junit:1.2.1"); androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0") }
