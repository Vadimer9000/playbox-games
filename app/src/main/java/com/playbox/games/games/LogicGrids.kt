package com.playbox.games.games

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.playbox.games.data.PlayerPrefs
import com.playbox.games.model.GameId
import com.playbox.games.model.LaunchConfig
import com.playbox.games.model.PlayMode
import com.playbox.games.network.OnlineClient
import com.playbox.games.network.OnlineEvent
import com.playbox.games.network.OnlineRoomState
import com.playbox.games.ui.components.GameResultBanner
import com.playbox.games.ui.components.PlayBoxOutlinedButton
import com.playbox.games.ui.theme.PlayBoxColors
import com.playbox.games.ui.theme.bounceClick
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

internal val SORT_PALETTE = listOf(
    Color(0xFFEF476F), Color(0xFF118AB2), Color(0xFF06D6A0),
    Color(0xFFFFCA3A), Color(0xFF9B5DE5), Color(0xFFF3722C)
)

private data class SortColorSpec(
    val face: Color,
    val leftSide: Color,
    val rightSide: Color,
    val topBevel: Color,
    val symbolShade: Color
)

private val NUT_SORT_SPECS = listOf(
    // 0: Pink Crown
    SortColorSpec(Color(0xFFFA68AB), Color(0xFFC23676), Color(0xFFE0488E), Color(0xFFFF9CC9), Color(0xFF8C1C50)),
    // 1: Orange Lightning
    SortColorSpec(Color(0xFFFB7D1E), Color(0xFFC24D06), Color(0xFFE3620C), Color(0xFFFFA75C), Color(0xFF8C3500)),
    // 2: Crimson Heart
    SortColorSpec(Color(0xFFE42D3C), Color(0xFF9F1422), Color(0xFFC41F2E), Color(0xFFFF6D7A), Color(0xFF700B15)),
    // 3: Cobalt Drop
    SortColorSpec(Color(0xFF1982E6), Color(0xFF0C54A1), Color(0xFF136BC4), Color(0xFF63B3FF), Color(0xFF073A73)),
    // 4: Emerald Clover
    SortColorSpec(Color(0xFF28B92E), Color(0xFF147819), Color(0xFF1E9923), Color(0xFF6DE372), Color(0xFF0D5710)),
    // 5: Sunny Yellow Sun
    SortColorSpec(Color(0xFFFFCA1A), Color(0xFFC88D04), Color(0xFFE8AB0B), Color(0xFFFFE16B), Color(0xFF916200)),
    // 6: Violet Diamond
    SortColorSpec(Color(0xFF9D4EDD), Color(0xFF65219E), Color(0xFF7E36BE), Color(0xFFC891F5), Color(0xFF471275))
)

private fun isSortContainerDone(stack: List<Int>): Boolean =
    stack.size == 4 && stack.distinct().size == 1

private fun findBestSortHint(containers: List<List<Int>>): Pair<Int, Int>? {
    if (containers.all { it.isEmpty() || isSortContainerDone(it) }) return null

    fun validMoves(state: List<List<Int>>): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var usedEmptyDst = false
        for (i in state.indices) {
            val src = state[i]
            if (src.isEmpty() || isSortContainerDone(src)) continue
            val top = src.last()
            val mono = src.all { it == top }
            for (j in state.indices) {
                if (i == j) continue
                val dst = state[j]
                if (dst.size >= 4) continue
                if (dst.isEmpty()) {
                    if (mono || usedEmptyDst) continue
                    usedEmptyDst = true
                    out.add(i to j)
                } else if (dst.last() == top) {
                    var cnt = 0
                    for (k in src.lastIndex downTo 0) {
                        if (src[k] == top) cnt++ else break
                    }
                    if (dst.size + cnt <= 4) out.add(i to j)
                }
            }
        }
        return out
    }

    fun applyMove(state: List<List<Int>>, from: Int, to: Int): List<List<Int>> {
        val src = state[from]
        val dst = state[to]
        val top = src.last()
        var count = 0
        for (k in src.lastIndex downTo 0) {
            if (src[k] == top && dst.size + count < 4) count++ else break
        }
        return state.mapIndexed { idx, list ->
            when (idx) {
                from -> list.dropLast(count)
                to -> list + List(count) { top }
                else -> list
            }
        }
    }

    fun canonicalKey(state: List<List<Int>>): String =
        state.map { it.joinToString(",") }.sorted().joinToString("|")

    val queue = ArrayDeque<Pair<List<List<Int>>, Pair<Int, Int>>>()
    val visited = HashSet<String>(2400)
    visited.add(canonicalKey(containers))

    val initialMoves = validMoves(containers)
    if (initialMoves.isEmpty()) return null

    for (mv in initialMoves) {
        val next = applyMove(containers, mv.first, mv.second)
        if (next.all { it.isEmpty() || isSortContainerDone(it) }) return mv
        val key = canonicalKey(next)
        if (visited.add(key)) queue.addLast(next to mv)
    }

    var steps = 0
    while (queue.isNotEmpty() && steps < 2200) {
        val (cur, firstMv) = queue.removeFirst()
        steps++
        for (mv in validMoves(cur)) {
            val next = applyMove(cur, mv.first, mv.second)
            if (next.all { it.isEmpty() || isSortContainerDone(it) }) return firstMv
            val key = canonicalKey(next)
            if (visited.add(key)) queue.addLast(next to firstMv)
        }
    }

    // Heuristic fallback if deep puzzle
    return initialMoves.maxByOrNull { (f, t) ->
        val next = applyMove(containers, f, t)
        val dstAfter = next[t]
        var score = 0
        if (isSortContainerDone(dstAfter)) score += 120
        if (containers[t].isNotEmpty()) score += 45 + dstAfter.size * 10
        if (next[f].isEmpty()) score += 25
        score
    }
}

private fun hasAnySortMove(containers: List<List<Int>>): Boolean {
    for (i in containers.indices) {
        val src = containers[i]
        if (src.isEmpty() || isSortContainerDone(src)) continue
        val top = src.last()
        for (j in containers.indices) {
            if (i == j) continue
            val dst = containers[j]
            if (dst.size < 4 && (dst.isEmpty() || dst.last() == top)) return true
        }
    }
    return false
}

