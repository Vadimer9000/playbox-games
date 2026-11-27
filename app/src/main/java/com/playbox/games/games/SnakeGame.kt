package com.playbox.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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

private const val SNK_GRID = 18

private enum class SnkDir(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    fun opposite(other: SnkDir): Boolean = dx + other.dx == 0 && dy + other.dy == 0
}

@Composable
fun SnakeScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var snake1 by remember(resetSignal) { mutableStateOf(listOf(6 to 12, 5 to 12, 4 to 12)) }
    var dir1 by remember(resetSignal) { mutableStateOf(SnkDir.RIGHT) }
    var snake2 by remember(resetSignal) { mutableStateOf(listOf(11 to 5, 12 to 5, 13 to 5)) }
    var dir2 by remember(resetSignal) { mutableStateOf(SnkDir.LEFT) }
    var apple by remember(resetSignal) { mutableStateOf(9 to 9) }
    var score1 by remember(resetSignal) { mutableIntStateOf(0) }
    var score2 by remember(resetSignal) { mutableIntStateOf(0) }
    var oppOnlineScore by remember(resetSignal) { mutableIntStateOf(0) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) } // 1, 2, 0=solo over
    var bestScore by remember { mutableIntStateOf(prefs.getBestScore(GameId.SNAKE)) }

    fun reset() {
        snake1 = listOf(6 to 12, 5 to 12, 4 to 12)
        dir1 = SnkDir.RIGHT
        snake2 = listOf(11 to 5, 12 to 5, 13 to 5)
        dir2 = SnkDir.LEFT
        apple = 9 to 9
        score1 = 0
        score2 = 0
        oppOnlineScore = 0
        winner = null
    }

    fun spawnApple(occupied: Set<Pair<Int, Int>>): Pair<Int, Int> {
        val free = mutableListOf<Pair<Int, Int>>()
        for (x in 0 until SNK_GRID) for (y in 0 until SNK_GRID) {
            if ((x to y) !in occupied) free.add(x to y)
        }
        return if (free.isNotEmpty()) free.random() else (0 to 0)
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentState -> {
                        oppOnlineScore = event.payload.optInt("score", oppOnlineScore)
                        if (event.payload.optBoolean("dead", false) && winner != null) {
                            // keep playing or compare
                        }
                    }
                    is OnlineEvent.RematchStart -> reset()
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(resetSignal, winner) {
        while (winner == null) {
            delay(165L)
            if (winner != null) break

            val h1 = snake1.first()
            val nh1 = (h1.first + dir1.dx) to (h1.second + dir1.dy)
            val hitWall1 = nh1.first !in 0 until SNK_GRID || nh1.second !in 0 until SNK_GRID
            val hitSelf1 = nh1 in snake1.dropLast(1)
            val hitOther1 = config.mode == PlayMode.LOCAL_2P && nh1 in snake2

            if (config.mode == PlayMode.LOCAL_2P) {
                val h2 = snake2.first()
                val nh2 = (h2.first + dir2.dx) to (h2.second + dir2.dy)
                val hitWall2 = nh2.first !in 0 until SNK_GRID || nh2.second !in 0 until SNK_GRID
                val hitSelf2 = nh2 in snake2.dropLast(1)
                val hitOther2 = nh2 in snake1

                val dead1 = hitWall1 || hitSelf1 || hitOther1
                val dead2 = hitWall2 || hitSelf2 || hitOther2
                if (dead1 && dead2) {
                    winner = if (score1 > score2) 1 else if (score2 > score1) 2 else 0
                    break
                } else if (dead1) {
                    winner = 2
                    break
                } else if (dead2) {
                    winner = 1
                    break
                }

                val eat1 = nh1 == apple
                val eat2 = nh2 == apple
                snake1 = if (eat1) listOf(nh1) + snake1 else listOf(nh1) + snake1.dropLast(1)
                snake2 = if (eat2) listOf(nh2) + snake2 else listOf(nh2) + snake2.dropLast(1)
                if (eat1) score1 += 10
                if (eat2) score2 += 10
                if (eat1 || eat2) apple = spawnApple((snake1 + snake2).toSet())
            } else {
                if (hitWall1 || hitSelf1) {
                    winner = 0
                    if (config.mode == PlayMode.ONLINE) {
                        onlineClient.sendState(JSONObject().put("score", score1).put("dead", true))
                    }
                    break
                }
                val eat1 = nh1 == apple
                snake1 = if (eat1) listOf(nh1) + snake1 else listOf(nh1) + snake1.dropLast(1)
                if (eat1) {
                    score1 += 10
                    if (score1 > bestScore) {
                        bestScore = score1
                        prefs.updateBestScore(GameId.SNAKE, score1)
                    }
                    apple = spawnApple(snake1.toSet())
                    if (config.mode == PlayMode.ONLINE) {
                        onlineClient.sendState(JSONObject().put("score", score1).put("dead", false))
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScorePill("Игрок 1", score1, Color(0xFF2D9D4E), true)
            when (config.mode) {
                PlayMode.LOCAL_2P -> ScorePill("Игрок 2", score2, PlayBoxColors.Player2Color, true)
                PlayMode.ONLINE -> ScorePill("Соперник", oppOnlineScore, PlayBoxColors.OnlinePurple, false)
                PlayMode.SOLO -> ScorePill("Рекорд", bestScore, PlayBoxColors.BoltGold, false)
            }
        }

        if (config.mode == PlayMode.LOCAL_2P) {
            Spacer(Modifier.height(4.dp))
            SnakePad(color = PlayBoxColors.Player2Color) { d -> if (!d.opposite(dir2)) dir2 = d }
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(if (config.mode == PlayMode.LOCAL_2P) 0.84f else 0.94f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFFAF0CA))
                .border(4.dp, PlayBoxColors.DarkInk, RoundedCornerShape(18.dp))
                .pointerInput(config.mode) {
                    if (config.mode != PlayMode.LOCAL_2P) {
                        var accX = 0f
                        var accY = 0f
                        var swiped = false
                        detectDragGestures(
                            onDragStart = { accX = 0f; accY = 0f; swiped = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (swiped) return@detectDragGestures
                                accX += dragAmount.x
                                accY += dragAmount.y
                                if (abs(accX) > 30f || abs(accY) > 30f) {
                                    swiped = true
                                    val nd = if (abs(accX) > abs(accY)) {
                                        if (accX > 0) SnkDir.RIGHT else SnkDir.LEFT
                                    } else {
                                        if (accY > 0) SnkDir.DOWN else SnkDir.UP
                                    }
                                    if (!nd.opposite(dir1)) dir1 = nd
                                }
                            }
                        )
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / SNK_GRID
                for (r in 0 until SNK_GRID) {
                    for (c in 0 until SNK_GRID) {
                        if ((r + c) % 2 == 0) {
                            drawRect(Color(0xFFF3E5B5), Offset(c * cell, r * cell), Size(cell, cell))
                        }
                    }
                }
                // Apple
                drawCircle(
                    Color(0xFFE63946),
                    cell * 0.42f,
                    Offset((apple.first + 0.5f) * cell, (apple.second + 0.5f) * cell)
                )
                // Snake 1
                snake1.forEachIndexed { idx, (x, y) ->
                    val center = Offset((x + 0.5f) * cell, (y + 0.5f) * cell)
                    drawCircle(if (idx == 0) Color(0xFF1B7F3A) else Color(0xFF38B058), cell * 0.45f, center)
                }
                // Snake 2
                if (config.mode == PlayMode.LOCAL_2P) {
                    snake2.forEachIndexed { idx, (x, y) ->
                        val center = Offset((x + 0.5f) * cell, (y + 0.5f) * cell)
                        drawCircle(if (idx == 0) Color(0xFF005F8C) else PlayBoxColors.Player2Color, cell * 0.45f, center)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        SnakePad(color = Color(0xFF2D9D4E)) { d -> if (!d.opposite(dir1)) dir1 = d }

        if (winner != null) {
            val title = when (config.mode) {
                PlayMode.LOCAL_2P -> if (winner == 0) "Столкновение! Ничья" else "Победил Игрок $winner!"
                PlayMode.ONLINE -> "Ваш забег окончен: $score1"
                PlayMode.SOLO -> "Игра окончена! Счёт: $score1"
            }
            GameResultBanner(
                title = title,
                subtitle = if (config.mode == PlayMode.ONLINE) "Счёт соперника: $oppOnlineScore" else "Рекорд: $bestScore",
                mode = config.mode,
                roomState = roomState,
                onRematch = {
                    if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch()
                    else reset()
                },
                onExit = onExit
            )
        }
    }
}

@Composable
private fun SnakePad(color: Color, onDir: (SnkDir) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onDir(SnkDir.LEFT) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Влево", tint = color)
        }
        FilledTonalIconButton(onClick = { onDir(SnkDir.UP) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Вверх", tint = color)
        }
        FilledTonalIconButton(onClick = { onDir(SnkDir.DOWN) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Вниз", tint = color)
        }
        FilledTonalIconButton(onClick = { onDir(SnkDir.RIGHT) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Вправо", tint = color)
        }
    }
}
