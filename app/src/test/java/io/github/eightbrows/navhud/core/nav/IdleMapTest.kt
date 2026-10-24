package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.io.MapLevel
import io.github.eightbrows.navhud.core.io.MapLine
import io.github.eightbrows.navhud.core.io.MapPoint
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudSceneBuilder
import io.github.eightbrows.navhud.core.view.HudViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 位置がまだないあいだの地図（20261024-D05）: 縮尺は R1 10 km、位置が来たら今までどおりの縮尺に移る。
 * 位置がないときも PAN・ダブルタップ・ピンチが使える（前回の位置・皇居から動かせる）。
 * 20261024-D08: AUTO が OFF のとき、位置がないあいだに変えた段は、位置が来ても変えた段のまま（変えていなければ起動したときの段）。
 */
class IdleMapTest {

    private val lat0 = TestGeo.LAT0
    private val lon0 = TestGeo.LON0

    /** エミュレータと同じ寸法の画面（PanTest と同じ） */
    private val rect = HudRect(0f, 0f, 720f, 690f)
    private val viewport = HudViewport(rect, reserved = HudInsets(right = 102f, bottom = 81.6f))

    private fun engine(s: NavSettings = NavSettings(initialRangeKm = 2.0)) =
        NavEngine(s, ZoneId.of("Asia/Tokyo"), SourceKind.LIVE).apply { setViewport(viewport) }

    private fun fix(t: Long) = Fix(timeMs = t, lat = lat0, lon = lon0)

    @Test
    fun idleRangeIsR1TenKmUntilTheFirstFix() {
        assertEquals(20.0, Tuning.IDLE_RANGE_KM, 0.0)
        // AUTO（初期値）: 位置がないあいだは 20 km（R1 10 km）。時計が進んでも AUTO は動かさない
        val e = engine()
        assertEquals(true, e.state.rangeAuto)
        assertEquals(20_000.0, e.setIdleCenter(lat0, lon0).rangeM, 0.0)
        assertEquals(20_000.0, e.onTick(5_000).rangeM, 0.0)
        // 位置が来たら、位置がないあいだの縮尺を使わなかったときと同じ段（AUTO が決める。次の WP がないので AUTO の段の中央）
        val plain = engine().also { it.onFix(fix(5_000), 5_000) }.state.rangeM
        assertEquals(200.0, plain, 0.0)
        assertEquals(plain, e.onFix(fix(5_000), 5_000).rangeM, 0.0)
        assertEquals(true, e.state.rangeAuto)
        // AUTO が OFF: 位置が来たら、起動したときの段（設定の起動時の縮尺）に戻す
        val manual = engine(NavSettings(initialRangeKm = 2.0, autoRange = false))
        assertEquals(20_000.0, manual.setIdleCenter(lat0, lon0).rangeM, 0.0)
        assertEquals(2_000.0, manual.onFix(fix(0), 0).rangeM, 0.0)
        // 位置が一度来たら、あとで位置の中心を決め直しても（呼ばれても）縮尺は変えない
        assertEquals(2_000.0, manual.setIdleCenter(lat0, lon0).rangeM, 0.0)
    }

    @Test
    fun zoomWhileIdleKeepsAutoAndAutoDecidesWhenTheFixArrives() {
        val e = engine()
        e.setIdleCenter(lat0, lon0)
        // ＋ / − ・ピンチは、位置がないあいだは PAN 中と同じく AUTO を OFF にしない
        assertEquals(10_000.0, e.zoomIn().rangeM, 0.0)
        assertEquals(true, e.state.rangeAuto)
        assertEquals(20_000.0, e.zoomBy(-1).rangeM, 0.0)
        assertEquals(50_000.0, e.zoomOut().rangeM, 0.0)
        assertEquals(true, e.state.rangeAuto)
        // 位置が来たら AUTO が決める
        assertEquals(200.0, e.onFix(fix(0), 0).rangeM, 0.0)
        // 位置が来たあとの ＋ は今までどおり AUTO を OFF にする
        e.zoomIn()
        assertEquals(false, e.state.rangeAuto)
    }

    @Test
    fun manualRangeChangedWhileIdleIsKeptWhenTheFixArrives() {
        // AUTO が OFF（起動時の縮尺 2 km）: 位置がないあいだに ＋ で変えた段は、位置が来ても戻さない（20261024-D08）
        val plus = engine(NavSettings(initialRangeKm = 2.0, autoRange = false))
        assertEquals(20_000.0, plus.setIdleCenter(lat0, lon0).rangeM, 0.0)
        assertEquals(10_000.0, plus.zoomIn().rangeM, 0.0)
        assertEquals(10_000.0, plus.onFix(fix(0), 0).rangeM, 0.0)
        assertEquals(false, plus.state.rangeAuto)
        // 時間が進んで次の位置が来ても、そのまま
        assertEquals(10_000.0, plus.onFix(fix(1_000), 1_000).rangeM, 0.0)
        // − ・ピンチでも同じ
        val minus = engine(NavSettings(initialRangeKm = 2.0, autoRange = false)).apply { setIdleCenter(lat0, lon0) }
        assertEquals(50_000.0, minus.zoomOut().rangeM, 0.0)
        assertEquals(50_000.0, minus.onFix(fix(0), 0).rangeM, 0.0)
        val pinch = engine(NavSettings(initialRangeKm = 2.0, autoRange = false)).apply { setIdleCenter(lat0, lon0) }
        val pinched = pinch.zoomBy(2).rangeM
        assertTrue(pinched < 20_000.0)
        assertEquals(pinched, pinch.onFix(fix(0), 0).rangeM, 0.0)
        // ＋ のあと − で元の段（R1 10 km）に戻した: 段を操作したので、その段（R1 10 km）のまま
        val back = engine(NavSettings(initialRangeKm = 2.0, autoRange = false)).apply { setIdleCenter(lat0, lon0) }
        back.zoomIn(); back.zoomOut()
        assertEquals(20_000.0, back.onFix(fix(0), 0).rangeM, 0.0)
    }

