package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
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
 * 設定画面の「ランドマーク」の項目（20261021-D04）。背景地図の点（空港・山頂）を、どの縮尺まで描くか。
 * 背景地図のスイッチ（NavSettings.baseMap）と線の太さ・明るさの段は効かない。
 * - 空港: 飛行機の記号
 * - 山頂（濃い三角）: 目印になる山（PeakKind.isLandmark）のうち、標高 Tuning.BASEMAP_PEAK_HIGH_M（1,500 m）以上
 * - 山頂（薄い三角）: 目印になる山のうち、それ未満
 * - 山頂（その他）: 目印になりにくい山（薄い丸）
 */
enum class LandmarkItem(val layer: MapLayer) {
    AIRPORT(MapLayer.AIRPORT),
    PEAK_HIGH(MapLayer.PEAK),
    PEAK_LOW(MapLayer.PEAK),
    PEAK_OTHER(MapLayer.PEAK);

    companion object {
        /** 点がどの項目に入るか。点のレイヤでないものは null */
        fun of(p: MapPoint): LandmarkItem? = when (p.layer) {
            MapLayer.AIRPORT -> AIRPORT
            MapLayer.PEAK -> when {
                !PeakKind.isLandmark(p.kind) -> PEAK_OTHER
                PeakKind.elevationM(p.kind) >= Tuning.BASEMAP_PEAK_HIGH_M -> PEAK_HIGH
                else -> PEAK_LOW
            }
            else -> null
        }
    }
}
