package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.io.TrackCsv
import io.github.eightbrows.navhud.core.io.TrackParseResult
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudRect
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.math.cos

/**
 * テストの基準点と、そこから北・東へ何 m の点の緯度・経度。
 * 緯度 1 度 = 111,195 m（Geo の地球半径で）。経度 1 度はその cos(緯度) 倍。
 */
object TestGeo {
    const val LAT0 = 33.5
    const val LON0 = 133.0
    const val M_PER_DEG_LAT = 111_195.0

    /** 緯度 lat での経度 1 度の距離 [m] */
    fun mPerDegLon(lat: Double = LAT0): Double = M_PER_DEG_LAT * cos(Math.toRadians(lat))

    /** 緯度 lat0 から北へ northM [m]（南は −）の緯度 */
    fun lat(northM: Double, lat0: Double = LAT0): Double = lat0 + northM / M_PER_DEG_LAT

    /** 緯度 lat0・経度 lon0 から東へ eastM [m]（西は −）の経度 */
    fun lon(eastM: Double, lat0: Double = LAT0, lon0: Double = LON0): Double = lon0 + eastM / mPerDegLon(lat0)
}

/** track.csv（git 管理外）。ファイルがなければ、使うテストを Assume でスキップする。 */
object SampleTrack {
    const val PATH = "sample/session_20260814_075234/track.csv"

    // 単体テストの作業ディレクトリは app/ のことが多い
    private val file: File? = listOf(File(PATH), File("../$PATH")).firstOrNull { it.exists() }

    private val parsed: TrackParseResult? by lazy { file?.inputStream()?.use { TrackCsv.parse(it) } }

    /** 読み込み結果。ファイルがなければスキップ */
    fun result(): TrackParseResult {
        assumeTrue("track.csv がないためスキップ: $PATH", parsed != null)
        return parsed!!
    }

    /** すべての Fix。ファイルがなければスキップ */
    fun fixes(): List<Fix> = result().fixes
}

/** 動作確認で使う WP リスト（wp_profile.csv）。標高は 峠の入口・展望台・ダム・終点 */
val WP_PROFILE = """
    lat,lon,ele,name,target_time,deadline_time,enabled
    33.47649101,133.00275172,1300,峠の入口,,,1
    33.46679346,132.96174603,1180,展望台,8:30,8:45,1
    33.48377796,132.87220649,,旧道（通行止め）,,,0
    33.48750794,132.90060923,,道の駅,09:10,9:20:00,1
    33.55944388,133.00766313,620,ダム,,,1
    33.667743,132.8949383,150,終点,11:00,,1
""".trimIndent()

/**
 * エミュレータ（Small_Phone_A14、密度 1.7）で測った画面: 描画の枠 720×1239 px、上（上部バー・数値）211 px、
 * 右の操作列 102 px、下（WP 列 40dp・プロファイル 小 56dp・再生の帯 36dp・ナビゲーションバー）LIVE 204 px / REPLAY 265 px。
 * 操作列（＋ / RNG / −、306 px）は回避枠の縦中央: LIVE y 470..776、REPLAY y 439.5..745.5。
 */
object MeasuredScreen {
    const val DENSITY = 1.7f
    val RECT = HudRect(0f, 0f, 720f, 1239f)
    val LIVE = HudInsets(top = 211f, right = 102f, bottom = 204f, rightSpan = 470f..776f)
    val REPLAY = HudInsets(top = 211f, right = 102f, bottom = 265f, rightSpan = 439.5f..745.5f)
}
