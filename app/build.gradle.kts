plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// バージョン名: yyyyMMdd-Xnn（X = D:開発版 / R:リリース版, nn = その日の通し番号 01..99）
// ここだけ変更すれば versionCode は自動で追従する。コミットメッセージの先頭にも使われる（.githooks）。
val appVersionName = "20261004-D12"

// versionCode = yyyyMMdd * 100 + nn
// 最大は 20991231 * 100 + 99 = 2,099,123,199（Int 上限 2,147,483,647、Google Play 上限 2,100,000,000 未満）
fun versionCodeFrom(name: String): Int {
    val m = Regex("""(\d{8})-[DR](\d{2})""").matchEntire(name)
        ?: error("appVersionName は yyyyMMdd-Xnn 形式にしてください: $name")
    val (date, seq) = m.destructured
    val year = date.take(4).toInt()
    require(year <= 2099) { "versionCode が Google Play の上限を超えます: $name" }
    require(seq.toInt() in 1..99) { "通し番号は 01..99: $name" }
    return date.toInt() * 100 + seq.toInt()
}

android {
    namespace = "io.github.eightbrows.navhud"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.eightbrows.navhud"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeFrom(appVersionName)
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
