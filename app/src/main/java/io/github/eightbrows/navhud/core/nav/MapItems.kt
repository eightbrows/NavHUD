package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.io.MapLayer

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
    DAM_LINE(setOf(MapLayer.DAM_LINE)),
    BUILT_UP_AREA(setOf(MapLayer.BUILT_UP_AREA)),
    AIRPORT(setOf(MapLayer.AIRPORT)),
    PORT(setOf(MapLayer.PORT)),
    STATION(setOf(MapLayer.STATION)),
    DAM_POINT(setOf(MapLayer.DAM_POINT)),
    CITY(setOf(MapLayer.CITY)),
}
