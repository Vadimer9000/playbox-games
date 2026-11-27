package com.playbox.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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

private const val WIN_GOALS = 7

@Composable
fun PingPongScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var p1X by remember(resetSignal) { mutableFloatStateOf(0.5f) }
    var p2X by remember(resetSignal) { mutableFloatStateOf(0.5f) }
    var ballX by remember(resetSignal) { mutableFloatStateOf(0.5f) }
    var ballY by remember(resetSignal) { mutableFloatStateOf(0.5f) }
    var trail by remember(resetSignal) { mutableStateOf<List<Offset>>(emptyList()) }
    var velX by remember(resetSignal) { mutableFloatStateOf(0.011f) }
    var velY by remember(resetSignal) { mutableFloatStateOf(0.014f) }
    var score1 by remember(resetSignal) { mutableIntStateOf(0) }
    var score2 by remember(resetSignal) { mutableIntStateOf(0) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    val paddleWidth = 0.24f
    val paddleHeight = 0.032f
    val ballRadius = 0.024f
    val isOnlineHost = config.mode != PlayMode.ONLINE || config.playerIndex == 1

    fun resetRound(towardsBottom: Boolean) {
        ballX = 0.5f; ballY = 0.5f; trail = emptyList()
        val dirX = if (Random.nextBoolean()) 1f else -1f
        velX = dirX * (0.008f + Random.nextFloat() * 0.005f)
        velY = if (towardsBottom) 0.013f else -0.013f
    }

    fun resetMatch() {
        p1X = 0.5f; p2X = 0.5f; score1 = 0; score2 = 0; winner = null
        resetRound(true)
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentState -> {
                        val p = event.payload
                        if (config.playerIndex == 1) {
                            p2X = p.optDouble("p2X", p2X.toDouble()).toFloat()
                        } else {
                            p1X = p.optDouble("p1X", p1X.toDouble()).toFloat()
                            ballX = p.optDouble("bx", ballX.toDouble()).toFloat()
                            ballY = p.optDouble("by", ballY.toDouble()).toFloat()
                            trail = (trail + Offset(ballX, ballY)).takeLast(6)
                            score1 = p.optInt("s1", score1); score2 = p.optInt("s2", score2)
                            val w = p.optInt("w", 0)
                            if (w > 0 && winner == null) {
                                winner = w
                                if (w == config.playerIndex) prefs.recordWin(GameId.PING_PONG)
                            }
                        }
                    }
                    is OnlineEvent.RematchStart -> resetMatch()
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(resetSignal, config.mode, config.difficulty, roomState?.bothConnected) {
        var tick = 0
        while (true) {
            delay(16L)
            if (winner != null) continue
            if (config.mode == PlayMode.ONLINE && roomState?.bothConnected != true) continue

            if (!isOnlineHost) {
                if (tick % 2 == 0) onlineClient.sendState(JSONObject().put("p2X", p2X.toDouble()))
                tick++
                continue
            }

            if (config.mode == PlayMode.SOLO) {
                val aiSpeed = when (config.difficulty) {
                    BotDifficulty.EASY -> 0.010f; BotDifficulty.MEDIUM -> 0.015f; BotDifficulty.HARD -> 0.024f
                }
                p2X = (p2X + (ballX - p2X).coerceIn(-aiSpeed, aiSpeed)).coerceIn(paddleWidth / 2f, 1f - paddleWidth / 2f)
            }

            ballX += velX; ballY += velY
            trail = (trail + Offset(ballX, ballY)).takeLast(6)

            if (ballX - ballRadius < 0f) { ballX = ballRadius; velX = abs(velX) }
            else if (ballX + ballRadius > 1f) { ballX = 1f - ballRadius; velX = -abs(velX) }

            val p1Top = 0.92f - paddleHeight / 2f
            if (velY > 0 && ballY + ballRadius >= p1Top && ballY - ballRadius <= p1Top + paddleHeight && abs(ballX - p1X) <= paddleWidth / 2f + ballRadius) {
                ballY = p1Top - ballRadius
                val offset = (ballX - p1X) / (paddleWidth / 2f)
                velY = -(abs(velY) * 1.04f).coerceAtMost(0.028f)
                velX = (offset * 0.018f).coerceIn(-0.022f, 0.022f)
            }

            val p2Bottom = 0.08f + paddleHeight / 2f
            if (velY < 0 && ballY - ballRadius <= p2Bottom && ballY + ballRadius >= p2Bottom - paddleHeight && abs(ballX - p2X) <= paddleWidth / 2f + ballRadius) {
                ballY = p2Bottom + ballRadius
                val offset = (ballX - p2X) / (paddleWidth / 2f)
                velY = (abs(velY) * 1.04f).coerceAtMost(0.028f)
                velX = (offset * 0.018f).coerceIn(-0.022f, 0.022f)
            }

            if (ballY < 0f) {
                score1 += 1
                if (score1 >= WIN_GOALS) {
                    winner = 1
                    if (config.mode != PlayMode.ONLINE || config.playerIndex == 1) prefs.recordWin(GameId.PING_PONG)
                    if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(1)
                } else resetRound(true)
            } else if (ballY > 1f) {
                score2 += 1
                if (score2 >= WIN_GOALS) {
                    winner = 2
                    if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(2)
                } else resetRound(false)
            }

            if (config.mode == PlayMode.ONLINE && tick % 2 == 0) {
                onlineClient.sendState(
                    JSONObject().put("p1X", p1X.toDouble()).put("bx", ballX.toDouble()).put("by", ballY.toDouble())
                        .put("s1", score1).put("s2", score2).put("w", winner ?: 0)
                )
            }
            tick++
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScorePill("Игрок 1", score1, PlayBoxColors.Player2Color, true)
            ScorePill(if (config.mode == PlayMode.SOLO) "Бот" else "Игрок 2", score2, PlayBoxColors.Player1Color, true)
        }
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .border(4.dp, PlayBoxColors.DarkInk, RoundedCornerShape(22.dp))
        ) {
            val mirrorForP2 = config.mode == PlayMode.ONLINE && config.playerIndex == 2

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(config.mode, config.playerIndex) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        val nx = (change.position.x / size.width).coerceIn(paddleWidth / 2f, 1f - paddleWidth / 2f)
                                        val ny = change.position.y / size.height
                                        when (config.mode) {
                                            PlayMode.SOLO -> p1X = nx
                                            PlayMode.LOCAL_2P -> if (ny < 0.5f) p2X = nx else p1X = nx
                                            PlayMode.ONLINE -> if (config.playerIndex == 1) p1X = nx else p2X = 1f - nx
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
            ) {
                val w = size.width; val h = size.height
                drawRect(Brush.verticalGradient(listOf(Color(0xFF38A385), Color(0xFF43AA8B), Color(0xFF38A385))))
                drawLine(Color.White.copy(alpha = 0.65f), Offset(0f, h / 2f), Offset(w, h / 2f), 3.dp.toPx())
                drawCircle(Color.White.copy(alpha = 0.45f), w * 0.14f, Offset(w / 2f, h / 2f), style = Stroke(3.dp.toPx()))

                val dispBottomX = if (mirrorForP2) 1f - p2X else p1X
                val dispTopX = if (mirrorForP2) 1f - p1X else p2X
                val dispBallX = if (mirrorForP2) 1f - ballX else ballX
                val dispBallY = if (mirrorForP2) 1f - ballY else ballY

                // Trail
                trail.forEachIndexed { idx, pt ->
                    val tx = if (mirrorForP2) 1f - pt.x else pt.x
                    val ty = if (mirrorForP2) 1f - pt.y else pt.y
                    val alpha = (idx + 1).toFloat() / (trail.size + 2) * 0.35f
                    drawCircle(Color(0xFFFFD166).copy(alpha = alpha), ballRadius * w * (0.5f + idx * 0.08f), Offset(tx * w, ty * h))
                }

                drawRoundRect(
                    color = if (mirrorForP2) PlayBoxColors.Player2Color else PlayBoxColors.Player1Color,
                    topLeft = Offset((dispTopX - paddleWidth / 2f) * w, (0.08f - paddleHeight / 2f) * h),
                    size = Size(paddleWidth * w, paddleHeight * h),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
                drawRoundRect(
                    color = if (mirrorForP2) PlayBoxColors.Player1Color else PlayBoxColors.Player2Color,
                    topLeft = Offset((dispBottomX - paddleWidth / 2f) * w, (0.92f - paddleHeight / 2f) * h),
                    size = Size(paddleWidth * w, paddleHeight * h),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )

                val bCenter = Offset(dispBallX * w, dispBallY * h)
                drawCircle(Color(0xFFFFD166), ballRadius * w, bCenter)
                drawCircle(PlayBoxColors.DarkInk, ballRadius * w, bCenter, style = Stroke(2.5.dp.toPx()))
            }

            if (winner != null) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    val title = when (config.mode) {
                        PlayMode.SOLO -> if (winner == 1) "Вы победили!" else "Бот победил!"
                        PlayMode.ONLINE -> if (winner == config.playerIndex) "Победа!" else "Соперник победил!"
                        PlayMode.LOCAL_2P -> "Победил Игрок $winner!"
                    }
                    GameResultBanner(
                        title = title,
                        subtitle = "Счёт $score1 : $score2 (до $WIN_GOALS)",
                        mode = config.mode,
                        roomState = roomState,
                        onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else resetMatch() },
                        onExit = onExit
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (config.mode == PlayMode.LOCAL_2P) "Верхняя половина — Игрок 2, нижняя — Игрок 1" else "Ведите пальцем по нижней части поля",
            fontSize = 12.sp,
            color = PlayBoxColors.MutedInk
        )
    }
}