// Shared AAA engine for NutsBolts & WaterSort with 3D graphics, 2-row layout, BFS hints & ad-free extra slots!
@Composable
private fun ColorSortScreen(
    gameId: GameId,
    isBolts: Boolean,
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var level by remember(resetSignal) { mutableIntStateOf(1) }
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }

    fun buildContainers(lvl: Int, s: Int): List<List<Int>> {
        val colorCount = (3 + ((lvl + 1) / 2)).coerceIn(4, 7)
        for (attempt in 0..8) {
            val rng = Random(s + lvl * 31 + attempt * 97)
            val pool = (0 until colorCount).flatMap { c -> List(4) { c } }.shuffled(rng)
            val filled = (0 until colorCount).map { i -> pool.subList(i * 4, (i + 1) * 4) }
            val validStart = filled.none { isSortContainerDone(it) }
            if (validStart) return filled + listOf(emptyList(), emptyList())
        }
        val rng = Random(s + lvl * 31)
        val pool = (0 until colorCount).flatMap { c -> List(4) { c } }.shuffled(rng)
        val filled = (0 until colorCount).map { i -> pool.subList(i * 4, (i + 1) * 4) }
        return filled + listOf(emptyList(), emptyList())
    }

    var containers by remember(level, seed) { mutableStateOf(buildContainers(level, seed)) }
    var history by remember(level, seed) { mutableStateOf<List<List<List<Int>>>>(emptyList()) }
    var selectedIdx by remember(level, seed) { mutableStateOf<Int?>(null) }
    var movesCount by remember(level, seed) { mutableIntStateOf(0) }
    var hintsLeft by remember(level, seed) { mutableIntStateOf(3) }
    var extraUsed by remember(level, seed) { mutableStateOf(false) }
    var hintPair by remember(level, seed) { mutableStateOf<Pair<Int, Int>?>(null) }
    var oppLevel by remember(resetSignal) { mutableIntStateOf(1) }

    val colorGroupsTotal = remember(level) { (3 + ((level + 1) / 2)).coerceIn(4, 7) }
    val completedGroups = containers.count { isSortContainerDone(it) }
    val solved = containers.all { it.isEmpty() || isSortContainerDone(it) }
    val stuck = !solved && !hasAnySortMove(containers)

    val diffLabel = when {
        level <= 2 -> "ЛЁГКИЙ"
        level <= 5 -> "СРЕДНИЙ"
        else -> "СЛОЖНЫЙ"
    }
    val diffColor = when {
        level <= 2 -> Color(0xFF28B92E)
        level <= 5 -> Color(0xFFFFA726)
        else -> Color(0xFFE0526D)
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentState -> oppLevel = ev.payload.optInt("lvl", oppLevel)
                    is OnlineEvent.RematchStart -> { level = 1; seed = ev.seed }
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(solved) {
        if (solved) {
            prefs.recordWin(gameId)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("lvl", level + 1))
        }
    }

    fun tryMove(from: Int, to: Int): Boolean {
        if (from == to) return false
        val src = containers[from]; val dst = containers[to]
        if (src.isEmpty() || dst.size >= 4) return false
        val topColor = src.last()
        if (dst.isNotEmpty() && dst.last() != topColor) return false
        var count = 0
        for (i in src.lastIndex downTo 0) {
            if (src[i] == topColor && dst.size + count < 4) count++ else break
        }
        if (count == 0) return false
        history = history + listOf(containers)
        containers = containers.mapIndexed { idx, list ->
            when (idx) { from -> list.dropLast(count); to -> list + List(count) { topColor }; else -> list }
        }
        movesCount++
        hintPair = null
        return true
    }

    val pulseTransition = rememberInfiniteTransition(label = "hintPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    val bgBrush = if (isBolts) {
        Brush.radialGradient(listOf(Color(0xFF331B52), Color(0xFF221138), Color(0xFF170B28)))
    } else {
        Brush.radialGradient(listOf(Color(0xFF563966), Color(0xFF3B2447), Color(0xFF271730)))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Level & Progress Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            ) {
                Text(
                    text = "Ходы: $movesCount",
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = diffColor.copy(alpha = 0.22f),
                    modifier = Modifier.border(1.dp, diffColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = diffLabel,
                        color = diffColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Уровень $level",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (config.mode == PlayMode.ONLINE) {
                ScorePill("Соп. ур.", oppLevel, PlayBoxColors.OnlinePurple, false)
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                ) {
                    Text(
                        text = "Собрано $completedGroups/$colorGroupsTotal",
                        color = PlayBoxColors.BoltGold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Subtle animated progress bar
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            val pct = (completedGroups.toFloat() / colorGroupsTotal.toFloat()).coerceIn(0f, 1f)
            val animPct by animateFloatAsState(pct, spring(stiffness = Spring.StiffnessMediumLow), label = "prog")
            Box(
                modifier = Modifier
                    .fillMaxWidth(animPct)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(PlayBoxColors.BoltGold, PlayBoxColors.SuccessGreen)))
            )
        }

        if (stuck) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFE63946).copy(alpha = 0.25f),
                modifier = Modifier.border(1.dp, Color(0xFFE63946).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            ) {
                Text(
                    text = if (!extraUsed) "Нет ходов! Нажми «Отменить» или «+ ${if (isBolts) "Болт" else "Колба"}»"
                    else "Нет ходов! Нажми «Отменить» или сбрось уровень",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Responsive 1-row or 2-row container grid (just like 4+4/5 layout in top-tier puzzle games)
        val splitTop = if (containers.size <= 4) containers.size else (containers.size + 1) / 2
        val rowIndices = if (containers.size <= 4) {
            listOf(containers.indices.toList())
        } else {
            listOf((0 until splitTop).toList(), (splitTop until containers.size).toList())
        }
        val itemWidth = when {
            splitTop <= 4 -> 72.dp
            else -> 64.dp
        }
        val itemHeight = if (rowIndices.size == 1) 216.dp else 182.dp

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            rowIndices.forEach { indicesInRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom
                ) {
                    indicesInRow.forEach { idx ->
                        val stack = containers[idx]
                        val isSel = selectedIdx == idx
                        val isDone = isSortContainerDone(stack)
                        val isHintSrc = hintPair?.first == idx
                        val isHintDst = hintPair?.second == idx
                        val canReceiveFromSel = selectedIdx != null && selectedIdx != idx &&
                            stack.size < 4 && containers[selectedIdx!!].isNotEmpty() &&
                            (stack.isEmpty() || stack.last() == containers[selectedIdx!!].last())

                        SortContainerItem(
                            isBolts = isBolts,
                            stack = stack,
                            width = itemWidth,
                            height = itemHeight,
                            isSelected = isSel,
                            isCompleted = isDone,
                            isValidTarget = canReceiveFromSel,
                            hintGlowAlpha = if (isHintSrc || isHintDst) pulseAlpha else 0f,
                            hintLabel = when {
                                isHintSrc -> "ВЗЯТЬ"
                                isHintDst -> "СЮДА"
                                else -> null
                            },
                            enabled = !solved,
                            onClick = {
                                val sel = selectedIdx
                                if (sel == null) {
                                    if (stack.isNotEmpty() && !isDone) selectedIdx = idx
                                } else if (sel == idx) {
                                    selectedIdx = null
                                } else {
                                    val moved = tryMove(sel, idx)
                                    if (moved) {
                                        selectedIdx = null
                                    } else if (stack.isNotEmpty() && !isDone) {
                                        selectedIdx = idx
                                    } else {
                                        selectedIdx = null
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (solved) {
            val parMoves = colorGroupsTotal * 5 + 4
            val stars = when {
                movesCount <= parMoves -> 3
                movesCount <= parMoves + 8 -> 2
                else -> 1
            }
            val starsText = "★".repeat(stars) + "☆".repeat(3 - stars)
            GameResultBanner(
                title = "Уровень $level пройден! $starsText",
                subtitle = "Собрано за $movesCount ходов",
                mode = config.mode,
                roomState = roomState,
                onRematch = { level += 1 },
                onExit = onExit,
                rematchLabelOverride = "Уровень ${level + 1}"
            )
        } else {
            // Ad-Free Bottom Action Dock (all labels clearly inside tactile buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SortActionButton(
                    icon = { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = "Отменить",
                    badgeText = if (history.isNotEmpty()) "${history.size}" else null,
                    enabled = history.isNotEmpty(),
                    containerColor = Color(0xFF42305E),
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        containers = history.last()
                        history = history.dropLast(1)
                        selectedIdx = null
                        hintPair = null
                    }
                )
                SortActionButton(
                    icon = { Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = "Подсказка",
                    badgeText = "$hintsLeft",
                    enabled = hintsLeft > 0,
                    containerColor = PlayBoxColors.BoltGold,
                    contentColor = PlayBoxColors.DarkInk,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val best = findBestSortHint(containers)
                        if (best != null) {
                            hintPair = best
                            selectedIdx = best.first
                            hintsLeft--
                        }
                    }
                )
                SortActionButton(
                    icon = {
                        Icon(
                            if (extraUsed) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = if (extraUsed) "Добавлен" else if (isBolts) "+ Болт" else "+ Колба",
                    badgeText = if (!extraUsed) "FREE" else null,
                    enabled = !extraUsed,
                    containerColor = PlayBoxColors.SuccessGreen,
                    contentColor = PlayBoxColors.DarkInk,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!extraUsed) {
                            containers = containers + listOf(emptyList())
                            extraUsed = true
                            hintPair = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SortActionButton(
    icon: @Composable () -> Unit,
    label: String,
    badgeText: String?,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (enabled) containerColor else Color.White.copy(alpha = 0.12f)
    val fg = if (enabled) contentColor else Color.White.copy(alpha = 0.4f)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        shadowElevation = if (enabled) 5.dp else 0.dp,
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Color.White.copy(alpha = if (enabled) 0.28f else 0.08f), RoundedCornerShape(16.dp))
            .bounceClick(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides fg
                ) {
                    icon()
                }
                if (badgeText != null) {
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (enabled) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = badgeText,
                            color = fg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun SortContainerItem(
    isBolts: Boolean,
    stack: List<Int>,
    width: Dp,
    height: Dp,
    isSelected: Boolean,
    isCompleted: Boolean,
    isValidTarget: Boolean,
    hintGlowAlpha: Float,
    hintLabel: String?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val topLift by animateDpAsState(
        targetValue = if (isSelected && stack.isNotEmpty()) (-24).dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lift"
    )
    val liftScale by animateFloatAsState(
        targetValue = if (isSelected && stack.isNotEmpty()) 1.07f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "liftScale"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Optional hint floating pill
        if (hintLabel != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = PlayBoxColors.BoltGold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-6).dp)
            ) {
                Text(
                    text = hintLabel,
                    color = PlayBoxColors.DarkInk,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Container backdrop + Bolt / Tube hardware rendered crisply on Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val baseH = h * 0.11f
            val baseTop = h - baseH - 4.dp.toPx()

            // Selection / Valid target / Completed / Hint base ring
            val ringColor = when {
                hintGlowAlpha > 0f -> PlayBoxColors.BoltGold.copy(alpha = hintGlowAlpha)
                isCompleted -> Color(0xFF4ADE80).copy(alpha = 0.8f)
                isSelected -> PlayBoxColors.BoltGold.copy(alpha = 0.9f)
                isValidTarget -> Color.White.copy(alpha = 0.55f)
                else -> Color.Transparent
            }
            if (ringColor != Color.Transparent) {
                drawOval(
                    color = ringColor.copy(alpha = ringColor.alpha * 0.35f),
                    topLeft = Offset(w * 0.04f, baseTop - 3.dp.toPx()),
                    size = Size(w * 0.92f, baseH + 8.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            if (isBolts) {
                // Ground shadow
                drawOval(
                    color = Color.Black.copy(alpha = 0.36f),
                    topLeft = Offset(w * 0.1f, baseTop + 4.dp.toPx()),
                    size = Size(w * 0.8f, baseH + 2.dp.toPx())
                )
                // 3D Base flange side extrusion
                drawOval(
                    color = Color(0xFF64748B),
                    topLeft = Offset(w * 0.1f, baseTop + 3.dp.toPx()),
                    size = Size(w * 0.8f, baseH)
                )
                // 3D Base top plate
                drawOval(
                    brush = Brush.verticalGradient(listOf(Color(0xFFF1F5F9), Color(0xFFCBD5E1))),
                    topLeft = Offset(w * 0.1f, baseTop - 1.dp.toPx()),
                    size = Size(w * 0.8f, baseH)
                )

                // Threaded metallic bolt shaft
                val rodW = w * 0.24f
                val rodLeft = (w - rodW) / 2f
                val rodTop = h * 0.22f
                val rodBottom = baseTop + baseH * 0.45f
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF7B8798), Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFF64748B)),
                        startX = rodLeft,
                        endX = rodLeft + rodW
                    ),
                    topLeft = Offset(rodLeft, rodTop),
                    size = Size(rodW, rodBottom - rodTop),
                    cornerRadius = CornerRadius(rodW / 2f, rodW / 2f)
                )
                // Spiral screw threads along the shaft
                val threadStep = 6.dp.toPx()
                var ty = rodTop + rodW * 0.48f
                while (ty < rodBottom - 4.dp.toPx()) {
                    drawLine(
                        color = Color(0xFF475569).copy(alpha = 0.55f),
                        start = Offset(rodLeft + 1.5.dp.toPx(), ty + 2.2.dp.toPx()),
                        end = Offset(rodLeft + rodW - 1.5.dp.toPx(), ty),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    ty += threadStep
                }
                // Chrome dome highlight at bolt tip
                drawOval(
                    color = Color.White.copy(alpha = 0.72f),
                    topLeft = Offset(rodLeft + rodW * 0.22f, rodTop + 2.dp.toPx()),
                    size = Size(rodW * 0.35f, rodW * 0.28f)
                )
            } else {
                // Glass test tube backdrop
                val tubeLeft = w * 0.14f
                val tubeW = w * 0.72f
                val tubeTop = h * 0.22f
                val tubeBottom = h - 8.dp.toPx()
                val tubeH = tubeBottom - tubeTop
                val radiusBottom = tubeW * 0.48f

                // Soft glow under tube
                drawOval(
                    color = Color.Black.copy(alpha = 0.3f),
                    topLeft = Offset(tubeLeft + 4.dp.toPx(), tubeBottom - 3.dp.toPx()),
                    size = Size(tubeW - 8.dp.toPx(), 10.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.13f),
                    topLeft = Offset(tubeLeft, tubeTop),
                    size = Size(tubeW, tubeH),
                    cornerRadius = CornerRadius(radiusBottom, radiusBottom)
                )
            }
        }

        // Stacked Nuts or Liquid Layers
        val slotBottomPad = if (isBolts) height * 0.105f else 12.dp
        val pieceHeight = if (isBolts) (height * 0.175f) else (height * 0.16f)
        val pieceGap = if (isBolts) (-4).dp else 1.5.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = slotBottomPad),
            verticalArrangement = Arrangement.spacedBy(pieceGap, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            stack.reversed().forEachIndexed { posFromTop, cIdx ->
                val isTopLifted = posFromTop == 0 && isSelected
                val baseMod = Modifier.zIndex((6 - posFromTop).toFloat())
                val mod = if (isTopLifted) {
                    baseMod
                        .offset(y = topLift)
                        .graphicsLayer { scaleX = liftScale; scaleY = liftScale }
                } else baseMod

                val spec = NUT_SORT_SPECS[cIdx % NUT_SORT_SPECS.size]
                if (isBolts) {
                    HexNut3D(
                        spec = spec,
                        symbolIdx = cIdx % NUT_SORT_SPECS.size,
                        showInnerHole = isTopLifted,
                        isCompleted = isCompleted,
                        modifier = mod
                            .fillMaxWidth(0.84f)
                            .height(pieceHeight)
                    )
                } else {
                    LiquidSegment3D(
                        spec = spec,
                        isTop = posFromTop == 0,
                        isBottom = posFromTop == stack.lastIndex,
                        isLifted = isTopLifted,
                        modifier = mod
                            .fillMaxWidth(0.66f)
                            .height(pieceHeight)
                    )
                }
            }
        }

        // Foreground Glass Reflection & Flared Lip for WaterSort
        if (!isBolts) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val tubeLeft = w * 0.14f
                val tubeW = w * 0.72f
                val tubeTop = h * 0.22f
                val tubeBottom = h - 8.dp.toPx()
                val tubeH = tubeBottom - tubeTop
                val radiusBottom = tubeW * 0.48f

                val outlineColor = when {
                    hintGlowAlpha > 0f -> PlayBoxColors.BoltGold.copy(alpha = hintGlowAlpha)
                    isCompleted -> Color(0xFF4ADE80)
                    isSelected -> PlayBoxColors.BoltGold
                    else -> Color.White.copy(alpha = 0.75f)
                }
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(tubeLeft, tubeTop),
                    size = Size(tubeW, tubeH),
                    cornerRadius = CornerRadius(radiusBottom, radiusBottom),
                    style = Stroke(width = 2.4.dp.toPx())
                )
                // Flared glass rim lip
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(tubeLeft - 4.dp.toPx(), tubeTop - 4.dp.toPx()),
                    size = Size(tubeW + 8.dp.toPx(), 7.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 2.2.dp.toPx())
                )
                // Specular glass highlight stripe
                drawLine(
                    color = Color.White.copy(alpha = 0.36f),
                    start = Offset(tubeLeft + tubeW * 0.18f, tubeTop + 10.dp.toPx()),
                    end = Offset(tubeLeft + tubeW * 0.18f, tubeBottom - 16.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        } else if (isCompleted) {
            // Subtle bolt tip cap shimmer when a nut column finishes all 4!
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = height * 0.11f)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4ADE80))
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF064E3B), modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun HexNut3D(
    spec: SortColorSpec,
    symbolIdx: Int,
    showInnerHole: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = this.size.width
        val h = this.size.height
        val topBevelH = h * 0.34f
        val bodyTop = topBevelH * 0.56f
        val bodyBottom = h * 0.94f
        val sideInset = w * 0.18f

        // Drop shadow underneath nut
        drawOval(
            color = Color.Black.copy(alpha = 0.32f),
            topLeft = Offset(w * 0.06f, h * 0.78f),
            size = Size(w * 0.88f, h * 0.24f)
        )

        // Left angled hex facet
        val leftPath = Path().apply {
            moveTo(0f, topBevelH * 0.52f)
            lineTo(sideInset, bodyTop + topBevelH * 0.42f)
            lineTo(sideInset, bodyBottom)
            lineTo(0f, bodyBottom - topBevelH * 0.36f)
            close()
        }
        drawPath(leftPath, spec.leftSide)

        // Right angled hex facet
        val rightPath = Path().apply {
            moveTo(w - sideInset, bodyTop + topBevelH * 0.42f)
            lineTo(w, topBevelH * 0.52f)
            lineTo(w, bodyBottom - topBevelH * 0.36f)
            lineTo(w - sideInset, bodyBottom)
            close()
        }
        drawPath(rightPath, spec.rightSide)

        // Center front facet
        val centerPath = Path().apply {
            moveTo(sideInset, bodyTop + topBevelH * 0.42f)
            lineTo(w - sideInset, bodyTop + topBevelH * 0.42f)
            lineTo(w - sideInset, bodyBottom)
            lineTo(sideInset, bodyBottom)
            close()
        }
        drawPath(centerPath, spec.face)

        // Top 3D hexagonal bevel lid
        val topHex = Path().apply {
            moveTo(sideInset, 0f)
            lineTo(w - sideInset, 0f)
            lineTo(w, topBevelH * 0.52f)
            lineTo(w - sideInset, bodyTop + topBevelH * 0.42f)
            lineTo(sideInset, bodyTop + topBevelH * 0.42f)
            lineTo(0f, topBevelH * 0.52f)
            close()
        }
        drawPath(
            topHex,
            brush = Brush.verticalGradient(listOf(spec.topBevel, spec.face))
        )
        drawPath(
            topHex,
            color = Color.White.copy(alpha = if (isCompleted) 0.55f else 0.28f),
            style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round)
        )

        // Inner threaded hole visible when lifted (or subtle bore around bolt rod)
        val holeW = w * 0.34f
        val holeH = topBevelH * 0.46f
        val holeLeft = (w - holeW) / 2f
        val holeTop = (bodyTop + topBevelH * 0.42f - holeH) * 0.46f
        if (showInnerHole) {
            drawOval(
                color = Color(0xFF4B281B),
                topLeft = Offset(holeLeft, holeTop),
                size = Size(holeW, holeH)
            )
            drawOval(
                color = Color(0xFFAD7D54),
                topLeft = Offset(holeLeft + 1.5.dp.toPx(), holeTop + holeH * 0.28f),
                size = Size(holeW - 3.dp.toPx(), holeH * 0.5f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        } else {
            drawOval(
                color = Color.Black.copy(alpha = 0.22f),
                topLeft = Offset(holeLeft, holeTop),
                size = Size(holeW, holeH)
            )
        }

        // Embossed vector symbol on center front face for tactile crispness & colorblind clarity
        val cx = w / 2f
        val cy = (bodyTop + topBevelH * 0.42f + bodyBottom) / 2f
        val r = (bodyBottom - (bodyTop + topBevelH * 0.42f)) * 0.32f
        drawNutSymbol(symbolIdx, cx, cy, r, spec.symbolShade)
    }
}

private fun DrawScope.drawNutSymbol(idx: Int, cx: Float, cy: Float, r: Float, color: Color) {
    when (idx % 7) {
        0 -> { // Crown
            val p = Path().apply {
                moveTo(cx - r, cy + r * 0.65f)
                lineTo(cx - r * 1.15f, cy - r * 0.55f)
                lineTo(cx - r * 0.45f, cy + r * 0.05f)
                lineTo(cx, cy - r * 0.75f)
                lineTo(cx + r * 0.45f, cy + r * 0.05f)
                lineTo(cx + r * 1.15f, cy - r * 0.55f)
                lineTo(cx + r, cy + r * 0.65f)
                close()
            }
            drawPath(p, color)
        }
        1 -> { // Lightning bolt
            val p = Path().apply {
                moveTo(cx + r * 0.15f, cy - r * 0.95f)
                lineTo(cx - r * 0.65f, cy + r * 0.08f)
                lineTo(cx - r * 0.05f, cy + r * 0.08f)
                lineTo(cx - r * 0.25f, cy + r * 0.95f)
                lineTo(cx + r * 0.65f, cy - r * 0.12f)
                lineTo(cx + r * 0.05f, cy - r * 0.12f)
                close()
            }
            drawPath(p, color)
        }
        2 -> { // Heart
            val p = Path().apply {
                moveTo(cx, cy + r * 0.85f)
                cubicTo(cx - r * 1.3f, cy + r * 0.1f, cx - r * 1.0f, cy - r * 0.95f, cx, cy - r * 0.28f)
                cubicTo(cx + r * 1.0f, cy - r * 0.95f, cx + r * 1.3f, cy + r * 0.1f, cx, cy + r * 0.85f)
                close()
            }
            drawPath(p, color)
        }
        3 -> { // Water drop
            val p = Path().apply {
                moveTo(cx, cy - r * 0.9f)
                cubicTo(cx + r * 0.9f, cy, cx + r * 0.85f, cy + r * 0.9f, cx, cy + r * 0.9f)
                cubicTo(cx - r * 0.85f, cy + r * 0.9f, cx - r * 0.9f, cy, cx, cy - r * 0.9f)
                close()
            }
            drawPath(p, color)
        }
        4 -> { // Three-leaf clover
            drawCircle(color, r * 0.44f, Offset(cx, cy - r * 0.4f))
            drawCircle(color, r * 0.44f, Offset(cx - r * 0.45f, cy + r * 0.2f))
            drawCircle(color, r * 0.44f, Offset(cx + r * 0.45f, cy + r * 0.2f))
            drawLine(color, Offset(cx, cy + r * 0.2f), Offset(cx, cy + r * 0.9f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        5 -> { // Sun
            drawCircle(color, r * 0.5f, Offset(cx, cy))
            for (a in 0 until 8) {
                val rad = Math.toRadians(a * 45.0)
                val sx = cx + (kotlin.math.cos(rad) * r * 0.68f).toFloat()
                val sy = cy + (kotlin.math.sin(rad) * r * 0.68f).toFloat()
                val ex = cx + (kotlin.math.cos(rad) * r * 1.0f).toFloat()
                val ey = cy + (kotlin.math.sin(rad) * r * 1.0f).toFloat()
                drawLine(color, Offset(sx, sy), Offset(ex, ey), strokeWidth = 1.7.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        else -> { // Diamond
            val p = Path().apply {
                moveTo(cx, cy - r * 0.9f)
                lineTo(cx + r * 0.8f, cy)
                lineTo(cx, cy + r * 0.9f)
                lineTo(cx - r * 0.8f, cy)
                close()
            }
            drawPath(p, color)
        }
    }
}

@Composable
private fun LiquidSegment3D(
    spec: SortColorSpec,
    isTop: Boolean,
    isBottom: Boolean,
    isLifted: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = this.size.width
        val h = this.size.height
        val corner = if (isBottom) 14.dp.toPx() else 4.dp.toPx()
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(spec.leftSide, spec.face, spec.rightSide)),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(corner, corner)
        )
        if (isTop) {
            drawOval(
                color = spec.topBevel.copy(alpha = if (isLifted) 0.95f else 0.72f),
                topLeft = Offset(w * 0.08f, 1.dp.toPx()),
                size = Size(w * 0.84f, h * 0.28f)
            )
        }
    }
}

@Composable
fun WaterSortScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) =
    ColorSortScreen(GameId.WATER_SORT, false, config, prefs, onlineClient, roomState, resetSignal, onExit)

@Composable
fun NutsBoltsScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) =
    ColorSortScreen(GameId.NUTS_BOLTS, true, config, prefs, onlineClient, roomState, resetSignal, onExit)

// 13. SUDOKU (With 3x3 borders, Pencil Notes, Erase, Highlight matching numbers)
private fun generateSudoku(seed: Int): Pair<IntArray, IntArray> {
    val rng = Random(seed)
    val digits = (1..9).shuffled(rng)
    val sol = IntArray(81)
    for (r in 0..8) for (c in 0..8) sol[r * 9 + c] = digits[(r * 3 + r / 3 + c) % 9]
    val puzzle = sol.copyOf()
    (0..80).shuffled(rng).take(44).forEach { puzzle[it] = 0 }
    return puzzle to sol
}

@Composable
fun SudokuScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    val (fixedInit, solution) = remember(seed) { generateSudoku(seed) }
    var grid by remember(seed) { mutableStateOf(fixedInit.copyOf()) }
    var notes by remember(seed) { mutableStateOf(List(81) { emptySet<Int>() }) }
    var notesMode by remember(seed) { mutableStateOf(false) }
    var selected by remember(seed) { mutableStateOf<Int?>(null) }
    var oppPct by remember(seed) { mutableIntStateOf(0) }

    val filledOk = grid.indices.count { grid[it] == solution[it] }
    val won = filledOk == 81
    val selectedDigit = selected?.let { grid[it] } ?: 0

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentState -> oppPct = ev.payload.optInt("pct", oppPct)
                    is OnlineEvent.RematchStart -> seed = ev.seed
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(won) {
        if (won) {
            prefs.recordWin(GameId.SUDOKU)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex)
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScorePill("Заполнено", filledOk, Color(0xFF1AA7E3), true)
            FilterChip(selected = notesMode, onClick = { notesMode = !notesMode }, label = { Text("✏️ Заметки", fontWeight = FontWeight.Bold, maxLines = 1) })
            if (config.mode == PlayMode.ONLINE) ScorePill("Соп.", oppPct, PlayBoxColors.OnlinePurple, false)
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White).border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(12.dp))) {
            Column(Modifier.fillMaxSize()) {
                for (r in 0..8) {
                    Row(Modifier.weight(1f)) {
                        for (c in 0..8) {
                            val idx = r * 9 + c
                            val isFixed = fixedInit[idx] != 0
                            val v = grid[idx]
                            val wrong = v != 0 && v != solution[idx]
                            val boxTint = ((r / 3) + (c / 3)) % 2 == 0
                            val bg = when {
                                idx == selected -> Color(0xFF7DD3FC)
                                selectedDigit > 0 && v == selectedDigit -> Color(0xFFBAE6FD)
                                wrong -> Color(0xFFFECACA)
                                boxTint -> Color(0xFFF8FAFC)
                                else -> Color(0xFFE2E8F0)
                            }
                            Box(
                                Modifier.weight(1f).fillMaxSize().border(0.5.dp, Color(0xFFCBD5E1)).background(bg).clickable(enabled = !won) { selected = idx },
                                contentAlignment = Alignment.Center
                            ) {
                                if (v != 0) {
                                    Text("$v", fontWeight = if (isFixed) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 18.sp, color = if (wrong) Color.Red else if (isFixed) PlayBoxColors.DarkInk else Color(0xFF0284C7))
                                } else if (notes[idx].isNotEmpty()) {
                                    Text(notes[idx].sorted().joinToString(""), fontSize = 8.sp, lineHeight = 9.sp, color = PlayBoxColors.MutedInk)
                                }
                            }
                        }
                    }
                }
            }
            // Thick 3x3 subgrid lines
            Canvas(Modifier.fillMaxSize()) {
                for (i in 1..2) {
                    val pos = size.width * i / 3f
                    drawLine(PlayBoxColors.DarkInk, Offset(pos, 0f), Offset(pos, size.height), 2.5.dp.toPx())
                    drawLine(PlayBoxColors.DarkInk, Offset(0f, pos), Offset(size.width, pos), 2.5.dp.toPx())
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (n in 1..9) {
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(2.dp, PlayBoxColors.DarkInk, RoundedCornerShape(10.dp))
                        .bounceClick(enabled = selected != null && !won && fixedInit[selected!!] == 0) {
                            val s = selected!!
                            if (notesMode) {
                                val cur = notes[s]
                                notes = notes.mapIndexed { i, set -> if (i == s) (if (n in cur) cur - n else cur + n) else set }
                            } else {
                                val next = grid.copyOf().apply { this[s] = n }
                                grid = next
                                if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("pct", next.indices.count { next[it] == solution[it] }))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("$n", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            }
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFEE2E2)).border(2.dp, PlayBoxColors.Player1Color, RoundedCornerShape(10.dp))
                    .bounceClick(enabled = selected != null && !won && fixedInit[selected!!] == 0) {
                        val s = selected!!
                        grid = grid.copyOf().apply { this[s] = 0 }
                        notes = notes.mapIndexed { i, set -> if (i == s) emptySet() else set }
                    },
                contentAlignment = Alignment.Center
            ) { Text("⌫", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = PlayBoxColors.Player1Color) }
        }
        if (won) {
            GameResultBanner("Судоку решено!", "Все 81 клетка верны", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}

// 14. SHIKAKU (Drag-to-select rectangles directly!)
private data class ShikakuClue(val r: Int, val c: Int, val area: Int)
private data class ShikakuPuzzle(val size: Int, val clues: List<ShikakuClue>)

private val SHIKAKU_LEVELS = listOf(
    ShikakuPuzzle(5, listOf(ShikakuClue(0, 0, 6), ShikakuClue(0, 3, 4), ShikakuClue(2, 1, 5), ShikakuClue(3, 2, 6), ShikakuClue(4, 0, 4))),
    ShikakuPuzzle(5, listOf(ShikakuClue(0, 1, 8), ShikakuClue(1, 4, 3), ShikakuClue(2, 2, 4), ShikakuClue(4, 0, 6), ShikakuClue(4, 3, 4))),
    ShikakuPuzzle(5, listOf(ShikakuClue(0, 0, 5), ShikakuClue(1, 2, 6), ShikakuClue(1, 4, 4), ShikakuClue(3, 1, 4), ShikakuClue(4, 3, 6))),
    ShikakuPuzzle(5, listOf(ShikakuClue(0, 0, 4), ShikakuClue(0, 4, 6), ShikakuClue(2, 2, 5), ShikakuClue(4, 1, 4), ShikakuClue(4, 4, 6)))
)

@Composable
fun ShikakuScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var lvlIdx by remember(resetSignal) { mutableIntStateOf(abs(config.seed) % SHIKAKU_LEVELS.size) }
    val puz = SHIKAKU_LEVELS[lvlIdx]
    var owners by remember(lvlIdx, resetSignal) { mutableStateOf(IntArray(puz.size * puz.size) { -1 }) }
    var dragStartCell by remember(lvlIdx, resetSignal) { mutableStateOf<Int?>(null) }
    var dragEndCell by remember(lvlIdx, resetSignal) { mutableStateOf<Int?>(null) }

    val won = remember(owners, lvlIdx) {
        if (owners.any { it < 0 }) false
        else puz.clues.indices.all { cIdx ->
            val cells = owners.indices.filter { owners[it] == cIdx }
            if (cells.size != puz.clues[cIdx].area) false
            else {
                val rs = cells.map { it / puz.size }; val cs = cells.map { it % puz.size }
                val rectArea = (rs.max() - rs.min() + 1) * (cs.max() - cs.min() + 1)
                rectArea == cells.size && (puz.clues[cIdx].r * puz.size + puz.clues[cIdx].c) in cells
            }
        }
    }

    LaunchedEffect(won) {
        if (won) { prefs.recordWin(GameId.SHIKAKU); if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex) }
    }

    fun previewCells(): Set<Int> {
        val s = dragStartCell ?: return emptySet()
        val e = dragEndCell ?: s
        val r1 = minOf(s / puz.size, e / puz.size); val r2 = maxOf(s / puz.size, e / puz.size)
        val c1 = minOf(s % puz.size, e % puz.size); val c2 = maxOf(s % puz.size, e % puz.size)
        return (r1..r2).flatMap { rr -> (c1..c2).map { cc -> rr * puz.size + cc } }.toSet()
    }

    val preview = previewCells()

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Проведите пальцем прямоугольник с одним числом (площадь = числу).", fontSize = 13.sp, color = PlayBoxColors.MutedInk)
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White).border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(16.dp)).padding(6.dp)
                .pointerInput(lvlIdx, won, owners) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (won) return@detectDragGestures
                            val c = ((pos.x / size.width) * puz.size).toInt().coerceIn(0, puz.size - 1)
                            val r = ((pos.y / size.height) * puz.size).toInt().coerceIn(0, puz.size - 1)
                            dragStartCell = r * puz.size + c; dragEndCell = r * puz.size + c
                        },
                        onDrag = { ch, _ ->
                            ch.consume()
                            val c = ((ch.position.x / size.width) * puz.size).toInt().coerceIn(0, puz.size - 1)
                            val r = ((ch.position.y / size.height) * puz.size).toInt().coerceIn(0, puz.size - 1)
                            dragEndCell = r * puz.size + c
                        },
                        onDragEnd = {
                            val rectCells = previewCells()
                            val cluesInside = puz.clues.indices.filter { ci -> (puz.clues[ci].r * puz.size + puz.clues[ci].c) in rectCells }
                            if (cluesInside.size == 1 && rectCells.size == puz.clues[cluesInside[0]].area) {
                                val next = owners.copyOf()
                                for (i in next.indices) if (next[i] == cluesInside[0]) next[i] = -1
                                rectCells.forEach { next[it] = cluesInside[0] }
                                owners = next
                            }
                            dragStartCell = null; dragEndCell = null
                        },
                        onDragCancel = { dragStartCell = null; dragEndCell = null }
                    )
                }
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0 until puz.size) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0 until puz.size) {
                            val idx = r * puz.size + c
                            val own = owners[idx]
                            val clue = puz.clues.firstOrNull { it.r == r && it.c == c }
                            val bg = when {
                                idx in preview -> PlayBoxColors.BoltGold.copy(alpha = 0.78f)
                                own >= 0 -> SORT_PALETTE[own % SORT_PALETTE.size].copy(alpha = 0.72f)
                                else -> PlayBoxColors.CreamBg
                            }
                            Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
                                if (clue != null) Text("${clue.area}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = PlayBoxColors.DarkInk)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PlayBoxOutlinedButton(onClick = { owners = IntArray(puz.size * puz.size) { -1 }; dragStartCell = null }) {
            Text("Сбросить сетку", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Шикаку решено!", "Идеальное разбиение", config.mode, roomState, onRematch = { lvlIdx = (lvlIdx + 1) % SHIKAKU_LEVELS.size }, onExit = onExit)
        }
    }
}

