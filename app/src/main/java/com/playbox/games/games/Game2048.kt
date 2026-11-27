package com.playbox.games.games

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.playbox.games.ui.components.PlayBoxOutlinedButton
import com.playbox.games.ui.theme.PlayBoxColors
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

private enum class Dir2048 { UP, DOWN, LEFT, RIGHT }

@Composable
fun Game2048Screen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var board by remember(resetSignal) { mutableStateOf(initial2048Board(config.seed)) }
    var score by remember(resetSignal) { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(prefs.getBestScore(GameId.GAME_2048)) }
    var prevBoard by remember(resetSignal) { mutableStateOf<IntArray?>(null) }
    var prevScore by remember(resetSignal) { mutableIntStateOf(0) }
    var gameOver by remember(resetSignal) { mutableStateOf(false) }
    var oppScore by remember(resetSignal) { mutableIntStateOf(0) }
    var timeLeftSec by remember(resetSignal) { mutableIntStateOf(90) }
    var onlineFinished by remember(resetSignal) { mutableStateOf(false) }

    fun reset(seed: Int) {
        board = initial2048Board(seed)
        score = 0; prevBoard = null; prevScore = 0; gameOver = false
        oppScore = 0; timeLeftSec = 90; onlineFinished = false
    }

    fun applySwipe(dir: Dir2048) {
        if (gameOver || onlineFinished) return
        val (next, gained, moved) = move2048(board, dir)
        if (!moved) return
        prevBoard = board; prevScore = score
        val withSpawn = spawnTile2048(next, Random)
        board = withSpawn
        score += gained
        if (score > bestScore) { bestScore = score; prefs.updateBestScore(GameId.GAME_2048, score) }
        if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
        if (!canMove2048(withSpawn)) gameOver = true
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentState -> oppScore = event.payload.optInt("score", oppScore)
                    is OnlineEvent.RematchStart -> reset(event.seed)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(config.mode, resetSignal, roomState?.bothConnected) {
        if (config.mode == PlayMode.ONLINE) {
            while (timeLeftSec > 0 && !onlineFinished) {
                delay(1000)
                if (roomState?.bothConnected == true) timeLeftSec -= 1
            }
            if (!onlineFinished) {
                onlineFinished = true
                val w = when { score > oppScore -> config.playerIndex; oppScore > score -> if (config.playerIndex == 1) 2 else 1; else -> 0 }
                if (w == config.playerIndex) prefs.recordWin(GameId.GAME_2048)
                onlineClient.sendGameOver(w)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("Счёт", score, Color(0xFF6A515E), true)
            if (config.mode == PlayMode.ONLINE) {
                ScorePill("Время", timeLeftSec, PlayBoxColors.OnlinePurple, true)
                ScorePill("Соп.", oppScore, PlayBoxColors.Player2Color, false)
            } else {
                ScorePill("Рекорд", bestScore, PlayBoxColors.BoltGold, false)
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF8F7A66))
                .border(4.dp, PlayBoxColors.DarkInk, RoundedCornerShape(22.dp))
                .padding(10.dp)
                .pointerInput(gameOver, onlineFinished, board) {
                    var dragAccX = 0f; var dragAccY = 0f; var triggered = false
                    detectDragGestures(
                        onDragStart = { dragAccX = 0f; dragAccY = 0f; triggered = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (triggered) return@detectDragGestures
                            dragAccX += dragAmount.x; dragAccY += dragAmount.y
                            if (abs(dragAccX) > 40f || abs(dragAccY) > 40f) {
                                triggered = true
                                val dir = if (abs(dragAccX) > abs(dragAccY)) {
                                    if (dragAccX > 0) Dir2048.RIGHT else Dir2048.LEFT
                                } else {
                                    if (dragAccY > 0) Dir2048.DOWN else Dir2048.UP
                                }
                                applySwipe(dir)
                            }
                        }
                    )
                }
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0 until 4) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until 4) {
                            val v = board[r * 4 + c]
                            val scale by animateFloatAsState(
                                targetValue = if (v > 0) 1f else 0.92f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "t2048_${r}_${c}_$v"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tileColor2048(v)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (v > 0) {
                                    Text(
                                        text = "$v",
                                        color = if (v <= 4) Color(0xFF4A4238) else Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = when { v >= 1024 -> 22.sp; v >= 100 -> 26.sp; else -> 30.sp },
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (config.mode != PlayMode.ONLINE && prevBoard != null && !gameOver) {
            PlayBoxOutlinedButton(
                onClick = { board = prevBoard!!; score = prevScore; prevBoard = null },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("↩ Отменить ход", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        } else {
            Text("Свайпайте по полю в любую сторону", fontSize = 13.sp, color = PlayBoxColors.MutedInk)
        }

        Spacer(Modifier.weight(1f))

        if (gameOver || onlineFinished) {
            val title = if (config.mode == PlayMode.ONLINE) {
                when { score > oppScore -> "Вы быстрее и круче!"; oppScore > score -> "Соперник набрал больше!"; else -> "Ничья!" }
            } else "Игра окончена!"
            GameResultBanner(
                title = title,
                subtitle = if (config.mode == PlayMode.ONLINE) "Ваш счёт: $score · Соперник: $oppScore" else "Ваш счёт: $score · Рекорд: $bestScore",
                mode = config.mode,
                roomState = roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else reset(Random.nextInt(10000)) },
                onExit = onExit
            )
        }
    }
}

private fun tileColor2048(v: Int): Color = when (v) {
    0 -> Color(0xFFCDC1B4); 2 -> Color(0xFFEEE4DA); 4 -> Color(0xFFEDE0C8)
    8 -> Color(0xFFF2B179); 16 -> Color(0xFFF59563); 32 -> Color(0xFFF67C5F)
    64 -> Color(0xFFF65E3B); 128 -> Color(0xFFEDCF72); 256 -> Color(0xFFEDCC61)
    512 -> Color(0xFFEDC850); 1024 -> Color(0xFFEDC53F); else -> Color(0xFFFAB824)
}

private fun initial2048Board(seed: Int): IntArray {
    val rng = Random(seed)
    return spawnTile2048(spawnTile2048(IntArray(16), rng), rng)
}

private fun spawnTile2048(board: IntArray, rng: Random): IntArray {
    val empty = board.indices.filter { board[it] == 0 }
    if (empty.isEmpty()) return board
    return board.copyOf().apply { this[empty.random(rng)] = if (rng.nextFloat() < 0.9f) 2 else 4 }
}

private fun slideRowLeft(row: IntArray): Triple<IntArray, Int, Boolean> {
    val nonZero = row.filter { it != 0 }; val merged = mutableListOf<Int>()
    var gained = 0; var i = 0
    while (i < nonZero.size) {
        if (i + 1 < nonZero.size && nonZero[i] == nonZero[i + 1]) {
            val val2 = nonZero[i] * 2; merged.add(val2); gained += val2; i += 2
        } else { merged.add(nonZero[i]); i += 1 }
    }
    while (merged.size < 4) merged.add(0)
    val res = merged.toIntArray()
    return Triple(res, gained, !res.contentEquals(row))
}

private fun move2048(board: IntArray, dir: Dir2048): Triple<IntArray, Int, Boolean> {
    val next = IntArray(16); var totalGain = 0; var anyMoved = false
    for (line in 0 until 4) {
        val raw = IntArray(4) { k ->
            when (dir) {
                Dir2048.LEFT -> board[line * 4 + k]; Dir2048.RIGHT -> board[line * 4 + (3 - k)]
                Dir2048.UP -> board[k * 4 + line]; Dir2048.DOWN -> board[(3 - k) * 4 + line]
            }
        }
        val (slid, gain, moved) = slideRowLeft(raw)
        if (moved) anyMoved = true; totalGain += gain
        for (k in 0 until 4) {
            val idx = when (dir) {
                Dir2048.LEFT -> line * 4 + k; Dir2048.RIGHT -> line * 4 + (3 - k)
                Dir2048.UP -> k * 4 + line; Dir2048.DOWN -> (3 - k) * 4 + line
            }
            next[idx] = slid[k]
        }
    }
    return Triple(next, totalGain, anyMoved)
}

private fun canMove2048(board: IntArray): Boolean {
    if (board.any { it == 0 }) return true
    for (r in 0 until 4) for (c in 0 until 4) {
        val v = board[r * 4 + c]
        if (r + 1 < 4 && board[(r + 1) * 4 + c] == v) return true
        if (c + 1 < 4 && board[r * 4 + c + 1] == v) return true
    }
    return false
}
