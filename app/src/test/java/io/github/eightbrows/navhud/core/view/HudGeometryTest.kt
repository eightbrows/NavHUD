package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.geo.EN
import org.junit.Assert.assertEquals
import org.junit.Test

class HudGeometryTest {

    private val rect = HudRect(0f, 0f, 100f, 200f)
    private val origin = P(50f, 150f)

    private fun assertP(expected: P, actual: P, tol: Float = 1e-3f) {
        assertEquals("x", expected.x, actual.x, tol)
        assertEquals("y", expected.y, actual.y, tol)
    }

    @Test
    fun rayToRectHitsTheRightEdge() {
        assertP(P(50f, 0f), HudGeometry.rayToRect(origin, 0.0, rect))
        assertP(P(100f, 150f), HudGeometry.rayToRect(origin, 90.0, rect))
        assertP(P(0f, 150f), HudGeometry.rayToRect(origin, -90.0, rect))
        assertP(P(50f, 200f), HudGeometry.rayToRect(origin, 180.0, rect))
        // 45°: 右の縁（x=100）に先に当たる
        assertP(P(100f, 100f), HudGeometry.rayToRect(origin, 45.0, rect))
        // -15°: 上の縁に当たる。x = 50 - 150·tan15°
        assertP(P(50f - 150f * 0.26794919f, 0f), HudGeometry.rayToRect(origin, -15.0, rect))
    }

    @Test
    fun rayToRectFromCorner() {
        assertP(P(100f, 0f), HudGeometry.rayToRect(P(0f, 200f), 26.565051, rect), 0.01f)
    }

    @Test
    fun angleOf() {
        assertEquals(0.0, HudGeometry.angleOf(origin, P(50f, 10f)), 1e-6)
        assertEquals(90.0, HudGeometry.angleOf(origin, P(90f, 150f)), 1e-6)
        assertEquals(-90.0, HudGeometry.angleOf(origin, P(10f, 150f)), 1e-6)
        assertEquals(180.0, HudGeometry.angleOf(origin, P(50f, 190f)), 1e-6)
    }

    @Test
    fun headingUpRotation() {
        // 機首 090: 東 100m は真上、北 100m は左
        val p = HudProjection(P(0f, 0f), 1.0, 90.0)
        assertP(P(0f, -100f), p.toScreen(EN(100.0, 0.0)))
        assertP(P(-100f, 0f), p.toScreen(EN(0.0, 100.0)))
        assertEquals(-90.0, p.screenAngle(0.0), 1e-9)
        assertEquals(90.0, p.screenAngle(180.0), 1e-9)
    }
}
