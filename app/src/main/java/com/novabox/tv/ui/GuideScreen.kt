package com.novabox.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.novabox.tv.data.Channel
import com.novabox.tv.data.EpgEntry
import com.novabox.tv.data.PlayRequest
import java.util.Date

internal const val HALF_HOUR = 30 * 60_000L
/** How much time the guide shows at once. */
internal const val GUIDE_SPAN = 4 * HALF_HOUR
/** How far ahead the guide can be moved. */
internal const val GUIDE_AHEAD = 24 * 60 * 60_000L

/** One box in a guide row: a programme, or a stretch with no information ([entry] null). */
data class GuideCell(val entry: EpgEntry?, val start: Long, val end: Long)

internal fun floorHalfHour(t: Long) = t - Math.floorMod(t, HALF_HOUR)
internal fun ceilHalfHour(t: Long) = floorHalfHour(t + HALF_HOUR - 1)

/**
 * The time ▲ ▼ keep to after focusing [cell]: null ("whatever is on now") for the programme on
 * now, else the cell's start (or the window's left edge for a programme that began earlier).
 */
internal fun guideAnchor(cell: GuideCell, now: Long, windowStart: Long): Long? =
    if (now >= cell.start && now < cell.end) null else maxOf(cell.start, windowStart)

/** The time to focus in the next row: now (kept on screen), or the anchored time. */
internal fun guideTarget(anchor: Long?, now: Long, windowStart: Long): Long =
    anchor ?: now.coerceIn(windowStart, windowStart + GUIDE_SPAN - 1)

/**
 * Where the guide's window should move when the clock ticks from [before] to [now]: to the new
 * current half hour if the window was showing the current time (or [force], on returning to the
 * app). Null to stay, e.g. while you're looking at other times.
 */
internal fun followClock(windowStart: Long, before: Long, now: Long, force: Boolean = false): Long? {
    val following = windowStart == floorHalfHour(before)
    val target = floorHalfHour(now)
    return if ((force || following) && windowStart != target) target else null
}

/**
 * Lays one channel's programmes out between [from] and [to]: clipped to the window, overlaps
 * trimmed, and gaps of [minGap] or more filled with "no information" cells so every part of
 * the row can be reached with the D-pad. Smaller gaps are absorbed by the next programme.
 */
fun guideCells(entries: List<EpgEntry>, from: Long, to: Long, minGap: Long = 5 * 60_000L): List<GuideCell> {
    val out = mutableListOf<GuideCell>()
    var cursor = from
    for (e in entries.filter { it.end > it.start && it.end > from && it.start < to }.sortedBy { it.start }) {
        var s = maxOf(e.start, cursor)
        val en = minOf(e.end, to)
        if (en <= s) continue
        if (s - cursor >= minGap) out += GuideCell(null, cursor, s) else s = cursor
        out += GuideCell(e, s, en)
        cursor = en
    }
    if (to - cursor >= minGap || out.isEmpty()) out += GuideCell(null, cursor, to)
    else if (cursor < to) out[out.lastIndex] = out.last().copy(end = to)
    return out
}

/** Where the window goes when ◀/▶ is pressed at its edge, and the time to focus there. Null: it can't move. */
internal fun guideShift(cell: GuideCell, windowStart: Long, now: Long, forward: Boolean): Pair<Long, Long>? {
    val minStart = floorHalfHour(now)
    return if (forward) {
        if (cell.end < windowStart + GUIDE_SPAN) return null
        val target = maxOf(cell.entry?.end ?: cell.end, cell.end)
        val next = maxOf(windowStart + HALF_HOUR, ceilHalfHour(target + HALF_HOUR - GUIDE_SPAN))
        if (next > minStart + GUIDE_AHEAD) null else next to target
    } else {
        if (cell.start > windowStart || windowStart <= minStart) return null
        val target = minOf(cell.entry?.start ?: cell.start, cell.start) - 1
        val next = minOf(windowStart - HALF_HOUR, floorHalfHour(target)).coerceAtLeast(minStart)
        next to maxOf(target, next)
    }
}

