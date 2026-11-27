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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

// 21. PAINT MAZE (Amaze-style slide-until-wall maze painter)
private data class MazeLevel(val size: Int, val walls: Set<Int>, val start: Int)

private val PAINT_MAZE_LEVELS = listOf(
    MazeLevel(6, setOf(7, 8, 16, 20, 25, 26), 0),
    MazeLevel(6, setOf(4, 10, 14, 19, 21, 28, 33), 0),
    MazeLevel(7, setOf(3, 9, 10, 18, 22, 24, 30, 38, 44), 0)
)

@Composable
fun PaintMazeScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var lvl by remember(resetSignal) { mutableIntStateOf(0) }
    val maze = PAINT_MAZE_LEVELS[lvl % PAINT_MAZE_LEVELS.size]
    var ball by remember(lvl, resetSignal) { mutableIntStateOf(maze.start) }
    var painted by remember(lvl, resetSignal) { mutableStateOf(setOf(maze.start)) }
    val totalFloor = maze.size * maze.size - maze.walls.size
    val won = painted.size >= totalFloor

    LaunchedEffect(won) {
        if (won) {
            prefs.recordWin(GameId.PAINT_MAZE)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("lvl", lvl + 1))
        }
    }

    fun slide(dr: Int, dc: Int) {
        if (won) return
        var r = ball / maze.size; var c = ball % maze.size
        val newly = painted.toMutableSet()
        while (true) {
            val nr = r + dr; val nc = c + dc
            val ni = nr * maze.size + nc
            if (nr !in 0 until maze.size || nc !in 0 until maze.size || ni in maze.walls) break
            r = nr; c = nc
            newly.add(ni)
        }
        ball = r * maze.size + c
        painted = newly
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF23262E)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Закрашено", painted.size * 100 / totalFloor, Color(0xFFE0526D), true)
        Spacer(Modifier.height(10.dp))
        Text("Свайпайте, чтобы катить шар до стенки и закрасить весь пол", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))

        Box(
            Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF171A21)).padding(6.dp)
                .pointerInput(ball, won) {
                    var ax = 0f; var ay = 0f; var done = false
                    detectDragGestures(
                        onDragStart = { ax = 0f; ay = 0f; done = false },
                        onDrag = { ch, d ->
                            ch.consume()
                            if (done) return@detectDragGestures
                            ax += d.x; ay += d.y
                            if (abs(ax) > 35f || abs(ay) > 35f) {
                                done = true
                                if (abs(ax) > abs(ay)) slide(0, if (ax > 0) 1 else -1)
                                else slide(if (ay > 0) 1 else -1, 0)
                            }
                        }
                    )
                }
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (r in 0 until maze.size) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (c in 0 until maze.size) {
                            val idx = r * maze.size + c
                            val isWall = idx in maze.walls
                            val isPainted = idx in painted
                            Box(
                                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp))
                                    .background(when { isWall -> Color(0xFF111827); isPainted -> Color(0xFFE0526D); else -> Color(0xFF3A414E) }),
                                contentAlignment = Alignment.Center
                            ) {
                                if (idx == ball) Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Лабиринт закрашен!", "Уровень ${lvl + 1} пройден", config.mode, roomState,
                onRematch = { lvl += 1 },
                onExit = onExit
            )
        }
    }
}

// 22. FLOW COLORS (Connect matching endpoints on 5x5 grid)
private data class FlowLevel(val size: Int, val pairs: List<Pair<Int, Int>>)

private val FLOW_LEVELS = listOf(
    FlowLevel(5, listOf(0 to 21, 2 to 18, 4 to 24, 6 to 17)),
    FlowLevel(5, listOf(0 to 20, 1 to 14, 3 to 23, 7 to 16)),
    FlowLevel(5, listOf(0 to 12, 4 to 22, 10 to 24, 15 to 21))
)

