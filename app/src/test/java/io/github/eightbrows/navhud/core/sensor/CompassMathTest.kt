package io.github.eightbrows.navhud.core.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CompassMathTest {

    /** 画面が鉛直から tiltDeg 傾いたときの「上向き」ベクトル（端末座標）。 */
    private fun up(tiltDeg: Double): Triple<Double, Double, Double> {
        val t = Math.toRadians(tiltDeg)
        // 端末の y 軸（画面の上）の方向へ起こしていく
        return Triple(0.0, 9.81 * sin(t), 9.81 * cos(t))
    }

    private fun posture(tiltDeg: Double, previous: Posture?) =
        up(tiltDeg).let { (x, y, z) -> CompassMath.posture(x, y, z, previous) }

    @Test
    fun tilt() {
        assertEquals(0.0, CompassMath.tiltDeg(0.0, 0.0, 9.81), 1e-9)
        assertEquals(90.0, CompassMath.tiltDeg(0.0, 9.81, 0.0), 1e-9)
        assertEquals(30.0, up(30.0).let { (x, y, z) -> CompassMath.tiltDeg(x, y, z) }, 1e-6)
        // 画面が下向き（伏せて置いた）も平置き扱い
        assertEquals(0.0, CompassMath.tiltDeg(0.0, 0.0, -9.81), 1e-9)
    }

    @Test
    fun flatAndUpright() {
        assertEquals(Posture.FLAT, posture(10.0, null))
        assertEquals(Posture.UPRIGHT, posture(80.0, null))
        // 車載ホルダーで少し後ろに倒した状態（約 70°）も立て置き
        assertEquals(Posture.UPRIGHT, posture(70.0, Posture.FLAT))
    }

    @Test
    fun hysteresisKeepsPreviousBetween35And55() {
        assertEquals(Posture.FLAT, posture(50.0, Posture.FLAT))
        assertEquals(Posture.UPRIGHT, posture(40.0, Posture.UPRIGHT))
        assertEquals(Posture.UPRIGHT, posture(55.01, Posture.FLAT))
        assertEquals(Posture.FLAT, posture(34.99, Posture.UPRIGHT))
        // 前の姿勢がなければ中間（45°）で分ける
        assertEquals(Posture.FLAT, posture(44.0, null))
        assertEquals(Posture.UPRIGHT, posture(46.0, null))
    }

    @Test
    fun declinationIsAddedAndWraps() {
        // 西偏 7.5°（日本付近は負）
        assertEquals(352.5, CompassMath.trueHeading(0.0, -7.5), 1e-9)
        assertEquals(2.5, CompassMath.trueHeading(10.0, -7.5), 1e-9)
        // 東偏で 360 をまたぐ
        assertEquals(5.0, CompassMath.trueHeading(355.0, 10.0), 1e-9)
        assertEquals(90.0, CompassMath.trueHeading(90.0, 0.0), 1e-9)
    }

    @Test
    fun azimuthRadiansToDegrees() {
        assertEquals(0.0, CompassMath.azimuthToDeg(0.0), 1e-9)
        assertEquals(90.0, CompassMath.azimuthToDeg(Math.PI / 2), 1e-9)
        assertEquals(270.0, CompassMath.azimuthToDeg(-Math.PI / 2), 1e-9)
        assertEquals(180.0, CompassMath.azimuthToDeg(Math.PI), 1e-9)
    }

    private fun angleDist(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180) 360 - d else d
    }

    @Test
    fun smoothingAcrossNorth() {
        // 359° と 1° を交互に入れても、平均は 0° 付近（180° にならない）
        val s = AngleSmoother(0.2)
        var out = 0.0
        repeat(50) { i -> out = s.update(if (i % 2 == 0) 359.0 else 1.0) }
        assertTrue("out=$out", angleDist(out, 0.0) < 1.0)
    }

    @Test
    fun smoothingFollowsAcrossZeroTheShortWay() {
        // 350° → 10° へ変わったとき、時計回りに（0° を通って）近づく
        val s = AngleSmoother(0.2)
        s.update(350.0)
        val steps = (1..30).map { s.update(10.0) }
        // 途中で 180° 側を通らない
        assertTrue(steps.all { angleDist(it, 0.0) <= 10.0 + 1e-6 })
        assertEquals(10.0, steps.last(), 0.1)
        // 最初の一歩は 350° と 10° の間（354° 付近）
        assertTrue("first=${steps.first()}", steps.first() > 350.0 || steps.first() < 10.0)
    }

    @Test
    fun smoothingFirstValueAndReset() {
        val s = AngleSmoother(0.2)
        assertEquals(123.0, s.update(123.0), 1e-9)
        s.update(200.0)
        s.reset()
        assertEquals(45.0, s.update(45.0), 1e-9)
    }

    @Test
    fun smoothingOppositeValuesDoNotStall() {
        // 真逆の値でベクトルがほぼ 0 になっても、新しい値に追従できる
        val s = AngleSmoother(1.0)
        s.update(0.0)
        assertEquals(180.0, s.update(180.0), 1e-6)
    }
}
