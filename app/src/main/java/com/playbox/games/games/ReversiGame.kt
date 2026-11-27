package com.playbox.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlin.random.Random

private const val REV_SIZE = 8

private fun initialReversiBoard(): IntArray = IntArray(REV_SIZE * REV_SIZE).apply {
    this[3 * REV_SIZE + 3] = 2
    this[3 * REV_SIZE + 4] = 1
    this[4 * REV_SIZE + 3] = 1
    this[4 * REV_SIZE + 4] = 2
}

@Composable
fun ReversiScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var board by remember(resetSignal) { mutableStateOf(initialReversiBoard()) }
    var currentPlayer by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var infoMsg by remember(resetSignal) { mutableStateOf<String?>(null) }

    fun reset(starter: Int = 1) {
        board = initialReversiBoard()
        currentPlayer = starter
        winner = null
        infoMsg = null
    }

    val validMoves = remember(board, currentPlayer, winner) {
        if (winner != null) emptySet() else getValidReversiMoves(board, currentPlayer)
    }

    fun applyMove(r: Int, c: Int, player: Int, fromRemote: Boolean) {
        if (winner != null) return
        val flips = getReversiFlips(board, r, c, player)
        if (flips.isEmpty()) return
        val next = board.copyOf()
        next[r * REV_SIZE + c] = player
        flips.forEach { next[it] = player }
        board = next
        infoMsg = null
        if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("r", r).put("c", c))
        }

        val opp = if (player == 1) 2 else 1
        val oppMoves = getValidReversiMoves(next, opp)
        val myMoves = getValidReversiMoves(next, player)
        if (oppMoves.isEmpty() && myMoves.isEmpty()) {
            val c1 = next.count { it == 1 }
            val c2 = next.count { it == 2 }
            val w = when {
                c1 > c2 -> 1
                c2 > c1 -> 2
                else -> 0
            }
            winner = w
            if (config.mode != PlayMode.ONLINE && w == 1) prefs.recordWin(GameId.REVERSI)
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendGameOver(w)
                if (w == config.playerIndex) prefs.recordWin(GameId.REVERSI)
            }
        } else if (oppMoves.isEmpty()) {
            infoMsg = "У соперника нет ходов — повторный ход!"
            currentPlayer = player
        } else {
            currentPlayer = opp
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentMove -> {
                        applyMove(event.payload.optInt("r"), event.payload.optInt("c"), event.playerIndex, true)
                    }
                    is OnlineEvent.RematchStart -> reset(event.startingPlayer)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(currentPlayer, winner, board) {
        if (config.mode == PlayMode.SOLO && currentPlayer == 2 && winner == null) {
            delay(400)
            val mv = chooseReversiMove(board, config.difficulty)
            if (mv != null) applyMove(mv / REV_SIZE, mv % REV_SIZE, 2, false)
        }
    }

    val p1 = board.count { it == 1 }
    val p2 = board.count { it == 2 }
    val canTap = when (config.mode) {
        PlayMode.SOLO -> currentPlayer == 1 && winner == null
        PlayMode.LOCAL_2P -> winner == null
        PlayMode.ONLINE -> currentPlayer == config.playerIndex && winner == null && (roomState?.bothConnected == true)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScorePill("Чёрные", p1, Color(0xFF1F2937), currentPlayer == 1 && winner == null)
            ScorePill("Белые", p2, Color(0xFF4B5563), currentPlayer == 2 && winner == null)
        }
        if (infoMsg != null) {
            Spacer(Modifier.height(6.dp))
            Text(infoMsg!!, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PlayBoxColors.OnlinePurple)
        }
        Spacer(Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF2B9348))
                .border(4.dp, Color(0xFF1B4332), RoundedCornerShape(18.dp))
                .pointerInput(canTap, validMoves) {
                    detectTapGestures { offset ->
                        if (!canTap) return@detectTapGestures
                        val cell = size.width / REV_SIZE
                        val c = (offset.x / cell).toInt().coerceIn(0, REV_SIZE - 1)
                        val r = (offset.y / cell).toInt().coerceIn(0, REV_SIZE - 1)
                        if ((r * REV_SIZE + c) in validMoves) {
                            applyMove(r, c, currentPlayer, false)
                        }
                    }
                }
        ) {
            val cell = size.width / REV_SIZE
            for (i in 1 until REV_SIZE) {
                drawLine(Color(0xFF1B4332), Offset(i * cell, 0f), Offset(i * cell, size.height), 1.5.dp.toPx())
                drawLine(Color(0xFF1B4332), Offset(0f, i * cell), Offset(size.width, i * cell), 1.5.dp.toPx())
            }
            for (r in 0 until REV_SIZE) {
                for (c in 0 until REV_SIZE) {
                    val idx = r * REV_SIZE + c
                    val center = Offset((c + 0.5f) * cell, (r + 0.5f) * cell)
                    when (board[idx]) {
                        1 -> drawCircle(Color(0xFF1F242D), cell * 0.38f, center)
                        2 -> {
                            drawCircle(Color.White, cell * 0.38f, center)
                            drawCircle(Color(0xFFD1D5DB), cell * 0.38f, center, style = Stroke(1.5.dp.toPx()))
                        }
                        else -> if (canTap && idx in validMoves) {
                            drawCircle(Color(0xFF80ED99).copy(alpha = 0.7f), cell * 0.14f, center)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (winner != null) {
            val title = when {
                winner == 0 -> "Ничья $p1 : $p2!"
                config.mode == PlayMode.SOLO -> if (winner == 1) "Вы победили!" else "Бот победил!"
                config.mode == PlayMode.ONLINE -> if (winner == config.playerIndex) "Вы победили!" else "Соперник победил!"
                else -> "Победил Игрок $winner!"
            }
            GameResultBanner(
                title = title,
                subtitle = "Счёт фишек: $p1 — $p2",
                mode = config.mode,
                roomState = roomState,
                onRematch = {
                    if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch()
                    else reset(1)
                },
                onExit = onExit
            )
        }
    }
}

private val REV_DIRS = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)

private fun getReversiFlips(board: IntArray, r: Int, c: Int, player: Int): List<Int> {
    if (r !in 0 until REV_SIZE || c !in 0 until REV_SIZE || board[r * REV_SIZE + c] != 0) return emptyList()
    val opp = if (player == 1) 2 else 1
    val result = mutableListOf<Int>()
    for ((dr, dc) in REV_DIRS) {
        val line = mutableListOf<Int>()
        var nr = r + dr
        var nc = c + dc
        while (nr in 0 until REV_SIZE && nc in 0 until REV_SIZE && board[nr * REV_SIZE + nc] == opp) {
            line.add(nr * REV_SIZE + nc)
            nr += dr
            nc += dc
        }
        if (line.isNotEmpty() && nr in 0 until REV_SIZE && nc in 0 until REV_SIZE && board[nr * REV_SIZE + nc] == player) {
            result.addAll(line)
        }
    }
    return result
}

private fun getValidReversiMoves(board: IntArray, player: Int): Set<Int> {
    val set = mutableSetOf<Int>()
    for (r in 0 until REV_SIZE) {
        for (c in 0 until REV_SIZE) {
            if (getReversiFlips(board, r, c, player).isNotEmpty()) set.add(r * REV_SIZE + c)
        }
    }
    return set
}

private val REV_WEIGHTS = intArrayOf(
    100, -20, 10, 5, 5, 10, -20, 100,
    -20, -50, -2, -2, -2, -2, -50, -20,
    10, -2, 3, 2, 2, 3, -2, 10,
    5, -2, 2, 1, 1, 2, -2, 5,
    5, -2, 2, 1, 1, 2, -2, 5,
    10, -2, 3, 2, 2, 3, -2, 10,
    -20, -50, -2, -2, -2, -2, -50, -20,
    100, -20, 10, 5, 5, 10, -20, 100
)

private fun chooseReversiMove(board: IntArray, difficulty: BotDifficulty): Int? {
    val moves = getValidReversiMoves(board, 2).toList()
    if (moves.isEmpty()) return null
    if (difficulty == BotDifficulty.EASY && Random.nextFloat() < 0.6f) return moves.random()
    return moves.maxByOrNull { idx ->
        val flips = getReversiFlips(board, idx / REV_SIZE, idx % REV_SIZE, 2).size
        REV_WEIGHTS[idx] + flips * 2
    }
}
