package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning

/**
 * NavSettings ⇔ 文字列のマップ（SharedPreferences に保存するため）。Android に依存しない。
 * 読めない値・ない値は初期値にする（アプリの更新で項目が増減しても落ちない）。
 *
 * 20261121-D03 から（保存の形 2）: 初期値から変えた所だけを保存する。
 * - 共通の値: アプリの初期値と違う所だけ（名前は前と同じ）
 * - 自動車・歩行: 「car.」「walk.」を頭に付けて、それぞれの初期値と違う所だけ
 * - カスタム1〜3: 「custom1.」などを頭に付けて、元（customN.base。自動車か歩行）・コピーしたか（customN.copied）と、元の初期値と違う所だけ
 * - 地図の項目の表・ランドマークの表は、初期値と違う行だけ（「名前:縮尺」をカンマでつなぐ）
 * - 保存の形（settingsFormat）と移動のプリセット（travelMode）はいつも書く
 * 変えていない項目は保存しないので、アプリの初期値が変わったら新しい初期値になる。
 *
 * 前の形（settingsFormat がない。20261121-D02 まで）は、前と同じに見えるように読み替える（decode の legacy）。
 */
object SettingsCodec {

    /** 保存の形（20261121-D03 から 2。前の版にはこの名前がない） */
    const val KEY_FORMAT = "settingsFormat"
    const val FORMAT = "2"

    /**
     * もう使わない保存の名前（読まない。保存のときに消す）。rateWindowSec: RATE の窓（20261104-D03 で RATE を V/S にして、設定をなくした）
     */
    val OBSOLETE_KEYS: Set<String> = setOf(
        "rateWindowSec",
        // 20261119-D03: 方位を止める / 戻す速度を到達判定のプリセットの値にした。前の全体の値は使わずに消す
        OLD_KEY_HOLD_ENTER, OLD_KEY_HOLD_EXIT,
        // 20261119-D02 の一度だけの移し替えの印（移し替えは 20261119-D03 で外した）
        "holdDefaultsMigrated",
        // 20261121-D01: コンパスを外したので、方位の求め方（HYBRID / GPS / COMPASS）は読まずに消す（方位の出し方は移動のプリセットの値）
        OLD_KEY_SOURCE_MODE,
        // 20261121-D01: 方位精度の上限を移動のプリセットの値にした。前の全体の値は読まずに消す（自動車は 20°）
        OLD_KEY_MAX_BEARING_ACC,
    )

    /** プリセットの頭（カスタムは 1〜3） */
    private const val CAR = "car."
    private const val WALK = "walk."
    private fun customPrefix(i: Int) = "custom${i + 1}."
    private val PRESET_PREFIXES = listOf(CAR, WALK) + (0..2).map(::customPrefix)

    // ---- 書く ----

    /** 共通の値（全部）。保存のときは初期値と違う所だけを書く */
    private fun encodeCommon(s: NavSettings): Map<String, String> = mapOf(
        "maxGpsAccM" to s.maxGpsAccM.toString(),
        "noFixTimeoutSec" to s.noFixTimeoutSec.toString(),
        "altOffsetM" to s.altOffsetM.toString(),
        "aglNegative" to s.aglNegative.name,
        "displayMode" to s.displayMode.name,
        "wpButtonsMax" to s.wpButtonsMax.toString(),
        "hudWpCount" to s.hudWpCount.toString(),
        "uiTheme" to s.uiTheme.name,
        "mapTheme" to s.mapTheme.name,
        "ownshipPosition" to s.ownshipPosition.name,
        "panReturnSec" to s.panReturnSec.toString(),
        "autoOpenLastList" to s.autoOpenLastList.toString(),
        "keepScreenOn" to s.keepScreenOn.toString(),
        "baseMap" to s.baseMap.toString(),
        "landmarkBrightnessLevel" to s.landmarkBrightness.toString(),
        // 線の太さ・明るさの段（1〜6）。20261018-D07 で 3 段から 6 段にしたので名前を変えた（前の mapLineWidth・mapLineBrightness は読まない）
        "mapLineWidthLevel" to s.mapLineWidth.toString(),
        "mapLineBrightnessLevel" to s.mapLineBrightness.toString(),
        "buttonOpacityPct" to s.buttonOpacityPct.toString(),
        "numbersOpacityPct" to s.numbersOpacityPct.toString(),
        "trackColor" to s.trackColor.name,
        "trackBrightnessPct" to s.trackBrightnessPct.toString(),
        "ringLabelScalePct" to s.ringLabelScalePct.toString(),
        // 距離環の明るさ [%]（20261104-D01 から。D01 の ringBrightnessLevel・D02 の ringLineBrightnessLevel（段）は読み替えて読む）
        "ringLineBrightnessPct" to s.ringBrightnessPct.toString(),
    )

