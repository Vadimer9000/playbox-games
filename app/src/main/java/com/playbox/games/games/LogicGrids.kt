package com.playbox.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.games.data.PlayerPrefs
import com.playbox.games.model.GameId
import com.playbox.games.model.LaunchConfig
import com.playbox.games.model.PlayMode
import com.playbox.games.network.OnlineClient
import com.playbox.games.network.OnlineEvent
import com.playbox.games.network.OnlineRoomState
import com.playbox.games.ui.components.GameResultBanner
import com.playbox.games.ui.theme.PlayBoxColors
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

internal val SORT_PALETTE = listOf(
    Color(0xFFEF476F), Color(0xFF118AB2), Color(0xFF06D6A0),
    Color(0xFFFFCA3A), Color(0xFF9B5DE5), Color(0xFFF3722C)
)

// Shared engine for WaterSort & NutsBolts
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
        val colorCount = (3 + (lvl / 2)).coerceAtMost(6)
        val rng = Random(s + lvl * 31)
        val pool = (0 until colorCount).flatMap { c -> List(4) { c } }.shuffled(rng)
        val filled = (0 until colorCount).map { i -> pool.subList(i * 4, (i + 1) * 4) }
        return filled + listOf(emptyList(), emptyList())
    }

    var containers by remember(level, seed) { mutableStateOf(buildContainers(level, seed)) }
    var history by remember(level, seed) { mutableStateOf<List<List<List<Int>>>>(emptyList()) }
    var selectedIdx by remember(level, seed) { mutableStateOf<Int?>(null) }
    var oppLevel by remember(resetSignal) { mutableIntStateOf(1) }

    val solved = containers.all { it.isEmpty() || (it.size == 4 && it.distinct().size == 1) }

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

    fun tryPour(from: Int, to: Int) {
        if (from == to) return
        val src = containers[from]; val dst = containers[to]
        if (src.isEmpty() || dst.size >= 4) return
        val topColor = src.last()
        if (dst.isNotEmpty() && dst.last() != topColor) return

        var count = 0
        for (i in src.lastIndex downTo 0) {
            if (src[i] == topColor && dst.size + count < 4) count++ else break
        }
        if (count == 0) return
        history = history + listOf(containers)
        containers = containers.mapIndexed { idx, list ->
            when (idx) {
                from -> list.dropLast(count)
                to -> list + List(count) { topColor }
                else -> list
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(if (isBolts) Color(0xFF221E2A) else Color(0xFF43304A)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScorePill("Уровень", level, PlayBoxColors.BoltGold, true)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соп. ур.", oppLevel, PlayBoxColors.OnlinePurple, false)
            OutlinedButton(enabled = history.isNotEmpty(), onClick = {
                containers = history.last(); history = history.dropLast(1); selectedIdx = null
            }) { Text("Отмена", color = Color.White) }
        }
        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            containers.forEachIndexed { idx, stack ->
                val isSel = selectedIdx == idx
                Column(
                    modifier = Modifier
                        .size(width = 48.dp, height = 164.dp)
                        .clip(RoundedCornerShape(if (isBolts) 10.dp else 22.dp))
                        .background(if (isBolts) Color(0xFF342E40) else Color.White.copy(alpha = 0.14f))
                        .border(if (isSel) 3.dp else 2.dp, if (isSel) PlayBoxColors.BoltGold else Color.White.copy(alpha = 0.7f), RoundedCornerShape(if (isBolts) 10.dp else 22.dp))
                        .clickable(enabled = !solved) {
                            val sel = selectedIdx
                            if (sel == null) {
                                if (stack.isNotEmpty()) selectedIdx = idx
                            } else {
                                tryPour(sel, idx)
                                selectedIdx = null
                            }
                        }
                        .padding(6.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(if (isBolts) 4.dp else 0.dp)
                ) {
                    stack.reversed().forEach { cIdx ->
                        Box(
                            Modifier.fillMaxWidth().height(34.dp)
                                .clip(RoundedCornerShape(if (isBolts) 8.dp else 4.dp))
                                .background(SORT_PALETTE[cIdx % SORT_PALETTE.size])
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (solved) {
            GameResultBanner("Уровень $level пройден!", "Все цвета собраны идеально", config.mode, roomState,
                onRematch = { level += 1 },
                onExit = onExit
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

// 13. SUDOKU (Generated valid 9x9 via base permutation)
private fun generateSudoku(seed: Int): Pair<IntArray, IntArray> {
    val rng = Random(seed)
    val digits = (1..9).shuffled(rng)
    val sol = IntArray(81)
    for (r in 0..8) for (c in 0..8) {
        val v = (r * 3 + r / 3 + c) % 9
        sol[r * 9 + c] = digits[v]
    }
    val puzzle = sol.copyOf()
    val blanks = (0..80).shuffled(rng).take(44)
    blanks.forEach { puzzle[it] = 0 }
    return puzzle to sol
}

@Composable
fun SudokuScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    val (fixedInit, solution) = remember(seed) { generateSudoku(seed) }
    var grid by remember(seed) { mutableStateOf(fixedInit.copyOf()) }
    var selected by remember(seed) { mutableStateOf<Int?>(null) }
    var oppPct by remember(seed) { mutableIntStateOf(0) }

    val filledOk = grid.indices.count { grid[it] == solution[it] }
    val won = filledOk == 81

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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Заполнено", filledOk, Color(0xFF1AA7E3), true)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соперник", oppPct, PlayBoxColors.OnlinePurple, false)
        }
        Spacer(Modifier.height(10.dp))

        Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White).border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(12.dp)).padding(4.dp)) {
            for (r in 0..8) {
                Row(Modifier.weight(1f)) {
                    for (c in 0..8) {
                        val idx = r * 9 + c
                        val isFixed = fixedInit[idx] != 0
                        val v = grid[idx]
                        val wrong = v != 0 && v != solution[idx]
                        val boxTint = ((r / 3) + (c / 3)) % 2 == 0
                        val bg = when {
                            idx == selected -> Color(0xFFBAE6FD)
                            wrong -> Color(0xFFFECACA)
                            boxTint -> Color(0xFFF8FAFC)
                            else -> Color(0xFFE2E8F0)
                        }
                        Box(
                            Modifier.weight(1f).fillMaxSize().border(0.5.dp, Color(0xFFCBD5E1)).background(bg).clickable(enabled = !isFixed && !won) { selected = idx },
                            contentAlignment = Alignment.Center
                        ) {
                            if (v != 0) Text("$v", fontWeight = if (isFixed) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 17.sp, color = if (wrong) Color.Red else if (isFixed) PlayBoxColors.DarkInk else Color(0xFF0284C7))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (n in 1..9) {
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(2.dp, PlayBoxColors.DarkInk, RoundedCornerShape(10.dp))
                        .clickable(enabled = selected != null && !won) {
                            val s = selected!!
                            val next = grid.copyOf().apply { this[s] = n }
                            grid = next
                            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("pct", next.indices.count { next[it] == solution[it] }))
                        },
                    contentAlignment = Alignment.Center
                ) { Text("$n", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            }
        }
        if (won) {
            GameResultBanner("Судоку решено!", "Все 81 клетка верны", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}

// 14. SHIKAKU (5x5 rectangular partition puzzles)
private data class ShikakuClue(val r: Int, val c: Int, val area: Int)
private data class ShikakuPuzzle(val size: Int, val clues: List<ShikakuClue>)

private val SHIKAKU_LEVELS = listOf(
    ShikakuPuzzle(5, listOf(ShikakuClue(0, 0, 6), ShikakuClue(0, 3, 4), ShikakuClue(2, 1, 5), ShikakuClue(3, 2, 6), ShikakuClue(4, 0, 4))),
    ShikakuPuzzle(5, listOf(ShikakuClue(0, 1, 8), ShikakuClue(1, 4, 3), ShikakuClue(2, 2, 4), ShikakuClue(4, 0, 6), ShikakuClue(4, 3, 4))),
    ShikakuPuzzle(5, listOf(ShikakuClue(0, 0, 5), ShikakuClue(1, 2, 6), ShikakuClue(1, 4, 4), ShikakuClue(3, 1, 4), ShikakuClue(4, 3, 6)))
)

@Composable
fun ShikakuScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var lvlIdx by remember(resetSignal) { mutableIntStateOf(abs(config.seed) % SHIKAKU_LEVELS.size) }
    val puz = SHIKAKU_LEVELS[lvlIdx]
    // Assign each cell an owner clue index (-1 = unassigned)
    var owners by remember(lvlIdx, resetSignal) { mutableStateOf(IntArray(puz.size * puz.size) { -1 }) }
    var startCell by remember(lvlIdx, resetSignal) { mutableStateOf<Int?>(null) }

    val won = remember(owners, lvlIdx) {
        if (owners.any { it < 0 }) false
        else puz.clues.indices.all { cIdx ->
            val cells = owners.indices.filter { owners[it] == cIdx }
            if (cells.size != puz.clues[cIdx].area) false
            else {
                val rs = cells.map { it / puz.size }; val cs = cells.map { it % puz.size }
                val rectArea = (rs.max() - rs.min() + 1) * (cs.max() - cs.min() + 1)
                val clueCell = puz.clues[cIdx].r * puz.size + puz.clues[cIdx].c
                rectArea == cells.size && clueCell in cells
            }
        }
    }

    LaunchedEffect(won) {
        if (won) {
            prefs.recordWin(GameId.SHIKAKU)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Нажмите первый угол и второй угол прямоугольника, содержащего одно число (площадь = числу).", fontSize = 13.sp, color = PlayBoxColors.MutedInk)
        Spacer(Modifier.height(12.dp))

        Column(Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White).border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(16.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until puz.size) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until puz.size) {
                        val idx = r * puz.size + c
                        val own = owners[idx]
                        val clue = puz.clues.firstOrNull { it.r == r && it.c == c }
                        val bg = when {
                            idx == startCell -> PlayBoxColors.BoltGold
                            own >= 0 -> SORT_PALETTE[own % SORT_PALETTE.size].copy(alpha = 0.72f)
                            else -> PlayBoxColors.CreamBg
                        }
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp)).background(bg).clickable(enabled = !won) {
                                val s = startCell
                                if (s == null) {
                                    startCell = idx
                                } else {
                                    val r1 = minOf(s / puz.size, r); val r2 = maxOf(s / puz.size, r)
                                    val c1 = minOf(s % puz.size, c); val c2 = maxOf(s % puz.size, c)
                                    val rectCells = (r1..r2).flatMap { rr -> (c1..c2).map { cc -> rr * puz.size + cc } }
                                    val cluesInside = puz.clues.indices.filter { ci -> (puz.clues[ci].r * puz.size + puz.clues[ci].c) in rectCells }
                                    if (cluesInside.size == 1 && rectCells.size == puz.clues[cluesInside[0]].area) {
                                        val next = owners.copyOf()
                                        for (i in next.indices) if (next[i] == cluesInside[0]) next[i] = -1
                                        rectCells.forEach { next[it] = cluesInside[0] }
                                        owners = next
                                    }
                                    startCell = null
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            if (clue != null) Text("${clue.area}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = PlayBoxColors.DarkInk)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { owners = IntArray(puz.size * puz.size) { -1 }; startCell = null }) { Text("Сбросить сетку") }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Шикаку решено!", "Идеальное разбиение", config.mode, roomState,
                onRematch = { lvlIdx = (lvlIdx + 1) % SHIKAKU_LEVELS.size },
                onExit = onExit
            )
        }
    }
}

// 15. NUMBER MATCH (Ten Pair)
@Composable
fun NumberMatchScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    var cells by remember(seed) {
        val rng = Random(seed)
        mutableStateOf(List(27) { rng.nextInt(1, 10) }) // 0 = cleared
    }
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
        // linear adjacent ignoring 0s OR grid horizontal/vertical/diagonal adjacent ignoring 0s
        if (abs(dr) <= 1 && abs(dc) <= 1) return true
        val step = when {
            r1 == r2 -> if (c2 > c1) 1 else -1
            c1 == c2 -> if (r2 > r1) cols else -cols
            abs(dr) == abs(dc) -> (if (dr > 0) cols else -cols) + (if (dc > 0) 1 else -1)
            else -> 0
        }
        if (step != 0) {
            var cur = i + step
            while (cur != j && cur in cells.indices) {
                if (cells[cur] != 0) return false
                cur += step
            }
            if (cur == j) return true
        }
        // Check flat reading order skipping 0s
        val lo = minOf(i, j); val hi = maxOf(i, j)
        return ((lo + 1) until hi).all { cells[it] == 0 }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Очки", score, Color(0xFF00A8E8), true)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соперник", oppScore, PlayBoxColors.OnlinePurple, false)
            OutlinedButton(enabled = cells.count { it > 0 } < 45, onClick = {
                val rem = cells.filter { it != 0 }
                cells = cells + rem
                selectedIdx = null
            }) { Text("+ Добавить") }
        }
        Spacer(Modifier.height(10.dp))

        val rows = (cells.size + 8) / 9
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(14.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0..8) {
                        val idx = r * 9 + c
                        val v = cells.getOrElse(idx) { 0 }
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                                .background(if (idx == selectedIdx) PlayBoxColors.BoltGold else if (v == 0) Color(0xFFF8FAFC) else Color(0xFFE0F2FE))
                                .clickable(enabled = v != 0) {
                                    val s = selectedIdx
                                    if (s == null) selectedIdx = idx
                                    else if (s == idx) selectedIdx = null
                                    else if (canPair(s, idx)) {
                                        cells = cells.mapIndexed { k, old -> if (k == s || k == idx) 0 else old }
                                        score += 10
                                        selectedIdx = null
                                        if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
                                        if (cells.all { it == 0 }) prefs.recordWin(GameId.NUMBER_MATCH)
                                    } else selectedIdx = idx
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (v != 0) Text("$v", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = PlayBoxColors.DarkInk)
                        }
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
        // ensure every row & col has at least 1 active
        for (i in 0 until size) {
            if ((0 until size).none { mask[i * size + it] }) mask[i * size + i] = true
        }
        nums to mask
    }
    var selectedMask by remember(seed) { mutableStateOf(BooleanArray(size * size)) }
    val rowTargets = remember(seed) { IntArray(size) { r -> (0 until size).filter { solutionMask[r * size + it] }.sumOf { numbers[r * size + it] } } }
    val colTargets = remember(seed) { IntArray(size) { c -> (0 until size).filter { solutionMask[it * size + c] }.sumOf { numbers[it * size + c] } } }

    val won = (0 until size).all { r ->
        (0 until size).filter { selectedMask[r * size + it] }.sumOf { numbers[r * size + it] } == rowTargets[r]
    } && (0 until size).all { c ->
        (0 until size).filter { selectedMask[it * size + c] }.sumOf { numbers[it * size + c] } == colTargets[c]
    }

    LaunchedEffect(won) {
        if (won) {
            prefs.recordWin(GameId.CROSS_SUMS)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex)
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Выделите числа так, чтобы суммы по всем строкам и столбцам совпали с целями.", fontSize = 13.sp, color = PlayBoxColors.MutedInk)
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
                        val idx = r * size + c
                        val on = selectedMask[idx]
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp))
                                .background(if (on) Color(0xFFFFCA3A) else Color.White)
                                .clickable(enabled = !won) { selectedMask = selectedMask.copyOf().apply { this[idx] = !this[idx] } },
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

// 17. CONNECT 2248 (Drag chain of powers of 2)
@Composable
fun Connect2248Screen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val rows = 6; val cols = 5
    var grid by remember(resetSignal) {
        val rng = Random(config.seed)
        mutableStateOf(IntArray(rows * cols) { listOf(2, 4, 8, 16).random(rng) })
    }
    var chain by remember(resetSignal) { mutableStateOf<List<Int>>(emptyList()) }
    var score by remember(resetSignal) { mutableIntStateOf(0) }
    var oppScore by remember(resetSignal) { mutableIntStateOf(0) }

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
        if (chain.size >= 2) {
            val total = chain.sumOf { grid[it] }
            var merged = 2
            while (merged * 2 <= total) merged *= 2
            val next = grid.copyOf()
            chain.dropLast(1).forEach { next[it] = listOf(2, 4, 8, 16).random() }
            next[chain.last()] = merged
            grid = next
            score += merged
            prefs.updateBestScore(GameId.CONNECT_2248, score)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
        }
        chain = emptyList()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF283B4A)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Счёт", score, Color(0xFF2EC4B6), true)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соперник", oppScore, PlayBoxColors.OnlinePurple, false)
        }
        Spacer(Modifier.height(10.dp))
        Text("Проведите пальцем по одинаковым плиткам (и удвоениям) для слияния", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
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
                                val last = chain.last()
                                val lr = last / cols; val lc = last % cols
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
                            val idx = r * cols + c
                            val v = grid[idx]
                            val sel = idx in chain
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
        }
    }
}

// 18. DROP MERGE (5-column 2048 drop)
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
            onlineClient.events.collect { ev ->
                if (ev is OnlineEvent.OpponentState) oppScore = ev.payload.optInt("score", oppScore)
            }
        }
    }

    fun dropInCol(c: Int) {
        if (gameOver) return
        val r = (rows - 1 downTo 0).firstOrNull { grid[it * cols + c] == 0 } ?: return
        val next = grid.copyOf()
        next[r * cols + c] = nextBlock
        // Cascade merges vertically & horizontally
        var mergedAny = true
        while (mergedAny) {
            mergedAny = false
            for (col in 0 until cols) {
                for (row in rows - 1 downTo 1) {
                    val v = next[row * cols + col]
                    if (v > 0 && next[(row - 1) * cols + col] == v) {
                        next[row * cols + col] = v * 2
                        next[(row - 1) * cols + col] = 0
                        score += v * 2
                        mergedAny = true
                    }
                }
            }
            // gravity
            for (col in 0 until cols) {
                val nonZero = (rows - 1 downTo 0).map { next[it * cols + col] }.filter { it > 0 }
                for (row in 0 until rows) {
                    val idxFromBottom = rows - 1 - row
                    next[row * cols + col] = nonZero.getOrElse(idxFromBottom) { 0 }
                }
            }
        }
        grid = next
        nextBlock = listOf(2, 4, 8, 16, 32).random()
        prefs.updateBestScore(GameId.DROP_MERGE, score)
        if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
        if ((0 until cols).all { next[it] != 0 }) gameOver = true
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF33363F)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Счёт", score, Color(0xFF6BCB77), true)
            ScorePill("Блок", nextBlock, PlayBoxColors.BoltGold, false)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соп.", oppScore, PlayBoxColors.OnlinePurple, false)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(0.9f).weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF23262E)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until cols) {
                        val v = grid[r * cols + c]
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp))
                                .background(if (v == 0) Color(0xFF2E323C) else SORT_PALETTE[(Integer.numberOfTrailingZeros(v.coerceAtLeast(2)) - 1) % SORT_PALETTE.size])
                                .clickable(enabled = !gameOver) { dropInCol(c) },
                            Alignment.Center
                        ) { if (v > 0) Text("$v", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
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

// 19. SLIDING PUZZLE (15-puzzle 4x4 & 3x3)
@Composable
fun SlidingPuzzleScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var size by remember { mutableIntStateOf(4) }
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }

    fun makeSolvable(n: Int, s: Int): IntArray {
        val arr = IntArray(n * n) { if (it == n * n - 1) 0 else it + 1 }
        val rng = Random(s)
        var empty = n * n - 1
        repeat(160) {
            val r = empty / n; val c = empty % n
            val nb = listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)
                .filter { (nr, nc) -> nr in 0 until n && nc in 0 until n }
                .map { (nr, nc) -> nr * n + nc }
                .random(rng)
            arr[empty] = arr[nb]; arr[nb] = 0; empty = nb
        }
        return arr
    }

    var tiles by remember(size, seed) { mutableStateOf(makeSolvable(size, seed)) }
    var moves by remember(size, seed) { mutableIntStateOf(0) }
    val won = tiles.indices.all { i -> if (i == tiles.lastIndex) tiles[i] == 0 else tiles[i] == i + 1 }

    LaunchedEffect(won) {
        if (won) {
            prefs.recordWin(GameId.SLIDING_PUZZLE)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = size == 3, onClick = { size = 3 }, label = { Text("3×3") })
            FilterChip(selected = size == 4, onClick = { size = 4 }, label = { Text("4×4") })
            ScorePill("Ходы", moves, PlayBoxColors.Player2Color, true)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2F5368)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until size) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until size) {
                        val idx = r * size + c
                        val v = tiles[idx]
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(12.dp))
                                .background(if (v == 0) Color(0xFF1F3847) else Color(0xFFF4A261))
                                .clickable(enabled = v != 0 && !won) {
                                    val empty = tiles.indexOfFirst { it == 0 }
                                    val er = empty / size; val ec = empty % size
                                    if (abs(er - r) + abs(ec - c) == 1) {
                                        tiles = tiles.copyOf().apply { this[empty] = v; this[idx] = 0 }
                                        moves += 1
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
            GameResultBanner("Собрано за $moves ходов!", "Отлично!", config.mode, roomState,
                onRematch = { seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}

// 20. ARROWS PUZZLE (Tap Away / Unblock arrows)
@Composable
fun ArrowsPuzzleScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val size = 5
    var level by remember(resetSignal) { mutableIntStateOf(1) }

    // 0=empty, 1=UP, 2=RIGHT, 3=DOWN, 4=LEFT
    fun generateClearable(lvl: Int): IntArray {
        val rng = Random(config.seed + lvl * 17)
        val arr = IntArray(size * size)
        val order = (0 until size * size).shuffled(rng)
        // Build backwards by picking a cell that currently has at least one clear direction to edge
        for (cell in order) {
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
        if (won) {
            prefs.recordWin(GameId.ARROWS_PUZZLE)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("lvl", level + 1))
        }
    }

    fun canFlyOut(idx: Int): Boolean {
        val d = board[idx]
        if (d == 0) return false
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
        Text("Нажимайте на стрелки, которым ничего не мешает вылететь с поля.", fontSize = 13.sp, color = PlayBoxColors.DarkInk)
        Spacer(Modifier.height(16.dp))

        val glyphs = listOf("", "⬆️", "➡️", "⬇️", "⬅️")
        Column(Modifier.fillMaxWidth(0.9f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color.White).border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(18.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until size) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until size) {
                        val idx = r * size + c
                        val d = board[idx]
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp))
                                .background(if (d == 0) Color(0xFFF1F5F9) else Color(0xFFBAE6FD))
                                .clickable(enabled = d != 0) {
                                    if (canFlyOut(idx)) board = board.copyOf().apply { this[idx] = 0 }
                                },
                            contentAlignment = Alignment.Center
                        ) { if (d != 0) Text(glyphs[d], fontSize = 24.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Поле свободно!", "Все указатели вылетели", config.mode, roomState,
                onRematch = { level += 1 },
                onExit = onExit
            )
        }
    }
}
