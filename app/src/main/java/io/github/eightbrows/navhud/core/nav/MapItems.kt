package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.io.AirportKind
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.io.MapPoint
import io.github.eightbrows.navhud.core.io.PeakKind

/**
 * 背景地図（§8）の項目を、どの縮尺まで描くか。R1（自機から 1 つ目の距離環の距離）がこの値以下のときに描く。
 * OFF は描かない、ALL はどの縮尺でも描く。
 */
enum class MapRange(val maxR1Km: Double?) {
    OFF(null),
    UPTO_10KM(10.0),
    UPTO_25KM(25.0),
    UPTO_50KM(50.0),
    ALL(Double.POSITIVE_INFINITY);

    /** R1 [m] のときに描くか（段の値ちょうどは描く） */
    fun shows(r1M: Double): Boolean = maxR1Km != null && r1M <= maxR1Km * 1000.0 + 1e-6
}

/**
 * 設定画面の背景地図の項目（§8）。1 つの項目が、ファイルのレイヤ（MapLayer）のどれを描くかを持つ。
 * - 道路は種類（高速道路・その他の自動車専用道路・国道・県道）ごとに分ける（どれも ROAD のレイヤ。描くときにレイヤの中の種類で分ける）
 * - 鉄道は新幹線と在来線に分ける（どちらも RAILWAY のレイヤ。種類の印で分ける。山形・秋田新幹線は在来線に入れる）
 * - 河川・湖沼は 1 つの項目（河川の線と、湖沼などの面の輪郭）
 * - 線だけ（港・駅・ダムと堰の点・都市と、市街地の輪郭は 20261020-D01、ダム・堰の線は 20261020-D02 でなくした。
 *   空港・山頂は 20261021-D04 でランドマーク（[LandmarkItem]）に移した。前の保存にある名前は読めない名前として飛ばす）
 */
enum class MapItem(val layers: Set<MapLayer>) {
    COASTLINE(setOf(MapLayer.COASTLINE)),
    PREF_BOUNDARY(setOf(MapLayer.PREF_BOUNDARY)),
    MUNI_BOUNDARY(setOf(MapLayer.MUNI_BOUNDARY)),
    MOTORWAY(setOf(MapLayer.ROAD)),
    OTHER_MOTORWAY(setOf(MapLayer.ROAD)),
    NATIONAL_ROAD(setOf(MapLayer.ROAD)),
    PREF_ROAD(setOf(MapLayer.ROAD)),
    SHINKANSEN(setOf(MapLayer.RAILWAY)),
    RAILWAY(setOf(MapLayer.RAILWAY)),
    WATER(setOf(MapLayer.RIVER, MapLayer.INLAND_WATER)),
    FERRY(setOf(MapLayer.FERRY)),
}

/**
 * 設定画面の「ランドマーク」の項目（20261021-D04。20261022-D01 で 7 項目、20261024-D01 で空港を民間・軍用に分けて 9 項目にした）。
 * 背景地図の空港・山頂を、どの縮尺まで描くか。
 * 背景地図のスイッチ（NavSettings.baseMap）と線の太さ・明るさの段は効かない（明るさはランドマークの明るさの段 NavSettings.landmarkBrightness）。
 * - 空港・民間（大・小）: 国土数値情報の空港（自衛隊との共用を含む）。塗りつぶしの飛行機（大は丸い輪付き）
 * - 空港・軍用（大・小）: OurAirports にだけある飛行場（自衛隊・米軍の専用。AirportKind.OURAIRPORTS）。白抜きの飛行機
 *   （大小はいちばん長い滑走路が 2,000 m 以上が大。AirportKind.LARGE）
 * - 空港の敷地: 空港の面を半透明で塗る（MapLayer.AIRPORT_AREA）
 * - 空港の略号: ICAO の略号の文字（空港の記号の右上）。記号が出ている空港にだけ出す（20261024-D01。前は記号が OFF でも出せた）
 * - 山頂（高）: 目印になる山（PeakKind.isLandmark）のうち、標高 Tuning.BASEMAP_PEAK_HIGH_M（1,500 m）以上
 * - 山頂（中）: 目印になる山のうち、それ未満
 * - 山頂（低）: 目印になりにくい山
 */
enum class LandmarkItem(val layer: MapLayer) {
    AIRPORT_CIVIL_LARGE(MapLayer.AIRPORT),
    AIRPORT_CIVIL_SMALL(MapLayer.AIRPORT),
    AIRPORT_MIL_LARGE(MapLayer.AIRPORT),
    AIRPORT_MIL_SMALL(MapLayer.AIRPORT),
    AIRPORT_AREA(MapLayer.AIRPORT_AREA),
    AIRPORT_CODE(MapLayer.AIRPORT),
    PEAK_HIGH(MapLayer.PEAK),
    PEAK_MID(MapLayer.PEAK),
    PEAK_LOW(MapLayer.PEAK);

    companion object {
        /** 空港の記号の 4 項目（民間・軍用 × 大・小） */
        val AIRPORT_MARKS: Set<LandmarkItem> = setOf(AIRPORT_CIVIL_LARGE, AIRPORT_CIVIL_SMALL, AIRPORT_MIL_LARGE, AIRPORT_MIL_SMALL)

        /** 点の記号がどの項目に入るか（空港の民間・軍用と大小、山頂の高中低）。記号のない点（点のレイヤでないもの）は null */
        fun of(p: MapPoint): LandmarkItem? = when (p.layer) {
            MapLayer.AIRPORT -> {
                val large = AirportKind.isLarge(p.kind)
                if (AirportKind.isOurAirportsOnly(p.kind)) (if (large) AIRPORT_MIL_LARGE else AIRPORT_MIL_SMALL)
                else (if (large) AIRPORT_CIVIL_LARGE else AIRPORT_CIVIL_SMALL)
            }
            MapLayer.PEAK -> when {
                !PeakKind.isLandmark(p.kind) -> PEAK_LOW
                PeakKind.elevationM(p.kind) >= Tuning.BASEMAP_PEAK_HIGH_M -> PEAK_HIGH
                else -> PEAK_MID
            }
            else -> null
        }
    }
}
