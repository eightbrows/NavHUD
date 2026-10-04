package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.io.CoordinateText
import io.github.eightbrows.navhud.core.io.TimeText
import io.github.eightbrows.navhud.core.model.ReachReason
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.WaypointTimes
import io.github.eightbrows.navhud.core.view.HudFormat
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

private val Body get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = HudColors.Scale)
private val Small get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = HudColors.ScaleDim)

/**
 * WP設定画面（§6.5）。≡ のドラッグで並べ替え、有効/無効スイッチ、タップで編集。
 * 追加・貼り付け・インポート・エクスポート。アプリ内には保存しない。
 */
@Composable
fun WaypointSettingsScreen(
    state: NavState,
    wpUi: WaypointUiState,
    defaultName: () -> String,
    onBack: () -> Unit,
    onAdd: (Waypoint) -> Unit,
    onUpdate: (Int, Waypoint) -> Unit,
    onDelete: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onSetEnabled: (Int, Boolean) -> Unit,
    onPaste: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onReverse: () -> Unit,
    onAdjustTimes: (Int, LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wps = state.waypoints
    // 編集中の WP の番号。-1 は新規
    var editing by remember { mutableStateOf<Int?>(null) }
    var confirmReverse by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudButton(stringResource(R.string.back), onBack)
            Text(stringResource(R.string.wp_settings_title), style = Body.copy(fontSize = 18.sp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton(stringResource(R.string.wp_add), { editing = -1 })
            HudButton(stringResource(R.string.wp_paste), onPaste)
            HudButton(stringResource(R.string.wp_import), onImport)
            HudButton(stringResource(R.string.wp_export), onExport, enabled = wps.isNotEmpty())
            HudButton(stringResource(R.string.wp_reverse), { confirmReverse = true }, enabled = wps.size >= 2)
            HudButton(stringResource(R.string.wp_adjust_times), { adjusting = true }, enabled = wps.any { it.targetTime != null })
        }
        Text(
            stringResource(
                R.string.wp_list_label,
                wpUi.listName ?: stringResource(R.string.wp_list_none),
                if (wpUi.dirty) stringResource(R.string.wp_list_dirty) else "",
            ),
            style = Small.copy(color = if (wpUi.dirty) HudColors.Caution else HudColors.ScaleDim),
        )
        Text(stringResource(R.string.wp_not_saved_note), style = Small.copy(fontSize = 11.sp))
        if (wpUi.loading) Text(stringResource(R.string.loading), style = Small.copy(color = HudColors.Caution))
        wpUi.message?.let { Text(it.asString(), style = Small.copy(color = HudColors.Caution)) }

        if (wps.isEmpty()) {
            Text(stringResource(R.string.wp_empty), style = Body.copy(color = HudColors.ScaleDim))
        }
        ReorderableWaypointList(
            wps = wps,
            nextIndex = state.nextWpIndex,
            onMove = onMove,
            onEdit = { editing = it },
            onSetEnabled = onSetEnabled,
            modifier = Modifier.weight(1f),
        )
    }

    if (confirmReverse) {
        AlertDialog(
            onDismissRequest = { confirmReverse = false },
            title = { Text(stringResource(R.string.wp_reverse)) },
            text = { Text(stringResource(R.string.wp_reverse_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onReverse()
                    confirmReverse = false
                }) { Text(stringResource(R.string.wp_reverse)) }
            },
            dismissButton = { TextButton(onClick = { confirmReverse = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (adjusting) {
        TimeAdjustDialog(
            wps = wps,
            onApply = { i, t ->
                onAdjustTimes(i, t)
                adjusting = false
            },
            onDismiss = { adjusting = false },
        )
    }

    editing?.let { i ->
        val isNew = i < 0
        val initial = if (isNew) Waypoint(defaultName(), Double.NaN, Double.NaN) else wps.getOrNull(i)
        if (initial == null) {
            editing = null
        } else {
            WaypointEditDialog(
                initial = initial,
                isNew = isNew,
                onSave = { wp ->
                    if (isNew) onAdd(wp) else onUpdate(i, wp)
                    editing = null
                },
                onDelete = {
                    onDelete(i)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
}

/**
 * ≡ をつまんでドラッグすると並べ替える（外部ライブラリなし）。
 * 隣の行の中央を越えたら入れ替える。画面端での自動スクロールはしない。
 */
@Composable
private fun ReorderableWaypointList(
    wps: List<Waypoint>,
    nextIndex: Int?,
    onMove: (Int, Int) -> Unit,
    onEdit: (Int) -> Unit,
    onSetEnabled: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val move by rememberUpdatedState(onMove)

    LazyColumn(modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(wps) { i, wp ->
            val index by rememberUpdatedState(i)
            val dragging = dragIndex == i
            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .background(if (dragging) HudColors.Frame else HudColors.Background)
                    .border(0.5.dp, HudColors.Frame),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 並べ替えのつまみ。長押しを待たずにすぐドラッグできる
                Box(
                    Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    dragIndex = index
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    dragIndex = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragIndex = null
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val di = dragIndex ?: return@detectDragGestures
                                    val items = listState.layoutInfo.visibleItemsInfo
                                    if (dragOffset > 0) {
                                        val next = items.firstOrNull { it.index == di + 1 } ?: return@detectDragGestures
                                        if (dragOffset > next.size / 2f) {
                                            move(di, di + 1)
                                            dragIndex = di + 1
                                            dragOffset -= next.size
                                        }
                                    } else {
                                        val prev = items.firstOrNull { it.index == di - 1 } ?: return@detectDragGestures
                                        if (-dragOffset > prev.size / 2f) {
                                            move(di, di - 1)
                                            dragIndex = di - 1
                                            dragOffset += prev.size
                                        }
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("≡", style = Body.copy(fontSize = 22.sp, color = HudColors.ScaleDim))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onEdit(i) }
                        .padding(vertical = 6.dp),
                ) {
                    val nameColor = when {
                        !wp.enabled -> HudColors.WpDisabled
                        i == nextIndex -> HudColors.Active
                        else -> HudColors.Scale
                    }
                    Text(
                        "${i + 1}. ${wp.name}" + if (wp.reached) "  ✓" else "",
                        style = Body.copy(color = nameColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(CoordinateText.format(wp.lat, wp.lon), style = Small, maxLines = 1)
                    val details = listOfNotNull(
                        wp.eleM?.let { stringResource(R.string.wp_detail_elevation, "%.0f".format(Locale.US, it)) },
                        wp.targetTime?.let { stringResource(R.string.wp_detail_target, TimeText.format(it)) },
                        wp.deadlineTime?.let { stringResource(R.string.wp_detail_deadline, TimeText.format(it)) },
                        wp.radiusM?.let { stringResource(R.string.wp_detail_radius, "%.0f".format(Locale.US, it)) },
                    )
                    if (details.isNotEmpty()) Text(details.joinToString("  "), style = Small, maxLines = 1)
                    // 到達済みなら、到達の理由・時刻・最接近（§5.4）
                    wp.reach?.takeIf { wp.reached }?.let {
                        Text(reachText(HudFormat.reach(it, ZoneId.systemDefault())), style = Small.copy(color = HudColors.Caution), maxLines = 1)
                    }
                }
                Switch(
                    checked = wp.enabled,
                    onCheckedChange = { onSetEnabled(i, it) },
                    // マゼンタは「次の WP」専用なので、スイッチは白〜グレー
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HudColors.Background,
                        checkedTrackColor = HudColors.Scale,
                        checkedBorderColor = HudColors.Scale,
                        uncheckedThumbColor = HudColors.ScaleDim,
                        uncheckedTrackColor = HudColors.Background,
                        uncheckedBorderColor = HudColors.WpDisabled,
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** WP の編集（名前、"lat, lon"、標高、目標時刻、締切時刻）。 */
@Composable
private fun WaypointEditDialog(
    initial: Waypoint,
    isNew: Boolean,
    onSave: (Waypoint) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var coord by remember {
        mutableStateOf(if (initial.lat.isNaN()) "" else CoordinateText.format(initial.lat, initial.lon))
    }
    var ele by remember { mutableStateOf(initial.eleM?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()) }
    var target by remember { mutableStateOf(initial.targetTime?.let(TimeText::format).orEmpty()) }
    var deadline by remember { mutableStateOf(initial.deadlineTime?.let(TimeText::format).orEmpty()) }
    var radius by remember { mutableStateOf(initial.radiusM?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()) }
    var error by remember { mutableStateOf<Int?>(null) }

    fun save() {
        val ll = CoordinateText.parse(coord)
            ?: return run { error = R.string.wp_err_coord }
        val eleM = if (ele.isBlank()) null else ele.trim().toDoubleOrNull()
            ?: return run { error = R.string.wp_err_elevation }
        val t = if (target.isBlank()) null else TimeText.parse(target)
            ?: return run { error = R.string.wp_err_target }
        val d = if (deadline.isBlank()) null else TimeText.parse(deadline)
            ?: return run { error = R.string.wp_err_deadline }
        val r = if (radius.isBlank()) null else radius.trim().toDoubleOrNull()?.takeIf { it > 0 }
            ?: return run { error = R.string.wp_err_radius }
        onSave(
            initial.copy(
                name = name.trim().ifEmpty { initial.name },
                lat = ll.lat,
                lon = ll.lon,
                eleM = eleM,
                targetTime = t,
                deadlineTime = d,
                radiusM = r,
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.wp_add_title else R.string.wp_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.wp_name)) }, singleLine = true)
                OutlinedTextField(
                    coord, { coord = it },
                    label = { Text("lat, lon") },
                    placeholder = { Text("34.69370, 135.50230") },
                    singleLine = true,
                )
                OutlinedTextField(
                    ele, { ele = it },
                    label = { Text(stringResource(R.string.wp_elevation_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(target, { target = it }, label = { Text(stringResource(R.string.wp_target_hint)) }, singleLine = true)
                OutlinedTextField(deadline, { deadline = it }, label = { Text(stringResource(R.string.wp_deadline_hint)) }, singleLine = true)
                OutlinedTextField(
                    radius, { radius = it },
                    label = { Text(stringResource(R.string.wp_radius_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                error?.let { Text(stringResource(it), color = HudColors.Warning, fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = HudColors.Warning) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/**
 * 時刻を一括調整（§6.5）: 基準の WP（目標時刻の入った WP から選ぶ。既定は先頭の有効な WP）と、その新しい目標時刻。
 * 元の隣どうしの時間差を保って、前後の目標時刻（と締切）を決め直す。
 */
@Composable
private fun TimeAdjustDialog(
    wps: List<Waypoint>,
    onApply: (Int, LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val timed = wps.indices.filter { wps[it].targetTime != null }
    var base by remember { mutableIntStateOf(WaypointTimes.defaultBaseIndex(wps) ?: timed.first()) }
    var time by remember { mutableStateOf(wps[base].targetTime?.let(TimeText::format).orEmpty()) }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wp_adjust_times)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.wp_base), fontSize = 13.sp)
                Column(
                    Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (i in timed) {
                        val wp = wps[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    base = i
                                    time = wp.targetTime?.let(TimeText::format).orEmpty()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = base == i, onClick = null)
                            Text(
                                "${i + 1}. ${wp.name}  ${TimeText.format(wp.targetTime!!)}" + if (!wp.enabled) stringResource(R.string.wp_disabled_suffix) else "",
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                OutlinedTextField(time, { time = it }, label = { Text(stringResource(R.string.wp_base_target_hint)) }, singleLine = true)
                Text(stringResource(R.string.wp_adjust_note), fontSize = 12.sp)
                error?.let { Text(stringResource(it), color = HudColors.Warning, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = TimeText.parse(time) ?: return@TextButton run { error = R.string.wp_err_time }
                onApply(base, t)
            }) { Text(stringResource(R.string.wp_adjust)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** 到達の理由の 1 行（§5.4）: 「真横 08:12:34 最接近 42 m」。シークで飛ばした区間なら「シーク・真横 …」。 */
@Composable
private fun reachText(r: HudFormat.ReachLine): String {
    val reason = stringResource(
        when (r.reason) {
            ReachReason.RADIUS -> R.string.reach_radius
            ReachReason.ARRIVAL -> R.string.reach_arrival
            ReachReason.SIDE -> R.string.reach_side
            ReachReason.PASS -> R.string.reach_pass
            ReachReason.MANUAL -> R.string.reach_manual
        },
    )
    val head = if (r.viaSeek) stringResource(R.string.reach_seek, reason) else reason
    val closest = r.closestM?.let { stringResource(R.string.reach_closest, it) }
    return listOfNotNull(head, r.time, closest).joinToString(" ")
}