/** The TV guide: one row per channel, programmes laid out on a time line. OK plays the channel. */
@Composable
fun TvGuide(
    vm: AppViewModel, channels: List<Channel>, onPlay: (PlayRequest) -> Unit, modifier: Modifier = Modifier,
    /** Focuses the programme on now in the top visible row; used for ▶ from the categories. */
    entry: FocusRequester = remember { FocusRequester() },
    /** ◀ from the first column; returns true if it moved focus. */
    onLeave: () -> Boolean = { false },
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var windowStart by remember { mutableLongStateOf(floorHalfHour(System.currentTimeMillis())) }
    // After the window moves: which row and time should get focus.
    var focusWant by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var info by remember { mutableStateOf<Pair<Channel, GuideCell>?>(null) }
    // The time ▲ ▼ aim for in the next row: "now" (null) until you move along the row.
    var anchor by remember { mutableStateOf<Long?>(null) }
    var movingVertically by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // The guide follows the clock: unless you have moved it to other times, it starts at the
    // current half hour and moves on by itself as time passes.
    fun tick(force: Boolean = false) {
        val t = System.currentTimeMillis()
        val next = followClock(windowStart, now, t, force)
        now = t
        if (next != null) {
            windowStart = next
            info?.let { (c, _) -> focusWant = c.id to t }
        }
    }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(15_000); tick() } }
    // Coming back to the app (from the player, the screensaver or another app) snaps to now.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { tick(force = true); onPauseOrDispose { } }
    val fmt = rememberTimeFormat(showAmPm = false)
    val windowEnd = windowStart + GUIDE_SPAN

    if (channels.isEmpty()) { CenterMessage("No channels", modifier); return }

    Column(modifier.fillMaxHeight()) {
        SectionTitle("TV GUIDE")
        GuideDetail(info, fmt)
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val nameW = 168.dp
            val lineW = maxWidth - nameW
            fun x(t: Long): Dp = lineW * ((t - windowStart).toFloat() / GUIDE_SPAN)
            Column {
                // Time header
                Row(Modifier.height(24.dp)) {
                    Text(
                        java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(Date(windowStart)),
                        fontSize = 12.sp, color = Palette.muted, modifier = Modifier.width(nameW).padding(start = 4.dp),
                    )
                    Box(Modifier.width(lineW).fillMaxHeight()) {
                        var t = windowStart
                        while (t < windowEnd) {
                            Text(fmt.format(Date(t)), fontSize = 12.sp, color = Palette.muted, modifier = Modifier.offset(x = x(t) + 4.dp))
                            t += HALF_HOUR
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        itemsIndexed(channels, key = { _, c -> c.id }) { index, c ->
                            LaunchedEffect(c.id) { vm.loadGuide(c) }
                            val entries = vm.epg[c.id]
                            val cells = remember(entries, windowStart) {
                                if (entries == null) listOf(GuideCell(null, windowStart, windowEnd)) else guideCells(entries, windowStart, windowEnd)
                            }
                            val entryCell = if (index == listState.firstVisibleItemIndex) cells.firstOrNull { now >= it.start && now < it.end } ?: cells.first() else null
                            Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.width(nameW).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${c.number}", fontSize = 12.sp, color = Palette.muted, modifier = Modifier.width(30.dp))
                                    Logo(c.logo, c.name, 30.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(c.name, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                }
                                Box(Modifier.width(lineW).fillMaxHeight()) {
                                    cells.forEach { cell ->
                                        // The first cell keeps its key when the row's guide arrives,
                                        // so a focused "Loading…" cell hands focus to the programme.
                                        key(if (cell.start == windowStart) -1L else cell.start) {
                                            GuideCellBox(
                                                cell, entries == null, now, fmt,
                                                Modifier.offset(x = x(cell.start)).width(x(cell.end) - x(cell.start)).fillMaxHeight()
                                                    .then(if (cell === entryCell) Modifier.focusRequester(entry) else Modifier),
                                                wantFocus = focusWant?.let { (id, t) -> id == c.id && t >= cell.start && t < cell.end } == true,
                                                onFocused = {
                                                    focusWant = null; info = c to cell
                                                    // ◀ ▶ (or a time jump) sets the time that ▲ ▼ keep to; ▲ ▼ themselves don't.
                                                    if (!movingVertically) anchor = guideAnchor(cell, System.currentTimeMillis(), windowStart)
                                                    movingVertically = false
                                                },
                                                onVertical = { step ->
                                                    val next = channels.getOrNull(index + step) ?: return@GuideCellBox false
                                                    movingVertically = true
                                                    focusWant = next.id to guideTarget(anchor, System.currentTimeMillis(), windowStart)
                                                    // Keep the row on screen; its cell takes focus once it's there.
                                                    val visible = listState.layoutInfo.visibleItemsInfo
                                                    val first = visible.firstOrNull()?.index ?: 0
                                                    val last = visible.lastOrNull { it.offset + it.size <= listState.layoutInfo.viewportEndOffset }?.index ?: first
                                                    if (index + step < first) scope.launch { listState.animateScrollToItem(index + step) }
                                                    else if (index + step > last) scope.launch { listState.animateScrollToItem((first + (index + step - last)).coerceAtLeast(0)) }
                                                    true
                                                },
                                                onEdge = { forward ->
                                                    val move = guideShift(cell, windowStart, System.currentTimeMillis(), forward)
                                                    if (move != null) { windowStart = move.first; focusWant = c.id to move.second }
                                                    // At the left edge of now: back to the "TV guide" category, not
                                                    // whichever category happens to sit beside this row.
                                                    move != null || (!forward && cell.start <= windowStart && onLeave())
                                                },
                                                onClick = { onPlay(PlayRequest.Live(channels, index)) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // The "now" line
                    if (now in windowStart until windowEnd) {
                        Box(Modifier.offset(x = nameW + x(now) - 1.dp).width(2.dp).fillMaxHeight().background(Palette.teal.copy(alpha = 0.8f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideDetail(info: Pair<Channel, GuideCell>?, fmt: java.text.SimpleDateFormat) {
    Column(Modifier.fillMaxWidth().height(92.dp).background(Palette.bg2, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
        if (info == null) {
            Text("Move through the guide with the arrows", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("OK watches the channel  ·  ◀ ▶ at the edge moves the time", fontSize = 13.sp, color = Palette.muted)
            return@Column
        }
        val (c, cell) = info
        val e = cell.entry
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, fontSize = 13.sp, color = Palette.teal, fontWeight = FontWeight.Bold, maxLines = 1)
            if (e != null) {
                Text("  ·  ${fmt.format(Date(e.start))}–${fmt.format(Date(e.end))}", fontSize = 13.sp, color = Palette.muted, maxLines = 1)
                if (e.isLive()) Text("  ·  ON NOW", fontSize = 12.sp, color = Palette.teal, fontWeight = FontWeight.Bold)
            }
        }
        Text(e?.title ?: "No guide information", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!e?.description.isNullOrBlank()) Text(e!!.description, fontSize = 13.sp, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GuideCellBox(
    cell: GuideCell, loading: Boolean, now: Long, fmt: java.text.SimpleDateFormat, modifier: Modifier,
    wantFocus: Boolean, onFocused: () -> Unit, onEdge: (forward: Boolean) -> Boolean, onClick: () -> Unit,
    onVertical: (step: Int) -> Boolean = { false },
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(wantFocus) { if (wantFocus) { kotlinx.coroutines.delay(30); runCatching { fr.requestFocus() } } }
    val e = cell.entry
    val live = e != null && e.start <= now && now < e.end
    FocusSurface(
        onClick = onClick,
        onFocus = onFocused,
        shape = RoundedCornerShape(6.dp),
        focusedScale = 1.0f,
        container = if (live) Color(0xFF1E2C4D) else Palette.card,
        modifier = modifier.padding(horizontal = 1.dp).focusRequester(fr).onPreviewKeyEvent { k ->
            if (k.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (k.key) {
                Key.DirectionRight -> onEdge(true)
                Key.DirectionLeft -> onEdge(false)
                Key.DirectionUp -> onVertical(-1)
                Key.DirectionDown -> onVertical(1)
                else -> false
            }
        },
    ) { focused ->
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.Center) {
            Text(
                e?.title ?: if (loading) "Loading…" else "No information",
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (e != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (e == null && !focused) Palette.muted else Color.Unspecified,
            )
            if (e != null) Text(
                fmt.format(Date(e.start)), fontSize = 11.sp, maxLines = 1,
                color = if (focused) Color(0xFF39425A) else if (live) Palette.teal else Palette.muted,
            )
        }
    }
}
