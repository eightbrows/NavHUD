import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// バージョン名: yyyyMMdd-Xnn（X = D:開発版 / R:リリース版, nn = その日の通し番号 01..99）
// ここだけ変更すれば versionCode は自動で追従する。コミットメッセージの先頭にも使われる（.githooks）。
val appVersionName = "20261010-D03"

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

/** リリース APK の署名情報。.github/workflows/release.yml が環境変数で渡す */
data class SigningEnv(
    val storeFile: File,
    val storePassword: String,
    val alias: String,
    val keyPassword: String,
)

/**
 * 署名情報を環境変数から読む。名前は release.yml が渡すものに合わせている。
 * 手元のビルドでは環境変数がないので null（署名なしの APK になる）。
 * 一部だけ設定されている状態は設定漏れなので、黙って署名なしの APK を出さずにビルドを失敗させる。
 */
fun computeSigningEnv(): SigningEnv? {
    fun env(name: String): String? =
        providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

    val names = listOf("KEYSTORE_PATH", "KEY_STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    val values = names.map { env(it) }
    if (values.all { it == null }) return null

    val missing = names.zip(values).filter { it.second == null }.map { it.first }
    require(missing.isEmpty()) {
        "リリース署名用の環境変数が足りません: ${missing.joinToString()}"
    }

    val storeFile = File(values[0]!!)
    require(storeFile.isFile) {
        "KEYSTORE_PATH のファイルが見つかりません: ${storeFile.absolutePath}"
    }
    return SigningEnv(storeFile, values[1]!!, values[2]!!, values[3]!!)
}

val signingEnv = computeSigningEnv()

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

    signingConfigs {
        signingEnv?.let { env ->
            create("release") {
                storeFile = env.storeFile
                storePassword = env.storePassword
                keyAlias = env.alias
                keyPassword = env.keyPassword
            }
        }
    }

    buildTypes {
        release {
            // 環境変数がない手元のビルドでは null のまま（署名なし）
            signingConfig = signingConfigs.findByName("release")
            // R8 によるコードの縮小・難読化・最適化（既定の proguard-android-optimize.txt の規則も入る）。
            // アプリのクラスを文字列の名前で引く所はない。反射で作られるのは NavViewModel のコンストラクタだけで、
            // proguard-rules.pro で残す（lifecycle の規則でも残るが、明示しておく）
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
            // 未使用リソースの削除。参照はすべてマニフェストか R.xxx 経由なので静的に追える
            isShrinkResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // 英語（values、初期値）と日本語（values-ja）だけを入れる。ライブラリのほかの言語の文字列は入れない。
        // ほかの言語の端末では、アプリもライブラリの文字（読み上げラベルなど）も英語になる
        localeFilters += listOf("ja", "en")
        // アプリごとの言語の設定（Android 13 以降）に出す言語の一覧を、values-* から作る（初期値の言語は res/resources.properties）
        generateLocaleConfig = true
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