@Composable
fun FlowColorsScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var lvl by remember(resetSignal) { mutableIntStateOf(0) }
    val flow = FLOW_LEVELS[lvl % FLOW_LEVELS.size]
    var paths by remember(lvl, resetSignal) { mutableStateOf(List(flow.pairs.size) { emptyList<Int>() }) }
    var activeColor by remember(lvl, resetSignal) { mutableIntStateOf(-1) }
    val flowColors = listOf(Color(0xFF70E000), Color(0xFFFFBE0B), Color(0xFF38BDF8), Color(0xFFEF476F))

    val won = flow.pairs.indices.all { c ->
        val p = paths[c]
        val (a, b) = flow.pairs[c]
        p.size >= 2 && ((p.first() == a && p.last() == b) || (p.first() == b && p.last() == a))
    }

    LaunchedEffect(won) { if (won) prefs.recordWin(GameId.FLOW_COLORS) }

    fun endpointColor(idx: Int): Int = flow.pairs.indexOfFirst { it.first == idx || it.second == idx }

    Column(Modifier.fillMaxSize().background(Color(0xFF4D384F)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Уровень", lvl + 1, PlayBoxColors.BoltGold, true)
        Spacer(Modifier.height(10.dp))
        Text("Соедините пары цветных точек, не пересекая линии", color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        Box(
            Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2B202C)).padding(6.dp)
                .pointerInput(flow, paths) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val c = ((pos.x / size.width) * flow.size).toInt().coerceIn(0, flow.size - 1)
                            val r = ((pos.y / size.height) * flow.size).toInt().coerceIn(0, flow.size - 1)
                            val idx = r * flow.size + c
                            val ep = endpointColor(idx)
                            if (ep >= 0) {
                                activeColor = ep
                                paths = paths.mapIndexed { i, list -> if (i == ep) listOf(idx) else list }
                            } else {
                                activeColor = -1
                            }
                        },
                        onDrag = { ch, _ ->
                            ch.consume()
                            if (activeColor < 0) return@detectDragGestures
                            val c = ((ch.position.x / size.width) * flow.size).toInt().coerceIn(0, flow.size - 1)
                            val r = ((ch.position.y / size.height) * flow.size).toInt().coerceIn(0, flow.size - 1)
                            val idx = r * flow.size + c
                            val curPath = paths[activeColor]
                            if (curPath.isEmpty()) return@detectDragGestures
                            val last = curPath.last()
                            if (idx == last) return@detectDragGestures
                            val adj = abs(last / flow.size - r) + abs(last % flow.size - c) == 1
                            val ep = endpointColor(idx)
                            if (adj && (ep == -1 || ep == activeColor)) {
                                if (idx in curPath) {
                                    paths = paths.mapIndexed { i, l -> if (i == activeColor) l.take(l.indexOf(idx) + 1) else l }
                                } else {
                                    // Clear any other path overlapping idx
                                    paths = paths.mapIndexed { i, l ->
                                        if (i == activeColor) l + idx else if (idx in l) l.take(l.indexOf(idx)) else l
                                    }
                                }
                            }
                        },
                        onDragEnd = { activeColor = -1 }
                    )
                }
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (r in 0 until flow.size) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (c in 0 until flow.size) {
                            val idx = r * flow.size + c
                            val ep = endpointColor(idx)
                            val pathOwner = paths.indexOfFirst { idx in it }
                            val cellColor = when {
                                ep >= 0 -> flowColors[ep]
                                pathOwner >= 0 -> flowColors[pathOwner].copy(alpha = 0.65f)
                                else -> Color(0xFF3E2E40)
                            }
                            Box(
                                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp)).background(cellColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (ep >= 0) Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Все цвета соединены!", "Отличная логика", config.mode, roomState,
                onRematch = { lvl += 1 },
                onExit = onExit
            )
        }
    }
}

// 23. MULTI COLOR FILL (Territory / path fill)
@Composable
fun MultiColorFillScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val size = 5
    var level by remember(resetSignal) { mutableIntStateOf(1) }
    val starts = listOf(0, 4, 20, 24)
    val palette = listOf(Color(0xFFF6B756), Color(0xFF2EC4B6), Color(0xFFEF476F), Color(0xFF8338EC))
    var grid by remember(level, resetSignal) {
        mutableStateOf(IntArray(size * size) { i -> starts.indexOf(i) })
    }
    var selectedColor by remember(level, resetSignal) { mutableIntStateOf(0) }
    val won = grid.none { it < 0 }

    LaunchedEffect(won) { if (won) prefs.recordWin(GameId.MULTI_COLOR_FILL) }

    fun canFill(idx: Int, col: Int): Boolean {
        if (grid[idx] >= 0) return false
        val r = idx / size; val c = idx % size
        return listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1).any { (nr, nc) ->
            nr in 0 until size && nc in 0 until size && grid[nr * size + nc] == col
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF2A3236)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Выберите цвет и заполните всё поле из цветных истоков", color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            palette.forEachIndexed { i, col ->
                Box(Modifier.size(44.dp).clip(CircleShape).background(col).border(if (selectedColor == i) 3.dp else 0.dp, Color.White, CircleShape).clickable { selectedColor = i })
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(0.9f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B2023)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until size) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until size) {
                        val idx = r * size + c
                        val owner = grid[idx]
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp))
                                .background(if (owner >= 0) palette[owner] else Color(0xFF374151))
                                .clickable(enabled = !won && canFill(idx, selectedColor)) {
                                    grid = grid.copyOf().apply { this[idx] = selectedColor }
                                }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { grid = IntArray(size * size) { i -> starts.indexOf(i) } }) { Text("Сброс", color = Color.White) }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Поле заполнено!", "Уровень $level", config.mode, roomState, onRematch = { level += 1 }, onExit = onExit)
        }
    }
}

