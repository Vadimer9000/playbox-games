package com.playbox.games.games

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.playbox.games.ui.components.PlayBoxButton
import com.playbox.games.ui.components.PlayBoxOutlinedButton
import com.playbox.games.ui.theme.PlayBoxColors
import com.playbox.games.ui.theme.bounceClick
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

// 21. PAINT MAZE (Smooth animated ball glide!)
private data class MazeLevel(val size: Int, val walls: Set<Int>, val start: Int)

private val PAINT_MAZE_LEVELS = listOf(
    MazeLevel(6, setOf(7, 8, 16, 20, 25, 26), 0),
    MazeLevel(6, setOf(4, 10, 14, 19, 21, 28, 33), 0),
    MazeLevel(7, setOf(3, 9, 10, 18, 22, 24, 30, 38, 44), 0),
    MazeLevel(7, setOf(5, 11, 15, 19, 26, 31, 37, 42), 0)
)

@Composable
fun PaintMazeScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var lvl by remember(resetSignal) { mutableIntStateOf(0) }
    val maze = PAINT_MAZE_LEVELS[lvl % PAINT_MAZE_LEVELS.size]
    var ball by remember(lvl, resetSignal) { mutableIntStateOf(maze.start) }
    var painted by remember(lvl, resetSignal) { mutableStateOf(setOf(maze.start)) }
    val totalFloor = maze.size * maze.size - maze.walls.size
    val won = painted.size >= totalFloor

    val animBallX by animateFloatAsState((ball % maze.size).toFloat(), spring(stiffness = Spring.StiffnessMedium), label = "mx")
    val animBallY by animateFloatAsState((ball / maze.size).toFloat(), spring(stiffness = Spring.StiffnessMedium), label = "my")

    LaunchedEffect(won) {
        if (won) { prefs.recordWin(GameId.PAINT_MAZE); if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("lvl", lvl + 1)) }
    }

    fun slide(dr: Int, dc: Int) {
        if (won) return
        var r = ball / maze.size; var c = ball % maze.size
        val newly = painted.toMutableSet()
        while (true) {
            val nr = r + dr; val nc = c + dc; val ni = nr * maze.size + nc
            if (nr !in 0 until maze.size || nc !in 0 until maze.size || ni in maze.walls) break
            r = nr; c = nc; newly.add(ni)
        }
        ball = r * maze.size + c; painted = newly
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF23262E)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Закрашено %", painted.size * 100 / totalFloor, Color(0xFFE0526D), true)
        Spacer(Modifier.height(10.dp))
        Text("Свайпайте, чтобы катить шар до стенки и закрасить весь пол", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))

        Box(
            Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF171A21)).padding(6.dp)
                .pointerInput(ball, won) {
                    var ax = 0f; var ay = 0f; var done = false
                    detectDragGestures(
                        onDragStart = { ax = 0f; ay = 0f; done = false },
                        onDrag = { ch, d ->
                            ch.consume(); if (done) return@detectDragGestures
                            ax += d.x; ay += d.y
                            if (abs(ax) > 35f || abs(ay) > 35f) {
                                done = true
                                if (abs(ax) > abs(ay)) slide(0, if (ax > 0) 1 else -1) else slide(if (ay > 0) 1 else -1, 0)
                            }
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / maze.size; val gap = 2.dp.toPx()
                for (r in 0 until maze.size) for (c in 0 until maze.size) {
                    val idx = r * maze.size + c
                    val col = when { idx in maze.walls -> Color(0xFF111827); idx in painted -> Color(0xFFE0526D); else -> Color(0xFF3A414E) }
                    drawRoundRect(col, Offset(c * cell + gap, r * cell + gap), Size(cell - 2 * gap, cell - 2 * gap), CornerRadius(6.dp.toPx()))
                }
                val bc = Offset((animBallX + 0.5f) * cell, (animBallY + 0.5f) * cell)
                drawCircle(Color.White, cell * 0.34f, bc)
                drawCircle(Color(0xFFF43F5E).copy(alpha = 0.25f), cell * 0.14f, Offset(bc.x - cell * 0.1f, bc.y - cell * 0.1f))
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Лабиринт закрашен!", "Уровень ${lvl + 1} пройден", config.mode, roomState, onRematch = { lvl += 1 }, onExit = onExit)
        }
    }
}

