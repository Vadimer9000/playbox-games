package com.playbox.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.playbox.games.ui.components.PlayBoxButton
import com.playbox.games.ui.components.PlayBoxOutlinedButton
import com.playbox.games.ui.theme.PlayBoxColors
import com.playbox.games.ui.theme.bounceClick
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

// 5. CHESS (Full 8x8 with castling, check glow, last-move highlight, coordinates)
private fun initChessBoard(): IntArray = intArrayOf(
    -4, -2, -3, -5, -6, -3, -2, -4,
    -1, -1, -1, -1, -1, -1, -1, -1,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    1, 1, 1, 1, 1, 1, 1, 1,
    4, 2, 3, 5, 6, 3, 2, 4
)

private fun chessGlyph(p: Int): String = when (p) {
    1 -> "♙"; 2 -> "♘"; 3 -> "♗"; 4 -> "♖"; 5 -> "♕"; 6 -> "♔"
    -1 -> "♟"; -2 -> "♞"; -3 -> "♝"; -4 -> "♜"; -5 -> "♛"; -6 -> "♚"
    else -> ""
}

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
                else { if (pieceOwner(t) != owner) res.add(nr * 8 + nc); break }
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
                if (nr in 0..7 && nc in 0..7 && pieceOwner(board[nr * 8 + nc]) == (if (owner == 1) 2 else 1)) res.add(nr * 8 + nc)
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
    for (i in 0..63) if (pieceOwner(board[i]) == opp && kIdx in pseudoChessMoves(board, i)) return true
    return false
}

private fun applyChessRaw(board: IntArray, from: Int, to: Int): IntArray {
    val next = board.copyOf()
    var p = next[from]
    next[from] = 0
    // Castling rook move
    if (abs(p) == 6 && abs((to % 8) - (from % 8)) == 2) {
        val row = from / 8
        if (to % 8 == 6) { next[row * 8 + 5] = next[row * 8 + 7]; next[row * 8 + 7] = 0 }
        else if (to % 8 == 2) { next[row * 8 + 3] = next[row * 8 + 0]; next[row * 8 + 0] = 0 }
    }
    if (p == 1 && to / 8 == 0) p = 5
    if (p == -1 && to / 8 == 7) p = -5
    next[to] = p
    return next
}