// 15. NUMBER MATCH (Ten Pair)
@Composable
fun NumberMatchScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    var cells by remember(seed) { val rng = Random(seed); mutableStateOf(List(27) { rng.nextInt(1, 10) }) }
    var selectedIdx by remember(seed) { mutableStateOf<Int?>(null) }
    var score by remember(seed) { mutableIntStateOf(0) }
    var oppScore by remember(seed) { mutableIntStateOf(0) }
    val won = cells.all { it == 0 }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                if (ev is OnlineEvent.OpponentState) oppScore = ev.payload.optInt("score", oppScore)
                if (ev is OnlineEvent.RematchStart) seed = ev.seed
            }
        }
    }

    fun canPair(i: Int, j: Int): Boolean {
        if (i == j || cells[i] == 0 || cells[j] == 0) return false
        if (cells[i] != cells[j] && cells[i] + cells[j] != 10) return false
        val cols = 9
        val r1 = i / cols; val c1 = i % cols; val r2 = j / cols; val c2 = j % cols
        val dr = r2 - r1; val dc = c2 - c1
        if (abs(dr) <= 1 && abs(dc) <= 1) return true
        val step = when {
            r1 == r2 -> if (c2 > c1) 1 else -1
            c1 == c2 -> if (r2 > r1) cols else -cols
            abs(dr) == abs(dc) -> (if (dr > 0) cols else -cols) + (if (dc > 0) 1 else -1)
            else -> 0
        }
        if (step != 0) {
            var cur = i + step
            while (cur != j && cur in cells.indices) { if (cells[cur] != 0) return false; cur += step }
            if (cur == j) return true
        }
        val lo = minOf(i, j); val hi = maxOf(i, j)
        return ((lo + 1) until hi).all { cells[it] == 0 }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScorePill("Очки", score, Color(0xFF00A8E8), true)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соп.", oppScore, PlayBoxColors.OnlinePurple, false)
            PlayBoxOutlinedButton(
                enabled = cells.count { it > 0 } < 45,
                onClick = { cells = cells + cells.filter { it != 0 }; selectedIdx = null },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("+ Добавить", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
        }
        Spacer(Modifier.height(10.dp))

        val rows = (cells.size + 8) / 9
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(14.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0..8) {
                        val idx = r * 9 + c; val v = cells.getOrElse(idx) { 0 }
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                                .background(if (idx == selectedIdx) PlayBoxColors.BoltGold else if (v == 0) Color(0xFFF8FAFC) else Color(0xFFE0F2FE))
                                .bounceClick(enabled = v != 0) {
                                    val s = selectedIdx
                                    if (s == null) selectedIdx = idx
                                    else if (s == idx) selectedIdx = null
                                    else if (canPair(s, idx)) {
                                        cells = cells.mapIndexed { k, old -> if (k == s || k == idx) 0 else old }
                                        score += 10; selectedIdx = null
                                        if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
                                        if (cells.all { it == 0 }) prefs.recordWin(GameId.NUMBER_MATCH)
                                    } else selectedIdx = idx
                                },
                            contentAlignment = Alignment.Center
                        ) { if (v != 0) Text("$v", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = PlayBoxColors.DarkInk) }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Поле очищено!", "Очки: $score", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}

// 16. CROSS SUMS (Kakuro-style subset sums)
@Composable
fun CrossSumsScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    val size = 5
    val (numbers, solutionMask) = remember(seed) {
        val rng = Random(seed)
        val nums = IntArray(size * size) { rng.nextInt(1, 10) }
        val mask = BooleanArray(size * size) { rng.nextBoolean() }
        for (i in 0 until size) if ((0 until size).none { mask[i * size + it] }) mask[i * size + i] = true
        nums to mask
    }
    var selectedMask by remember(seed) { mutableStateOf(BooleanArray(size * size)) }
    val rowTargets = remember(seed) { IntArray(size) { r -> (0 until size).filter { solutionMask[r * size + it] }.sumOf { numbers[r * size + it] } } }
    val colTargets = remember(seed) { IntArray(size) { c -> (0 until size).filter { solutionMask[it * size + c] }.sumOf { numbers[it * size + c] } } }

    val won = (0 until size).all { r -> (0 until size).filter { selectedMask[r * size + it] }.sumOf { numbers[r * size + it] } == rowTargets[r] } &&
        (0 until size).all { c -> (0 until size).filter { selectedMask[it * size + c] }.sumOf { numbers[it * size + c] } == colTargets[c] }

    LaunchedEffect(won) { if (won) { prefs.recordWin(GameId.CROSS_SUMS); if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex) } }

    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Отметьте числа так, чтобы суммы в строках и столбцах совпали с целями.", fontSize = 13.sp, color = PlayBoxColors.MutedInk)
        Spacer(Modifier.height(12.dp))

        Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF00B4E6)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.weight(0.8f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f))
                for (c in 0 until size) {
                    val cur = (0 until size).filter { selectedMask[it * size + c] }.sumOf { numbers[it * size + c] }
                    Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(if (cur == colTargets[c]) Color(0xFF06D6A0) else Color(0xFF023E8A)), Alignment.Center) {
                        Text("${colTargets[c]}", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            for (r in 0 until size) {
                val curRow = (0 until size).filter { selectedMask[r * size + it] }.sumOf { numbers[r * size + it] }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(if (curRow == rowTargets[r]) Color(0xFF06D6A0) else Color(0xFF023E8A)), Alignment.Center) {
                        Text("${rowTargets[r]}", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                    for (c in 0 until size) {
                        val idx = r * size + c; val on = selectedMask[idx]
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp))
                                .background(if (on) Color(0xFFFFCA3A) else Color.White)
                                .bounceClick(enabled = !won) { selectedMask = selectedMask.copyOf().apply { this[idx] = !this[idx] } },
                            Alignment.Center
                        ) { Text("${numbers[idx]}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = PlayBoxColors.DarkInk) }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Суммы сошлись!", "Все строки и столбцы верны", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}

// 17. CONNECT 2248 (With glowing connector line + live merge preview)
@Composable
fun Connect2248Screen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val rows = 6; val cols = 5
    var grid by remember(resetSignal) { val rng = Random(config.seed); mutableStateOf(IntArray(rows * cols) { listOf(2, 4, 8, 16).random(rng) }) }
    var chain by remember(resetSignal) { mutableStateOf<List<Int>>(emptyList()) }
    var score by remember(resetSignal) { mutableIntStateOf(0) }
    var oppScore by remember(resetSignal) { mutableIntStateOf(0) }

    val previewMerged = remember(chain, grid) {
        if (chain.size < 2) null else {
            val total = chain.sumOf { grid[it] }
            var merged = 2; while (merged * 2 <= total) merged *= 2
            merged
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                if (ev is OnlineEvent.OpponentState) oppScore = ev.payload.optInt("score", oppScore)
                if (ev is OnlineEvent.RematchStart) {
                    val rng = Random(ev.seed)
                    grid = IntArray(rows * cols) { listOf(2, 4, 8, 16).random(rng) }
                    score = 0; chain = emptyList()
                }
            }
        }
    }

    fun finishChain() {
        val merged = previewMerged
        if (merged != null && chain.size >= 2) {
            val next = grid.copyOf()
            chain.dropLast(1).forEach { next[it] = listOf(2, 4, 8, 16).random() }
            next[chain.last()] = merged
            grid = next; score += merged
            prefs.updateBestScore(GameId.CONNECT_2248, score)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
        }
        chain = emptyList()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF283B4A)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScorePill("Счёт", score, Color(0xFF2EC4B6), true)
            if (previewMerged != null) {
                Surface(shape = RoundedCornerShape(14.dp), color = PlayBoxColors.BoltGold) {
                    Text("→ $previewMerged", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = PlayBoxColors.DarkInk, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            if (config.mode == PlayMode.ONLINE) ScorePill("Соп.", oppScore, PlayBoxColors.OnlinePurple, false)
        }
        Spacer(Modifier.height(10.dp))
        Text("Тяните палец по одинаковым плиткам (и удвоениям) для слияния", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.fillMaxWidth(0.92f).aspectRatio(5f / 6f)
                .clip(RoundedCornerShape(18.dp)).background(Color(0xFF1D2C38)).padding(8.dp)
                .pointerInput(grid) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val c = ((pos.x / size.width) * cols).toInt().coerceIn(0, cols - 1)
                            val r = ((pos.y / size.height) * rows).toInt().coerceIn(0, rows - 1)
                            chain = listOf(r * cols + c)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pos = change.position
                            val c = ((pos.x / size.width) * cols).toInt().coerceIn(0, cols - 1)
                            val r = ((pos.y / size.height) * rows).toInt().coerceIn(0, rows - 1)
                            val idx = r * cols + c
                            if (chain.isNotEmpty() && idx !in chain) {
                                val last = chain.last(); val lr = last / cols; val lc = last % cols
                                val adj = abs(lr - r) <= 1 && abs(lc - c) <= 1
                                val valOk = grid[idx] == grid[last] || (chain.size >= 2 && grid[idx] == grid[last] * 2)
                                if (adj && valOk) chain = chain + idx
                            }
                        },
                        onDragEnd = { finishChain() },
                        onDragCancel = { chain = emptyList() }
                    )
                }
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until rows) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until cols) {
                            val idx = r * cols + c; val v = grid[idx]; val sel = idx in chain
                            Box(
                                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(12.dp))
                                    .background(SORT_PALETTE[(Integer.numberOfTrailingZeros(v.coerceAtLeast(2)) - 1) % SORT_PALETTE.size])
                                    .border(if (sel) 3.dp else 0.dp, Color.White, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text("$v", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
                        }
                    }
                }
            }
            if (chain.size >= 2) {
                Canvas(Modifier.fillMaxSize()) {
                    val cw = size.width / cols; val ch = size.height / rows
                    for (i in 0 until chain.lastIndex) {
                        val a = chain[i]; val b = chain[i + 1]
                        drawLine(
                            Color.White.copy(alpha = 0.85f),
                            Offset((a % cols + 0.5f) * cw, (a / cols + 0.5f) * ch),
                            Offset((b % cols + 0.5f) * cw, (b / cols + 0.5f) * ch),
                            strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

// 18. DROP MERGE
@Composable
fun DropMergeScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val rows = 7; val cols = 5
    var grid by remember(resetSignal) { mutableStateOf(IntArray(rows * cols)) }
    var nextBlock by remember(resetSignal) { mutableIntStateOf(listOf(2, 4, 8, 16, 32).random()) }
    var score by remember(resetSignal) { mutableIntStateOf(0) }
    var gameOver by remember(resetSignal) { mutableStateOf(false) }
    var oppScore by remember(resetSignal) { mutableIntStateOf(0) }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev -> if (ev is OnlineEvent.OpponentState) oppScore = ev.payload.optInt("score", oppScore) }
        }
    }

    fun dropInCol(c: Int) {
        if (gameOver) return
        val r = (rows - 1 downTo 0).firstOrNull { grid[it * cols + c] == 0 } ?: return
        val next = grid.copyOf(); next[r * cols + c] = nextBlock
        var mergedAny = true
        while (mergedAny) {
            mergedAny = false
            for (col in 0 until cols) for (row in rows - 1 downTo 1) {
                val v = next[row * cols + col]
                if (v > 0 && next[(row - 1) * cols + col] == v) {
                    next[row * cols + col] = v * 2; next[(row - 1) * cols + col] = 0; score += v * 2; mergedAny = true
                }
            }
            for (col in 0 until cols) {
                val nonZero = (rows - 1 downTo 0).map { next[it * cols + col] }.filter { it > 0 }
                for (row in 0 until rows) next[row * cols + col] = nonZero.getOrElse(rows - 1 - row) { 0 }
            }
        }
        grid = next; nextBlock = listOf(2, 4, 8, 16, 32).random()
        prefs.updateBestScore(GameId.DROP_MERGE, score)
        if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
        if ((0 until cols).all { next[it] != 0 }) gameOver = true
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF33363F)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Счёт", score, Color(0xFF6BCB77), true)
            ScorePill("След.", nextBlock, PlayBoxColors.BoltGold, false)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соп.", oppScore, PlayBoxColors.OnlinePurple, false)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(0.9f).weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF23262E)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until cols) {
                        val v = grid[r * cols + c]
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp))
                                .background(if (v == 0) Color(0xFF2E323C) else SORT_PALETTE[(Integer.numberOfTrailingZeros(v.coerceAtLeast(2)) - 1) % SORT_PALETTE.size])
                                .clickable(enabled = !gameOver) { dropInCol(c) },
                            Alignment.Center
                        ) { if (v > 0) Text("$v", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
                    }
                }
            }
        }
        if (gameOver) {
            GameResultBanner("Колонки заполнены!", "Счёт: $score", config.mode, roomState,
                onRematch = { grid = IntArray(rows * cols); score = 0; gameOver = false },
                onExit = onExit
            )
        }
    }
}