// 22. FLOW COLORS (Continuous rounded glowing pipes on Canvas!)
private data class FlowLevel(val size: Int, val pairs: List<Pair<Int, Int>>)

private val FLOW_LEVELS = listOf(
    FlowLevel(5, listOf(0 to 21, 2 to 18, 4 to 24, 6 to 17)),
    FlowLevel(5, listOf(0 to 20, 1 to 14, 3 to 23, 7 to 16)),
    FlowLevel(5, listOf(0 to 12, 4 to 22, 10 to 24, 15 to 21)),
    FlowLevel(5, listOf(0 to 24, 1 to 16, 4 to 20, 8 to 22))
)

@Composable
fun FlowColorsScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var lvl by remember(resetSignal) { mutableIntStateOf(0) }
    val flow = FLOW_LEVELS[lvl % FLOW_LEVELS.size]
    var paths by remember(lvl, resetSignal) { mutableStateOf(List(flow.pairs.size) { emptyList<Int>() }) }
    var activeColor by remember(lvl, resetSignal) { mutableIntStateOf(-1) }
    val flowColors = listOf(Color(0xFF70E000), Color(0xFFFFBE0B), Color(0xFF38BDF8), Color(0xFFEF476F))

    val won = flow.pairs.indices.all { c ->
        val p = paths[c]; val (a, b) = flow.pairs[c]
        p.size >= 2 && ((p.first() == a && p.last() == b) || (p.first() == b && p.last() == a))
    }

    LaunchedEffect(won) { if (won) prefs.recordWin(GameId.FLOW_COLORS) }
    fun endpointColor(idx: Int): Int = flow.pairs.indexOfFirst { it.first == idx || it.second == idx }

    Column(Modifier.fillMaxSize().background(Color(0xFF4D384F)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Уровень", lvl + 1, PlayBoxColors.BoltGold, true)
        Spacer(Modifier.height(10.dp))
        Text("Соедините пары точек трубами, не пересекая линии", color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        Canvas(
            Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2B202C)).border(3.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .pointerInput(flow, paths) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val c = ((pos.x / size.width) * flow.size).toInt().coerceIn(0, flow.size - 1)
                            val r = ((pos.y / size.height) * flow.size).toInt().coerceIn(0, flow.size - 1)
                            val idx = r * flow.size + c; val ep = endpointColor(idx)
                            if (ep >= 0) { activeColor = ep; paths = paths.mapIndexed { i, list -> if (i == ep) listOf(idx) else list } }
                            else activeColor = -1
                        },
                        onDrag = { ch, _ ->
                            ch.consume()
                            if (activeColor < 0) return@detectDragGestures
                            val c = ((ch.position.x / size.width) * flow.size).toInt().coerceIn(0, flow.size - 1)
                            val r = ((ch.position.y / size.height) * flow.size).toInt().coerceIn(0, flow.size - 1)
                            val idx = r * flow.size + c; val curPath = paths[activeColor]
                            if (curPath.isEmpty() || idx == curPath.last()) return@detectDragGestures
                            val last = curPath.last()
                            val adj = abs(last / flow.size - r) + abs(last % flow.size - c) == 1
                            val ep = endpointColor(idx)
                            if (adj && (ep == -1 || ep == activeColor)) {
                                if (idx in curPath) paths = paths.mapIndexed { i, l -> if (i == activeColor) l.take(l.indexOf(idx) + 1) else l }
                                else paths = paths.mapIndexed { i, l -> if (i == activeColor) l + idx else if (idx in l) l.take(l.indexOf(idx)) else l }
                            }
                        },
                        onDragEnd = { activeColor = -1 }
                    )
                }
        ) {
            val cell = size.width / flow.size
            for (i in 1 until flow.size) {
                drawLine(Color(0xFF3E2E40), Offset(i * cell, 0f), Offset(i * cell, size.height), 1.5.dp.toPx())
                drawLine(Color(0xFF3E2E40), Offset(0f, i * cell), Offset(size.width, i * cell), 1.5.dp.toPx())
            }
            // Continuous rounded pipes
            paths.forEachIndexed { colorIdx, path ->
                val col = flowColors[colorIdx]
                for (k in 0 until path.lastIndex) {
                    val a = path[k]; val b = path[k + 1]
                    drawLine(
                        col,
                        Offset((a % flow.size + 0.5f) * cell, (a / flow.size + 0.5f) * cell),
                        Offset((b % flow.size + 0.5f) * cell, (b / flow.size + 0.5f) * cell),
                        strokeWidth = cell * 0.34f,
                        cap = StrokeCap.Round
                    )
                }
            }
            // Endpoints
            flow.pairs.forEachIndexed { colorIdx, (a, b) ->
                val col = flowColors[colorIdx]
                listOf(a, b).forEach { idx ->
                    val center = Offset((idx % flow.size + 0.5f) * cell, (idx / flow.size + 0.5f) * cell)
                    drawCircle(col, cell * 0.34f, center)
                    drawCircle(Color.White, cell * 0.13f, center)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Все цвета соединены!", "Отличная логика", config.mode, roomState, onRematch = { lvl += 1 }, onExit = onExit)
        }
    }
}

