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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.eightbrows.navhud.core.io.CoordinateText
import io.github.eightbrows.navhud.core.io.TimeText
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.NavState

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
    modifier: Modifier = Modifier,
) {
    val wps = state.waypoints
    // 編集中の WP の番号。-1 は新規
    var editing by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudButton("← 戻る", onBack)
            Text("WP設定", style = Body.copy(fontSize = 18.sp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton("追加", { editing = -1 })
            HudButton("貼り付け", onPaste)
            HudButton("インポート", onImport)
            HudButton("エクスポート", onExport, enabled = wps.isNotEmpty())
        }
        Text(
            "リスト: " + (wpUi.listName ?: "なし") + if (wpUi.dirty) "（未エクスポートの変更あり）" else "",
            style = Small.copy(color = if (wpUi.dirty) HudColors.Caution else HudColors.ScaleDim),
        )
        Text("アプリ内には保存しません。未エクスポートの編集はアプリ終了で失われます。", style = Small.copy(fontSize = 11.sp))
        if (wpUi.loading) Text("読み込み中…", style = Small.copy(color = HudColors.Caution))
        wpUi.message?.let { Text(it, style = Small.copy(color = HudColors.Caution)) }

        if (wps.isEmpty()) {
            Text("WP がありません。追加・貼り付け・インポートで作ってください。", style = Body.copy(color = HudColors.ScaleDim))
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
                        wp.eleM?.let { "標高 %.0f m".format(it) },
                        wp.targetTime?.let { "目標 ${TimeText.format(it)}" },
                        wp.deadlineTime?.let { "締切 ${TimeText.format(it)}" },
                        wp.radiusM?.let { "半径 %.0f m".format(it) },
                    )
                    if (details.isNotEmpty()) Text(details.joinToString("  "), style = Small, maxLines = 1)
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
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val ll = CoordinateText.parse(coord)
            ?: return run { error = "座標は「34.69370, 135.50230」の形で入力してください" }
        val eleM = if (ele.isBlank()) null else ele.trim().toDoubleOrNull()
            ?: return run { error = "標高は数値で入力してください（空欄可）" }
        val t = if (target.isBlank()) null else TimeText.parse(target)
            ?: return run { error = "目標時刻は 9:30 や 09:30:00 の形で入力してください（空欄可）" }
        val d = if (deadline.isBlank()) null else TimeText.parse(deadline)
            ?: return run { error = "締切時刻は 9:30 や 09:30:00 の形で入力してください（空欄可）" }
        val r = if (radius.isBlank()) null else radius.trim().toDoubleOrNull()?.takeIf { it > 0 }
            ?: return run { error = "到達半径は 0 より大きい数値で入力してください（空欄で全体の設定）" }
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
        title = { Text(if (isNew) "WP を追加" else "WP を編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名前") }, singleLine = true)
                OutlinedTextField(
                    coord, { coord = it },
                    label = { Text("lat, lon") },
                    placeholder = { Text("34.69370, 135.50230") },
                    singleLine = true,
                )
                OutlinedTextField(
                    ele, { ele = it },
                    label = { Text("標高 m（空欄可）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(target, { target = it }, label = { Text("目標時刻 H:mm（空欄可）") }, singleLine = true)
                OutlinedTextField(deadline, { deadline = it }, label = { Text("締切時刻 H:mm（空欄可）") }, singleLine = true)
                OutlinedTextField(
                    radius, { radius = it },
                    label = { Text("到達半径 m（空欄で全体の設定）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                error?.let { Text(it, color = HudColors.Warning, fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("保存") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("削除", color = HudColors.Warning) }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        },
    )
}
