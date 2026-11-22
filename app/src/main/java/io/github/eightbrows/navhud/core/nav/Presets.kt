package io.github.eightbrows.navhud.core.nav

/**
 * 移動のプリセットの決まり（20261121-D03）。Android に依存しない。
 *
 * - どのプリセットも値を変えられる。変えたかどうかは、自動車は自動車の初期値、歩行は歩行の初期値、
 *   カスタムは元（自動車か歩行）の初期値と比べる。共通の値はアプリの初期値（NavSettings()）と比べる。
 * - 端末には初期値から変えた所だけを保存する（SettingsCodec）。変えていない項目は、アプリの初期値が変わったら新しい値に付いていく。
 * - 初期値と同じ値に戻せば、変えた所ではなくなる（いつも初期値と比べるので、別に覚えない）。
 */

/**
 * 比べる初期値。ふだんは CURRENT（アプリの初期値）。テストでは差し替えて、初期値が変わったときの付いていき方を確かめる
 * @param common 共通の値の初期値（プリセットの値の所は使わない）
 * @param car 自動車の初期値
 * @param walk 歩行の初期値
 */
data class SettingsDefaults(val common: NavSettings, val car: ReachProfile, val walk: ReachProfile) {
    /** 元のプリセット（自動車か歩行）の初期値 */
    fun of(base: TravelMode): ReachProfile = if (base == TravelMode.WALK) walk else car

    /** 初期値のままの設定（全部のプリセットが初期値、自動車を選んでいる） */
    fun initialSettings(): NavSettings = common.copy(
        carProfile = car,
        walkProfile = walk,
        customReach = List(3) { car },
        customBase = List(3) { TravelMode.CAR },
        customCopied = List(3) { false },
        travelMode = TravelMode.CAR,
    ).selectTravelMode(TravelMode.CAR)

    companion object {
        val CURRENT: SettingsDefaults by lazy { SettingsDefaults(NavSettings(), ReachProfile.CAR, ReachProfile.WALK) }
    }
}

/** そのプリセットの元（自動車は自動車、歩行は歩行、カスタムは customBase） */
fun NavSettings.baseOf(m: TravelMode): TravelMode = when (m) {
    TravelMode.CAR, TravelMode.WALK -> m
    else -> customBase.getOrNull(m.customIndex!!) ?: TravelMode.CAR
}

/** そのプリセットと比べる初期値（元の初期値） */
fun NavSettings.defaultProfileOf(m: TravelMode, d: SettingsDefaults = SettingsDefaults.CURRENT): ReachProfile = d.of(baseOf(m))

/**
 * 今のプリセットの初期値を入れた NavSettings（設定画面の「（初期値：…）」と、変えた値の強調に使う）。
 * プリセットの値の所は今のプリセットの元の初期値、共通の値の所はアプリの初期値
 */
fun NavSettings.defaultsForDisplay(d: SettingsDefaults = SettingsDefaults.CURRENT): NavSettings =
    d.common.withReachValues(defaultProfileOf(travelMode, d))

/**
 * プリセットの値の 1 つ 1 つ（変えた所の数を数える単位）。地図の項目の表・ランドマークの表は行ごとに 1 つ。
 * 方位を止める / 戻す速度は 2 つ
 */
object PresetFields {
    val ALL: List<Pair<String, (ReachProfile) -> Any?>> = listOf<Pair<String, (ReachProfile) -> Any?>>(
        "headingMethod" to { it.headingMethod },
        "holdEnterKmh" to { it.holdEnterKmh },
        "holdExitKmh" to { it.holdExitKmh },
        "maxBearingAccDeg" to { it.maxBearingAccDeg },
        "trackDistanceM" to { it.trackDistanceM },
        "avgHeadingLine" to { it.avgHeadingLine },
        "avgHeadingSec" to { it.avgHeadingSec },
        "trackStepM" to { it.trackStepM },
        "reachRadiusM" to { it.reachRadiusM },
        "sidePass" to { it.sidePass },
        "sidePassMaxM" to { it.sidePassMaxM },
        "sidePassDepartM" to { it.sidePassDepartM },
        "sidePassMinKmh" to { it.sidePassMinKmh },
        "passDetection" to { it.passDetection },
        "passMaxApproachM" to { it.passMaxApproachM },
        "passDepartM" to { it.passDepartM },
        "passHoldSec" to { it.passHoldSec },
        "rangeStepsKm" to { it.rangeStepsKm },
        "initialRangeKm" to { it.initialRangeKm },
        "autoRange" to { it.autoRange },
        "autoMinRangeKm" to { it.autoMinRangeKm },
        "autoMaxRangeKm" to { it.autoMaxRangeKm },
        "autoRangeZoomInDelaySec" to { it.autoRangeZoomInDelaySec },
        "autoHoldAfterWpSec" to { it.autoHoldAfterWpSec },
        "autoZoomInDistRatio" to { it.autoZoomInDistRatio },
        "profileSize" to { it.profileSize },
        "gsAverageSec" to { it.gsAverageSec },
        "etaAverageSec" to { it.etaAverageSec },
    ) + MapItem.entries.map { item -> "mapItems.${item.name}" to { p: ReachProfile -> p.mapItems[item] ?: MapRange.OFF } } +
        LandmarkItem.entries.map { item -> "landmarks.${item.name}" to { p: ReachProfile -> p.landmarks[item] ?: MapRange.OFF } }
}