// 23. MULTI COLOR FILL
@Composable
fun MultiColorFillScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val size = 5
    var level by remember(resetSignal) { mutableIntStateOf(1) }
    val starts = listOf(0, 4, 20, 24)
    val palette = listOf(Color(0xFFF6B756), Color(0xFF2EC4B6), Color(0xFFEF476F), Color(0xFF8338EC))
    var grid by remember(level, resetSignal) { mutableStateOf(IntArray(size * size) { i -> starts.indexOf(i) }) }
    var selectedColor by remember(level, resetSignal) { mutableIntStateOf(0) }
    val won = grid.none { it < 0 }

    LaunchedEffect(won) { if (won) prefs.recordWin(GameId.MULTI_COLOR_FILL) }

    fun canFill(idx: Int, col: Int): Boolean {
        if (grid[idx] >= 0) return false
        val r = idx / size; val c = idx % size
        return listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1).any { (nr, nc) -> nr in 0 until size && nc in 0 until size && grid[nr * size + nc] == col }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF2A3236)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Выберите цвет и расширяйте территорию на соседние клетки", color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            palette.forEachIndexed { i, col ->
                Box(Modifier.size(46.dp).clip(CircleShape).background(col).border(if (selectedColor == i) 3.5.dp else 1.dp, Color.White, CircleShape).bounceClick { selectedColor = i })
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(0.9f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B2023)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until size) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until size) {
                        val idx = r * size + c; val owner = grid[idx]
                        val fillOk = !won && canFill(idx, selectedColor)
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp))
                                .background(if (owner >= 0) palette[owner] else if (fillOk) palette[selectedColor].copy(alpha = 0.25f) else Color(0xFF374151))
                                .clickable(enabled = fillOk) { grid = grid.copyOf().apply { this[idx] = selectedColor } }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PlayBoxOutlinedButton(onClick = { grid = IntArray(size * size) { i -> starts.indexOf(i) } }, contentColor = Color.White) {
            Text("↺ Сбросить поле", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (won) GameResultBanner("Поле заполнено!", "Уровень $level", config.mode, roomState, onRematch = { level += 1 }, onExit = onExit)
    }
}

// 24. ESCAPE PARKING (Illustrated cars + direct drag + readable buttons)
private data class CarBlock(val id: Int, val r: Int, val c: Int, val len: Int, val horiz: Boolean, val isTarget: Boolean = false)

private val ESCAPE_PUZZLES = listOf(
    listOf(CarBlock(0, 2, 0, 2, true, true), CarBlock(1, 0, 2, 3, false), CarBlock(2, 1, 4, 3, false), CarBlock(3, 3, 2, 2, true), CarBlock(4, 4, 0, 3, true), CarBlock(5, 0, 0, 2, false)),
    listOf(CarBlock(0, 2, 1, 2, true, true), CarBlock(1, 0, 0, 2, true), CarBlock(2, 0, 3, 3, false), CarBlock(3, 1, 0, 3, false), CarBlock(4, 3, 2, 2, false), CarBlock(5, 4, 3, 3, true))
)