    @Test
    fun manualRangeNotChangedWhileIdleGoesBackToTheStartupRange() {
        // AUTO が OFF で段を変えていなければ、今までどおり起動したときの段（2 km）に戻す
        val e = engine(NavSettings(initialRangeKm = 2.0, autoRange = false))
        e.setIdleCenter(lat0, lon0)
        e.onTick(5_000)
        assertEquals(2_000.0, e.onFix(fix(5_000), 5_000).rangeM, 0.0)
        // PAN（ドラッグ）だけで段を変えていなければ、PAN が終わったとき起動したときの段
        val panOnly = engine(NavSettings(initialRangeKm = 2.0, autoRange = false))
        panOnly.setIdleCenter(lat0, lon0)
        panOnly.panBy(-50f, 0f)
        panOnly.onFix(fix(0), 0)
        assertEquals(20_000.0, panOnly.state.rangeM, 0.0)
        assertEquals(2_000.0, panOnly.endPan().rangeM, 0.0)
    }

    @Test
    fun doubleTapWhileIdleWithAutoOffIsKeptAfterThePan() {
        // AUTO が OFF: 位置がないあいだのダブルタップ（PAN になる）で変えた段も、PAN が終わったあと変えた段のまま（20261024-D08）
        val e = engine(NavSettings(initialRangeKm = 2.0, autoRange = false))
        e.setIdleCenter(lat0, lon0)
        e.zoomInAt(300f, 300f)
        assertEquals(10_000.0, e.state.rangeM, 0.0)
        e.onFix(fix(0), 0)
        assertEquals(10_000.0, e.endPan().rangeM, 0.0)
        assertEquals(false, e.state.rangeAuto)
    }

    @Test
    fun autoOnDecidesWhenTheFixArrivesEvenIfTheRangeWasChanged() {
        // AUTO が ON: 位置がないあいだに段を変えても、位置が来たら待たずに AUTO が決める（今までどおり）
        val e = engine()
        e.setIdleCenter(lat0, lon0)
        e.zoomIn(); e.zoomIn()
        assertEquals(5_000.0, e.state.rangeM, 0.0)
        assertEquals(200.0, e.onFix(fix(0), 0).rangeM, 0.0)
        assertEquals(true, e.state.rangeAuto)
    }

    @Test
    fun panAndDoubleTapWorkWithoutAFix() {
        val e = engine()
        // 中心（前回の位置か皇居）がなければ、今までどおり PAN にしない
        assertNull(e.panBy(-50f, 0f).pan)
        e.setIdleCenter(lat0, lon0)
        // 位置がなくても、中心から PAN を始める（North Up。表示枠の中心から始め、指を左へ動かすと中心は東へ）
        val start = e.panBy(0f, 0f).pan
        assertNotNull(start)
        val moved = e.panBy(-50f, 0f).pan!!
        assertTrue(Geo.toEN(start!!.lat, start.lon, moved.lat, moved.lon).e > 0)
        // ダブルタップも使える（PAN 中なので AUTO はそのまま）
        e.zoomInAt(300f, 300f)
        assertEquals(10_000.0, e.state.rangeM, 0.0)
        assertEquals(true, e.state.rangeAuto)
        // PAN のまま位置が来ても、今の PAN の決まりどおり PAN のまま（縮尺もそのまま）
        e.onFix(fix(0), 0)
        assertNotNull(e.state.pan)
        assertEquals(10_000.0, e.state.rangeM, 0.0)
        // PAN が終わったら、自機の位置に移り、AUTO が決める
        e.endPan()
        assertNull(e.state.pan)
        assertEquals(200.0, e.state.rangeM, 0.0)
    }

    @Test
    fun doubleTapStartsPanFromTheIdleCenter() {
        val e = engine()
        e.setIdleCenter(lat0, lon0)
        e.zoomInAt(300f, 300f)
        assertNotNull(e.state.pan)
        assertEquals(10_000.0, e.state.rangeM, 0.0)
        assertEquals(true, e.state.rangeAuto)
        // 位置がないまま PAN が終わったら、中心（前回の位置か皇居）の表示に戻る。縮尺は変えた段のまま
        e.endPan()
        assertNull(e.state.pan)
        assertEquals(10_000.0, e.state.rangeM, 0.0)
        assertEquals(lat0 to lon0, e.state.idleCenter)
    }

    @Test
    fun mapIsDrawnAroundThePanCenterWithoutAFix() {
        val e = engine()
        e.setIdleCenter(lat0, lon0)
        // 中心から東へ 60 km の空港の点。中心のままでは枠の外（R1 10 km）、PAN で東へ動かすと見える
        val far = MapPoint(MapLayer.AIRPORT, 0, lat0, TestGeo.lon(60_000.0), "RJXX")
        val map = BaseMap(listOf(MapLevel(0.0, emptyList<MapLine>())), listOf(far))
        assertTrue(HudSceneBuilder.build(e.state, rect, baseMap = map).baseMapMarks.isEmpty())
        // 60 km ぶん、指を左へ動かす
        val pxPerM = viewport.pxPerM(e.state.rangeM, e.state.settings)
        e.panBy(-(60_000 * pxPerM).toFloat(), 0f)
        val scene = HudSceneBuilder.build(e.state, rect, baseMap = map)
        assertEquals(1, scene.baseMapMarks.size)
        // 自機の記号は描かない（位置がない）
        assertNull(scene.ownShip)
    }
}