    /** プリセットの値（全部。表は全部の行）。名前は頭を付けずに */
    private fun encodeProfile(p: ReachProfile): Map<String, String> = mapOf(
        "reachRadiusM" to p.reachRadiusM.toString(),
        "sidePass" to p.sidePass.toString(),
        "sidePassMaxM" to p.sidePassMaxM.toString(),
        "sidePassDepartM" to p.sidePassDepartM.toString(),
        "passDetection" to p.passDetection.toString(),
        "passMaxApproachM" to p.passMaxApproachM.toString(),
        "passDepartM" to p.passDepartM.toString(),
        "passHoldSec" to p.passHoldSec.toString(),
        "sidePassMinKmh" to p.sidePassMinKmh.toString(),
        "holdEnterKmh" to p.holdEnterKmh.toString(),
        "holdExitKmh" to p.holdExitKmh.toString(),
        "headingMethod" to p.headingMethod.name,
        "maxBearingAccDeg" to p.maxBearingAccDeg.toString(),
        "gsAverageSec" to p.gsAverageSec.toString(),
        "etaAverageSec" to p.etaAverageSec.toString(),
        "trackDistanceM" to p.trackDistanceM.toString(),
        "rangeStepsKm" to p.rangeStepsKm.joinToString(","),
        "initialRangeKm" to p.initialRangeKm.toString(),
        "autoRange" to p.autoRange.toString(),
        "autoRangeZoomInDelaySec" to p.autoRangeZoomInDelaySec.toString(),
        "autoMinRangeKm" to p.autoMinRangeKm.toString(),
        "autoMaxRangeKm" to p.autoMaxRangeKm.toString(),
        "autoHoldAfterWpSec" to p.autoHoldAfterWpSec.toString(),
        // D10 で意味が変わった（1つ詳細側の段の R1 → 今の段の R1）ので、前の版のキー autoZoomInDistRatio とは別のキーにする
        KEY_ZOOM_IN_RATIO to p.autoZoomInDistRatio.toString(),
        "profileSize" to p.profileSize.name,
    )

    /** プリセットの値のうち、base（初期値）と違う所だけ（頭 pre を付けて） */
    private fun encodeProfileDiff(pre: String, p: ReachProfile, base: ReachProfile): Map<String, String> {
        val now = encodeProfile(p)
        val def = encodeProfile(base)
        val out = now.filter { (k, v) -> def[k] != v }.mapKeys { pre + it.key }.toMutableMap()
        // 表: 初期値と違う行だけ（「名前:縮尺」をカンマでつなぐ。例 MOTORWAY:UPTO_50KM）
        val maps = MapItem.entries.filter { (p.mapItems[it] ?: MapRange.OFF) != (base.mapItems[it] ?: MapRange.OFF) }
        if (maps.isNotEmpty()) out[pre + KEY_MAP_ITEMS] = maps.joinToString(",") { "${it.name}:${(p.mapItems[it] ?: MapRange.OFF).name}" }
        val lms = LandmarkItem.entries.filter { (p.landmarks[it] ?: MapRange.OFF) != (base.landmarks[it] ?: MapRange.OFF) }
        if (lms.isNotEmpty()) out[pre + KEY_LANDMARK_ITEMS] = lms.joinToString(",") { "${it.name}:${(p.landmarks[it] ?: MapRange.OFF).name}" }
        return out
    }