@Composable
fun EscapeParkingScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var lvl by remember(resetSignal) { mutableIntStateOf(0) }
    var cars by remember(lvl, resetSignal) { mutableStateOf(ESCAPE_PUZZLES[lvl % ESCAPE_PUZZLES.size]) }
    var selectedCar by remember(lvl, resetSignal) { mutableIntStateOf(0) }
    var moves by remember(lvl, resetSignal) { mutableIntStateOf(0) }
    val target = cars.first { it.isTarget }
    val won = target.c + target.len == 6

    LaunchedEffect(won) { if (won) prefs.recordWin(GameId.ESCAPE_PARKING) }

    fun occupiedByOther(excludeId: Int): Set<Pair<Int, Int>> = buildSet {
        cars.filter { it.id != excludeId }.forEach { car ->
            for (k in 0 until car.len) add(if (car.horiz) car.r to (car.c + k) else (car.r + k) to car.c)
        }
    }

    fun moveCar(carId: Int, delta: Int) {
        val car = cars.firstOrNull { it.id == carId } ?: return
        val occ = occupiedByOther(car.id)
        val nr = if (car.horiz) car.r else car.r + delta
        val nc = if (car.horiz) car.c + delta else car.c
        val ok = (0 until car.len).all { k ->
            val rr = if (car.horiz) nr else nr + k; val cc = if (car.horiz) nc + k else nc
            rr in 0..5 && cc in 0..5 && (rr to cc) !in occ
        }
        if (ok) { cars = cars.map { if (it.id == car.id) it.copy(r = nr, c = nc) else it }; moves += 1 }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF6B4C3B)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Ходы", moves, PlayBoxColors.Player1Color, true)
        Spacer(Modifier.height(8.dp))
        Text("Двигайте машины пальцем или кнопками — выведите красный авто вправо!", color = Color.White, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF4A3327)).padding(6.dp)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(cars, won) {
                    var dragCarId = -1; var accX = 0f; var accY = 0f
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (won) return@detectDragGestures
                            val cell = size.width / 6f
                            val c = (pos.x / cell).toInt().coerceIn(0, 5); val r = (pos.y / cell).toInt().coerceIn(0, 5)
                            val hit = cars.firstOrNull { if (it.horiz) it.r == r && c in it.c until (it.c + it.len) else it.c == c && r in it.r until (it.r + it.len) }
                            if (hit != null) { dragCarId = hit.id; selectedCar = hit.id; accX = 0f; accY = 0f }
                            else dragCarId = -1
                        },
                        onDrag = { ch, drag ->
                            ch.consume()
                            val car = cars.firstOrNull { it.id == dragCarId } ?: return@detectDragGestures
                            val cell = size.width / 6f
                            if (car.horiz) {
                                accX += drag.x
                                if (abs(accX) > cell * 0.55f) { moveCar(car.id, if (accX > 0) 1 else -1); accX = 0f }
                            } else {
                                accY += drag.y
                                if (abs(accY) > cell * 0.55f) { moveCar(car.id, if (accY > 0) 1 else -1); accY = 0f }
                            }
                        }
                    )
                }
            ) {
                val cell = size.width / 6f
                for (r in 0..5) for (c in 0..5) {
                    drawRoundRect(Color(0xFF5A4032), Offset(c * cell + 2f, r * cell + 2f), Size(cell - 4f, cell - 4f), CornerRadius(6.dp.toPx()))
                }
                // Exit gate marker on row 2 right
                drawRect(Color(0xFF06D6A0), Offset(size.width - 5.dp.toPx(), 2 * cell + 4.dp.toPx()), Size(5.dp.toPx(), cell - 8.dp.toPx()))

                cars.forEach { car ->
                    val x = car.c * cell + 4.dp.toPx(); val y = car.r * cell + 4.dp.toPx()
                    val w = (if (car.horiz) car.len * cell else cell) - 8.dp.toPx()
                    val h = (if (car.horiz) cell else car.len * cell) - 8.dp.toPx()
                    val bodyCol = if (car.isTarget) Color(0xFFE63946) else Color(0xFFEDE0C8)
                    drawRoundRect(bodyCol, Offset(x, y), Size(w, h), CornerRadius(10.dp.toPx()))
                    if (car.id == selectedCar) {
                        drawRoundRect(PlayBoxColors.BoltGold, Offset(x, y), Size(w, h), CornerRadius(10.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    }
                    // Windshield accent
                    val wsCol = Color(0xFF334155)
                    if (car.horiz) drawRoundRect(wsCol, Offset(x + w * 0.62f, y + h * 0.18f), Size(w * 0.22f, h * 0.64f), CornerRadius(4.dp.toPx()))
                    else drawRoundRect(wsCol, Offset(x + w * 0.18f, y + h * 0.62f), Size(w * 0.64f, h * 0.22f), CornerRadius(4.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val sel = cars.firstOrNull { it.id == selectedCar }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PlayBoxButton(enabled = sel != null && !won, onClick = { moveCar(selectedCar, -1) }, containerColor = PlayBoxColors.DarkInk) {
                Text(if (sel?.horiz == true) "◀ Назад" else "▲ Вверх", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            PlayBoxButton(enabled = sel != null && !won, onClick = { moveCar(selectedCar, 1) }, containerColor = PlayBoxColors.DarkInk) {
                Text(if (sel?.horiz == true) "Вперёд ▶" else "▼ Вниз", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) GameResultBanner("Побег удался!", "Решено за $moves ходов", config.mode, roomState, onRematch = { lvl += 1 }, onExit = onExit)
    }
}

// 25. WOOD BLOCKS
private val WOOD_SHAPES = listOf(
    listOf(0 to 0), listOf(0 to 0, 0 to 1), listOf(0 to 0, 1 to 0),
    listOf(0 to 0, 0 to 1, 0 to 2), listOf(0 to 0, 1 to 0, 2 to 0),
    listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), listOf(0 to 0, 1 to 0, 1 to 1)
)

@Composable
fun WoodBlocksScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var board by remember(resetSignal) { mutableStateOf(BooleanArray(81)) }
    var tray by remember(resetSignal) { mutableStateOf(List(3) { WOOD_SHAPES.random() }) }
    var selectedPiece by remember(resetSignal) { mutableIntStateOf(0) }
    var score by remember(resetSignal) { mutableIntStateOf(0) }
    var oppScore by remember(resetSignal) { mutableIntStateOf(0) }

    fun canPlace(shape: List<Pair<Int, Int>>, br: Int, bc: Int, b: BooleanArray): Boolean =
        shape.all { (dr, dc) -> val rr = br + dr; val cc = bc + dc; rr in 0..8 && cc in 0..8 && !b[rr * 9 + cc] }

    val gameOver = tray.withIndex().none { (_, sh) -> sh.isNotEmpty() && (0..8).any { r -> (0..8).any { c -> canPlace(sh, r, c, board) } } }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) onlineClient.events.collect { if (it is OnlineEvent.OpponentState) oppScore = it.payload.optInt("score", oppScore) }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF704E58)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Счёт", score, Color(0xFFF3C68F), true)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соп.", oppScore, PlayBoxColors.OnlinePurple, false)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF4A3138)).padding(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0..8) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0..8) {
                        val idx = r * 9 + c; val boxTint = ((r / 3) + (c / 3)) % 2 == 0
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp))
                                .background(when { board[idx] -> Color(0xFFF3C68F); boxTint -> Color(0xFF5D4048); else -> Color(0xFF52383F) })
                                .border(if (board[idx]) 1.dp else 0.dp, Color(0xFFD9A05B), RoundedCornerShape(4.dp))
                                .clickable(enabled = !gameOver && selectedPiece in tray.indices && tray[selectedPiece].isNotEmpty()) {
                                    val sh = tray[selectedPiece]
                                    if (canPlace(sh, r, c, board)) {
                                        val next = board.copyOf()
                                        sh.forEach { (dr, dc) -> next[(r + dr) * 9 + (c + dc)] = true }
                                        var cleared = 0; val toClear = mutableSetOf<Int>()
                                        for (rr in 0..8) if ((0..8).all { next[rr * 9 + it] }) { cleared++; (0..8).forEach { toClear.add(rr * 9 + it) } }
                                        for (cc in 0..8) if ((0..8).all { next[it * 9 + cc] }) { cleared++; (0..8).forEach { toClear.add(it * 9 + cc) } }
                                        for (br in 0..2) for (bc in 0..2) {
                                            val boxCells = (0..2).flatMap { dr -> (0..2).map { dc -> (br * 3 + dr) * 9 + (bc * 3 + dc) } }
                                            if (boxCells.all { next[it] }) { cleared++; toClear.addAll(boxCells) }
                                        }
                                        toClear.forEach { next[it] = false }
                                        board = next; score += sh.size * 2 + cleared * 20
                                        prefs.updateBestScore(GameId.WOOD_BLOCKS, score)
                                        if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
                                        val newTray = tray.mapIndexed { i, old -> if (i == selectedPiece) emptyList() else old }
                                        tray = if (newTray.all { it.isEmpty() }) List(3) { WOOD_SHAPES.random() } else newTray
                                        selectedPiece = tray.indexOfFirst { it.isNotEmpty() }.coerceAtLeast(0)
                                    }
                                }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tray.forEachIndexed { idx, shape ->
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (selectedPiece == idx) Color(0xFF4A3138) else Color(0xFF5A3D45))
                        .border(if (selectedPiece == idx) 2.5.dp else 0.dp, PlayBoxColors.BoltGold, RoundedCornerShape(12.dp))
                        .bounceClick(enabled = shape.isNotEmpty()) { selectedPiece = idx },
                    contentAlignment = Alignment.Center
                ) {
                    if (shape.isNotEmpty()) {
                        Canvas(Modifier.size(44.dp)) {
                            val cell = size.width / 3f
                            shape.forEach { (dr, dc) -> drawRoundRect(Color(0xFFF3C68F), Offset(dc * cell, dr * cell), Size(cell - 2.dp.toPx(), cell - 2.dp.toPx()), CornerRadius(3.dp.toPx())) }
                        }
                    }
                }
            }
        }
        if (gameOver) {
            GameResultBanner("Места нет!", "Итоговый счёт: $score", config.mode, roomState,
                onRematch = { board = BooleanArray(81); tray = List(3) { WOOD_SHAPES.random() }; score = 0 },
                onExit = onExit
            )
        }
    }
}

