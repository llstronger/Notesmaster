plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "net.micode.notes"
    compileSdk = 36
//    {
//        version = release(36) {
//            minorApiLevel = 1
//        }
//    }

    defaultConfig {
        applicationId = "net.micode.notes"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packagingOptions {
        // 排除重复的 META-INF/DEPENDENCIES 文件
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/LICENSE")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/NOTICE")
        exclude("META-INF/NOTICE.txt")
        // 如果需要，也可以排除其他常见的重复文件
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
//    implementation(fileTree("D:\\Code\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib") {
//        include("*.aar", "*.jar")
//    })
//    implementation(fileTree(mapOf(
//        "dir" to "D:\\Code\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib",
//        "include" to listOf("*.aar", "*.jar"),
//        "exclude" to listOf()
//    )))
//    implementation(files("D:\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib\\httpclient-osgi-4.5.14.jar"))
//    implementation(files("D:\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib\\httpclient-win-4.5.14.jar"))
//    implementation(files("D:\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib\\httpcore-4.4.16.jar"))
//    implementation(fileTree(mapOf(
//        "dir" to "D:\\Code\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib",
//        "include" to listOf("*.aar", "*.jar"),
//        "exclude" to listOf()
//    )))
    implementation(files(
        "D:\\Code\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib\\httpclient-win-4.5.14.jar",
        "D:\\Code\\Notesmaster\\httpcomponents-client-4.5.14-bin\\lib\\httpcore-4.4.16.jar"
    ))
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}