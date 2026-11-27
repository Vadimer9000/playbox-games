package com.playbox.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.playbox.games.data.PlayerPrefs
import com.playbox.games.model.BotDifficulty
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

// 5. CHESS (Full 8x8 with legal moves, check, checkmate, promotion)
private fun initChessBoard(): IntArray = intArrayOf(
    -4, -2, -3, -5, -6, -3, -2, -4,
    -1, -1, -1, -1, -1, -1, -1, -1,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    1, 1, 1, 1, 1, 1, 1, 1,
    4, 2, 3, 5, 6, 3, 2, 4
) // 1=P,2=N,3=B,4=R,5=Q,6=K (+ white, - black)

private fun chessGlyph(p: Int): String = when (p) {
    1 -> "♙"; 2 -> "♘"; 3 -> "♗"; 4 -> "♖"; 5 -> "♕"; 6 -> "♔"
    -1 -> "♟"; -2 -> "♞"; -3 -> "♝"; -4 -> "♜"; -5 -> "♛"; -6 -> "♚"
    else -> ""
}

private fun isWhitePiece(p: Int) = p > 0
private fun pieceOwner(p: Int) = if (p > 0) 1 else if (p < 0) 2 else 0

private fun pseudoChessMoves(board: IntArray, idx: Int): List<Int> {
    val p = board[idx]
    if (p == 0) return emptyList()
    val white = p > 0
    val owner = if (white) 1 else 2
    val r = idx / 8; val c = idx % 8
    val res = mutableListOf<Int>()

    fun addSlide(dirs: List<Pair<Int, Int>>) {
        for ((dr, dc) in dirs) {
            var nr = r + dr; var nc = c + dc
            while (nr in 0..7 && nc in 0..7) {
                val t = board[nr * 8 + nc]
                if (t == 0) res.add(nr * 8 + nc)
                else {
                    if (pieceOwner(t) != owner) res.add(nr * 8 + nc)
                    break
                }
                nr += dr; nc += dc
            }
        }
    }

    when (abs(p)) {
        1 -> {
            val dir = if (white) -1 else 1
            val startRow = if (white) 6 else 1
            if (r + dir in 0..7 && board[(r + dir) * 8 + c] == 0) {
                res.add((r + dir) * 8 + c)
                if (r == startRow && board[(r + 2 * dir) * 8 + c] == 0) res.add((r + 2 * dir) * 8 + c)
            }
            for (dc in listOf(-1, 1)) {
                val nr = r + dir; val nc = c + dc
                if (nr in 0..7 && nc in 0..7 && pieceOwner(board[nr * 8 + nc]) == (if (owner == 1) 2 else 1)) {
                    res.add(nr * 8 + nc)
                }
            }
        }
        2 -> listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1).forEach { (dr, dc) ->
            val nr = r + dr; val nc = c + dc
            if (nr in 0..7 && nc in 0..7 && pieceOwner(board[nr * 8 + nc]) != owner) res.add(nr * 8 + nc)
        }
        3 -> addSlide(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
        4 -> addSlide(listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
        5 -> addSlide(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1))
        6 -> for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr; val nc = c + dc
            if (nr in 0..7 && nc in 0..7 && pieceOwner(board[nr * 8 + nc]) != owner) res.add(nr * 8 + nc)
        }
    }
    return res
}

private fun isKingInCheck(board: IntArray, player: Int): Boolean {
    val kingVal = if (player == 1) 6 else -6
    val kIdx = board.indexOfFirst { it == kingVal }
    if (kIdx < 0) return true
    val opp = if (player == 1) 2 else 1
    for (i in 0..63) {
        if (pieceOwner(board[i]) == opp && kIdx in pseudoChessMoves(board, i)) return true
    }
    return false
}

private fun applyChessRaw(board: IntArray, from: Int, to: Int): IntArray {
    val next = board.copyOf()
    var p = next[from]
    next[from] = 0
    if (p == 1 && to / 8 == 0) p = 5
    if (p == -1 && to / 8 == 7) p = -5
    next[to] = p
    return next
}

private fun legalChessMoves(board: IntArray, player: Int): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    for (i in 0..63) {
        if (pieceOwner(board[i]) != player) continue
        for (t in pseudoChessMoves(board, i)) {
            val next = applyChessRaw(board, i, t)
            if (!isKingInCheck(next, player)) out.add(i to t)
        }
    }
    return out
}