// 26. COLOR BLOCKS (Tetris with Ghost Piece + Next Piece preview + crisp buttons)
private val TETRIS_SHAPES = listOf(
    listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
    listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
    listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2),
    listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2),
    listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1)
)

@Composable
fun ColorBlocksScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val rows = 16; val cols = 10
    var board by remember(resetSignal) { mutableStateOf(IntArray(rows * cols)) }
    var piece by remember(resetSignal) { mutableStateOf(TETRIS_SHAPES.random()) }
    var pColor by remember(resetSignal) { mutableIntStateOf(1) }
    var nextPiece by remember(resetSignal) { mutableStateOf(TETRIS_SHAPES.random()) }
    var nextColor by remember(resetSignal) { mutableIntStateOf(2) }
    var pr by remember(resetSignal) { mutableIntStateOf(0) }
    var pc by remember(resetSignal) { mutableIntStateOf(3) }
    var score by remember(resetSignal) { mutableIntStateOf(0) }
    var gameOver by remember(resetSignal) { mutableStateOf(false) }

    fun fits(sh: List<Pair<Int, Int>>, r: Int, c: Int, b: IntArray): Boolean =
        sh.all { (dr, dc) -> val rr = r + dr; val cc = c + dc; rr in 0 until rows && cc in 0 until cols && b[rr * cols + cc] == 0 }

    val ghostRow = remember(piece, pr, pc, board) {
        var gr = pr; while (fits(piece, gr + 1, pc, board)) gr++; gr
    }

    fun lockAndSpawn() {
        val next = board.copyOf()
        piece.forEach { (dr, dc) -> val rr = pr + dr; val cc = pc + dc; if (rr in 0 until rows && cc in 0 until cols) next[rr * cols + cc] = pColor }
        val keptRows = (0 until rows).filter { r -> (0 until cols).any { c -> next[r * cols + c] == 0 } }
        val cleared = rows - keptRows.size
        val compacted = IntArray(rows * cols)
        val offset = rows - keptRows.size
        keptRows.forEachIndexed { idx, oldR -> for (c in 0 until cols) compacted[(offset + idx) * cols + c] = next[oldR * cols + c] }
        board = compacted
        if (cleared > 0) {
            score += cleared * 100
            prefs.updateBestScore(GameId.COLOR_BLOCKS, score)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
        }
        piece = nextPiece; pColor = nextColor; nextPiece = TETRIS_SHAPES.random(); nextColor = Random.nextInt(1, 6); pr = 0; pc = 3
        if (!fits(piece, 0, 3, compacted)) gameOver = true
    }

    LaunchedEffect(resetSignal, gameOver) {
        while (!gameOver) { delay(440L); if (fits(piece, pr + 1, pc, board)) pr += 1 else lockAndSpawn() }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF2B2438)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScorePill("Очки", score, Color(0xFFF6B756), true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("След.:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Canvas(Modifier.size(36.dp)) {
                    val c = size.width / 4f
                    nextPiece.forEach { (dr, dc) -> drawRoundRect(SORT_PALETTE[(nextColor - 1) % SORT_PALETTE.size], Offset(dc * c, dr * c), Size(c - 1f, c - 1f), CornerRadius(2.dp.toPx())) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.weight(1f).aspectRatio(10f / 16f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1624)).border(3.dp, Color.White, RoundedCornerShape(14.dp))) {
            val cw = size.width / cols; val ch = size.height / rows
            for (r in 0 until rows) for (c in 0 until cols) {
                val v = board[r * cols + c]
                if (v > 0) drawRoundRect(SORT_PALETTE[(v - 1) % SORT_PALETTE.size], Offset(c * cw + 1f, r * ch + 1f), Size(cw - 2f, ch - 2f), CornerRadius(3.dp.toPx()))
            }
            if (!gameOver) {
                // Ghost piece
                piece.forEach { (dr, dc) ->
                    drawRoundRect(Color.White.copy(alpha = 0.22f), Offset((pc + dc) * cw + 1f, (ghostRow + dr) * ch + 1f), Size(cw - 2f, ch - 2f), CornerRadius(3.dp.toPx()))
                }
                piece.forEach { (dr, dc) ->
                    drawRoundRect(SORT_PALETTE[(pColor - 1) % SORT_PALETTE.size], Offset((pc + dc) * cw + 1f, (pr + dr) * ch + 1f), Size(cw - 2f, ch - 2f), CornerRadius(3.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayBoxButton(enabled = !gameOver, onClick = { if (fits(piece, pr, pc - 1, board)) pc -= 1 }, modifier = Modifier.weight(1f), containerColor = Color(0xFF3E3552), contentPadding = PaddingValues(6.dp)) { Text("◀", fontSize = 18.sp, maxLines = 1) }
            PlayBoxButton(enabled = !gameOver, onClick = {
                val rot = piece.map { (r, c) -> c to -r }.let { list ->
                    val mr = list.minOf { it.first }; val mc = list.minOf { it.second }; list.map { (r, c) -> (r - mr) to (c - mc) }
                }
                if (fits(rot, pr, pc, board)) piece = rot
            }, modifier = Modifier.weight(1f), containerColor = Color(0xFF3E3552), contentPadding = PaddingValues(6.dp)) { Text("↻", fontSize = 18.sp, maxLines = 1) }
            PlayBoxButton(enabled = !gameOver, onClick = {
                while (fits(piece, pr + 1, pc, board)) pr += 1; lockAndSpawn()
            }, modifier = Modifier.weight(1.4f), containerColor = PlayBoxColors.Player1Color, contentPadding = PaddingValues(6.dp)) { Text("▼ Сброс", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
            PlayBoxButton(enabled = !gameOver, onClick = { if (fits(piece, pr, pc + 1, board)) pc += 1 }, modifier = Modifier.weight(1f), containerColor = Color(0xFF3E3552), contentPadding = PaddingValues(6.dp)) { Text("▶", fontSize = 18.sp, maxLines = 1) }
        }
        if (gameOver) GameResultBanner("Стакан заполнен!", "Очки: $score", config.mode, roomState, onRematch = { board = IntArray(rows * cols); score = 0; gameOver = false }, onExit = onExit)
    }
}

// 27. FALLING SAND (Sandtrix — Tetris blocks dissolve into cascading sand!)
@Composable
fun FallingSandScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val rows = 26; val cols = 18
    var sand by remember(resetSignal) { mutableStateOf(IntArray(rows * cols)) }
    var activeColor by remember(resetSignal) { mutableIntStateOf(1) }
    var dropCol by remember(resetSignal) { mutableIntStateOf(cols / 2) }
    var score by remember(resetSignal) { mutableIntStateOf(0) }

    LaunchedEffect(resetSignal) {
        var tick = 0
        while (true) {
            delay(60L)
            val next = sand.copyOf()
            if (tick % 3 == 0 && next[dropCol] == 0) {
                for (dc in -1..1) { val c = (dropCol + dc).coerceIn(0, cols - 1); if (next[c] == 0) next[c] = activeColor }
            }
            for (r in rows - 2 downTo 0) for (c in 0 until cols) {
                val v = next[r * cols + c]; if (v == 0) continue
                val below = (r + 1) * cols + c
                if (next[below] == 0) { next[below] = v; next[r * cols + c] = 0 }
                else {
                    val dl = if (c > 0 && next[(r + 1) * cols + (c - 1)] == 0) -1 else 0
                    val dr = if (c < cols - 1 && next[(r + 1) * cols + (c + 1)] == 0) 1 else 0
                    val slide = if (dl != 0 && dr != 0) (if (Random.nextBoolean()) dl else dr) else if (dl != 0) dl else dr
                    if (slide != 0) { next[(r + 1) * cols + (c + slide)] = v; next[r * cols + c] = 0 }
                }
            }
            for (color in 1..4) {
                val queue = ArrayDeque<Int>(); val visited = BooleanArray(rows * cols)
                for (r in 0 until rows) if (next[r * cols] == color) { queue.add(r * cols); visited[r * cols] = true }
                var touchesRight = false; val comp = mutableListOf<Int>()
                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst(); comp.add(cur)
                    val cr = cur / cols; val cc = cur % cols
                    if (cc == cols - 1) touchesRight = true
                    for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                        val nr = cr + dr; val nc = cc + dc
                        if (nr in 0 until rows && nc in 0 until cols) {
                            val ni = nr * cols + nc
                            if (!visited[ni] && next[ni] == color) { visited[ni] = true; queue.add(ni) }
                        }
                    }
                }
                if (touchesRight && comp.isNotEmpty()) {
                    comp.forEach { next[it] = 0 }
                    score += comp.size * 5
                    prefs.updateBestScore(GameId.FALLING_SAND, score)
                    if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
                }
            }
            sand = next; tick++
            if (tick % 45 == 0) activeColor = Random.nextInt(1, 5)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF141F38)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScorePill("Очки", score, Color(0xFFF6B756), true)
            PlayBoxOutlinedButton(onClick = { sand = IntArray(rows * cols); score = 0 }, contentColor = Color.White, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Text("Очистить", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Двигайте поток песка! Сплошной мостик одного цвета от края до края исчезает.", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))

        Canvas(
            Modifier.weight(1f).aspectRatio(cols.toFloat() / rows)
                .clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B1222)).border(3.dp, Color.White, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectDragGestures { ch, _ -> ch.consume(); dropCol = ((ch.position.x / size.width) * cols).toInt().coerceIn(1, cols - 2) }
                }
        ) {
            val cw = size.width / cols; val ch = size.height / rows
            drawCircle(SORT_PALETTE[activeColor - 1], cw * 0.75f, Offset((dropCol + 0.5f) * cw, ch * 0.55f))
            for (r in 0 until rows) for (c in 0 until cols) {
                val v = sand[r * cols + c]
                if (v > 0) drawRect(SORT_PALETTE[(v - 1) % SORT_PALETTE.size], Offset(c * cw, r * ch), Size(cw, ch))
            }
        }
    }
}