// 19. SLIDING PUZZLE
@Composable
fun SlidingPuzzleScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var size by remember { mutableIntStateOf(4) }
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }

    fun makeSolvable(n: Int, s: Int): IntArray {
        val arr = IntArray(n * n) { if (it == n * n - 1) 0 else it + 1 }
        val rng = Random(s); var empty = n * n - 1
        repeat(160) {
            val r = empty / n; val c = empty % n
            val nb = listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)
                .filter { (nr, nc) -> nr in 0 until n && nc in 0 until n }.map { (nr, nc) -> nr * n + nc }.random(rng)
            arr[empty] = arr[nb]; arr[nb] = 0; empty = nb
        }
        return arr
    }

    var tiles by remember(size, seed) { mutableStateOf(makeSolvable(size, seed)) }
    var moves by remember(size, seed) { mutableIntStateOf(0) }
    val won = tiles.indices.all { i -> if (i == tiles.lastIndex) tiles[i] == 0 else tiles[i] == i + 1 }

    LaunchedEffect(won) { if (won) { prefs.recordWin(GameId.SLIDING_PUZZLE); if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex) } }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = size == 3, onClick = { size = 3 }, label = { Text("3×3", fontWeight = FontWeight.Bold, maxLines = 1) })
            FilterChip(selected = size == 4, onClick = { size = 4 }, label = { Text("4×4", fontWeight = FontWeight.Bold, maxLines = 1) })
            ScorePill("Ходы", moves, PlayBoxColors.Player2Color, true)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2F5368)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until size) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until size) {
                        val idx = r * size + c; val v = tiles[idx]
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(12.dp))
                                .background(if (v == 0) Color(0xFF1F3847) else Color(0xFFF4A261))
                                .bounceClick(enabled = v != 0 && !won) {
                                    val empty = tiles.indexOfFirst { it == 0 }; val er = empty / size; val ec = empty % size
                                    if (abs(er - r) + abs(ec - c) == 1) {
                                        tiles = tiles.copyOf().apply { this[empty] = v; this[idx] = 0 }; moves += 1
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { if (v > 0) Text("$v", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Собрано за $moves ходов!", "Отлично!", config.mode, roomState, onRematch = { seed = Random.nextInt(100000) }, onExit = onExit)
        }
    }
}

// 20. ARROWS PUZZLE (Vector Arrows on Canvas)
@Composable
fun ArrowsPuzzleScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val size = 5
    var level by remember(resetSignal) { mutableIntStateOf(1) }

    fun generateClearable(lvl: Int): IntArray {
        val rng = Random(config.seed + lvl * 17)
        val arr = IntArray(size * size)
        for (cell in (0 until size * size).shuffled(rng)) {
            val r = cell / size; val c = cell % size
            val clearDirs = mutableListOf<Int>()
            if ((0 until r).all { arr[it * size + c] == 0 }) clearDirs.add(1)
            if (((c + 1) until size).all { arr[r * size + it] == 0 }) clearDirs.add(2)
            if (((r + 1) until size).all { arr[it * size + c] == 0 }) clearDirs.add(3)
            if ((0 until c).all { arr[r * size + it] == 0 }) clearDirs.add(4)
            if (clearDirs.isNotEmpty()) arr[cell] = clearDirs.random(rng)
        }
        return arr
    }

    var board by remember(level, resetSignal) { mutableStateOf(generateClearable(level)) }
    val won = board.all { it == 0 }

    LaunchedEffect(won) {
        if (won) { prefs.recordWin(GameId.ARROWS_PUZZLE); if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("lvl", level + 1)) }
    }

    fun canFlyOut(idx: Int): Boolean {
        val d = board[idx]; if (d == 0) return false
        val r = idx / size; val c = idx % size
        return when (d) {
            1 -> (0 until r).all { board[it * size + c] == 0 }
            2 -> ((c + 1) until size).all { board[r * size + it] == 0 }
            3 -> ((r + 1) until size).all { board[it * size + c] == 0 }
            4 -> (0 until c).all { board[r * size + it] == 0 }
            else -> false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFD8EFF7)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Уровень", level, Color(0xFF0096C7), true)
        Spacer(Modifier.height(10.dp))
        Text("Нажимайте на стрелки со свободным путём до края поля.", fontSize = 13.sp, color = PlayBoxColors.DarkInk)
        Spacer(Modifier.height(16.dp))

        Column(Modifier.fillMaxWidth(0.9f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color.White).border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(18.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until size) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until size) {
                        val idx = r * size + c; val d = board[idx]
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp))
                                .background(if (d == 0) Color(0xFFF1F5F9) else Color(0xFFBAE6FD))
                                .bounceClick(enabled = d != 0) { if (canFlyOut(idx)) board = board.copyOf().apply { this[idx] = 0 } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (d != 0) {
                                Canvas(Modifier.fillMaxSize(0.55f)) {
                                    val w = this.size.width; val h = this.size.height
                                    val col = Color(0xFF0369A1); val st = 4.dp.toPx()
                                    val cx = w / 2f; val cy = h / 2f
                                    when (d) {
                                        1 -> { drawLine(col, Offset(cx, h), Offset(cx, 0f), st, StrokeCap.Round); drawLine(col, Offset(cx, 0f), Offset(cx - 8.dp.toPx(), 10.dp.toPx()), st, StrokeCap.Round); drawLine(col, Offset(cx, 0f), Offset(cx + 8.dp.toPx(), 10.dp.toPx()), st, StrokeCap.Round) }
                                        2 -> { drawLine(col, Offset(0f, cy), Offset(w, cy), st, StrokeCap.Round); drawLine(col, Offset(w, cy), Offset(w - 10.dp.toPx(), cy - 8.dp.toPx()), st, StrokeCap.Round); drawLine(col, Offset(w, cy), Offset(w - 10.dp.toPx(), cy + 8.dp.toPx()), st, StrokeCap.Round) }
                                        3 -> { drawLine(col, Offset(cx, 0f), Offset(cx, h), st, StrokeCap.Round); drawLine(col, Offset(cx, h), Offset(cx - 8.dp.toPx(), h - 10.dp.toPx()), st, StrokeCap.Round); drawLine(col, Offset(cx, h), Offset(cx + 8.dp.toPx(), h - 10.dp.toPx()), st, StrokeCap.Round) }
                                        4 -> { drawLine(col, Offset(w, cy), Offset(0f, cy), st, StrokeCap.Round); drawLine(col, Offset(0f, cy), Offset(10.dp.toPx(), cy - 8.dp.toPx()), st, StrokeCap.Round); drawLine(col, Offset(0f, cy), Offset(10.dp.toPx(), cy + 8.dp.toPx()), st, StrokeCap.Round) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Поле свободно!", "Все указатели вылетели", config.mode, roomState, onRematch = { level += 1 }, onExit = onExit)
        }
    }
}