@Composable
fun ChessScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var board by remember(resetSignal) { mutableStateOf(initChessBoard()) }
    var turn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var selected by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    fun reset(starter: Int = 1) {
        board = initChessBoard()
        turn = starter
        selected = null
        winner = null
    }

    val legal = remember(board, turn, winner) { if (winner == null) legalChessMoves(board, turn) else emptyList() }

    fun makeMove(from: Int, to: Int, fromRemote: Boolean) {
        if (winner != null) return
        val next = applyChessRaw(board, from, to)
        board = next
        selected = null
        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("from", from).put("to", to))
        }
        val opp = if (turn == 1) 2 else 1
        val oppMoves = legalChessMoves(next, opp)
        if (oppMoves.isEmpty()) {
            val w = if (isKingInCheck(next, opp)) turn else 0
            winner = w
            if (config.mode != PlayMode.ONLINE && w == 1) prefs.recordWin(GameId.CHESS)
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendGameOver(w)
                if (w == config.playerIndex) prefs.recordWin(GameId.CHESS)
            }
        } else {
            turn = opp
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentMove -> makeMove(ev.payload.optInt("from"), ev.payload.optInt("to"), true)
                    is OnlineEvent.RematchStart -> reset(ev.startingPlayer)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(turn, winner, board) {
        if (config.mode == PlayMode.SOLO && turn == 2 && winner == null) {
            delay(360)
            val mv = chooseChessBotMove(board, config.difficulty)
            if (mv != null) makeMove(mv.first, mv.second, false)
        }
    }

    val canTap = when (config.mode) {
        PlayMode.SOLO -> turn == 1 && winner == null
        PlayMode.LOCAL_2P -> winner == null
        PlayMode.ONLINE -> turn == config.playerIndex && winner == null && (roomState?.bothConnected == true)
    }
    val flip = config.mode == PlayMode.ONLINE && config.playerIndex == 2

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val status = when {
            winner == 0 -> "Пат! Ничья"
            winner != null -> if (winner == 1) "Мат! Белые победили" else "Мат! Чёрные победили"
            isKingInCheck(board, turn) -> "Шах! Ход ${if (turn == 1) "белых" else "чёрных"}"
            else -> "Ход ${if (turn == 1) "белых" else "чёрных"}"
        }
        Text(status, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = PlayBoxColors.DarkInk)
        Spacer(Modifier.height(10.dp))

        val targets = selected?.let { sel -> legal.filter { it.first == sel }.map { it.second }.toSet() } ?: emptySet()
        Column(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).border(3.dp, Color(0xFF4A2511), RoundedCornerShape(14.dp))
        ) {
            for (rawR in 0..7) {
                Row(Modifier.weight(1f)) {
                    for (rawC in 0..7) {
                        val r = if (flip) 7 - rawR else rawR
                        val c = if (flip) 7 - rawC else rawC
                        val idx = r * 8 + c
                        val dark = (r + c) % 2 == 1
                        val bg = when {
                            idx == selected -> Color(0xFFFDE047)
                            idx in targets -> Color(0xFF86EFAC)
                            dark -> Color(0xFFB58863)
                            else -> Color(0xFFF0D9B5)
                        }
                        Box(
                            Modifier.weight(1f).fillMaxSize().background(bg).clickable(enabled = canTap) {
                                if (selected != null && idx in targets) {
                                    makeMove(selected!!, idx, false)
                                } else if (pieceOwner(board[idx]) == turn && legal.any { it.first == idx }) {
                                    selected = idx
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            val p = board[idx]
                            if (p != 0) {
                                Text(
                                    chessGlyph(p),
                                    fontSize = 26.sp,
                                    color = if (isWhitePiece(p)) Color.White else Color(0xFF1F242D)
                                )
                            } else if (idx in targets) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF16A34A)))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (winner != null) {
            GameResultBanner(status, "Партия завершена", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else reset(1) },
                onExit = onExit
            )
        }
    }
}

private fun pieceValue(p: Int): Int = when (abs(p)) {
    1 -> 100; 2 -> 320; 3 -> 330; 4 -> 500; 5 -> 900; 6 -> 10000; else -> 0
}

private fun chooseChessBotMove(board: IntArray, diff: BotDifficulty): Pair<Int, Int>? {
    val moves = legalChessMoves(board, 2)
    if (moves.isEmpty()) return null
    if (diff == BotDifficulty.EASY && Random.nextFloat() < 0.6f) return moves.random()
    return moves.maxByOrNull { (from, to) ->
        val after = applyChessRaw(board, from, to)
        var score = 0
        for (i in 0..63) {
            val p = after[i]
            if (p < 0) score += pieceValue(p) else if (p > 0) score -= pieceValue(p)
        }
        if (legalChessMoves(after, 1).isEmpty() && isKingInCheck(after, 1)) score += 50000
        score + Random.nextInt(15)
    }
}

// 6. CHESS PUZZLES
private data class ChessPuzzleDef(val title: String, val board: IntArray, val from: Int, val to: Int)

private fun buildChessPuzzles(): List<ChessPuzzleDef> = listOf(
    ChessPuzzleDef("Мат по последней горизонтали", IntArray(64).apply {
        this[6] = -6; this[13] = -1; this[14] = -1; this[15] = -1; this[56] = 4; this[62] = 6
    }, 56, 0),
    ChessPuzzleDef("Мат ферзём на h7", IntArray(64).apply {
        this[6] = -6; this[5] = -4; this[14] = -1; this[15] = -1; this[29] = 5; this[35] = 3; this[62] = 6
    }, 29, 15),
    ChessPuzzleDef("Спёртый мат конём", IntArray(64).apply {
        this[7] = -6; this[6] = -4; this[14] = -1; this[15] = -1; this[21] = 2; this[62] = 6
    }, 21, 13),
    ChessPuzzleDef("Батарея ферзь + слон", IntArray(64).apply {
        this[4] = -6; this[11] = -1; this[13] = -1; this[20] = 5; this[38] = 3; this[60] = 6
    }, 20, 12),
    ChessPuzzleDef("Двойная ладья", IntArray(64).apply {
        this[3] = -6; this[10] = -1; this[12] = -1; this[8] = 4; this[16] = 4; this[60] = 6
    }, 8, 0)
)

@Composable
fun ChessPuzzlesScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val puzzles = remember { buildChessPuzzles() }
    var pIdx by remember(resetSignal) { mutableIntStateOf(abs(config.seed) % puzzles.size) }
    var solvedCount by remember(resetSignal) { mutableIntStateOf(0) }
    var selected by remember(pIdx) { mutableStateOf<Int?>(null) }
    var feedback by remember(pIdx) { mutableStateOf("Найдите лучший ход белых (мат в 1)") }
    var oppSolved by remember(resetSignal) { mutableIntStateOf(0) }

    val cur = puzzles[pIdx]

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                if (ev is OnlineEvent.OpponentState) oppSolved = ev.payload.optInt("solved", oppSolved)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Решено", solvedCount, Color(0xFF70E000), true)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соперник", oppSolved, PlayBoxColors.OnlinePurple, false)
        }
        Spacer(Modifier.height(6.dp))
        Text(cur.title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(feedback, color = PlayBoxColors.MutedInk, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))

        Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(14.dp))) {
            for (r in 0..7) {
                Row(Modifier.weight(1f)) {
                    for (c in 0..7) {
                        val idx = r * 8 + c
                        val dark = (r + c) % 2 == 1
                        Box(
                            Modifier.weight(1f).fillMaxSize()
                                .background(if (idx == selected) Color(0xFFFDE047) else if (dark) Color(0xFF769656) else Color(0xFFEEEED2))
                                .clickable {
                                    if (selected == null) {
                                        if (cur.board[idx] > 0) selected = idx
                                    } else {
                                        if (selected == cur.from && idx == cur.to) {
                                            solvedCount += 1
                                            prefs.recordWin(GameId.CHESS_PUZZLES)
                                            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("solved", solvedCount))
                                            pIdx = (pIdx + 1) % puzzles.size
                                        } else {
                                            feedback = "Не совсем! Попробуйте другую фигуру"
                                            selected = null
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val p = cur.board[idx]
                            if (p != 0) Text(chessGlyph(p), fontSize = 26.sp, color = if (p > 0) Color.White else Color.Black)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { feedback = "Подсказка: ходите фигурой с ${'a' + (cur.from % 8)}${8 - cur.from / 8}" }) {
            Text("Подсказка")
        }
    }
}

// 7. BACKGAMMON (Simplified full 24-pt board, P1 moves 23->0, P2 moves 0->23)
@Composable
fun BackgammonScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    // positive = P1, negative = P2
    fun initPts(): IntArray = IntArray(24).apply {
        this[23] = 2; this[12] = 5; this[7] = 3; this[5] = 5
        this[0] = -2; this[11] = -5; this[16] = -3; this[18] = -5
    }

    var points by remember(resetSignal) { mutableStateOf(initPts()) }
    var off1 by remember(resetSignal) { mutableIntStateOf(0) }
    var off2 by remember(resetSignal) { mutableIntStateOf(0) }
    var turn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var dice by remember(resetSignal) { mutableStateOf<List<Int>>(emptyList()) }
    var selectedPt by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    fun reset(starter: Int = 1) {
        points = initPts(); off1 = 0; off2 = 0; turn = starter; dice = emptyList(); selectedPt = null; winner = null
    }

    fun validTargets(from: Int, player: Int): List<Pair<Int, Int>> { // targetPt (-1 = bear off), dieUsed
        val sign = if (player == 1) 1 else -1
        if (points[from] * sign <= 0) return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        for (d in dice.distinct()) {
            val to = if (player == 1) from - d else from + d
            if (to in 0..23) {
                if (points[to] * sign >= -1) out.add(to to d)
            } else {
                // Allow bear off if all checkers in home
                val allHome = if (player == 1) (6..23).all { points[it] <= 0 } else (0..17).all { points[it] >= 0 }
                if (allHome) out.add(-1 to d)
            }
        }
        return out
    }

    fun hasAnyMove(player: Int): Boolean = (0..23).any { validTargets(it, player).isNotEmpty() }

    fun rollDice(player: Int, d1: Int, d2: Int, fromRemote: Boolean) {
        dice = if (d1 == d2) listOf(d1, d1, d1, d1) else listOf(d1, d2)
        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("act", "roll").put("d1", d1).put("d2", d2))
        }
        if (!hasAnyMove(player)) {
            dice = emptyList()
            turn = if (player == 1) 2 else 1
        }
    }

    fun applyMove(from: Int, to: Int, die: Int, player: Int, fromRemote: Boolean) {
        val next = points.copyOf()
        val sign = if (player == 1) 1 else -1
        next[from] -= sign
        if (to < 0) {
            if (player == 1) off1++ else off2++
        } else {
            if (next[to] * sign == -1) {
                // hit opponent back to start
                next[to] = sign
                val home = if (player == 1) 0 else 23
                next[home] -= sign
            } else {
                next[to] += sign
            }
        }
        points = next
        selectedPt = null
        val newDice = dice.toMutableList().apply { remove(die) }
        dice = newDice
        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("act", "move").put("from", from).put("to", to).put("die", die))
        }
        if (off1 >= 15 || off2 >= 15) {
            val w = if (off1 >= 15) 1 else 2
            winner = w
            if (config.mode != PlayMode.ONLINE && w == 1) prefs.recordWin(GameId.BACKGAMMON)
            if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendGameOver(w)
            return
        }
        if (newDice.isEmpty() || !hasAnyMove(player)) {
            dice = emptyList()
            turn = if (player == 1) 2 else 1
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentMove -> {
                        if (ev.payload.optString("act") == "roll") {
                            rollDice(ev.playerIndex, ev.payload.optInt("d1"), ev.payload.optInt("d2"), true)
                        } else {
                            applyMove(ev.payload.optInt("from"), ev.payload.optInt("to"), ev.payload.optInt("die"), ev.playerIndex, true)
                        }
                    }
                    is OnlineEvent.RematchStart -> reset(ev.startingPlayer)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(turn, dice, winner) {
        if (config.mode == PlayMode.SOLO && turn == 2 && winner == null) {
            delay(450)
            if (dice.isEmpty()) {
                rollDice(2, Random.nextInt(1, 7), Random.nextInt(1, 7), false)
            } else {
                val allMoves = (0..23).flatMap { f -> validTargets(f, 2).map { (t, d) -> Triple(f, t, d) } }
                val mv = allMoves.maxByOrNull { (_, t, _) -> if (t < 0) 100 else t }
                if (mv != null) applyMove(mv.first, mv.second, mv.third, 2, false)
            }
        }
    }

    val canAct = when (config.mode) {
        PlayMode.SOLO -> turn == 1 && winner == null
        PlayMode.LOCAL_2P -> winner == null
        PlayMode.ONLINE -> turn == config.playerIndex && winner == null && (roomState?.bothConnected == true)
    }

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScorePill("Белые вывели", off1, Color(0xFF475569), turn == 1)
            ScorePill("Чёрные вывели", off2, Color(0xFF1E293B), turn == 2)
        }
        Spacer(Modifier.height(8.dp))

        // 24-pt Board (top 12..23, bottom 11 down to 0)
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFD4A373), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(16.dp)).padding(8.dp)) {
                val targets = selectedPt?.let { validTargets(it, turn).associate { (t, d) -> t to d } } ?: emptyMap()
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (pt in 12..23) {
                        BackgammonPoint(pt, points[pt], selectedPt == pt, pt in targets) {
                            if (!canAct || dice.isEmpty()) return@BackgammonPoint
                            if (pt in targets) applyMove(selectedPt!!, pt, targets[pt]!!, turn, false)
                            else if (validTargets(pt, turn).isNotEmpty()) selectedPt = pt
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (pt in 11 downTo 0) {
                        BackgammonPoint(pt, points[pt], selectedPt == pt, pt in targets) {
                            if (!canAct || dice.isEmpty()) return@BackgammonPoint
                            if (pt in targets) applyMove(selectedPt!!, pt, targets[pt]!!, turn, false)
                            else if (validTargets(pt, turn).isNotEmpty()) selectedPt = pt
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dice.isEmpty()) {
                Button(enabled = canAct, onClick = { rollDice(turn, Random.nextInt(1, 7), Random.nextInt(1, 7), false) }) {
                    Text("🎲 Бросить кубики", fontWeight = FontWeight.ExtraBold)
                }
            } else {
                Text("Кубики: ${dice.joinToString(" · ")}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                val bearOffDie = selectedPt?.let { validTargets(it, turn).firstOrNull { t -> t.first == -1 }?.second }
                if (bearOffDie != null && canAct) {
                    Button(onClick = { applyMove(selectedPt!!, -1, bearOffDie, turn, false) }) {
                        Text("Вывести фишку")
                    }
                }
            }
        }

        if (winner != null) {
            GameResultBanner(if (winner == 1) "Белые победили!" else "Чёрные победили!", "Все 15 фишек выведены", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else reset(1) },
                onExit = onExit
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BackgammonPoint(
    pt: Int,
    count: Int,
    selected: Boolean,
    isTarget: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        selected -> Color(0xFFFDE047)
        isTarget -> Color(0xFF86EFAC)
        pt % 2 == 0 -> Color(0xFFBC6C25)
        else -> Color(0xFFFEFAE0)
    }
    Column(
        modifier = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(bg).clickable(onClick = onClick).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (pt >= 12) Arrangement.Top else Arrangement.Bottom
    ) {
        val absC = abs(count)
        repeat(absC.coerceAtMost(5)) {
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(if (count > 0) Color.White else Color(0xFF1F242D)).border(1.dp, Color.Gray, CircleShape)
            )
        }
        if (absC > 5) Text("$absC", fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// 8. LUDO (2 players: P1 Red vs P2 Blue on 28-cell ring + 4 home slots)
@Composable
fun LudoScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    // progress: -1 = base, 0..27 = track steps from own start, 28..31 = goal (31 = home!)
    var p1Tokens by remember(resetSignal) { mutableStateOf(IntArray(4) { -1 }) }
    var p2Tokens by remember(resetSignal) { mutableStateOf(IntArray(4) { -1 }) }
    var turn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var diceVal by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    fun reset(starter: Int = 1) {
        p1Tokens = IntArray(4) { -1 }; p2Tokens = IntArray(4) { -1 }; turn = starter; diceVal = null; winner = null
    }

    fun trackPos(player: Int, step: Int): Int = if (player == 1) step % 28 else (step + 14) % 28

    fun validIndices(player: Int, d: Int): List<Int> {
        val toks = if (player == 1) p1Tokens else p2Tokens
        return toks.indices.filter { i ->
            val s = toks[i]
            (s == -1 && d == 6) || (s in 0..30 && s + d <= 31)
        }
    }

    fun roll(player: Int, d: Int, fromRemote: Boolean) {
        diceVal = d
        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("act", "roll").put("d", d))
        }
        if (validIndices(player, d).isEmpty()) {
            diceVal = null
            turn = if (player == 1) 2 else 1
        }
    }

    fun moveToken(player: Int, tokenIdx: Int, d: Int, fromRemote: Boolean) {
        val mine = (if (player == 1) p1Tokens else p2Tokens).copyOf()
        val opp = (if (player == 1) p2Tokens else p1Tokens).copyOf()
        val cur = mine[tokenIdx]
        val next = if (cur == -1 && d == 6) 0 else cur + d
        mine[tokenIdx] = next

        if (next in 0..27) {
            val myPos = trackPos(player, next)
            val safe = myPos == 0 || myPos == 14
            if (!safe) {
                for (j in opp.indices) {
                    if (opp[j] in 0..27 && trackPos(if (player == 1) 2 else 1, opp[j]) == myPos) {
                        opp[j] = -1
                    }
                }
            }
        }
        if (player == 1) { p1Tokens = mine; p2Tokens = opp } else { p2Tokens = mine; p1Tokens = opp }
        diceVal = null

        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("act", "move").put("idx", tokenIdx).put("d", d))
        }

        if (mine.all { it == 31 }) {
            winner = player
            if (config.mode != PlayMode.ONLINE && player == 1) prefs.recordWin(GameId.LUDO)
            if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendGameOver(player)
            return
        }
        if (d != 6) turn = if (player == 1) 2 else 1
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentMove -> {
                        if (ev.payload.optString("act") == "roll") roll(ev.playerIndex, ev.payload.optInt("d"), true)
                        else moveToken(ev.playerIndex, ev.payload.optInt("idx"), ev.payload.optInt("d"), true)
                    }
                    is OnlineEvent.RematchStart -> reset(ev.startingPlayer)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(turn, diceVal, winner) {
        if (config.mode == PlayMode.SOLO && turn == 2 && winner == null) {
            delay(500)
            val d = diceVal
            if (d == null) roll(2, Random.nextInt(1, 7), false)
            else {
                val opts = validIndices(2, d)
                if (opts.isNotEmpty()) moveToken(2, opts.maxBy { p2Tokens[it] }, d, false)
            }
        }
    }

    val canAct = when (config.mode) {
        PlayMode.SOLO -> turn == 1 && winner == null
        PlayMode.LOCAL_2P -> winner == null
        PlayMode.ONLINE -> turn == config.playerIndex && winner == null && (roomState?.bothConnected == true)
    }

    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScorePill("Красные (Дом)", p1Tokens.count { it == 31 }, PlayBoxColors.Player1Color, turn == 1)
            ScorePill("Синие (Дом)", p2Tokens.count { it == 31 }, PlayBoxColors.Player2Color, turn == 2)
        }
        Spacer(Modifier.height(14.dp))

        // Visual ring of 28 cells in 8x8 perimeter
        val coords = remember {
            buildList {
                for (c in 0..7) add(0 to c)
                for (r in 1..7) add(r to 7)
                for (c in 6 downTo 0) add(7 to c)
                for (r in 6 downTo 1) add(r to 0)
            }
        }
        Column(Modifier.fillMaxWidth(0.9f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A4759)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0..7) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0..7) {
                        val ringIdx = coords.indexOf(r to c)
                        val hasP1 = ringIdx >= 0 && p1Tokens.any { it in 0..27 && trackPos(1, it) == ringIdx }
                        val hasP2 = ringIdx >= 0 && p2Tokens.any { it in 0..27 && trackPos(2, it) == ringIdx }
                        val cellBg = when {
                            ringIdx == 0 -> PlayBoxColors.Player1Color.copy(alpha = 0.45f)
                            ringIdx == 14 -> PlayBoxColors.Player2Color.copy(alpha = 0.45f)
                            ringIdx >= 0 -> Color(0xFFFFF8EE)
                            else -> Color(0xFF1D3240)
                        }
                        Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(cellBg), Alignment.Center) {
                            if (hasP1) Box(Modifier.size(16.dp).clip(CircleShape).background(PlayBoxColors.Player1Color).border(2.dp, Color.White, CircleShape))
                            else if (hasP2) Box(Modifier.size(16.dp).clip(CircleShape).background(PlayBoxColors.Player2Color).border(2.dp, Color.White, CircleShape))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (diceVal == null) {
            Button(enabled = canAct, onClick = { roll(turn, Random.nextInt(1, 7), false) }) {
                Text("🎲 Бросить кубик (Игрок $turn)", fontWeight = FontWeight.ExtraBold)
            }
        } else {
            val d = diceVal!!
            val valid = validIndices(turn, d)
            Text("Выпало $d! Выберите фишку Игрока $turn:", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val toks = if (turn == 1) p1Tokens else p2Tokens
                for (i in 0..3) {
                    val label = when (toks[i]) {
                        -1 -> "База"; 31 -> "Дом"; else -> "${toks[i]}/31"
                    }
                    Button(
                        enabled = canAct && i in valid,
                        onClick = { moveToken(turn, i, d, false) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (turn == 1) PlayBoxColors.Player1Color else PlayBoxColors.Player2Color)
                    ) { Text("#${i + 1}\n$label", fontSize = 11.sp) }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (winner != null) {
            GameResultBanner("Победил Игрок $winner!", "Все 4 фишки в доме", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else reset(1) },
                onExit = onExit
            )
        }
    }
}

// 9. DOMINO
private data class DominoTile(val a: Int, val b: Int)

@Composable
fun DominoScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    val all = remember(seed) {
        (0..6).flatMap { a -> (a..6).map { b -> DominoTile(a, b) } }.shuffled(Random(seed))
    }
    var p1Hand by remember(seed) { mutableStateOf(all.take(7)) }
    var p2Hand by remember(seed) { mutableStateOf(all.subList(7, 14)) }
    var boneyard by remember(seed) { mutableStateOf(all.drop(14)) }
    var chain by remember(seed) { mutableStateOf<List<DominoTile>>(emptyList()) }
    var turn by remember(seed) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(seed) { mutableStateOf<Int?>(null) }

    fun canPlaceLeft(t: DominoTile) = chain.isEmpty() || t.a == chain.first().a || t.b == chain.first().a
    fun canPlaceRight(t: DominoTile) = chain.isEmpty() || t.a == chain.last().b || t.b == chain.last().b

    fun place(player: Int, idx: Int, onLeft: Boolean, fromRemote: Boolean) {
        if (winner != null) return
        val hand = if (player == 1) p1Hand else p2Hand
        if (idx !in hand.indices) return
        val tile = hand[idx]
        val nextChain = if (chain.isEmpty()) listOf(tile)
        else if (onLeft) {
            val need = chain.first().a
            val oriented = if (tile.b == need) tile else if (tile.a == need) DominoTile(tile.b, tile.a) else return
            listOf(oriented) + chain
        } else {
            val need = chain.last().b
            val oriented = if (tile.a == need) tile else if (tile.b == need) DominoTile(tile.b, tile.a) else return
            chain + oriented
        }

        val nextHand = hand.toMutableList().apply { removeAt(idx) }
        if (player == 1) p1Hand = nextHand else p2Hand = nextHand
        chain = nextChain

        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("act", "play").put("idx", idx).put("left", onLeft))
        }
        if (nextHand.isEmpty()) {
            winner = player
            if (config.mode != PlayMode.ONLINE && player == 1) prefs.recordWin(GameId.DOMINO)
            if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendGameOver(player)
        } else {
            turn = if (player == 1) 2 else 1
        }
    }

    fun drawOrPass(player: Int, fromRemote: Boolean) {
        if (boneyard.isNotEmpty()) {
            val drawn = boneyard.first()
            boneyard = boneyard.drop(1)
            if (player == 1) p1Hand = p1Hand + drawn else p2Hand = p2Hand + drawn
        } else {
            turn = if (player == 1) 2 else 1
        }
        if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendMove(JSONObject().put("act", "draw"))
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentMove -> {
                        if (ev.payload.optString("act") == "draw") drawOrPass(ev.playerIndex, true)
                        else place(ev.playerIndex, ev.payload.optInt("idx"), ev.payload.optBoolean("left"), true)
                    }
                    is OnlineEvent.RematchStart -> seed = ev.seed
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(turn, winner, p2Hand, boneyard) {
        if (config.mode == PlayMode.SOLO && turn == 2 && winner == null) {
            delay(480)
            val lIdx = p2Hand.indexOfFirst { canPlaceLeft(it) }
            val rIdx = p2Hand.indexOfFirst { canPlaceRight(it) }
            when {
                lIdx >= 0 -> place(2, lIdx, true, false)
                rIdx >= 0 -> place(2, rIdx, false, false)
                boneyard.isNotEmpty() -> drawOrPass(2, false)
                else -> {
                    // blocked check
                    val p1Can = p1Hand.any { canPlaceLeft(it) || canPlaceRight(it) }
                    if (!p1Can) {
                        val s1 = p1Hand.sumOf { it.a + it.b }
                        val s2 = p2Hand.sumOf { it.a + it.b }
                        winner = if (s1 <= s2) 1 else 2
                    } else turn = 1
                }
            }
        }
    }

    val viewer = if (config.mode == PlayMode.ONLINE) config.playerIndex else if (config.mode == PlayMode.LOCAL_2P) turn else 1
    val hand = if (viewer == 1) p1Hand else p2Hand
    val canAct = (config.mode == PlayMode.LOCAL_2P || turn == viewer) && winner == null

    Column(Modifier.fillMaxSize().background(Color(0xFF0A4F44)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Игрок 1: ${p1Hand.size}", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Базар: ${boneyard.size}", color = Color(0xFFFFCA3A), fontWeight = FontWeight.Bold)
            Text("Игрок 2: ${p2Hand.size}", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (chain.isEmpty()) Text("Выложите первую костяшку", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
            else chain.forEach { t ->
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFAF5E8)).border(2.dp, Color.Black, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("${t.a}|${t.b}", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B), fontSize = 16.sp)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        val hasPlayable = hand.any { canPlaceLeft(it) || canPlaceRight(it) }
        if (!hasPlayable && canAct) {
            Button(onClick = { drawOrPass(viewer, false) }) {
                Text(if (boneyard.isNotEmpty()) "Взять с базара" else "Пас")
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            hand.forEachIndexed { idx, tile ->
                val okL = canPlaceLeft(tile); val okR = canPlaceRight(tile)
                Box(
                    Modifier.size(width = 50.dp, height = 74.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFAF5E8))
                        .border(if (canAct && (okL || okR)) 3.dp else 1.5.dp, if (canAct && (okL || okR)) PlayBoxColors.BoltGold else Color.Gray, RoundedCornerShape(10.dp))
                        .clickable(enabled = canAct && (okL || okR)) {
                            place(viewer, idx, okL, false)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${tile.a}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Box(Modifier.width(30.dp).height(2.dp).background(Color.Black))
                        Text("${tile.b}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }
            }
        }

        if (winner != null) {
            GameResultBanner("Победил Игрок $winner!", "Костяшки разыграны", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}

// 10. PETDOKU (4x4 Latin Square duel with pets)
private val PET_ICONS = listOf("", "🐱", "🐶", "🐮", "✖️")

@Composable
fun PetdokuScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var board by remember(resetSignal) { mutableStateOf(IntArray(16)) }
    var selectedPet by remember(resetSignal) { mutableIntStateOf(1) }
    var turn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    fun isLegalPet(b: IntArray, idx: Int, pet: Int): Boolean {
        if (b[idx] != 0) return false
        val r = idx / 4; val c = idx % 4
        for (k in 0..3) {
            if (b[r * 4 + k] == pet || b[k * 4 + c] == pet) return false
        }
        val br = (r / 2) * 2; val bc = (c / 2) * 2
        for (dr in 0..1) for (dc in 0..1) if (b[(br + dr) * 4 + (bc + dc)] == pet) return false
        return true
    }

    fun anyLegal(b: IntArray): Boolean = (0..15).any { i -> (1..4).any { p -> isLegalPet(b, i, p) } }

    fun placePet(idx: Int, pet: Int, player: Int, fromRemote: Boolean) {
        if (winner != null || !isLegalPet(board, idx, pet)) return
        val next = board.copyOf().apply { this[idx] = pet }
        board = next
        if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendMove(JSONObject().put("idx", idx).put("pet", pet))
        if (!anyLegal(next)) {
            winner = player
            if (config.mode != PlayMode.ONLINE && player == 1) prefs.recordWin(GameId.PETDOKU)
            if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendGameOver(player)
        } else {
            turn = if (player == 1) 2 else 1
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentMove -> placePet(ev.payload.optInt("idx"), ev.payload.optInt("pet"), ev.playerIndex, true)
                    is OnlineEvent.RematchStart -> { board = IntArray(16); turn = ev.startingPlayer; winner = null }
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(turn, winner, board) {
        if (config.mode == PlayMode.SOLO && turn == 2 && winner == null) {
            delay(380)
            val moves = (0..15).flatMap { i -> (1..4).filter { p -> isLegalPet(board, i, p) }.map { p -> i to p } }
            moves.randomOrNull()?.let { placePet(it.first, it.second, 2, false) }
        }
    }

    val canTap = (config.mode == PlayMode.LOCAL_2P || (config.mode == PlayMode.SOLO && turn == 1) || (config.mode == PlayMode.ONLINE && turn == config.playerIndex)) && winner == null

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ставь питомцев без повторов в строке, столбце и блоке 2×2. Кто поставит последним — победил!", fontSize = 13.sp, color = PlayBoxColors.MutedInk)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (p in 1..4) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (selectedPet == p) PlayBoxColors.BoltGold else Color.White)
                        .border(2.dp, PlayBoxColors.DarkInk, RoundedCornerShape(14.dp))
                        .clickable { selectedPet = p },
                    contentAlignment = Alignment.Center
                ) { Text(PET_ICONS[p], fontSize = 26.sp) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(0.9f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color.White).border(4.dp, PlayBoxColors.DarkInk, RoundedCornerShape(18.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0..3) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0..3) {
                        val idx = r * 4 + c
                        val v = board[idx]
                        val ok = canTap && isLegalPet(board, idx, selectedPet)
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp))
                                .background(if (ok) Color(0xFFE0F7FA) else PlayBoxColors.CreamBg)
                                .clickable(enabled = ok) { placePet(idx, selectedPet, turn, false) },
                            contentAlignment = Alignment.Center
                        ) { if (v > 0) Text(PET_ICONS[v], fontSize = 28.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (winner != null) {
            GameResultBanner("Победил Игрок $winner!", "Больше нет свободных ходов", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else { board = IntArray(16); turn = 1; winner = null } },
                onExit = onExit
            )
        }
    }
}

// 11. GUESS WHO (16 illustrated characters + trait questioning)
private data class GwChar(val id: Int, val name: String, val emoji: String, val glasses: Boolean, val hat: Boolean, val beard: Boolean, val lightHair: Boolean)

private val GW_ROSTER = listOf(
    GwChar(0, "Анна", "👩", false, false, false, false),
    GwChar(1, "Борис", "🧔", false, false, true, false),
    GwChar(2, "Вера", "👵", true, false, false, true),
    GwChar(3, "Глеб", "🤠", false, true, true, false),
    GwChar(4, "Даша", "👱‍♀️", false, false, false, true),
    GwChar(5, "Егор", "🤓", true, false, false, false),
    GwChar(6, "Женя", "🧢", false, true, false, false),
    GwChar(7, "Зоя", "👸", false, true, false, true),
    GwChar(8, "Иван", "👴", false, false, true, true),
    GwChar(9, "Катя", "👩‍🎤", true, false, false, false),
    GwChar(10, "Лев", "👨‍🌾", false, true, true, true),
    GwChar(11, "Мила", "👧", false, false, false, false),
    GwChar(12, "Ник", "🥸", true, false, true, false),
    GwChar(13, "Оля", "👩‍🎓", true, true, false, false),
    GwChar(14, "Паша", "👱", false, false, false, true),
    GwChar(15, "Рита", "👩‍🔬", true, false, false, true)
)

@Composable
fun GuessWhoScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    var targetId by remember(seed) { mutableIntStateOf(abs(seed) % GW_ROSTER.size) }
    var oppTargetId by remember(seed) { mutableIntStateOf(abs(seed * 7 + 3) % GW_ROSTER.size) }
    var eliminated by remember(seed) { mutableStateOf(emptySet<Int>()) }
    var turn by remember(seed) { mutableIntStateOf(config.startingPlayer) }
    var answerMsg by remember(seed) { mutableStateOf("Задайте вопрос или нажмите на персонажа, чтобы угадать") }
    var guessMode by remember(seed) { mutableStateOf(false) }
    var winner by remember(seed) { mutableStateOf<Int?>(null) }

    val oppChar = GW_ROSTER[oppTargetId]

    fun askTrait(label: String, pred: (GwChar) -> Boolean) {
        val yes = pred(oppChar)
        val toElim = GW_ROSTER.filter { pred(it) != yes }.map { it.id }.toSet()
        eliminated = eliminated + toElim
        answerMsg = "$label → ${if (yes) "ДА!" else "НЕТ!"}"
        if (config.mode == PlayMode.ONLINE) onlineClient.sendMove(JSONObject().put("act", "pass"))
        turn = if (turn == 1) 2 else 1
    }

    fun tryGuess(id: Int) {
        if (id == oppTargetId) {
            winner = if (config.mode == PlayMode.ONLINE) config.playerIndex else 1
            prefs.recordWin(GameId.GUESS_WHO)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex)
        } else {
            eliminated = eliminated + id
            answerMsg = "Это не ${GW_ROSTER[id].name}!"
            guessMode = false
            if (config.mode == PlayMode.ONLINE) onlineClient.sendMove(JSONObject().put("act", "pass"))
            turn = if (turn == 1) 2 else 1
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentMove -> turn = config.playerIndex
                    is OnlineEvent.MatchFinished -> winner = ev.winner
                    is OnlineEvent.RematchStart -> seed = ev.seed
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(turn, winner) {
        if (config.mode == PlayMode.SOLO && turn == 2 && winner == null) {
            delay(650)
            if (Random.nextFloat() < 0.14f) {
                winner = 2
                answerMsg = "Бот угадал вашего персонажа!"
            } else {
                answerMsg = "Бот задал вопрос... Теперь ваш ход!"
                turn = 1
            }
        }
    }

    val canAct = (config.mode == PlayMode.LOCAL_2P || (config.mode == PlayMode.SOLO && turn == 1) || (config.mode == PlayMode.ONLINE && turn == config.playerIndex)) && winner == null

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(answerMsg, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PlayBoxColors.DarkInk)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0..3) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0..3) {
                        val ch = GW_ROSTER[r * 4 + c]
                        val out = ch.id in eliminated
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(12.dp))
                                .background(if (out) Color(0xFFE5E7EB) else Color.White)
                                .border(2.dp, if (guessMode && !out) PlayBoxColors.Player1Color else PlayBoxColors.DarkInk, RoundedCornerShape(12.dp))
                                .clickable(enabled = canAct && !out) {
                                    if (guessMode) tryGuess(ch.id) else eliminated = eliminated + ch.id
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (out) "❌" else ch.emoji, fontSize = 26.sp)
                                Text(ch.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (out) Color.Gray else PlayBoxColors.DarkInk)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled = canAct, onClick = { askTrait("Очки?") { it.glasses } }) { Text("👓 Очки") }
            OutlinedButton(enabled = canAct, onClick = { askTrait("Шляпа?") { it.hat } }) { Text("🎩 Шляпа") }
            OutlinedButton(enabled = canAct, onClick = { askTrait("Борода?") { it.beard } }) { Text("🧔 Борода") }
            OutlinedButton(enabled = canAct, onClick = { askTrait("Светлые?") { it.lightHair } }) { Text("👱 Светлые") }
            Button(enabled = canAct, onClick = { guessMode = !guessMode }, colors = ButtonDefaults.buttonColors(containerColor = PlayBoxColors.Player1Color)) {
                Text(if (guessMode) "Отмена" else "🎯 Угадать!")
            }
        }

        if (winner != null) {
            GameResultBanner(
                title = if (winner == 1 || winner == config.playerIndex) "Вы угадали: ${oppChar.name}!" else "Соперник угадал первым!",
                subtitle = "Отличная дедукция",
                mode = config.mode,
                roomState = roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}