    /**
     * 保存する文字のマップ（初期値から変えた所だけ。保存の形と移動のプリセットはいつも）。
     * @param d 比べる初期値（ふだんはアプリの初期値。テストで差し替える）
     */
    fun encode(s: NavSettings, d: SettingsDefaults = SettingsDefaults.CURRENT): Map<String, String> {
        val out = linkedMapOf(KEY_FORMAT to FORMAT, "travelMode" to s.travelMode.name)
        val defCommon = encodeCommon(d.common)
        encodeCommon(s).forEach { (k, v) -> if (defCommon[k] != v) out[k] = v }
        // 今の値（選んでいるプリセット）を、そのプリセットの値として書く（storeActive と同じ）
        val st = s.storeActive()
        out += encodeProfileDiff(CAR, st.carProfile, d.car)
        out += encodeProfileDiff(WALK, st.walkProfile, d.walk)
        for (i in 0..2) {
            val base = st.customBase.getOrNull(i) ?: TravelMode.CAR
            val pre = customPrefix(i)
            out[pre + "base"] = base.name
            if (st.customCopied.getOrNull(i) == true) out[pre + "copied"] = "true"
            out += encodeProfileDiff(pre, st.customReach.getOrNull(i) ?: d.of(base), d.of(base))
        }
        return out
    }

    /**
     * この保存の名前が設定のものか（保存のときに、変えた所でなくなった名前を消すため。SettingsStore.save）。
     * 設定以外の名前（INPUT・スクロール位置・閉じた欄・前回の位置・表示言語）は false
     */
    fun isSettingsKey(k: String): Boolean =
        k in SETTINGS_KEYS || k in OBSOLETE_KEYS || PRESET_PREFIXES.any { k.startsWith(it) }

    // ---- 読む ----

    /** 設定の項目のキー（今の版と前の版の全体の名前）。前の版の設定かどうかの見分けと、保存のときの片付けに使う */
    private val SETTINGS_KEYS: Set<String> by lazy {
        encodeCommon(NavSettings()).keys + encodeProfile(ReachProfile.CAR).keys + KEY_MAP_ITEMS + KEY_LANDMARK_ITEMS +
            KEY_FORMAT + "travelMode" + "colorTheme" + OLD_KEY_ZOOM_IN_RATIO + OLD_KEY_HOLD_ENTER + OLD_KEY_HOLD_EXIT + OLD_KEY_SOURCE_MODE + OLD_KEY_MAX_BEARING_ACC +
            "landmarks" + "mapLineWidth" + "mapLineBrightness" + "ringBrightnessLevel" + "ringLineBrightnessLevel"
    }

    /** 20261119-D03 までの全体の方位を止める / 戻す速度（m/s）。読まない（前の版の設定かどうかの見分けにだけ使う） */
    private const val OLD_KEY_HOLD_ENTER = "holdEnterSpeedMps"
    private const val OLD_KEY_HOLD_EXIT = "holdExitSpeedMps"

    /** 20261119-D03 までの方位の求め方と、全体の方位精度の上限。読まない（前の版の設定かどうかの見分けにだけ使う） */
    private const val OLD_KEY_SOURCE_MODE = "sourceMode"
    private const val OLD_KEY_MAX_BEARING_ACC = "maxGpsBearingAccDeg"

    /** AUTO で詳細へ切り替える距離の倍率（今の段の R1 が基準。D10 から） */
    private const val KEY_ZOOM_IN_RATIO = "autoZoomInR1Ratio"

    /** 前の版の倍率（1つ詳細側の段の R1 が基準）。意味が違うので読まない（初期値にする） */
    private const val OLD_KEY_ZOOM_IN_RATIO = "autoZoomInDistRatio"

    /** 地図の項目の表・ランドマークの表（20261022-D01 で項目を作り直し、ランドマークの名前を landmarkItems にした） */
    private const val KEY_MAP_ITEMS = "mapItems"
    private const val KEY_LANDMARK_ITEMS = "landmarkItems"

    /** 20261121-D02 まで全体の値だった、縮尺・地図の表・ランドマークの表・標高プロファイルの名前（前の形の読み替えで全部のプリセットに入れる） */
    private val LEGACY_GLOBAL_PROFILE_KEYS = setOf(
        "rangeStepsKm", "initialRangeKm", "autoRange", "autoRangeZoomInDelaySec", "autoMinRangeKm", "autoMaxRangeKm", "autoHoldAfterWpSec",
        KEY_ZOOM_IN_RATIO, KEY_MAP_ITEMS, KEY_LANDMARK_ITEMS, "profileSize",
    )

