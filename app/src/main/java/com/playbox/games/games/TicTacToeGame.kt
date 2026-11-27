package com.playbox.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

@Composable
fun TicTacToeScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var boardSize by remember { mutableIntStateOf(3) } // 3 (3 in a row) or 5 (4 in a row)
    val winLen = if (boardSize == 3) 3 else 4
    var board by remember(boardSize, resetSignal) { mutableStateOf(IntArray(boardSize * boardSize)) }
    var currentPlayer by remember(boardSize, resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(boardSize, resetSignal) { mutableStateOf<Int?>(null) } // 0 = draw, 1/2 = player
    var winLine by remember(boardSize, resetSignal) { mutableStateOf<List<Int>>(emptyList()) }

    fun reset(size: Int = boardSize, starter: Int = 1) {
        boardSize = size
        board = IntArray(size * size)
        currentPlayer = starter
        winner = null
        winLine = emptyList()
    }

    fun applyMove(idx: Int, player: Int, fromRemote: Boolean) {
        if (winner != null || idx !in board.indices || board[idx] != 0) return
        val next = board.copyOf()
        next[idx] = player
        board = next
        if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        val line = findWinningLine(next, boardSize, winLen, player)
        if (line != null) {
            winner = player
            winLine = line
            if (config.mode != PlayMode.ONLINE && player == 1) prefs.recordWin(GameId.TICTACTOE)
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendMove(JSONObject().put("index", idx).put("size", boardSize))
                onlineClient.sendGameOver(player)
                if (player == config.playerIndex) prefs.recordWin(GameId.TICTACTOE)
            }
        } else if (next.none { it == 0 }) {
            winner = 0
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendMove(JSONObject().put("index", idx).put("size", boardSize))
                onlineClient.sendGameOver(0)
            }
        } else {
            currentPlayer = if (player == 1) 2 else 1
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendMove(JSONObject().put("index", idx).put("size", boardSize))
            }
        }
    }

    // Online event listener
    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentMove -> {
                        val size = event.payload.optInt("size", boardSize)
                        if (size != boardSize) reset(size, currentPlayer)
                        val idx = event.payload.optInt("index", -1)
                        applyMove(idx, event.playerIndex, fromRemote = true)
                    }
                    is OnlineEvent.RematchStart -> {
                        reset(boardSize, event.startingPlayer)
                    }
                    else -> Unit
                }
            }
        }
    }

    // Bot turn
    LaunchedEffect(currentPlayer, winner, boardSize) {
        if (config.mode == PlayMode.SOLO && currentPlayer == 2 && winner == null) {
            delay(360)
            val botIdx = chooseTicTacToeMove(board, boardSize, winLen, config.difficulty)
            if (botIdx >= 0) applyMove(botIdx, 2, fromRemote = false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (config.mode != PlayMode.ONLINE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = boardSize == 3,
                    onClick = { reset(3, 1) },
                    label = { Text("3×3 (3 в ряд)", fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = boardSize == 5,
                    onClick = { reset(5, 1) },
                    label = { Text("5×5 (4 в ряд)", fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        val canTap = when (config.mode) {
            PlayMode.SOLO -> currentPlayer == 1 && winner == null
            PlayMode.LOCAL_2P -> winner == null
            PlayMode.ONLINE -> currentPlayer == config.playerIndex && winner == null && (roomState?.bothConnected == true)
        }

        val statusText = when {
            winner == 0 -> "Ничья!"
            winner != null -> {
                if (config.mode == PlayMode.SOLO) {
                    if (winner == 1) "Вы победили!" else "Бот победил!"
                } else if (config.mode == PlayMode.ONLINE) {
                    if (winner == config.playerIndex) "Вы победили!" else "Соперник победил!"
                } else {
                    "Победил Игрок $winner!"
                }
            }
            config.mode == PlayMode.ONLINE && roomState?.bothConnected != true -> "Ожидаем соперника..."
            config.mode == PlayMode.ONLINE -> if (currentPlayer == config.playerIndex) "Ваш ход (${if (config.playerIndex == 1) "X" else "O"})" else "Ход соперника..."
            config.mode == PlayMode.SOLO -> if (currentPlayer == 1) "Ваш ход (X)" else "Бот думает..."
            else -> "Ход Игрока $currentPlayer (${if (currentPlayer == 1) "X" else "O"})"
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = PlayBoxColors.HeaderSurface,
            shadowElevation = 2.dp
        ) {
            Text(
                text = statusText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (currentPlayer == 1) PlayBoxColors.Player1Color else PlayBoxColors.Player2Color,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(4.dp, PlayBoxColors.DarkInk, RoundedCornerShape(24.dp))
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in 0 until boardSize) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (c in 0 until boardSize) {
                            val idx = r * boardSize + c
                            val cellVal = board[idx]
                            val isWinCell = idx in winLine
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isWinCell) Color(0xFFFFF3B0) else PlayBoxColors.CreamBg)
                                    .border(
                                        2.dp,
                                        if (isWinCell) PlayBoxColors.BoltGold else Color(0xFFEBD8BC),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable(enabled = canTap && cellVal == 0) {
                                        applyMove(idx, currentPlayer, fromRemote = false)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (cellVal != 0) {
                                    Canvas(Modifier.fillMaxSize(0.62f)) {
                                        val stroke = (if (boardSize == 3) 10.dp else 6.dp).toPx()
                                        if (cellVal == 1) {
                                            drawLine(
                                                PlayBoxColors.Player1Color,
                                                Offset(0f, 0f),
                                                Offset(size.width, size.height),
                                                stroke,
                                                StrokeCap.Round
                                            )
                                            drawLine(
                                                PlayBoxColors.Player1Color,
                                                Offset(size.width, 0f),
                                                Offset(0f, size.height),
                                                stroke,
                                                StrokeCap.Round
                                            )
                                        } else {
                                            drawCircle(
                                                color = PlayBoxColors.Player2Color,
                                                style = Stroke(width = stroke)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (winner != null) {
            GameResultBanner(
                title = statusText,
                subtitle = if (winner == 0) "Боевая партия!" else "Отличный ход!",
                mode = config.mode,
                roomState = roomState,
                onRematch = {
                    if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch()
                    else reset(boardSize, 1)
                },
                onExit = onExit
            )
        }
    }
}

private fun findWinningLine(board: IntArray, size: Int, winLen: Int, player: Int): List<Int>? {
    val dirs = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
    for (r in 0 until size) {
        for (c in 0 until size) {
            if (board[r * size + c] != player) continue
            for ((dr, dc) in dirs) {
                val cells = mutableListOf<Int>()
                var ok = true
                for (k in 0 until winLen) {
                    val nr = r + dr * k
                    val nc = c + dc * k
                    if (nr !in 0 until size || nc !in 0 until size || board[nr * size + nc] != player) {
                        ok = false
                        break
                    }
                    cells.add(nr * size + nc)
                }
                if (ok) return cells
            }
        }
    }
    return null
}

private fun chooseTicTacToeMove(board: IntArray, size: Int, winLen: Int, difficulty: BotDifficulty): Int {
    val empty = board.indices.filter { board[it] == 0 }
    if (empty.isEmpty()) return -1
    if (difficulty == BotDifficulty.EASY && Random.nextFloat() < 0.65f) {
        return empty.random()
    }
    // Check immediate win or immediate block
    for (idx in empty) {
        val copy = board.copyOf().apply { this[idx] = 2 }
        if (findWinningLine(copy, size, winLen, 2) != null) return idx
    }
    for (idx in empty) {
        val copy = board.copyOf().apply { this[idx] = 1 }
        if (findWinningLine(copy, size, winLen, 1) != null) return idx
    }
    if (size == 3 && difficulty == BotDifficulty.HARD) {
        var bestScore = Int.MIN_VALUE
        var bestMove = empty.first()
        for (idx in empty) {
            val copy = board.copyOf().apply { this[idx] = 2 }
            val s = minimax3x3(copy, isBot = false, depth = 0)
            if (s > bestScore) {
                bestScore = s
                bestMove = idx
            }
        }
        return bestMove
    }
    val center = (size / 2) * size + (size / 2)
    if (board[center] == 0) return center
    return empty.random()
}

private fun minimax3x3(board: IntArray, isBot: Boolean, depth: Int): Int {
    if (findWinningLine(board, 3, 3, 2) != null) return 10 - depth
    if (findWinningLine(board, 3, 3, 1) != null) return depth - 10
    val empty = board.indices.filter { board[it] == 0 }
    if (empty.isEmpty()) return 0

    var best = if (isBot) Int.MIN_VALUE else Int.MAX_VALUE
    for (idx in empty) {
        board[idx] = if (isBot) 2 else 1
        val score = minimax3x3(board, !isBot, depth + 1)
        board[idx] = 0
        best = if (isBot) maxOf(best, score) else minOf(best, score)
    }
    return best
}
