package io.github.eightbrows.navhud.source

import android.content.Context
import android.location.Location
import android.location.altitude.AltitudeConverter
import android.os.Build
import io.github.eightbrows.navhud.core.model.Fix
import kotlin.math.roundToInt

/**
 * 海抜高度（20261025-D07）: Android の AltitudeConverter（API 34 以上）で、楕円体高から海抜高度を求めて Fix.mslAltM に入れる。
 * 求められないとき（API 33 以下・モデルが読めない・楕円体高がない）は Fix をそのまま返す（地上高の計算は 楕円体高 − 標高オフセット）。
 *
 * 海抜高度 = 楕円体高 − ジオイド高で、ジオイド高は場所でゆっくり変わるだけなので、AltitudeConverter で求めたジオイド高を
 * 緯度・経度 0.01 度（約 1 km）の升ごとに覚えて使う（REPLAY の何千点もの Fix を、待たせずに直すため）。
 * AltitudeConverter はファイルを読むので、画面のスレッドでは呼ばない（IO のスレッドで呼ぶ）。
 */
object MslAltitude {
    private const val CELL = 100.0
    private const val CACHE_MAX = 4096

    private val converter: Any? by lazy { if (Build.VERSION.SDK_INT >= 34) runCatching { AltitudeConverter() }.getOrNull() else null }
    private val cache = object : LinkedHashMap<Long, Double>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Double>?): Boolean = size > CACHE_MAX
    }

    /** AltitudeConverter が使える端末か（API 34 以上） */
    val available: Boolean get() = Build.VERSION.SDK_INT >= 34

    /** 海抜高度を入れた Fix。求められなければそのまま */
    fun withMsl(context: Context, fix: Fix): Fix {
        val h = fix.altRawM ?: return fix
        val n = geoidM(context, fix.lat, fix.lon) ?: return fix
        return fix.copy(mslAltM = h - n)
    }

    /** ジオイド高 [m]（楕円体高 − 海抜高度）。求められなければ null */
    @Synchronized
    fun geoidM(context: Context, lat: Double, lon: Double): Double? {
        if (Build.VERSION.SDK_INT < 34) return null
        val key = ((lat * CELL).roundToInt().toLong() shl 32) or ((lon * CELL).roundToInt().toLong() and 0xFFFFFFFFL)
        cache[key]?.let { return it }
        val c = converter as? AltitudeConverter ?: return null
        val n = runCatching {
            val loc = Location("navhud").apply {
                latitude = (lat * CELL).roundToInt() / CELL
                longitude = (lon * CELL).roundToInt() / CELL
                altitude = 0.0
            }
            c.addMslAltitudeToLocation(context.applicationContext, loc)
            if (loc.hasMslAltitude()) loc.altitude - loc.mslAltitudeMeters else null
        }.getOrNull() ?: return null
        cache[key] = n
        return n
    }
}