    /**
     * 保存の文字のマップを読む。
     * @param d 比べる初期値（ふだんはアプリの初期値。テストで差し替えて、変えていない項目が新しい初期値になることを確かめる）
     */
    fun decode(m: Map<String, String?>, d: SettingsDefaults = SettingsDefaults.CURRENT): NavSettings {
        val r = Reader(m)
        val common = readCommon(r, d.common)
        val format2 = r.str(KEY_FORMAT) == FORMAT
        val savedMode = r.str("travelMode")
        val s = if (format2) {
            val travelMode = TravelMode.entries.firstOrNull { it.name == savedMode } ?: TravelMode.CAR
            val bases = (0..2).map { i -> r.enum(customPrefix(i) + "base", arrayOf(TravelMode.CAR, TravelMode.WALK), TravelMode.CAR) }
            common.copy(
                travelMode = travelMode,
                carProfile = readProfile(r, CAR, d.car),
                walkProfile = readProfile(r, WALK, d.walk),
                customBase = bases,
                customCopied = (0..2).map { i -> r.bool(customPrefix(i) + "copied", false) },
                customReach = (0..2).map { i -> readProfile(r, customPrefix(i), d.of(bases[i])) },
            )
        } else {
            decodeLegacy(r, m, common, d, savedMode)
        }
        return s.selectTravelMode(s.travelMode)
    }

    /**
     * 前の形（20261121-D02 まで）の読み替え。前と同じに見えるようにする:
     * - 全体の値だった縮尺・地図の表・ランドマークの表・標高プロファイルは、自動車・歩行・カスタム1〜3 の全部に入れる（初期値と違えば変えた所）
     * - カスタム1〜3 は「元：自動車の初期値」とし、保存の値で初期値と違う所が変えた所になる
     * - 自動車・歩行は前は変えられなかったので、上の全体の値のほかは初期値
     * - 移動のプリセットの項目がない設定（D06 より前）は、保存してあった到達判定の値をカスタム1 に移す（前からの決まり）
     */
    private fun decodeLegacy(r: Reader, m: Map<String, String?>, common: NavSettings, d: SettingsDefaults, savedMode: String?): NavSettings {
        val travelMode = when {
            savedMode == "CUSTOM" -> TravelMode.CUSTOM1
            savedMode != null -> TravelMode.entries.firstOrNull { it.name == savedMode } ?: TravelMode.CAR
            m.keys.none { it in SETTINGS_KEYS } -> TravelMode.CAR
            else -> TravelMode.CUSTOM1
        }
        fun global(p: ReachProfile) = readProfile(r, "", p, only = LEGACY_GLOBAL_PROFILE_KEYS)
        // 移動のプリセットの項目がない前の版: カスタム1 には保存してあった値（全体の名前で保存されていた到達判定の値）
        val flat = readProfile(r, "", d.car)
        val noSlots = travelMode == TravelMode.CUSTOM1 && m.keys.none { it.startsWith("custom") }
        return common.copy(
            travelMode = travelMode,
            carProfile = global(d.car),
            walkProfile = global(d.walk),
            customBase = List(3) { TravelMode.CAR },
            customCopied = List(3) { false },
            customReach = (0..2).map { i -> readProfile(r, customPrefix(i), if (i == 0 && noSlots) flat else global(d.car)) },
        )
    }