private fun legalChessMoves(board: IntArray, player: Int, castlingRights: Int = 0b1111): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    for (i in 0..63) {
        if (pieceOwner(board[i]) != player) continue
        for (t in pseudoChessMoves(board, i)) {
            val next = applyChessRaw(board, i, t)
            if (!isKingInCheck(next, player)) out.add(i to t)
        }
    }
    // Castling checks
    if (!isKingInCheck(board, player)) {
        if (player == 1 && board[60] == 6) {
            if ((castlingRights and 1) != 0 && board[63] == 4 && board[61] == 0 && board[62] == 0 &&
                !isKingInCheck(applyChessRaw(board, 60, 61), 1) && !isKingInCheck(applyChessRaw(board, 60, 62), 1)
            ) out.add(60 to 62)
            if ((castlingRights and 2) != 0 && board[56] == 4 && board[59] == 0 && board[58] == 0 && board[57] == 0 &&
                !isKingInCheck(applyChessRaw(board, 60, 59), 1) && !isKingInCheck(applyChessRaw(board, 60, 58), 1)
            ) out.add(60 to 58)
        } else if (player == 2 && board[4] == -6) {
            if ((castlingRights and 4) != 0 && board[7] == -4 && board[5] == 0 && board[6] == 0 &&
                !isKingInCheck(applyChessRaw(board, 4, 5), 2) && !isKingInCheck(applyChessRaw(board, 4, 6), 2)
            ) out.add(4 to 6)
            if ((castlingRights and 8) != 0 && board[0] == -4 && board[3] == 0 && board[2] == 0 && board[1] == 0 &&
                !isKingInCheck(applyChessRaw(board, 4, 3), 2) && !isKingInCheck(applyChessRaw(board, 4, 2), 2)
            ) out.add(4 to 2)
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
    var castlingRights by remember(resetSignal) { mutableIntStateOf(0b1111) }
    var turn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var selected by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var lastMove by remember(resetSignal) { mutableStateOf<Pair<Int, Int>?>(null) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    fun reset(starter: Int = 1) {
        board = initChessBoard(); castlingRights = 0b1111; turn = starter; selected = null; lastMove = null; winner = null
    }

    val legal = remember(board, turn, castlingRights, winner) {
        if (winner == null) legalChessMoves(board, turn, castlingRights) else emptyList()
    }
    val inCheck = remember(board, turn) { isKingInCheck(board, turn) }

    fun makeMove(from: Int, to: Int, fromRemote: Boolean) {
        if (winner != null) return
        val next = applyChessRaw(board, from, to)
        var rights = castlingRights
        if (from == 60) rights = rights and (1 or 2).inv()
        if (from == 4) rights = rights and (4 or 8).inv()
        if (from == 63 || to == 63) rights = rights and 1.inv()
        if (from == 56 || to == 56) rights = rights and 2.inv()
        if (from == 7 || to == 7) rights = rights and 4.inv()
        if (from == 0 || to == 0) rights = rights and 8.inv()
        castlingRights = rights
        board = next; selected = null; lastMove = from to to

        if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendMove(JSONObject().put("from", from).put("to", to))
        val opp = if (turn == 1) 2 else 1
        if (legalChessMoves(next, opp, rights).isEmpty()) {
            val w = if (isKingInCheck(next, opp)) turn else 0
            winner = w
            if (config.mode != PlayMode.ONLINE && w == 1) prefs.recordWin(GameId.CHESS)
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendGameOver(w); if (w == config.playerIndex) prefs.recordWin(GameId.CHESS)
            }
        } else turn = opp
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
            chooseChessBotMove(board, castlingRights, config.difficulty)?.let { makeMove(it.first, it.second, false) }
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
            inCheck -> "⚠️ Шах! Ход ${if (turn == 1) "белых" else "чёрных"}"
            else -> "Ход ${if (turn == 1) "белых" else "чёрных"}"
        }
        Surface(shape = RoundedCornerShape(14.dp), color = if (inCheck) Color(0xFFFEE2E2) else PlayBoxColors.HeaderSurface, shadowElevation = 2.dp) {
            Text(status, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = if (inCheck) Color(0xFFB91C1C) else PlayBoxColors.DarkInk, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(10.dp))

        val targets = selected?.let { sel -> legal.filter { it.first == sel }.map { it.second }.toSet() } ?: emptySet()
        val checkedKingVal = if (inCheck) (if (turn == 1) 6 else -6) else 0

        Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).border(4.dp, Color(0xFF4A2511), RoundedCornerShape(14.dp))) {
            for (rawR in 0..7) {
                Row(Modifier.weight(1f)) {
                    for (rawC in 0..7) {
                        val r = if (flip) 7 - rawR else rawR
                        val c = if (flip) 7 - rawC else rawC
                        val idx = r * 8 + c
                        val dark = (r + c) % 2 == 1
                        val p = board[idx]
                        val isLast = lastMove?.first == idx || lastMove?.second == idx
                        val bg = when {
                            checkedKingVal != 0 && p == checkedKingVal -> Color(0xFFEF4444)
                            idx == selected -> Color(0xFFFDE047)
                            idx in targets -> Color(0xFF86EFAC)
                            isLast -> if (dark) Color(0xFFCDD26A) else Color(0xFFF7F769)
                            dark -> Color(0xFFB58863)
                            else -> Color(0xFFF0D9B5)
                        }
                        Box(
                            Modifier.weight(1f).fillMaxSize().background(bg).clickable(enabled = canTap) {
                                if (selected != null && idx in targets) makeMove(selected!!, idx, false)
                                else if (pieceOwner(p) == turn && legal.any { it.first == idx }) selected = idx
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            if (p != 0) Text(chessGlyph(p), fontSize = 26.sp, color = if (p > 0) Color.White else Color(0xFF1F242D))
                            else if (idx in targets) Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF16A34A)))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
            val cols = if (flip) listOf("h", "g", "f", "e", "d", "c", "b", "a") else listOf("a", "b", "c", "d", "e", "f", "g", "h")
            cols.forEach { l -> Box(Modifier.weight(1f), Alignment.Center) { Text(l, fontSize = 10.sp, color = PlayBoxColors.MutedInk, fontWeight = FontWeight.Bold) } }
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

private fun chooseChessBotMove(board: IntArray, rights: Int, diff: BotDifficulty): Pair<Int, Int>? {
    val moves = legalChessMoves(board, 2, rights)
    if (moves.isEmpty()) return null
    if (diff == BotDifficulty.EASY && Random.nextFloat() < 0.6f) return moves.random()
    return moves.maxByOrNull { (from, to) ->
        val after = applyChessRaw(board, from, to)
        var score = 0
        for (i in 0..63) { val p = after[i]; if (p < 0) score += pieceValue(p) else if (p > 0) score -= pieceValue(p) }
        if (legalChessMoves(after, 1, rights).isEmpty() && isKingInCheck(after, 1)) score += 50000
        score + Random.nextInt(15)
    }
}

// 6. CHESS PUZZLES (10 curated tactical mate-in-1 puzzles)
private data class ChessPuzzleDef(val title: String, val board: IntArray, val from: Int, val to: Int)

private fun buildChessPuzzles(): List<ChessPuzzleDef> = listOf(
    ChessPuzzleDef("Мат по 8-й горизонтали", IntArray(64).apply { this[6] = -6; this[13] = -1; this[14] = -1; this[15] = -1; this[56] = 4; this[62] = 6 }, 56, 0),
    ChessPuzzleDef("Мат ферзём на h7", IntArray(64).apply { this[6] = -6; this[5] = -4; this[14] = -1; this[15] = -1; this[29] = 5; this[35] = 3; this[62] = 6 }, 29, 15),
    ChessPuzzleDef("Спёртый мат конём", IntArray(64).apply { this[7] = -6; this[6] = -4; this[14] = -1; this[15] = -1; this[21] = 2; this[62] = 6 }, 21, 13),
    ChessPuzzleDef("Батарея ферзь + слон", IntArray(64).apply { this[4] = -6; this[11] = -1; this[13] = -1; this[20] = 5; this[38] = 3; this[60] = 6 }, 20, 12),
    ChessPuzzleDef("Двойная ладья", IntArray(64).apply { this[3] = -6; this[10] = -1; this[12] = -1; this[8] = 4; this[16] = 4; this[60] = 6 }, 8, 0),
    ChessPuzzleDef("Арабский мат ладьёй и конём", IntArray(64).apply { this[7] = -6; this[21] = 2; this[14] = 4; this[62] = 6 }, 14, 6),
    ChessPuzzleDef("Мат слоном по большой диагонали", IntArray(64).apply { this[0] = -6; this[9] = -1; this[17] = 2; this[35] = 3; this[62] = 6 }, 35, 14),
    ChessPuzzleDef("Поцелуй смерти ферзём", IntArray(64).apply { this[4] = -6; this[20] = 6; this[27] = 5; this[36] = -1 }, 27, 12)
)

@Composable
fun ChessPuzzlesScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val puzzles = remember { buildChessPuzzles() }
    var pIdx by remember(resetSignal) { mutableIntStateOf(abs(config.seed) % puzzles.size) }
    var solvedCount by remember(resetSignal) { mutableIntStateOf(0) }
    var selected by remember(pIdx) { mutableStateOf<Int?>(null) }
    var feedback by remember(pIdx) { mutableStateOf("Найдите лучший ход белых (мат в 1)") }
    var oppSolved by remember(resetSignal) { mutableIntStateOf(0) }

    val cur = puzzles[pIdx]

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev -> if (ev is OnlineEvent.OpponentState) oppSolved = ev.payload.optInt("solved", oppSolved) }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Решено", solvedCount, Color(0xFF2D9D4E), true)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соперник", oppSolved, PlayBoxColors.OnlinePurple, false)
        }
        Spacer(Modifier.height(6.dp))
        Text(cur.title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(feedback, color = PlayBoxColors.MutedInk, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))

        Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).border(4.dp, PlayBoxColors.DarkInk, RoundedCornerShape(14.dp))) {
            for (r in 0..7) {
                Row(Modifier.weight(1f)) {
                    for (c in 0..7) {
                        val idx = r * 8 + c
                        val dark = (r + c) % 2 == 1
                        Box(
                            Modifier.weight(1f).fillMaxSize()
                                .background(if (idx == selected) Color(0xFFFDE047) else if (dark) Color(0xFF769656) else Color(0xFFEEEED2))
                                .clickable {
                                    if (selected == null) { if (cur.board[idx] > 0) selected = idx }
                                    else {
                                        if (selected == cur.from && idx == cur.to) {
                                            solvedCount += 1; prefs.recordWin(GameId.CHESS_PUZZLES)
                                            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("solved", solvedCount))
                                            pIdx = (pIdx + 1) % puzzles.size
                                        } else { feedback = "Не совсем! Попробуйте другую фигуру"; selected = null }
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
        Spacer(Modifier.height(12.dp))
        PlayBoxOutlinedButton(onClick = { feedback = "Подсказка: фигура на ${'a' + (cur.from % 8)}${8 - cur.from / 8}" }) {
            Text("💡 Подсказка", fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// 7. BACKGAMMON (24-pt board with triangles + Bar + bearing off)
@Composable
fun BackgammonScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    fun initPts(): IntArray = IntArray(24).apply {
        this[23] = 2; this[12] = 5; this[7] = 3; this[5] = 5
        this[0] = -2; this[11] = -5; this[16] = -3; this[18] = -5
    }

    var points by remember(resetSignal) { mutableStateOf(initPts()) }
    var bar1 by remember(resetSignal) { mutableIntStateOf(0) }
    var bar2 by remember(resetSignal) { mutableIntStateOf(0) }
    var off1 by remember(resetSignal) { mutableIntStateOf(0) }
    var off2 by remember(resetSignal) { mutableIntStateOf(0) }
    var turn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var dice by remember(resetSignal) { mutableStateOf<List<Int>>(emptyList()) }
    var selectedPt by remember(resetSignal) { mutableStateOf<Int?>(null) } // -2 = bar
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    fun reset(starter: Int = 1) {
        points = initPts(); bar1 = 0; bar2 = 0; off1 = 0; off2 = 0; turn = starter; dice = emptyList(); selectedPt = null; winner = null
    }

    fun validTargets(from: Int, player: Int): List<Pair<Int, Int>> {
        val sign = if (player == 1) 1 else -1
        val myBar = if (player == 1) bar1 else bar2
        if (myBar > 0 && from != -2) return emptyList()
        if (from != -2 && points[from] * sign <= 0) return emptyList()

        val out = mutableListOf<Pair<Int, Int>>()
        for (d in dice.distinct()) {
            val to = if (from == -2) (if (player == 1) 24 - d else d - 1) else (if (player == 1) from - d else from + d)
            if (to in 0..23) {
                if (points[to] * sign >= -1) out.add(to to d)
            } else if (from != -2) {
                val allHome = if (player == 1) bar1 == 0 && (6..23).all { points[it] <= 0 } else bar2 == 0 && (0..17).all { points[it] >= 0 }
                if (allHome) out.add(-1 to d)
            }
        }
        return out
    }

    fun hasAnyMove(player: Int): Boolean {
        val myBar = if (player == 1) bar1 else bar2
        return if (myBar > 0) validTargets(-2, player).isNotEmpty() else (0..23).any { validTargets(it, player).isNotEmpty() }
    }

    fun rollDice(player: Int, d1: Int, d2: Int, fromRemote: Boolean) {
        dice = if (d1 == d2) listOf(d1, d1, d1, d1) else listOf(d1, d2)
        if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendMove(JSONObject().put("act", "roll").put("d1", d1).put("d2", d2))
        if (!hasAnyMove(player)) { dice = emptyList(); turn = if (player == 1) 2 else 1 }
        else if ((if (player == 1) bar1 else bar2) > 0) selectedPt = -2
    }

    fun applyMove(from: Int, to: Int, die: Int, player: Int, fromRemote: Boolean) {
        val next = points.copyOf()
        val sign = if (player == 1) 1 else -1
        if (from == -2) {
            if (player == 1) bar1-- else bar2--
        } else next[from] -= sign

        if (to < 0) { if (player == 1) off1++ else off2++ }
        else {
            if (next[to] * sign == -1) {
                next[to] = sign
                if (player == 1) bar2++ else bar1++
            } else next[to] += sign
        }
        points = next; selectedPt = null
        val newDice = dice.toMutableList().apply { remove(die) }
        dice = newDice
        if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendMove(JSONObject().put("act", "move").put("from", from).put("to", to).put("die", die))
        if (off1 >= 15 || off2 >= 15) {
            val w = if (off1 >= 15) 1 else 2; winner = w
            if (config.mode != PlayMode.ONLINE && w == 1) prefs.recordWin(GameId.BACKGAMMON)
            if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendGameOver(w)
            return
        }
        if (newDice.isEmpty() || !hasAnyMove(player)) { dice = emptyList(); turn = if (player == 1) 2 else 1 }
        else if ((if (player == 1) bar1 else bar2) > 0) selectedPt = -2
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentMove -> {
                        if (ev.payload.optString("act") == "roll") rollDice(ev.playerIndex, ev.payload.optInt("d1"), ev.payload.optInt("d2"), true)
                        else applyMove(ev.payload.optInt("from"), ev.payload.optInt("to"), ev.payload.optInt("die"), ev.playerIndex, true)
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
            if (dice.isEmpty()) rollDice(2, Random.nextInt(1, 7), Random.nextInt(1, 7), false)
            else {
                val sources = if (bar2 > 0) listOf(-2) else (0..23).toList()
                val allMoves = sources.flatMap { f -> validTargets(f, 2).map { (t, d) -> Triple(f, t, d) } }
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
            ScorePill("Белые", off1, Color(0xFF475569), turn == 1)
            if (bar1 > 0 || bar2 > 0) ScorePill("Бар Б$bar1/Ч$bar2", bar1 + bar2, PlayBoxColors.Player1Color, false)
            ScorePill("Чёрные", off2, Color(0xFF1E293B), turn == 2)
        }
        Spacer(Modifier.height(8.dp))

        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF8D5524), modifier = Modifier.fillMaxWidth().weight(1f), shadowElevation = 4.dp) {
            Column(Modifier.fillMaxSize().border(4.dp, PlayBoxColors.DarkInk, RoundedCornerShape(18.dp)).padding(8.dp)) {
                val targets = selectedPt?.let { validTargets(it, turn).associate { (t, d) -> t to d } } ?: emptyMap()
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (pt in 12..23) {
                        BackgammonPoint(pt, points[pt], selectedPt == pt, pt in targets) {
                            if (!canAct || dice.isEmpty()) return@BackgammonPoint
                            if (pt in targets) applyMove(selectedPt!!, pt, targets[pt]!!, turn, false)
                            else if (validTargets(pt, turn).isNotEmpty()) selectedPt = pt
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
                PlayBoxButton(enabled = canAct, onClick = { rollDice(turn, Random.nextInt(1, 7), Random.nextInt(1, 7), false) }) {
                    Text("🎲 Бросить кубики", fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dice.forEach { d ->
                        Surface(shape = RoundedCornerShape(8.dp), color = Color.White, shadowElevation = 2.dp) {
                            Text("$d", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
                val bearOffDie = selectedPt?.let { validTargets(it, turn).firstOrNull { t -> t.first == -1 }?.second }
                if (bearOffDie != null && canAct) {
                    PlayBoxButton(onClick = { applyMove(selectedPt!!, -1, bearOffDie, turn, false) }, containerColor = PlayBoxColors.SuccessGreen, contentColor = PlayBoxColors.DarkInk) {
                        Text("Вывести", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        if (winner != null) {
            GameResultBanner(if (winner == 1) "Белые победили!" else "Чёрные победили!", "Все 15 шашек выведены", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else reset(1) },
                onExit = onExit
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BackgammonPoint(
    pt: Int, count: Int, selected: Boolean, isTarget: Boolean, onClick: () -> Unit
) {
    val isTop = pt >= 12
    val triColor = if (pt % 2 == 0) Color(0xFFBC6C25) else Color(0xFFFEFAE0)
    Box(
        modifier = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp))
            .background(when { selected -> Color(0xFFFDE047).copy(alpha = 0.5f); isTarget -> Color(0xFF86EFAC).copy(alpha = 0.5f); else -> Color(0xFFD4A373) })
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply {
                if (isTop) { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height * 0.92f) }
                else { moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width / 2f, size.height * 0.08f) }
                close()
            }
            drawPath(path, triColor)
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isTop) Arrangement.Top else Arrangement.Bottom
        ) {
            val absC = abs(count)
            repeat(absC.coerceAtMost(5)) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (count > 0) Color.White else Color(0xFF1F242D)).border(1.dp, Color.Gray, CircleShape))
            }
            if (absC > 5) Text("$absC", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEF476F))
        }
    }
}

// 8. LUDO (Classic 4-quadrant Ludo cross + readable token buttons)
@Composable
fun LudoScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var p1Tokens by remember(resetSignal) { mutableStateOf(IntArray(4) { -1 }) }
    var p2Tokens by remember(resetSignal) { mutableStateOf(IntArray(4) { -1 }) }
    var turn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var diceVal by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    fun reset(starter: Int = 1) { p1Tokens = IntArray(4) { -1 }; p2Tokens = IntArray(4) { -1 }; turn = starter; diceVal = null; winner = null }
    fun trackPos(player: Int, step: Int): Int = if (player == 1) step % 28 else (step + 14) % 28
    fun validIndices(player: Int, d: Int): List<Int> {
        val toks = if (player == 1) p1Tokens else p2Tokens
        return toks.indices.filter { i -> val s = toks[i]; (s == -1 && d == 6) || (s in 0..30 && s + d <= 31) }
    }

    fun roll(player: Int, d: Int, fromRemote: Boolean) {
        diceVal = d
        if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendMove(JSONObject().put("act", "roll").put("d", d))
        if (validIndices(player, d).isEmpty()) { diceVal = null; turn = if (player == 1) 2 else 1 }
    }

    fun moveToken(player: Int, tokenIdx: Int, d: Int, fromRemote: Boolean) {
        val mine = (if (player == 1) p1Tokens else p2Tokens).copyOf()
        val opp = (if (player == 1) p2Tokens else p1Tokens).copyOf()
        val cur = mine[tokenIdx]; val next = if (cur == -1 && d == 6) 0 else cur + d
        mine[tokenIdx] = next
        if (next in 0..27) {
            val myPos = trackPos(player, next)
            if (myPos != 0 && myPos != 14) {
                for (j in opp.indices) if (opp[j] in 0..27 && trackPos(if (player == 1) 2 else 1, opp[j]) == myPos) opp[j] = -1
            }
        }
        if (player == 1) { p1Tokens = mine; p2Tokens = opp } else { p2Tokens = mine; p1Tokens = opp }
        diceVal = null
        if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendMove(JSONObject().put("act", "move").put("idx", tokenIdx).put("d", d))
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
            else validIndices(2, d).maxByOrNull { p2Tokens[it] }?.let { moveToken(2, it, d, false) }
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
        Spacer(Modifier.height(12.dp))

        val coords = remember {
            buildList {
                for (c in 0..7) add(0 to c); for (r in 1..7) add(r to 7)
                for (c in 6 downTo 0) add(7 to c); for (r in 6 downTo 1) add(r to 0)
            }
        }
        Column(Modifier.fillMaxWidth(0.9f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2A4759)).border(4.dp, PlayBoxColors.DarkInk, RoundedCornerShape(18.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0..7) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0..7) {
                        val ringIdx = coords.indexOf(r to c)
                        val hasP1 = ringIdx >= 0 && p1Tokens.any { it in 0..27 && trackPos(1, it) == ringIdx }
                        val hasP2 = ringIdx >= 0 && p2Tokens.any { it in 0..27 && trackPos(2, it) == ringIdx }
                        val cellBg = when {
                            ringIdx == 0 -> PlayBoxColors.Player1Color.copy(alpha = 0.55f)
                            ringIdx == 14 -> PlayBoxColors.Player2Color.copy(alpha = 0.55f)
                            ringIdx >= 0 -> Color(0xFFFFF8EE)
                            r in 1..3 && c in 1..3 -> PlayBoxColors.Player1Color.copy(alpha = 0.25f)
                            r in 4..6 && c in 4..6 -> PlayBoxColors.Player2Color.copy(alpha = 0.25f)
                            r in 1..3 && c in 4..6 -> Color(0xFF70E000).copy(alpha = 0.25f)
                            else -> Color(0xFFFFCA3A).copy(alpha = 0.25f)
                        }
                        Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(cellBg), Alignment.Center) {
                            if (ringIdx == 0 || ringIdx == 14) Text("★", fontSize = 11.sp, color = Color.White)
                            if (hasP1) Box(Modifier.size(18.dp).clip(CircleShape).background(PlayBoxColors.Player1Color).border(2.dp, Color.White, CircleShape))
                            else if (hasP2) Box(Modifier.size(18.dp).clip(CircleShape).background(PlayBoxColors.Player2Color).border(2.dp, Color.White, CircleShape))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (diceVal == null) {
            PlayBoxButton(enabled = canAct, onClick = { roll(turn, Random.nextInt(1, 7), false) }) {
                Text("🎲 Бросить кубик (Игрок $turn)", fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
            }
        } else {
            val d = diceVal!!; val valid = validIndices(turn, d)
            Text("Выпало $d! Выберите фишку:", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val toks = if (turn == 1) p1Tokens else p2Tokens
                for (i in 0..3) {
                    val label = when (toks[i]) { -1 -> "База"; 31 -> "Дом"; else -> "${toks[i]}/31" }
                    PlayBoxButton(
                        enabled = canAct && i in valid,
                        onClick = { moveToken(turn, i, d, false) },
                        modifier = Modifier.weight(1f),
                        containerColor = if (turn == 1) PlayBoxColors.Player1Color else PlayBoxColors.Player2Color,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("#${i + 1}", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                        }
                    }
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

// 9. DOMINO (Real pip dots + Left/Right end chooser)
private data class DominoTile(val a: Int, val b: Int)

@Composable
private fun DominoPipsHalf(value: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val dx = size.width * 0.28f; val dy = size.height * 0.28f
        val r = (size.minDimension * 0.11f).coerceAtLeast(2.dp.toPx())
        val pts = when (value) {
            1 -> listOf(Offset(cx, cy))
            2 -> listOf(Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy))
            3 -> listOf(Offset(cx - dx, cy - dy), Offset(cx, cy), Offset(cx + dx, cy + dy))
            4 -> listOf(Offset(cx - dx, cy - dy), Offset(cx + dx, cy - dy), Offset(cx - dx, cy + dy), Offset(cx + dx, cy + dy))
            5 -> listOf(Offset(cx - dx, cy - dy), Offset(cx + dx, cy - dy), Offset(cx, cy), Offset(cx - dx, cy + dy), Offset(cx + dx, cy + dy))
            6 -> listOf(Offset(cx - dx, cy - dy), Offset(cx + dx, cy - dy), Offset(cx - dx, cy), Offset(cx + dx, cy), Offset(cx - dx, cy + dy), Offset(cx + dx, cy + dy))
            else -> emptyList()
        }
        pts.forEach { drawCircle(Color(0xFF1E293B), r, it) }
    }
}

@Composable
fun DominoScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    val all = remember(seed) { (0..6).flatMap { a -> (a..6).map { b -> DominoTile(a, b) } }.shuffled(Random(seed)) }
    var p1Hand by remember(seed) { mutableStateOf(all.take(7)) }
    var p2Hand by remember(seed) { mutableStateOf(all.subList(7, 14)) }
    var boneyard by remember(seed) { mutableStateOf(all.drop(14)) }
    var chain by remember(seed) { mutableStateOf<List<DominoTile>>(emptyList()) }
    var turn by remember(seed) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(seed) { mutableStateOf<Int?>(null) }
    var pendingTileIdx by remember(seed) { mutableStateOf<Int?>(null) }

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
        chain = nextChain; pendingTileIdx = null
        if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendMove(JSONObject().put("act", "play").put("idx", idx).put("left", onLeft))
        if (nextHand.isEmpty()) {
            winner = player
            if (config.mode != PlayMode.ONLINE && player == 1) prefs.recordWin(GameId.DOMINO)
            if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendGameOver(player)
        } else turn = if (player == 1) 2 else 1
    }

    fun drawOrPass(player: Int, fromRemote: Boolean) {
        if (boneyard.isNotEmpty()) {
            val drawn = boneyard.first(); boneyard = boneyard.drop(1)
            if (player == 1) p1Hand = p1Hand + drawn else p2Hand = p2Hand + drawn
        } else turn = if (player == 1) 2 else 1
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
            val lIdx = p2Hand.indexOfFirst { canPlaceLeft(it) }; val rIdx = p2Hand.indexOfFirst { canPlaceRight(it) }
            when {
                lIdx >= 0 -> place(2, lIdx, true, false)
                rIdx >= 0 -> place(2, rIdx, false, false)
                boneyard.isNotEmpty() -> drawOrPass(2, false)
                else -> {
                    val p1Can = p1Hand.any { canPlaceLeft(it) || canPlaceRight(it) }
                    if (!p1Can) winner = if (p1Hand.sumOf { it.a + it.b } <= p2Hand.sumOf { it.a + it.b }) 1 else 2
                    else turn = 1
                }
            }
        }
    }

    val viewer = if (config.mode == PlayMode.ONLINE) config.playerIndex else if (config.mode == PlayMode.LOCAL_2P) turn else 1
    val hand = if (viewer == 1) p1Hand else p2Hand
    val canAct = (config.mode == PlayMode.LOCAL_2P || turn == viewer) && winner == null

    Column(Modifier.fillMaxSize().background(Color(0xFF0A4F44)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Игрок 1", p1Hand.size, Color(0xFF0D695B), turn == 1)
            ScorePill("Базар", boneyard.size, PlayBoxColors.BoltGold, false)
            ScorePill("Игрок 2", p2Hand.size, Color(0xFF0D695B), turn == 2)
        }
        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (chain.isEmpty()) Text("Выложите первую костяшку", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold)
            else chain.forEach { t ->
                Row(
                    Modifier.size(width = 62.dp, height = 32.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFFAF5E8)).border(2.dp, Color(0xFF1E293B), RoundedCornerShape(7.dp)).padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DominoPipsHalf(t.a, Modifier.weight(1f).fillMaxSize())
                    Box(Modifier.width(1.5.dp).height(22.dp).background(Color(0xFF1E293B)))
                    DominoPipsHalf(t.b, Modifier.weight(1f).fillMaxSize())
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (pendingTileIdx != null && canAct) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayBoxButton(onClick = { place(viewer, pendingTileIdx!!, true, false) }, containerColor = PlayBoxColors.BoltGold, contentColor = PlayBoxColors.DarkInk) {
                    Text("◀ Влево", fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                PlayBoxButton(onClick = { place(viewer, pendingTileIdx!!, false, false) }, containerColor = PlayBoxColors.BoltGold, contentColor = PlayBoxColors.DarkInk) {
                    Text("Вправо ▶", fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val hasPlayable = hand.any { canPlaceLeft(it) || canPlaceRight(it) }
        if (!hasPlayable && canAct) {
            PlayBoxButton(onClick = { drawOrPass(viewer, false) }, containerColor = PlayBoxColors.DarkInk) {
                Text(if (boneyard.isNotEmpty()) "Взять с базара" else "Пас", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            hand.forEachIndexed { idx, tile ->
                val okL = canPlaceLeft(tile); val okR = canPlaceRight(tile)
                Column(
                    Modifier.size(width = 48.dp, height = 84.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFAF5E8))
                        .border(if (canAct && (okL || okR)) 3.dp else 1.5.dp, if (canAct && (okL || okR)) PlayBoxColors.BoltGold else Color.Gray, RoundedCornerShape(10.dp))
                        .bounceClick(enabled = canAct && (okL || okR)) {
                            if (chain.isNotEmpty() && okL && okR) pendingTileIdx = idx
                            else place(viewer, idx, okL, false)
                        }
                        .padding(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DominoPipsHalf(tile.a, Modifier.weight(1f).fillMaxWidth())
                    Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color(0xFF1E293B)))
                    DominoPipsHalf(tile.b, Modifier.weight(1f).fillMaxWidth())
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

// 10. PETDOKU (4x4 with crisp 2x2 subgrid dividers)
private val PET_ICONS = listOf("", "🐱", "🐶", "🐮", "✖️")

@Composable
fun PetdokuScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var board by remember(resetSignal) { mutableStateOf(IntArray(16)) }
    var selectedPet by remember(resetSignal) { mutableIntStateOf(1) }
    var turn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    fun isLegalPet(b: IntArray, idx: Int, pet: Int): Boolean {
        if (b[idx] != 0) return false
        val r = idx / 4; val c = idx % 4
        for (k in 0..3) if (b[r * 4 + k] == pet || b[k * 4 + c] == pet) return false
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
        } else turn = if (player == 1) 2 else 1
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
        Text("Выберите питомца и поставьте в клетку без повторов в строке, столбце и блоке 2×2:", fontSize = 13.sp, color = PlayBoxColors.MutedInk)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (p in 1..4) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (selectedPet == p) PlayBoxColors.BoltGold else Color.White)
                        .border(2.5.dp, PlayBoxColors.DarkInk, RoundedCornerShape(14.dp))
                        .bounceClick { selectedPet = p },
                    contentAlignment = Alignment.Center
                ) { Text(PET_ICONS[p], fontSize = 26.sp) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(0.9f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color.White).border(4.dp, PlayBoxColors.DarkInk, RoundedCornerShape(18.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0..3) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0..3) {
                        val idx = r * 4 + c; val v = board[idx]
                        val ok = canTap && isLegalPet(board, idx, selectedPet)
                        val boxTint = ((r / 2) + (c / 2)) % 2 == 0
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp))
                                .background(if (ok) Color(0xFFE0F7FA) else if (boxTint) PlayBoxColors.CreamBg else Color(0xFFF3E4CE))
                                .bounceClick(enabled = ok) { placePet(idx, selectedPet, turn, false) },
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

// 11. GUESS WHO (Illustrated Canvas portraits + Secret card badge + Trait chips)
private data class GwChar(val id: Int, val name: String, val skin: Color, val hair: Color, val glasses: Boolean, val hat: Boolean, val beard: Boolean, val lightHair: Boolean)

private val GW_ROSTER = listOf(
    GwChar(0, "Анна", Color(0xFFF6D5B0), Color(0xFF3E2723), false, false, false, false),
    GwChar(1, "Борис", Color(0xFFE8B88A), Color(0xFF212121), false, false, true, false),
    GwChar(2, "Вера", Color(0xFFF6D5B0), Color(0xFFE5E7EB), true, false, false, true),
    GwChar(3, "Глеб", Color(0xFFD49A6A), Color(0xFF4E342E), false, true, true, false),
    GwChar(4, "Даша", Color(0xFFF6D5B0), Color(0xFFF4C542), false, false, false, true),
    GwChar(5, "Егор", Color(0xFFE8B88A), Color(0xFF1F242D), true, false, false, false),
    GwChar(6, "Женя", Color(0xFFD49A6A), Color(0xFF3E2723), false, true, false, false),
    GwChar(7, "Зоя", Color(0xFFF6D5B0), Color(0xFFFBBF24), false, true, false, true),
    GwChar(8, "Иван", Color(0xFFE8B88A), Color(0xFFD1D5DB), false, false, true, true),
    GwChar(9, "Катя", Color(0xFFF6D5B0), Color(0xFFEF476F), true, false, false, false),
    GwChar(10, "Лев", Color(0xFFC68B59), Color(0xFFEAB308), false, true, true, true),
    GwChar(11, "Мила", Color(0xFFF6D5B0), Color(0xFF5D4037), false, false, false, false),
    GwChar(12, "Ник", Color(0xFFE8B88A), Color(0xFF2B2D42), true, false, true, false),
    GwChar(13, "Оля", Color(0xFFD49A6A), Color(0xFF3E2723), true, true, false, false),
    GwChar(14, "Паша", Color(0xFFF6D5B0), Color(0xFFFCD34D), false, false, false, true),
    GwChar(15, "Рита", Color(0xFFE8B88A), Color(0xFFE5E7EB), true, false, false, true)
)

@Composable
private fun GwPortrait(ch: GwChar, eliminated: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (eliminated) return@Canvas
        val cx = size.width / 2f; val cy = size.height * 0.56f; val r = size.minDimension * 0.34f
        drawCircle(ch.hair, r * 1.15f, Offset(cx, cy - r * 0.25f))
        drawCircle(ch.skin, r, Offset(cx, cy))
        if (ch.beard) drawArc(ch.hair, 0f, 180f, true, Offset(cx - r * 0.9f, cy - r * 0.1f), Size(r * 1.8f, r * 1.1f))
        drawCircle(Color(0xFF1F242D), r * 0.12f, Offset(cx - r * 0.35f, cy - r * 0.1f))
        drawCircle(Color(0xFF1F242D), r * 0.12f, Offset(cx + r * 0.35f, cy - r * 0.1f))
        if (ch.glasses) {
            drawCircle(Color(0xFF1E293B), r * 0.25f, Offset(cx - r * 0.35f, cy - r * 0.1f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawCircle(Color(0xFF1E293B), r * 0.25f, Offset(cx + r * 0.35f, cy - r * 0.1f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        if (ch.hat) {
            drawRect(Color(0xFF3B82F6), Offset(cx - r, cy - r * 1.35f), Size(r * 2f, r * 0.55f))
            drawRect(Color(0xFF1D4ED8), Offset(cx - r * 1.25f, cy - r * 0.85f), Size(r * 2.5f, r * 0.2f))
        }
    }
}

@Composable
fun GuessWhoScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    val mySecret = remember(seed, config.playerIndex) {
        GW_ROSTER[abs(seed + config.playerIndex * 5) % GW_ROSTER.size]
    }
    val oppTarget = remember(seed, config.playerIndex) {
        val oppIdx = if (config.playerIndex == 1) 2 else 1
        GW_ROSTER[abs(seed + oppIdx * 5) % GW_ROSTER.size]
    }
    var eliminated by remember(seed) { mutableStateOf(emptySet<Int>()) }
    var turn by remember(seed) { mutableIntStateOf(config.startingPlayer) }
    var answerMsg by remember(seed) { mutableStateOf("Задайте вопрос или нажмите «Угадать!»") }
    var guessMode by remember(seed) { mutableStateOf(false) }
    var winner by remember(seed) { mutableStateOf<Int?>(null) }

    fun askTrait(label: String, pred: (GwChar) -> Boolean) {
        val yes = pred(oppTarget)
        val toElim = GW_ROSTER.filter { pred(it) != yes }.map { it.id }.toSet()
        eliminated = eliminated + toElim
        answerMsg = "$label → ${if (yes) "ДА!" else "НЕТ!"}"
        if (config.mode == PlayMode.ONLINE) onlineClient.sendMove(JSONObject().put("act", "pass"))
        turn = if (turn == 1) 2 else 1
    }

    fun tryGuess(id: Int) {
        if (id == oppTarget.id) {
            winner = if (config.mode == PlayMode.ONLINE) config.playerIndex else 1
            prefs.recordWin(GameId.GUESS_WHO)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex)
        } else {
            eliminated = eliminated + id
            answerMsg = "Нет, это не ${GW_ROSTER[id].name}!"
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
            if (Random.nextFloat() < 0.14f) { winner = 2; answerMsg = "Бот угадал вашего персонажа!" }
            else { answerMsg = "Бот задал вопрос — ваш ход!"; turn = 1 }
        }
    }

    val canAct = (config.mode == PlayMode.LOCAL_2P || (config.mode == PlayMode.SOLO && turn == 1) || (config.mode == PlayMode.ONLINE && turn == config.playerIndex)) && winner == null

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(12.dp), color = PlayBoxColors.HeaderSurface, shadowElevation = 2.dp) {
            Text("Ваш персонаж: ${mySecret.name} · $answerMsg", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PlayBoxColors.DarkInk, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(8.dp))

        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0..3) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0..3) {
                        val ch = GW_ROSTER[r * 4 + c]; val out = ch.id in eliminated
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
                                if (out) Text("❌", fontSize = 20.sp)
                                else GwPortrait(ch, out, Modifier.size(44.dp))
                                Text(ch.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (out) Color.Gray else PlayBoxColors.DarkInk, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PlayBoxOutlinedButton(enabled = canAct, onClick = { askTrait("Очки?") { it.glasses } }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) { Text("👓 Очки", maxLines = 1) }
            PlayBoxOutlinedButton(enabled = canAct, onClick = { askTrait("Шляпа?") { it.hat } }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) { Text("🎩 Шляпа", maxLines = 1) }
            PlayBoxOutlinedButton(enabled = canAct, onClick = { askTrait("Борода?") { it.beard } }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) { Text("🧔 Борода", maxLines = 1) }
            PlayBoxOutlinedButton(enabled = canAct, onClick = { askTrait("Светлые?") { it.lightHair } }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) { Text("👱 Светлые", maxLines = 1) }
            PlayBoxButton(enabled = canAct, onClick = { guessMode = !guessMode }, containerColor = PlayBoxColors.Player1Color, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Text(if (guessMode) "Отмена" else "🎯 Угадать!", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        if (winner != null) {
            GameResultBanner(
                title = if (winner == 1 || winner == config.playerIndex) "Вы угадали: ${oppTarget.name}!" else "Соперник угадал первым!",
                subtitle = "Отличная дедукция",
                mode = config.mode,
                roomState = roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}