// 24. ESCAPE PARKING (Rush Hour 6x6 sliding cars)
private data class CarBlock(val id: Int, val r: Int, val c: Int, val len: Int, val horiz: Boolean, val isTarget: Boolean = false)

private val ESCAPE_PUZZLES = listOf(
    listOf(
        CarBlock(0, 2, 0, 2, true, true),
        CarBlock(1, 0, 2, 3, false),
        CarBlock(2, 1, 4, 3, false),
        CarBlock(3, 3, 2, 2, true),
        CarBlock(4, 4, 0, 3, true),
        CarBlock(5, 0, 0, 2, false)
    ),
    listOf(
        CarBlock(0, 2, 1, 2, true, true),
        CarBlock(1, 0, 0, 2, true),
        CarBlock(2, 0, 3, 3, false),
        CarBlock(3, 1, 0, 3, false),
        CarBlock(4, 3, 2, 2, false),
        CarBlock(5, 4, 3, 3, true)
    )
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

    fun moveCar(delta: Int) {
        val car = cars.firstOrNull { it.id == selectedCar } ?: return
        val occ = occupiedByOther(car.id)
        val nr = if (car.horiz) car.r else car.r + delta
        val nc = if (car.horiz) car.c + delta else car.c
        val ok = (0 until car.len).all { k ->
            val rr = if (car.horiz) nr else nr + k
            val cc = if (car.horiz) nc + k else nc
            rr in 0..5 && cc in 0..5 && (rr to cc) !in occ
        }
        if (ok) {
            cars = cars.map { if (it.id == car.id) it.copy(r = nr, c = nc) else it }
            moves += 1
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF6B4C3B)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Ходы", moves, PlayBoxColors.Player1Color, true)
        Spacer(Modifier.height(8.dp))
        Text("Выведите красную машину к правому краю (выберите авто и жмите ◀ / ▶ / ▲ / ▼)", color = Color.White, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        Column(Modifier.fillMaxWidth(0.92f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF4A3327)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (r in 0..5) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (c in 0..5) {
                        val car = cars.firstOrNull {
                            if (it.horiz) it.r == r && c in it.c until (it.c + it.len) else it.c == c && r in it.r until (it.r + it.len)
                        }
                        val bg = when {
                            car == null -> Color(0xFF5A4032)
                            car.isTarget -> Color(0xFFE63946)
                            else -> Color(0xFFEDE0C8)
                        }
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(bg)
                                .border(if (car != null && car.id == selectedCar) 3.dp else 0.dp, PlayBoxColors.BoltGold, RoundedCornerShape(6.dp))
                                .clickable(enabled = car != null && !won) { selectedCar = car!!.id }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val sel = cars.firstOrNull { it.id == selectedCar }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(enabled = sel != null && !won, onClick = { moveCar(-1) }) {
                Text(if (sel?.horiz == true) "◀ Назад" else "▲ Вверх", fontWeight = FontWeight.Bold)
            }
            Button(enabled = sel != null && !won, onClick = { moveCar(1) }) {
                Text(if (sel?.horiz == true) "Вперёд ▶" else "▼ Вниз", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Побег удался!", "Решено за $moves ходов", config.mode, roomState, onRematch = { lvl += 1 }, onExit = onExit)
        }
    }
}

// 25. WOOD BLOCKS (9x9 Blockudoku)
private val WOOD_SHAPES = listOf(
    listOf(0 to 0),
    listOf(0 to 0, 0 to 1),
    listOf(0 to 0, 1 to 0),
    listOf(0 to 0, 0 to 1, 0 to 2),
    listOf(0 to 0, 1 to 0, 2 to 0),
    listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
    listOf(0 to 0, 1 to 0, 1 to 1)
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

    val gameOver = tray.withIndex().none { (_, sh) ->
        sh.isNotEmpty() && (0..8).any { r -> (0..8).any { c -> canPlace(sh, r, c, board) } }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { if (it is OnlineEvent.OpponentState) oppScore = it.payload.optInt("score", oppScore) }
        }
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
                        val idx = r * 9 + c
                        val boxTint = ((r / 3) + (c / 3)) % 2 == 0
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp))
                                .background(when { board[idx] -> Color(0xFFF3C68F); boxTint -> Color(0xFF5D4048); else -> Color(0xFF52383F) })
                                .clickable(enabled = !gameOver && selectedPiece in tray.indices && tray[selectedPiece].isNotEmpty()) {
                                    val sh = tray[selectedPiece]
                                    if (canPlace(sh, r, c, board)) {
                                        val next = board.copyOf()
                                        sh.forEach { (dr, dc) -> next[(r + dr) * 9 + (c + dc)] = true }
                                        var cleared = 0
                                        val toClear = mutableSetOf<Int>()
                                        for (rr in 0..8) if ((0..8).all { next[rr * 9 + it] }) { cleared++; (0..8).forEach { toClear.add(rr * 9 + it) } }
                                        for (cc in 0..8) if ((0..8).all { next[it * 9 + cc] }) { cleared++; (0..8).forEach { toClear.add(it * 9 + cc) } }
                                        for (br in 0..2) for (bc in 0..2) {
                                            val boxCells = (0..2).flatMap { dr -> (0..2).map { dc -> (br * 3 + dr) * 9 + (bc * 3 + dc) } }
                                            if (boxCells.all { next[it] }) { cleared++; toClear.addAll(boxCells) }
                                        }
                                        toClear.forEach { next[it] = false }
                                        board = next
                                        score += sh.size * 2 + cleared * 20
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
                        .clickable(enabled = shape.isNotEmpty()) { selectedPiece = idx },
                    contentAlignment = Alignment.Center
                ) {
                    if (shape.isNotEmpty()) {
                        Canvas(Modifier.size(44.dp)) {
                            val cell = size.width / 3f
                            shape.forEach { (dr, dc) -> drawRect(Color(0xFFF3C68F), Offset(dc * cell, dr * cell), Size(cell - 2.dp.toPx(), cell - 2.dp.toPx())) }
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

// 26. COLOR BLOCKS (Tetris-style 10x16)
private val TETRIS_SHAPES = listOf(
    listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3), // I
    listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), // O
    listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2), // T
    listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2), // L
    listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1)  // S
)

@Composable
fun ColorBlocksScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val rows = 16; val cols = 10
    var board by remember(resetSignal) { mutableStateOf(IntArray(rows * cols)) }
    var piece by remember(resetSignal) { mutableStateOf(TETRIS_SHAPES.random()) }
    var pColor by remember(resetSignal) { mutableIntStateOf(1) }
    var pr by remember(resetSignal) { mutableIntStateOf(0) }
    var pc by remember(resetSignal) { mutableIntStateOf(3) }
    var score by remember(resetSignal) { mutableIntStateOf(0) }
    var gameOver by remember(resetSignal) { mutableStateOf(false) }

    fun fits(sh: List<Pair<Int, Int>>, r: Int, c: Int, b: IntArray): Boolean =
        sh.all { (dr, dc) -> val rr = r + dr; val cc = c + dc; rr in 0 until rows && cc in 0 until cols && b[rr * cols + cc] == 0 }

    fun lockAndSpawn() {
        val next = board.copyOf()
        piece.forEach { (dr, dc) -> val rr = pr + dr; val cc = pc + dc; if (rr in 0 until rows && cc in 0 until cols) next[rr * cols + cc] = pColor }
        // clear full lines
        val keptRows = (0 until rows).filter { r -> (0 until cols).any { c -> next[r * cols + c] == 0 } }
        val cleared = rows - keptRows.size
        val compacted = IntArray(rows * cols)
        val offset = rows - keptRows.size
        keptRows.forEachIndexed { idx, oldR ->
            for (c in 0 until cols) compacted[(offset + idx) * cols + c] = next[oldR * cols + c]
        }
        board = compacted
        if (cleared > 0) {
            score += cleared * 100
            prefs.updateBestScore(GameId.COLOR_BLOCKS, score)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
        }
        val np = TETRIS_SHAPES.random()
        piece = np; pColor = Random.nextInt(1, 6); pr = 0; pc = 3
        if (!fits(np, 0, 3, compacted)) gameOver = true
    }

    LaunchedEffect(resetSignal, gameOver) {
        while (!gameOver) {
            delay(460L)
            if (fits(piece, pr + 1, pc, board)) pr += 1 else lockAndSpawn()
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF2B2438)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Очки", score, Color(0xFFF6B756), true)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.weight(1f).aspectRatio(10f / 16f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1624)).border(3.dp, Color.White, RoundedCornerShape(14.dp))) {
            val cw = size.width / cols; val ch = size.height / rows
            for (r in 0 until rows) for (c in 0 until cols) {
                val v = board[r * cols + c]
                if (v > 0) drawRect(SORT_PALETTE[(v - 1) % SORT_PALETTE.size], Offset(c * cw + 1f, r * ch + 1f), Size(cw - 2f, ch - 2f))
            }
            if (!gameOver) {
                piece.forEach { (dr, dc) ->
                    drawRect(SORT_PALETTE[(pColor - 1) % SORT_PALETTE.size], Offset((pc + dc) * cw + 1f, (pr + dr) * ch + 1f), Size(cw - 2f, ch - 2f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !gameOver, onClick = { if (fits(piece, pr, pc - 1, board)) pc -= 1 }) { Text("◀") }
            Button(enabled = !gameOver, onClick = {
                val rot = piece.map { (r, c) -> c to -r }.let { list ->
                    val mr = list.minOf { it.first }; val mc = list.minOf { it.second }
                    list.map { (r, c) -> (r - mr) to (c - mc) }
                }
                if (fits(rot, pr, pc, board)) piece = rot
            }) { Text("↻") }
            Button(enabled = !gameOver, onClick = {
                while (fits(piece, pr + 1, pc, board)) pr += 1
                lockAndSpawn()
            }) { Text("▼ Сброс") }
            Button(enabled = !gameOver, onClick = { if (fits(piece, pr, pc + 1, board)) pc += 1 }) { Text("▶") }
        }
        if (gameOver) {
            GameResultBanner("Стакан заполнен!", "Очки: $score", config.mode, roomState,
                onRematch = { board = IntArray(rows * cols); score = 0; gameOver = false },
                onExit = onExit
            )
        }
    }
}

// 27. FALLING SAND (Sandtrix cellular automata)
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
            delay(65L)
            val next = sand.copyOf()
            // Spawn small sand stream from dropCol
            if (tick % 3 == 0 && next[dropCol] == 0) {
                for (dc in -1..1) {
                    val c = (dropCol + dc).coerceIn(0, cols - 1)
                    if (next[c] == 0) next[c] = activeColor
                }
            }
            // Fall simulation bottom-up
            for (r in rows - 2 downTo 0) {
                for (c in 0 until cols) {
                    val v = next[r * cols + c]
                    if (v == 0) continue
                    val below = (r + 1) * cols + c
                    if (next[below] == 0) {
                        next[below] = v; next[r * cols + c] = 0
                    } else {
                        val dl = if (c > 0 && next[(r + 1) * cols + (c - 1)] == 0) -1 else 0
                        val dr = if (c < cols - 1 && next[(r + 1) * cols + (c + 1)] == 0) 1 else 0
                        val slide = if (dl != 0 && dr != 0) (if (Random.nextBoolean()) dl else dr) else if (dl != 0) dl else dr
                        if (slide != 0) {
                            next[(r + 1) * cols + (c + slide)] = v; next[r * cols + c] = 0
                        }
                    }
                }
            }
            // Check connected horizontal band of same color spanning left (col 0) to right (col cols-1)
            for (color in 1..4) {
                val queue = ArrayDeque<Int>()
                val visited = BooleanArray(rows * cols)
                for (r in 0 until rows) if (next[r * cols] == color) { queue.add(r * cols); visited[r * cols] = true }
                var touchesRight = false
                val comp = mutableListOf<Int>()
                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst()
                    comp.add(cur)
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
            sand = next
            tick++
            if (tick % 45 == 0) activeColor = Random.nextInt(1, 5)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF141F38)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Очки", score, Color(0xFFF6B756), true)
            OutlinedButton(onClick = { sand = IntArray(rows * cols); score = 0 }) { Text("Очистить", color = Color.White) }
        }
        Spacer(Modifier.height(6.dp))
        Text("Двигайте поток песка! Мостик одного цвета от левого края до правого исчезает.", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))

        Canvas(
            Modifier.weight(1f).aspectRatio(cols.toFloat() / rows)
                .clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B1222)).border(3.dp, Color.White, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectDragGestures { ch, _ ->
                        ch.consume()
                        dropCol = ((ch.position.x / size.width) * cols).toInt().coerceIn(1, cols - 2)
                    }
                }
        ) {
            val cw = size.width / cols; val ch = size.height / rows
            drawCircle(SORT_PALETTE[activeColor - 1], cw * 0.7f, Offset((dropCol + 0.5f) * cw, ch * 0.5f))
            for (r in 0 until rows) for (c in 0 until cols) {
                val v = sand[r * cols + c]
                if (v > 0) drawRect(SORT_PALETTE[(v - 1) % SORT_PALETTE.size], Offset(c * cw, r * ch), Size(cw, ch))
            }
        }
    }
}