    /** 共通の値を読む（保存がない・読めない・範囲外の値は def の値） */
    private fun readCommon(r: Reader, def: NavSettings): NavSettings {
        val d = def
        val oldTheme = ColorTheme.entries.firstOrNull { it.name == r.str("colorTheme") }
        return d.copy(
            // 選べる値にない数は、一番近い値に読み替える（数でなければ初期値）
            maxGpsAccM = NavSettings.nearestFloat(NavSettings.MAX_GPS_ACC_CHOICES_M, r.f("maxGpsAccM", d.maxGpsAccM)),
            noFixTimeoutSec = NavSettings.nearestInt(NavSettings.NO_FIX_TIMEOUT_CHOICES_SEC, r.int("noFixTimeoutSec", d.noFixTimeoutSec)),
            altOffsetM = r.dbl("altOffsetM", d.altOffsetM).inOr(NavSettings.ALT_OFFSET_M_RANGE, d.altOffsetM),
            // 地上高の負の値（20261025-D08）: 保存がない（前の版）・読めない名前なら初期値の ZERO（0 にする）
            aglNegative = r.enum("aglNegative", AglNegative.entries.toTypedArray(), d.aglNegative),
            displayMode = r.enum("displayMode", DisplayMode.entries.toTypedArray(), d.displayMode),
            wpButtonsMax = r.int("wpButtonsMax", d.wpButtonsMax).coerceIn(NavSettings.WP_BUTTONS_MAX_RANGE),
            hudWpCount = r.int("hudWpCount", d.hudWpCount).coerceIn(NavSettings.HUD_WP_COUNT_RANGE),
            // 色: 古い版の「色テーマ」（1つ）が保存されていれば、UI・地図の両方の初期値として引き継ぐ
            uiTheme = r.enum("uiTheme", ColorTheme.entries.toTypedArray(), oldTheme ?: d.uiTheme),
            mapTheme = r.enum("mapTheme", ColorTheme.entries.toTypedArray(), oldTheme ?: d.mapTheme),
            ownshipPosition = r.enum("ownshipPosition", OwnshipPosition.entries.toTypedArray(), d.ownshipPosition),
            panReturnSec = r.int("panReturnSec", d.panReturnSec).coerceIn(NavSettings.PAN_RETURN_SEC_RANGE),
            autoOpenLastList = r.bool("autoOpenLastList", d.autoOpenLastList),
            keepScreenOn = r.bool("keepScreenOn", d.keepScreenOn),
            baseMap = r.bool("baseMap", d.baseMap),
            landmarkBrightness = r.int("landmarkBrightnessLevel", d.landmarkBrightness).takeIf { it in NavSettings.MAP_LINE_LEVELS } ?: d.landmarkBrightness,
            mapLineWidth = r.int("mapLineWidthLevel", d.mapLineWidth).takeIf { it in NavSettings.MAP_LINE_LEVELS } ?: d.mapLineWidth,
            mapLineBrightness = r.int("mapLineBrightnessLevel", d.mapLineBrightness).takeIf { it in NavSettings.MAP_LINE_LEVELS } ?: d.mapLineBrightness,
            // 不透明度: 選べる値は 20 / 40 / 60 / 80 / 100（20261104-D01）。前の値（20〜100）は一番近い値（真ん中なら大きい方）に読む
            buttonOpacityPct = r.str("buttonOpacityPct")?.toIntOrNull()?.let(NavSettings::readPct) ?: d.buttonOpacityPct,
            numbersOpacityPct = r.str("numbersOpacityPct")?.toIntOrNull()?.let(NavSettings::readPct) ?: d.numbersOpacityPct,
            // 読み込んだ軌跡の色: 保存がない（前の版）・読めない名前なら初期値
            trackColor = r.enum("trackColor", TrackColor.entries.toTypedArray(), d.trackColor),
            trackBrightnessPct = r.str("trackBrightnessPct")?.toIntOrNull()?.let(NavSettings::readPct) ?: d.trackBrightnessPct,
            // 距離環の数字の大きさ: 選べる値（100〜200 の 25 刻み）にない数は一番近い段に読み替える（前の版の 250 は 200）
            ringLabelScalePct = r.str("ringLabelScalePct")?.toIntOrNull()?.let(NavSettings::nearestRingLabelScalePct) ?: d.ringLabelScalePct,
            // 距離環の明るさ: 今の % → D02 の段 → D01 の段 → 初期値の順に探す（20261104-D01）
            ringBrightnessPct = r.str("ringLineBrightnessPct")?.toIntOrNull()?.let(NavSettings::readPct)
                ?: r.str("ringLineBrightnessLevel")?.toIntOrNull()?.let { NavSettings.ringBrightnessPctFromLevel(it, Tuning.RING_BRIGHTNESS_D02_FACTORS) }
                ?: r.str("ringBrightnessLevel")?.toIntOrNull()?.let { NavSettings.ringBrightnessPctFromLevel(it, Tuning.RING_BRIGHTNESS_D01_FACTORS) }
                ?: d.ringBrightnessPct,
        )
    }

