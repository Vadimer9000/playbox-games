package com.playbox.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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

private const val C4_ROWS = 6
private const val C4_COLS = 7

@Composable
fun Connect4Screen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var board by remember(resetSignal) { mutableStateOf(IntArray(C4_ROWS * C4_COLS)) }
    var currentPlayer by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var winCells by remember(resetSignal) { mutableStateOf<Set<Int>>(emptySet()) }
    var lastMoveCell by remember(resetSignal) { mutableIntStateOf(-1) }

    fun reset(starter: Int = 1) {
        board = IntArray(C4_ROWS * C4_COLS)
        currentPlayer = starter
        winner = null
        winCells = emptySet()
        lastMoveCell = -1
    }

    fun dropInColumn(col: Int, player: Int, fromRemote: Boolean) {
        if (winner != null || col !in 0 until C4_COLS) return
        val row = (C4_ROWS - 1 downTo 0).firstOrNull { r -> board[r * C4_COLS + col] == 0 } ?: return
        val idx = row * C4_COLS + col
        val next = board.copyOf().apply { this[idx] = player }
        board = next
        lastMoveCell = idx
        if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        val win = findConnect4Win(next, player)
        if (win != null) {
            winner = player
            winCells = win
            if (config.mode != PlayMode.ONLINE && player == 1) prefs.recordWin(GameId.CONNECT4)
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendMove(JSONObject().put("col", col))
                onlineClient.sendGameOver(player)
                if (player == config.playerIndex) prefs.recordWin(GameId.CONNECT4)
            }
        } else if ((0 until C4_COLS).all { c -> next[c] != 0 }) {
            winner = 0
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendMove(JSONObject().put("col", col))
                onlineClient.sendGameOver(0)
            }
        } else {
            currentPlayer = if (player == 1) 2 else 1
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendMove(JSONObject().put("col", col))
            }
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentMove -> {
                        val col = event.payload.optInt("col", -1)
                        dropInColumn(col, event.playerIndex, fromRemote = true)
                    }
                    is OnlineEvent.RematchStart -> reset(event.startingPlayer)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(currentPlayer, winner) {
        if (config.mode == PlayMode.SOLO && currentPlayer == 2 && winner == null) {
            delay(380)
            val col = chooseConnect4Move(board, config.difficulty)
            if (col >= 0) dropInColumn(col, 2, fromRemote = false)
        }
    }

    val canTap = when (config.mode) {
        PlayMode.SOLO -> currentPlayer == 1 && winner == null
        PlayMode.LOCAL_2P -> winner == null
        PlayMode.ONLINE -> currentPlayer == config.playerIndex && winner == null && (roomState?.bothConnected == true)
    }

    val statusText = when {
        winner == 0 -> "Ничья!"
        winner != null -> when (config.mode) {
            PlayMode.SOLO -> if (winner == 1) "Вы победили!" else "Бот победил!"
            PlayMode.ONLINE -> if (winner == config.playerIndex) "Вы победили!" else "Соперник победил!"
            PlayMode.LOCAL_2P -> "Победил Игрок $winner!"
        }
        config.mode == PlayMode.ONLINE && roomState?.bothConnected != true -> "Ожидаем соперника..."
        config.mode == PlayMode.ONLINE -> if (currentPlayer == config.playerIndex) "Ваш ход" else "Ход соперника..."
        config.mode == PlayMode.SOLO -> if (currentPlayer == 1) "Ваш ход (Красный)" else "Бот думает..."
        else -> "Ход Игрока $currentPlayer (${if (currentPlayer == 1) "Красный" else "Голубой"})"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = PlayBoxColors.HeaderSurface, shadowElevation = 2.dp) {
            Text(
                text = statusText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (currentPlayer == 1) PlayBoxColors.Player1Color else PlayBoxColors.Player2Color,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(7f / 6f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1F3647))
                .pointerInput(canTap, board) {
                    detectTapGestures { offset ->
                        if (!canTap) return@detectTapGestures
                        val col = ((offset.x / size.width) * C4_COLS).toInt().coerceIn(0, C4_COLS - 1)
                        dropInColumn(col, currentPlayer, fromRemote = false)
                    }
                }
        ) {
            val cellW = size.width / C4_COLS
            val cellH = size.height / C4_ROWS
            val r = minOf(cellW, cellH) * 0.38f

            drawRoundRect(
                color = Color(0xFF1F3647),
                cornerRadius = CornerRadius(22.dp.toPx())
            )
            for (row in 0 until C4_ROWS) {
                for (col in 0 until C4_COLS) {
                    val idx = row * C4_COLS + col
                    val center = Offset((col + 0.5f) * cellW, (row + 0.5f) * cellH)
                    val v = board[idx]
                    val discColor = when (v) {
                        1 -> PlayBoxColors.Player1Color
                        2 -> Color(0xFF38BDF8)
                        else -> PlayBoxColors.CreamBg
                    }
                    drawCircle(color = discColor, radius = r, center = center)
                    if (idx in winCells) {
                        drawCircle(
                            color = Color(0xFFFFD166),
                            radius = r * 1.06f,
                            center = center,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    } else if (idx == lastMoveCell) {
                        drawCircle(
                            color = Color.White,
                            radius = r * 0.3f,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (winner != null) {
            GameResultBanner(
                title = statusText,
                subtitle = if (winner == 0) "Поле полностью заполнено!" else "4 фишки собраны!",
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

private fun findConnect4Win(board: IntArray, player: Int): Set<Int>? {
    val dirs = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
    for (r in 0 until C4_ROWS) {
        for (c in 0 until C4_COLS) {
            if (board[r * C4_COLS + c] != player) continue
            for ((dr, dc) in dirs) {
                val cells = mutableSetOf<Int>()
                var ok = true
                for (k in 0 until 4) {
                    val nr = r + dr * k
                    val nc = c + dc * k
                    if (nr !in 0 until C4_ROWS || nc !in 0 until C4_COLS || board[nr * C4_COLS + nc] != player) {
                        ok = false
                        break
                    }
                    cells.add(nr * C4_COLS + nc)
                }
                if (ok) return cells
            }
        }
    }
    return null
}

private fun chooseConnect4Move(board: IntArray, difficulty: BotDifficulty): Int {
    val validCols = (0 until C4_COLS).filter { board[it] == 0 }
    if (validCols.isEmpty()) return -1
    if (difficulty == BotDifficulty.EASY && Random.nextFloat() < 0.6f) return validCols.random()

    val depth = when (difficulty) {
        BotDifficulty.EASY -> 2
        BotDifficulty.MEDIUM -> 4
        BotDifficulty.HARD -> 5
    }
    val orderedCols = listOf(3, 2, 4, 1, 5, 0, 6).filter { it in validCols }
    var bestScore = Int.MIN_VALUE
    var bestCol = orderedCols.first()
    for (col in orderedCols) {
        val row = (C4_ROWS - 1 downTo 0).first { board[it * C4_COLS + col] == 0 }
        board[row * C4_COLS + col] = 2
        val score = alphaBetaC4(board, depth - 1, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2, maximizing = false)
        board[row * C4_COLS + col] = 0
        if (score > bestScore) {
            bestScore = score
            bestCol = col
        }
    }
    return bestCol
}

private fun alphaBetaC4(board: IntArray, depth: Int, alphaInit: Int, betaInit: Int, maximizing: Boolean): Int {
    if (findConnect4Win(board, 2) != null) return 100000 + depth
    if (findConnect4Win(board, 1) != null) return -100000 - depth
    val validCols = listOf(3, 2, 4, 1, 5, 0, 6).filter { board[it] == 0 }
    if (depth == 0 || validCols.isEmpty()) return evaluateC4(board)

    var alpha = alphaInit
    var beta = betaInit
    if (maximizing) {
        var value = Int.MIN_VALUE / 2
        for (col in validCols) {
            val row = (C4_ROWS - 1 downTo 0).first { board[it * C4_COLS + col] == 0 }
            board[row * C4_COLS + col] = 2
            value = maxOf(value, alphaBetaC4(board, depth - 1, alpha, beta, false))
            board[row * C4_COLS + col] = 0
            alpha = maxOf(alpha, value)
            if (alpha >= beta) break
        }
        return value
    } else {
        var value = Int.MAX_VALUE / 2
        for (col in validCols) {
            val row = (C4_ROWS - 1 downTo 0).first { board[it * C4_COLS + col] == 0 }
            board[row * C4_COLS + col] = 1
            value = minOf(value, alphaBetaC4(board, depth - 1, alpha, beta, true))
            board[row * C4_COLS + col] = 0
            beta = minOf(beta, value)
            if (alpha >= beta) break
        }
        return value
    }
}

private fun evaluateC4(board: IntArray): Int {
    var score = 0
    for (r in 0 until C4_ROWS) {
        if (board[r * C4_COLS + 3] == 2) score += 4
        if (board[r * C4_COLS + 3] == 1) score -= 4
    }
    return score
}