/** base（初期値）と比べて変えた所の名前（PresetFields の名前）。 */
fun ReachProfile.changesFrom(base: ReachProfile): List<String> = PresetFields.ALL.filter { (_, get) -> get(this) != get(base) }.map { it.first }

/** そのプリセットの変えた所の数（元の初期値と比べる）。 */
fun NavSettings.changedCount(m: TravelMode, d: SettingsDefaults = SettingsDefaults.CURRENT): Int =
    profileOf(m).changesFrom(defaultProfileOf(m, d)).size

/**
 * 共通の値の変えた所の数（アプリの初期値と比べる。20261121-D04）。
 * 表示言語（NavSettings の外。初期値は端末に合わせる）も、設定画面で共通の値として強調するので数に入れる
 */
fun NavSettings.commonChangedCount(language: LanguageMode = LanguageMode.SYSTEM, d: SettingsDefaults = SettingsDefaults.CURRENT): Int =
    SettingsCodec.commonChanges(this, d).size + if (language != LanguageMode.SYSTEM) 1 else 0

/**
 * 確かめの枠の種類（20261121-D04 で、設定画面の決まりを core に移した）。
 * RESET_PRESET: 自動車・歩行を初期値に戻す、RESET_CUSTOM: カスタムを自動車 / 歩行の初期値に、COPY: カスタムにコピー、
 * COPY_OVERWRITE: コピーで変えた所を消すときの確かめ、RESET_ALL: 設定を初期値に戻す（全部のプリセットと共通の値）
 */
enum class ConfirmKind { RESET_PRESET, RESET_CUSTOM, COPY, COPY_OVERWRITE, RESET_ALL }

/** 帯の「初期値に戻す」を押したときの確かめ。自動車・歩行で変えた所がなければ null（押せない） */
fun NavSettings.bandResetConfirm(): ConfirmKind? = when {
    travelMode.customIndex != null -> ConfirmKind.RESET_CUSTOM
    changedCount(travelMode) > 0 -> ConfirmKind.RESET_PRESET
    else -> null
}

/** コピーで今の値を入れるときの確かめ。そのカスタムに変えた所があれば上書きの確かめ、なければ null（そのまま入れる） */
fun NavSettings.copyConfirm(m: TravelMode): ConfirmKind? = if (changedCount(m) > 0) ConfirmKind.COPY_OVERWRITE else null

/** 「設定を初期値に戻す」の確かめ（全部のプリセットと共通の値を消すので、いつも出す。20261121-D04） */
fun NavSettings.resetAllConfirm(): ConfirmKind = ConfirmKind.RESET_ALL

/** 自動車・歩行を初期値に戻す（共通の値・ほかのプリセットは変えない）。カスタムは resetCustom を使う */
fun NavSettings.resetPreset(m: TravelMode, d: SettingsDefaults = SettingsDefaults.CURRENT): NavSettings {
    require(m == TravelMode.CAR || m == TravelMode.WALK)
    return withProfile(m, d.of(m))
}

/** カスタムを、元 base（自動車か歩行）の初期値にする（変えた所は消える。元も base になる） */
fun NavSettings.resetCustom(m: TravelMode, base: TravelMode, d: SettingsDefaults = SettingsDefaults.CURRENT): NavSettings {
    val i = m.customIndex!!
    require(base == TravelMode.CAR || base == TravelMode.WALK)
    return copy(
        customBase = customBase.toMutableList().also { it[i] = base },
        customCopied = customCopied.toMutableList().also { it[i] = false },
    ).withProfile(m, d.of(base))
}

/** カスタムに、今の自動車・今の歩行の値（変えた所も含めて）を入れる。元はそのプリセットになる */
fun NavSettings.copyToCustom(m: TravelMode, src: TravelMode): NavSettings {
    val i = m.customIndex!!
    require(src == TravelMode.CAR || src == TravelMode.WALK)
    return copy(
        customBase = customBase.toMutableList().also { it[i] = src },
        customCopied = customCopied.toMutableList().also { it[i] = true },
    ).withProfile(m, profileOf(src))
}

/** 「設定を初期値に戻す」（§6.9。20261121-D03 から全部のプリセットと共通の値を初期値にする。前はカスタムの値を残した） */
fun NavSettings.resetAll(d: SettingsDefaults = SettingsDefaults.CURRENT): NavSettings = d.initialSettings()