    /**
     * プリセットの値を読む（キーの頭 pre）。保存がない・読めない・範囲外の値は base の値。表は base の表に、保存してある行を重ねる。
     * @param only null でなければ、この名前だけを読む（前の形の全体の値の読み替え）
     */
    private fun readProfile(r: Reader, pre: String, base: ReachProfile, only: Set<String>? = null): ReachProfile {
        fun has(k: String) = only == null || k in only
        val (enter, exit) = if (has("holdEnterKmh")) {
            NavSettings.holdSpeedsKmh(r.int("${pre}holdEnterKmh", base.holdEnterKmh), r.int("${pre}holdExitKmh", base.holdExitKmh))
        } else base.holdEnterKmh to base.holdExitKmh
        val steps = if (!has("rangeStepsKm")) null else r.str("${pre}rangeStepsKm")
            ?.split(',')
            ?.mapNotNull { it.trim().toDoubleOrNull() }
            ?.filter { it in RangeAuto.ALL_STEPS_KM }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }
        fun <T> v(k: String, read: () -> T, def: T): T = if (has(k)) read() else def
        return ReachProfile(
            reachRadiusM = v("reachRadiusM", { r.dbl("${pre}reachRadiusM", base.reachRadiusM).takeIf { it in NavSettings.REACH_RADIUS_CHOICES_M } ?: base.reachRadiusM }, base.reachRadiusM),
            sidePass = v("sidePass", { r.bool("${pre}sidePass", base.sidePass) }, base.sidePass),
            sidePassMaxM = v("sidePassMaxM", { r.dbl("${pre}sidePassMaxM", base.sidePassMaxM).inOr(NavSettings.SIDE_PASS_MAX_M_RANGE, base.sidePassMaxM) }, base.sidePassMaxM),
            sidePassDepartM = v("sidePassDepartM", { r.dbl("${pre}sidePassDepartM", base.sidePassDepartM).inOr(NavSettings.SIDE_PASS_DEPART_M_RANGE, base.sidePassDepartM) }, base.sidePassDepartM),
            passDetection = v("passDetection", { r.bool("${pre}passDetection", base.passDetection) }, base.passDetection),
            passMaxApproachM = v("passMaxApproachM", { r.dbl("${pre}passMaxApproachM", base.passMaxApproachM).inOr(NavSettings.PASS_MAX_APPROACH_M_RANGE, base.passMaxApproachM) }, base.passMaxApproachM),
            passDepartM = v("passDepartM", { r.dbl("${pre}passDepartM", base.passDepartM).inOr(NavSettings.PASS_DEPART_M_RANGE, base.passDepartM) }, base.passDepartM),
            passHoldSec = v("passHoldSec", { r.int("${pre}passHoldSec", base.passHoldSec).inOr(NavSettings.PASS_HOLD_SEC_RANGE, base.passHoldSec) }, base.passHoldSec),
            sidePassMinKmh = v("sidePassMinKmh", { r.int("${pre}sidePassMinKmh", base.sidePassMinKmh).inOr(NavSettings.SIDE_PASS_MIN_SPEED_KMH_RANGE, base.sidePassMinKmh) }, base.sidePassMinKmh),
            holdEnterKmh = enter,
            holdExitKmh = exit,
            headingMethod = v("headingMethod", { r.enum("${pre}headingMethod", HeadingMethod.entries.toTypedArray(), base.headingMethod) }, base.headingMethod),
            maxBearingAccDeg = v("maxBearingAccDeg", { r.f("${pre}maxBearingAccDeg", base.maxBearingAccDeg).inOr(NavSettings.MAX_GPS_BEARING_ACC_DEG_RANGE, base.maxBearingAccDeg) }, base.maxBearingAccDeg),
            gsAverageSec = v("gsAverageSec", { r.int("${pre}gsAverageSec", base.gsAverageSec).takeIf { it in NavSettings.GS_AVERAGE_CHOICES_SEC } ?: base.gsAverageSec }, base.gsAverageSec),
            etaAverageSec = v("etaAverageSec", { r.int("${pre}etaAverageSec", base.etaAverageSec).takeIf { it in NavSettings.ETA_AVERAGE_CHOICES_SEC } ?: base.etaAverageSec }, base.etaAverageSec),
            trackDistanceM = v("trackDistanceM", { r.int("${pre}trackDistanceM", base.trackDistanceM).inOr(NavSettings.TRACK_DISTANCE_M_RANGE, base.trackDistanceM) }, base.trackDistanceM),
            rangeStepsKm = steps ?: base.rangeStepsKm,
            // 起動時の縮尺: 段の一覧にない値は初期値（使う段にない段は、設定画面ではいちばん近い段に見える）
            initialRangeKm = v("initialRangeKm", { r.dbl("${pre}initialRangeKm", base.initialRangeKm).takeIf { it in RangeAuto.ALL_STEPS_KM } ?: base.initialRangeKm }, base.initialRangeKm),
            autoRange = v("autoRange", { r.bool("${pre}autoRange", base.autoRange) }, base.autoRange),
            autoRangeZoomInDelaySec = v("autoRangeZoomInDelaySec", {
                r.int("${pre}autoRangeZoomInDelaySec", base.autoRangeZoomInDelaySec).inOr(NavSettings.AUTO_RANGE_ZOOM_IN_DELAY_SEC_RANGE, base.autoRangeZoomInDelaySec)
            }, base.autoRangeZoomInDelaySec),
            // AUTO の詳細の限度・広域の限度: 段の一覧にない値は初期値（使う段への寄せは RangeAuto.limitsKm）
            autoMinRangeKm = v("autoMinRangeKm", { r.dbl("${pre}autoMinRangeKm", base.autoMinRangeKm).takeIf { it in RangeAuto.ALL_STEPS_KM } ?: base.autoMinRangeKm }, base.autoMinRangeKm),
            autoMaxRangeKm = v("autoMaxRangeKm", { r.dbl("${pre}autoMaxRangeKm", base.autoMaxRangeKm).takeIf { it in RangeAuto.ALL_STEPS_KM } ?: base.autoMaxRangeKm }, base.autoMaxRangeKm),
            autoHoldAfterWpSec = v("autoHoldAfterWpSec", {
                NavSettings.nearestInt(NavSettings.AUTO_HOLD_AFTER_WP_CHOICES_SEC, r.int("${pre}autoHoldAfterWpSec", base.autoHoldAfterWpSec))
            }, base.autoHoldAfterWpSec),
            // 倍率: 1.0〜2.0 の 0.1 刻み。上限を超えていた値（前の版の 3.0 まで）は 2.0 として読む
            autoZoomInDistRatio = v(KEY_ZOOM_IN_RATIO, { NavSettings.nearestZoomInDistRatio(r.dbl("$pre$KEY_ZOOM_IN_RATIO", base.autoZoomInDistRatio)) }, base.autoZoomInDistRatio),
            // 表: 「名前:縮尺」。保存にない行・読めない名前や縮尺は base の値（20261018-D04 より前の保存の形は読まない）
            mapItems = if (has(KEY_MAP_ITEMS)) base.mapItems + r.table("$pre$KEY_MAP_ITEMS") { n -> MapItem.entries.firstOrNull { it.name == n } } else base.mapItems,
            // ランドマーク: 前の mapItems の AIRPORT・PEAK、前の landmarks は引き継がない
            landmarks = if (has(KEY_LANDMARK_ITEMS)) base.landmarks + r.table("$pre$KEY_LANDMARK_ITEMS") { n -> LandmarkItem.entries.firstOrNull { it.name == n } } else base.landmarks,
            profileSize = v("profileSize", { r.enum("${pre}profileSize", ProfileSize.entries.toTypedArray(), base.profileSize) }, base.profileSize),
        )
    }

    /** 保存の文字のマップを読む道具 */
    private class Reader(private val m: Map<String, String?>) {
        fun str(k: String) = m[k]?.trim()
        fun f(k: String, def: Float) = str(k)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: def
        fun dbl(k: String, def: Double) = str(k)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: def
        fun int(k: String, def: Int) = str(k)?.toIntOrNull() ?: def
        fun bool(k: String, def: Boolean) = str(k)?.toBooleanStrictOrNull() ?: def
        fun <E : Enum<E>> enum(k: String, values: Array<E>, def: E) = values.firstOrNull { it.name == str(k) } ?: def
        fun <K> table(k: String, key: (String) -> K?): Map<K, MapRange> = str(k)?.split(',')?.mapNotNull { e ->
            val (n, v) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val item = key(n.trim()) ?: return@mapNotNull null
            val range = MapRange.fromSavedName(v.trim()) ?: return@mapNotNull null
            item to range
        }.orEmpty().toMap()
    }

    /** 範囲内ならその値、範囲外なら def */
    private fun <T : Comparable<T>> T.inOr(range: ClosedRange<T>, def: T): T = if (this in range) this else def
}
