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
import kotlin.math.abs
import kotlin.random.Random

// 0=empty, 1=p1 man, 3=p1 king, 2=p2 man, 4=p2 king
private const val CHK_SIZE = 8

private fun initialCheckersBoard(): IntArray {
    val b = IntArray(CHK_SIZE * CHK_SIZE)
    for (r in 0 until CHK_SIZE) {
        for (c in 0 until CHK_SIZE) {
            if ((r + c) % 2 == 1) {
                if (r < 3) b[r * CHK_SIZE + c] = 2
                else if (r > 4) b[r * CHK_SIZE + c] = 1
            }
        }
    }
    return b
}

private data class ChkMove(val from: Int, val to: Int, val captured: Int? = null)

@Composable
fun CheckersScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var board by remember(resetSignal) { mutableStateOf(initialCheckersBoard()) }
    var currentPlayer by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var selectedCell by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var forcedChainCell by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    fun reset(starter: Int = 1) {
        board = initialCheckersBoard()
        currentPlayer = starter
        selectedCell = null
        forcedChainCell = null
        winner = null
    }

    val legalMoves = remember(board, currentPlayer, forcedChainCell, winner) {
        if (winner != null) emptyList() else getCheckersMoves(board, currentPlayer, forcedChainCell)
    }

    fun executeMove(mv: ChkMove, player: Int, fromRemote: Boolean) {
        if (winner != null) return
        val next = board.copyOf()
        var piece = next[mv.from]
        next[mv.from] = 0
        if (mv.captured != null) next[mv.captured] = 0
        val toRow = mv.to / CHK_SIZE
        if (piece == 1 && toRow == 0) piece = 3
        if (piece == 2 && toRow == CHK_SIZE - 1) piece = 4
        next[mv.to] = piece
        board = next
        if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        val chainNext = if (mv.captured != null) {
            getCheckersCapturesForPiece(next, mv.to, player)
        } else emptyList()

        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(
                JSONObject()
                    .put("from", mv.from)
                    .put("to", mv.to)
                    .put("captured", mv.captured ?: -1)
            )
        }

        if (chainNext.isNotEmpty()) {
            forcedChainCell = mv.to
            selectedCell = mv.to
            return
        }

        forcedChainCell = null
        selectedCell = null
        val opp = if (player == 1) 2 else 1
        val oppMoves = getCheckersMoves(next, opp, null)
        if (oppMoves.isEmpty()) {
            winner = player
            if (config.mode != PlayMode.ONLINE && player == 1) prefs.recordWin(GameId.CHECKERS)
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendGameOver(player)
                if (player == config.playerIndex) prefs.recordWin(GameId.CHECKERS)
            }
        } else {
            currentPlayer = opp
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentMove -> {
                        val from = event.payload.optInt("from", -1)
                        val to = event.payload.optInt("to", -1)
                        val cap = event.payload.optInt("captured", -1).let { if (it >= 0) it else null }
                        if (from >= 0 && to >= 0) {
                            executeMove(ChkMove(from, to, cap), event.playerIndex, fromRemote = true)
                        }
                    }
                    is OnlineEvent.RematchStart -> reset(event.startingPlayer)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(currentPlayer, winner, board, forcedChainCell) {
        if (config.mode == PlayMode.SOLO && currentPlayer == 2 && winner == null) {
            delay(380)
            val mv = chooseCheckersMove(board, forcedChainCell, config.difficulty)
            if (mv != null) executeMove(mv, 2, false)
        }
    }

    val p1Count = board.count { it == 1 || it == 3 }
    val p2Count = board.count { it == 2 || it == 4 }
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
            ScorePill("Белые", p1Count, PlayBoxColors.Player1Color, currentPlayer == 1 && winner == null)
            ScorePill(if (config.mode == PlayMode.SOLO) "Бот" else "Чёрные", p2Count, Color(0xFF374151), currentPlayer == 2 && winner == null)
        }
        Spacer(Modifier.height(16.dp))

        val flipBoard = config.mode == PlayMode.ONLINE && config.playerIndex == 2

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(4.dp, Color(0xFF4A3728), RoundedCornerShape(16.dp))
                .pointerInput(canTap, legalMoves, selectedCell, flipBoard) {
                    detectTapGestures { offset ->
                        if (!canTap) return@detectTapGestures
                        val cell = size.width / CHK_SIZE
                        val rawC = (offset.x / cell).toInt().coerceIn(0, CHK_SIZE - 1)
                        val rawR = (offset.y / cell).toInt().coerceIn(0, CHK_SIZE - 1)
                        val r = if (flipBoard) CHK_SIZE - 1 - rawR else rawR
                        val c = if (flipBoard) CHK_SIZE - 1 - rawC else rawC
                        val idx = r * CHK_SIZE + c

                        val matchingMove = selectedCell?.let { sel ->
                            legalMoves.firstOrNull { it.from == sel && it.to == idx }
                        }
                        if (matchingMove != null) {
                            executeMove(matchingMove, currentPlayer, false)
                        } else if (forcedChainCell == null && legalMoves.any { it.from == idx }) {
                            selectedCell = idx
                        }
                    }
                }
        ) {
            val cell = size.width / CHK_SIZE
            val targetSet = selectedCell?.let { sel -> legalMoves.filter { it.from == sel }.map { it.to }.toSet() } ?: emptySet()

            for (rawR in 0 until CHK_SIZE) {
                for (rawC in 0 until CHK_SIZE) {
                    val r = if (flipBoard) CHK_SIZE - 1 - rawR else rawR
                    val c = if (flipBoard) CHK_SIZE - 1 - rawC else rawC
                    val idx = r * CHK_SIZE + c
                    val dark = (r + c) % 2 == 1
                    val bg = when {
                        idx == selectedCell -> Color(0xFFFDE047)
                        idx in targetSet -> Color(0xFF86EFAC)
                        dark -> Color(0xFF9C7A54)
                        else -> Color(0xFFF5E6D3)
                    }
                    drawRect(bg, Offset(rawC * cell, rawR * cell), Size(cell, cell))
                    val v = board[idx]
                    val center = Offset((rawC + 0.5f) * cell, (rawR + 0.5f) * cell)
                    if (v != 0) {
                        val isP1 = (v == 1 || v == 3)
                        val isKing = (v == 3 || v == 4)
                        val fill = if (isP1) Color(0xFFFFF8EE) else Color(0xFF272B33)
                        val ring = if (isP1) PlayBoxColors.Player1Color else Color(0xFF6B7280)
                        drawCircle(fill, cell * 0.38f, center)
                        drawCircle(ring, cell * 0.38f, center, style = Stroke(3.dp.toPx()))
                        if (isKing) {
                            drawCircle(PlayBoxColors.BoltGold, cell * 0.16f, center)
                        }
                    } else if (idx in targetSet) {
                        drawCircle(Color(0xFF15803D), cell * 0.14f, center)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (legalMoves.any { it.captured != null } && winner == null) {
            Text("Обязательное взятие!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PlayBoxColors.Player1Color)
        }

        Spacer(Modifier.weight(1f))
        if (winner != null) {
            val title = when (config.mode) {
                PlayMode.SOLO -> if (winner == 1) "Вы победили!" else "Бот победил!"
                PlayMode.ONLINE -> if (winner == config.playerIndex) "Вы победили!" else "Соперник победил!"
                PlayMode.LOCAL_2P -> "Победил Игрок $winner!"
            }
            GameResultBanner(
                title = title,
                subtitle = "Осталось шашек: $p1Count — $p2Count",
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

private fun ownsPiece(piece: Int, player: Int): Boolean =
    if (player == 1) (piece == 1 || piece == 3) else (piece == 2 || piece == 4)

private fun getCheckersCapturesForPiece(board: IntArray, idx: Int, player: Int): List<ChkMove> {
    val piece = board[idx]
    if (!ownsPiece(piece, player)) return emptyList()
    val r = idx / CHK_SIZE
    val c = idx % CHK_SIZE
    val isKing = (piece == 3 || piece == 4)
    val opp = if (player == 1) 2 else 1
    val dirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
    val res = mutableListOf<ChkMove>()

    for ((dr, dc) in dirs) {
        if (!isKing) {
            val mr = r + dr
            val mc = c + dc
            val jr = r + 2 * dr
            val jc = c + 2 * dc
            if (jr in 0 until CHK_SIZE && jc in 0 until CHK_SIZE) {
                if (ownsPiece(board[mr * CHK_SIZE + mc], opp) && board[jr * CHK_SIZE + jc] == 0) {
                    res.add(ChkMove(idx, jr * CHK_SIZE + jc, mr * CHK_SIZE + mc))
                }
            }
        } else {
            var sr = r + dr
            var sc = c + dc
            var capturedIdx: Int? = null
            while (sr in 0 until CHK_SIZE && sc in 0 until CHK_SIZE) {
                val cell = board[sr * CHK_SIZE + sc]
                if (cell == 0) {
                    if (capturedIdx != null) res.add(ChkMove(idx, sr * CHK_SIZE + sc, capturedIdx))
                } else if (ownsPiece(cell, opp) && capturedIdx == null) {
                    capturedIdx = sr * CHK_SIZE + sc
                } else {
                    break
                }
                sr += dr
                sc += dc
            }
        }
    }
    return res
}

private fun getCheckersMoves(board: IntArray, player: Int, forcedCell: Int?): List<ChkMove> {
    if (forcedCell != null) return getCheckersCapturesForPiece(board, forcedCell, player)

    val captures = mutableListOf<ChkMove>()
    for (idx in board.indices) {
        if (ownsPiece(board[idx], player)) {
            captures.addAll(getCheckersCapturesForPiece(board, idx, player))
        }
    }
    if (captures.isNotEmpty()) return captures

    val quiet = mutableListOf<ChkMove>()
    for (idx in board.indices) {
        val piece = board[idx]
        if (!ownsPiece(piece, player)) continue
        val r = idx / CHK_SIZE
        val c = idx % CHK_SIZE
        val isKing = (piece == 3 || piece == 4)
        val dirs = when {
            isKing -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
            player == 1 -> listOf(-1 to -1, -1 to 1)
            else -> listOf(1 to -1, 1 to 1)
        }
        for ((dr, dc) in dirs) {
            var nr = r + dr
            var nc = c + dc
            while (nr in 0 until CHK_SIZE && nc in 0 until CHK_SIZE && board[nr * CHK_SIZE + nc] == 0) {
                quiet.add(ChkMove(idx, nr * CHK_SIZE + nc, null))
                if (!isKing) break
                nr += dr
                nc += dc
            }
        }
    }
    return quiet
}

private fun chooseCheckersMove(board: IntArray, forcedCell: Int?, difficulty: BotDifficulty): ChkMove? {
    val moves = getCheckersMoves(board, 2, forcedCell)
    if (moves.isEmpty()) return null
    if (difficulty == BotDifficulty.EASY && Random.nextFloat() < 0.65f) return moves.random()

    return moves.maxByOrNull { mv ->
        var score = 0
        if (mv.captured != null) score += 50
        val toR = mv.to / CHK_SIZE
        if (toR == CHK_SIZE - 1) score += 30
        val safeAfter = getCheckersCapturesForPiece(board.copyOf().apply {
            this[mv.to] = this[mv.from]; this[mv.from] = 0; mv.captured?.let { this[it] = 0 }
        }, mv.to, 1).isEmpty()
        if (safeAfter) score += 10
        score + abs(3 - (mv.to % CHK_SIZE))
    }
}
