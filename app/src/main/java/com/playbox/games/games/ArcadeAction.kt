package com.playbox.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// 32. FRUIT MERGE (Suika 2D circle physics & merge)
private data class FruitBody(val id: Int, val tier: Int, var x: Float, var y: Float, var vx: Float, var vy: Float)

private val FRUIT_RADII = listOf(0.045f, 0.062f, 0.080f, 0.100f, 0.125f, 0.150f, 0.180f)
private val FRUIT_COLORS = listOf(
    Color(0xFF3A86FF), Color(0xFFEF476F), Color(0xFFFFCA3A),
    Color(0xFFF3722C), Color(0xFF9B5DE5), Color(0xFF70E000), Color(0xFF2D9D4E)
)

@Composable
fun FruitMergeScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var fruits by remember(resetSignal) { mutableStateOf<List<FruitBody>>(emptyList()) }
    var nextId by remember(resetSignal) { mutableIntStateOf(1) }
    var nextTier by remember(resetSignal) { mutableIntStateOf(0) }
    var dropX by remember(resetSignal) { mutableFloatStateOf(0.5f) }
    var score by remember(resetSignal) { mutableIntStateOf(0) }
    var gameOver by remember(resetSignal) { mutableStateOf(false) }

    LaunchedEffect(resetSignal, gameOver) {
        while (!gameOver) {
            delay(20L)
            val list = fruits.map { it.copy() }.toMutableList()
            for (f in list) {
                f.vy += 0.0014f
                f.x += f.vx; f.y += f.vy
                f.vx *= 0.99f
                val r = FRUIT_RADII[f.tier]
                if (f.x < r) { f.x = r; f.vx = abs(f.vx) * 0.4f }
                if (f.x > 1f - r) { f.x = 1f - r; f.vx = -abs(f.vx) * 0.4f }
                if (f.y > 1f - r) { f.y = 1f - r; f.vy = -abs(f.vy) * 0.2f; f.vx *= 0.92f }
            }
            val mergedRemove = mutableSetOf<Int>()
            val mergedAdd = mutableListOf<FruitBody>()
            for (i in 0 until list.size) {
                for (j in i + 1 until list.size) {
                    val a = list[i]; val b = list[j]
                    if (a.id in mergedRemove || b.id in mergedRemove) continue
                    val ra = FRUIT_RADII[a.tier]; val rb = FRUIT_RADII[b.tier]
                    val dx = b.x - a.x; val dy = b.y - a.y
                    val dist = hypot(dx, dy).coerceAtLeast(0.0001f)
                    val minDist = ra + rb
                    if (dist < minDist) {
                        if (a.tier == b.tier && a.tier < FRUIT_RADII.lastIndex) {
                            mergedRemove.add(a.id); mergedRemove.add(b.id)
                            val newTier = a.tier + 1
                            mergedAdd.add(FruitBody(nextId++, newTier, (a.x + b.x) / 2f, (a.y + b.y) / 2f, 0f, -0.005f))
                            score += (newTier + 1) * 10
                            prefs.updateBestScore(GameId.FRUIT_MERGE, score)
                            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
                        } else {
                            val overlap = (minDist - dist) * 0.5f
                            val nx = dx / dist; val ny = dy / dist
                            a.x -= nx * overlap; a.y -= ny * overlap
                            b.x += nx * overlap; b.y += ny * overlap
                            a.vx -= nx * 0.002f; a.vy -= ny * 0.002f
                            b.vx += nx * 0.002f; b.vy += ny * 0.002f
                        }
                    }
                }
            }
            val final = (list.filter { it.id !in mergedRemove } + mergedAdd)
            fruits = final
            if (final.any { it.y < 0.09f && abs(it.vy) < 0.003f }) gameOver = true
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFFCEBC9)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Очки", score, Color(0xFFE6533C), true)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.weight(1f).fillMaxWidth(0.92f).clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFF8EE)).border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(18.dp))
                .pointerInput(gameOver, nextTier) {
                    detectTapGestures { pos ->
                        if (gameOver) return@detectTapGestures
                        val r = FRUIT_RADII[nextTier]
                        val x = (pos.x / size.width).coerceIn(r, 1f - r)
                        dropX = x
                        fruits = fruits + FruitBody(nextId++, nextTier, x, 0.08f, 0f, 0.004f)
                        nextTier = Random.nextInt(0, 3)
                    }
                }
        ) {
            drawLine(Color(0xFFEF476F).copy(alpha = 0.4f), Offset(0f, size.height * 0.14f), Offset(size.width, size.height * 0.14f), 2.dp.toPx())
            // Next fruit preview
            drawCircle(FRUIT_COLORS[nextTier], FRUIT_RADII[nextTier] * size.width, Offset(dropX * size.width, size.height * 0.07f))
            fruits.forEach { f ->
                val c = Offset(f.x * size.width, f.y * size.height)
                val r = FRUIT_RADII[f.tier] * size.width
                drawCircle(FRUIT_COLORS[f.tier], r, c)
                drawCircle(Color.White.copy(alpha = 0.3f), r * 0.28f, Offset(c.x - r * 0.25f, c.y - r * 0.25f))
            }
        }
        if (gameOver) {
            GameResultBanner("Корзина переполнена!", "Очки: $score", config.mode, roomState,
                onRematch = { fruits = emptyList(); score = 0; gameOver = false },
                onExit = onExit
            )
        }
    }
}

// 33. TOWER STACK & 34. ANIMAL STACK (Shared precision stacker)
@Composable
private fun PrecisionStackScreen(
    gameId: GameId,
    isAnimal: Boolean,
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    // Each placed block: (centerX [0..1], width [0..1])
    var stack by remember(resetSignal) { mutableStateOf(listOf(0.5f to 0.50f)) }
    var moverX by remember(resetSignal) { mutableFloatStateOf(0.5f) }
    var moverDir by remember(resetSignal) { mutableFloatStateOf(1f) }
    var gameOver by remember(resetSignal) { mutableStateOf(false) }

    val activeWidth = stack.last().second

    LaunchedEffect(resetSignal, gameOver, stack.size) {
        while (!gameOver) {
            delay(16L)
            val speed = (0.011f + stack.size * 0.0007f).coerceAtMost(0.028f)
            var nx = moverX + moverDir * speed
            if (nx > 0.85f) { nx = 0.85f; moverDir = -1f }
            if (nx < 0.15f) { nx = 0.15f; moverDir = 1f }
            moverX = nx
        }
    }

    fun drop() {
        if (gameOver) return
        val prev = stack.last()
        val prevLeft = prev.first - prev.second / 2f
        val prevRight = prev.first + prev.second / 2f
        val curLeft = moverX - activeWidth / 2f
        val curRight = moverX + activeWidth / 2f
        val interLeft = maxOf(prevLeft, curLeft)
        val interRight = minOf(prevRight, curRight)
        val newW = interRight - interLeft
        if (newW <= 0.04f) {
            gameOver = true
        } else {
            val snappedW = if (abs(moverX - prev.first) < 0.02f) prev.second else newW
            val snappedC = if (abs(moverX - prev.first) < 0.02f) prev.first else (interLeft + interRight) / 2f
            stack = stack + (snappedC to snappedW)
            val height = stack.size - 1
            prefs.updateBestScore(gameId, height)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", height))
        }
    }

    Column(
        Modifier.fillMaxSize().background(if (isAnimal) Color(0xFF26B7E8) else Color(0xFFF2D4E6)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScorePill("Высота", stack.size - 1, PlayBoxColors.OnlinePurple, true)
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (isAnimal) Color(0xFF198AB3) else Color(0xFF3A0CA3).copy(alpha = 0.15f))
                .pointerInput(gameOver, stack) { detectTapGestures { drop() } }
        ) {
            val blockH = 26.dp.toPx()
            val visible = stack.takeLast(14)
            visible.forEachIndexed { i, (cx, w) ->
                val y = size.height - (i + 1) * blockH - 12.dp.toPx()
                val col = SORT_PALETTE[i % SORT_PALETTE.size]
                drawRoundRect(col, Offset((cx - w / 2f) * size.width, y), Size(w * size.width, blockH - 3.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            }
            if (!gameOver) {
                val my = size.height - (visible.size + 1) * blockH - 12.dp.toPx()
                drawRoundRect(Color.White, Offset((moverX - activeWidth / 2f) * size.width, my), Size(activeWidth * size.width, blockH - 3.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Нажмите на экран, чтобы сбросить блок ровно", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (gameOver) {
            GameResultBanner("Башня упала!", "Высота: ${stack.size - 1}", config.mode, roomState,
                onRematch = { stack = listOf(0.5f to 0.50f); moverX = 0.5f; gameOver = false },
                onExit = onExit
            )
        }
    }
}

@Composable
fun TowerStackScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) =
    PrecisionStackScreen(GameId.TOWER_STACK, false, config, prefs, onlineClient, roomState, resetSignal, onExit)

@Composable
fun AnimalStackScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) =
    PrecisionStackScreen(GameId.ANIMAL_STACK, true, config, prefs, onlineClient, roomState, resetSignal, onExit)

// 35. MULTI SNAKES (Slither-style arena with rainbow worm + AI snakes + orbs)
@Composable
fun MultiSnakesScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var playerBody by remember(resetSignal) { mutableStateOf(List(12) { i -> Offset(0.5f - i * 0.015f, 0.5f) }) }
    var angle by remember(resetSignal) { mutableFloatStateOf(0f) }
    var bots by remember(resetSignal) {
        mutableStateOf(listOf(
            List(10) { i -> Offset(0.2f, 0.2f + i * 0.015f) },
            List(10) { i -> Offset(0.8f, 0.8f - i * 0.015f) }
        ))
    }
    var orbs by remember(resetSignal) { mutableStateOf(List(18) { Offset(Random.nextFloat() * 0.9f + 0.05f, Random.nextFloat() * 0.9f + 0.05f) }) }
    var score by remember(resetSignal) { mutableIntStateOf(0) }
    var gameOver by remember(resetSignal) { mutableStateOf(false) }

    LaunchedEffect(resetSignal, gameOver) {
        while (!gameOver) {
            delay(32L)
            val h = playerBody.first()
            val nh = Offset(h.x + cos(angle) * 0.012f, h.y + sin(angle) * 0.012f)
            if (nh.x !in 0.02f..0.98f || nh.y !in 0.02f..0.98f || bots.any { b -> b.any { hypot(it.x - nh.x, it.y - nh.y) < 0.024f } }) {
                gameOver = true
                break
            }
            var grow = false
            val remOrbs = orbs.filter { orb ->
                if (hypot(orb.x - nh.x, orb.y - nh.y) < 0.032f) { grow = true; false } else true
            }
            if (grow) {
                score += 10
                prefs.updateBestScore(GameId.MULTI_SNAKES, score)
                if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", score))
                orbs = remOrbs + Offset(Random.nextFloat() * 0.9f + 0.05f, Random.nextFloat() * 0.9f + 0.05f)
            }
            playerBody = if (grow) listOf(nh) + playerBody else listOf(nh) + playerBody.dropLast(1)

            // Move AI snakes gently
            bots = bots.mapIndexed { idx, bot ->
                val bh = bot.first()
                val dir = (System.currentTimeMillis() / 900.0 + idx * 2.1).toFloat()
                val nb = Offset((bh.x + cos(dir) * 0.008f).coerceIn(0.06f, 0.94f), (bh.y + sin(dir) * 0.008f).coerceIn(0.06f, 0.94f))
                listOf(nb) + bot.dropLast(1)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF1A1E24)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Длина", score, Color(0xFFF6B756), true)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF111419)).border(3.dp, Color.White, RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectDragGestures { ch, _ ->
                        ch.consume()
                        val cx = size.width / 2f; val cy = size.height / 2f
                        angle = atan2(ch.position.y - cy, ch.position.x - cx)
                    }
                }
        ) {
            orbs.forEachIndexed { i, o ->
                drawCircle(SORT_PALETTE[i % SORT_PALETTE.size], 6.dp.toPx(), Offset(o.x * size.width, o.y * size.height))
            }
            bots.forEachIndexed { bIdx, bot ->
                bot.forEach { pt -> drawCircle(if (bIdx == 0) Color(0xFF38BDF8) else Color(0xFFEF476F), 9.dp.toPx(), Offset(pt.x * size.width, pt.y * size.height)) }
            }
            playerBody.forEachIndexed { i, pt ->
                drawCircle(SORT_PALETTE[i % SORT_PALETTE.size], 10.dp.toPx(), Offset(pt.x * size.width, pt.y * size.height))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Тяните палец относительно центра для поворота", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        if (gameOver) {
            GameResultBanner("Столкновение!", "Очки: $score", config.mode, roomState,
                onRematch = { playerBody = List(12) { i -> Offset(0.5f - i * 0.015f, 0.5f) }; score = 0; gameOver = false },
                onExit = onExit
            )
        }
    }
}

// 36. BALL RUN (Neon Tunnel 3-lane dodger)
@Composable
fun BallRunScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var ballX by remember(resetSignal) { mutableFloatStateOf(0.5f) }
    var obstacles by remember(resetSignal) { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) } // (x, y)
    var distance by remember(resetSignal) { mutableIntStateOf(0) }
    var gameOver by remember(resetSignal) { mutableStateOf(false) }

    LaunchedEffect(resetSignal, gameOver) {
        var tick = 0
        while (!gameOver) {
            delay(20L)
            val speed = (0.012f + distance * 0.00004f).coerceAtMost(0.032f)
            val moved = obstacles.map { (ox, oy) -> ox to (oy + speed) }.filter { it.second <= 1.05f }.toMutableList()
            if (tick % 22 == 0) moved.add((Random.nextFloat() * 0.78f + 0.11f) to -0.05f)
            obstacles = moved
            distance += 1
            if (distance % 30 == 0) {
                prefs.updateBestScore(GameId.BALL_RUN, distance)
                if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", distance))
            }
            if (moved.any { (ox, oy) -> abs(ox - ballX) < 0.08f && abs(oy - 0.85f) < 0.06f }) gameOver = true
            tick++
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF141830)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Дистанция", distance, Color(0xFFC77DFF), true)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF0A0D1C)).border(3.dp, Color(0xFF3A86FF), RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectDragGestures { ch, _ ->
                        ch.consume()
                        ballX = (ch.position.x / size.width).coerceIn(0.08f, 0.92f)
                    }
                }
        ) {
            for (i in 1..4) {
                val x = size.width * i / 5f
                drawLine(Color(0xFF1E3A8A), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
            }
            obstacles.forEach { (ox, oy) ->
                val s = 34.dp.toPx()
                drawRect(Color(0xFFFF006E), Offset(ox * size.width - s / 2f, oy * size.height - s / 2f), Size(s, s))
            }
            drawCircle(Color(0xFFC77DFF), 20.dp.toPx(), Offset(ballX * size.width, size.height * 0.85f))
        }
        if (gameOver) {
            GameResultBanner("Врезался!", "Дистанция: $distance м", config.mode, roomState,
                onRematch = { obstacles = emptyList(); distance = 0; gameOver = false },
                onExit = onExit
            )
        }
    }
}

// 37. ARCHERY & 38. DARTS (Precision aiming games)
@Composable
fun ArcheryScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    PrecisionTargetGame(GameId.ARCHERY, isDarts = false, config, prefs, onlineClient, roomState, resetSignal, onExit)
}

@Composable
fun DartsScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    PrecisionTargetGame(GameId.DARTS, isDarts = true, config, prefs, onlineClient, roomState, resetSignal, onExit)
}

@Composable
private fun PrecisionTargetGame(
    gameId: GameId,
    isDarts: Boolean,
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var score1 by remember(resetSignal) { mutableIntStateOf(if (isDarts) 301 else 0) }
    var score2 by remember(resetSignal) { mutableIntStateOf(if (isDarts) 301 else 0) }
    var round by remember(resetSignal) { mutableIntStateOf(1) }
    var turn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var aimX by remember(resetSignal) { mutableFloatStateOf(0.5f) }
    var aimY by remember(resetSignal) { mutableFloatStateOf(0.5f) }
    var lastHit by remember(resetSignal) { mutableStateOf<Offset?>(null) }
    var lastMsg by remember(resetSignal) { mutableStateOf("Нажмите на мишень в момент прицела!") }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    LaunchedEffect(resetSignal, winner) {
        var t = 0f
        while (winner == null) {
            delay(20L)
            t += 0.055f
            aimX = 0.5f + 0.34f * sin(t * 1.3f)
            aimY = 0.5f + 0.34f * cos(t * 1.9f)
        }
    }

    fun applyThrow(player: Int, pts: Int, hx: Float, hy: Float, fromRemote: Boolean) {
        lastHit = Offset(hx, hy)
        lastMsg = "Попадание: $pts очков!"
        if (isDarts) {
            if (player == 1) { if (score1 - pts >= 0) score1 -= pts }
            else { if (score2 - pts >= 0) score2 -= pts }
            if ((player == 1 && score1 == 0) || (player == 2 && score2 == 0)) winner = player
        } else {
            if (player == 1) score1 += pts else score2 += pts
            if (round >= 5 && player == 2) {
                winner = if (score1 > score2) 1 else if (score2 > score1) 2 else 0
            }
        }
        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("pts", pts).put("x", hx.toDouble()).put("y", hy.toDouble()))
        }
        if (winner == null) {
            if (player == 2) round += 1
            turn = if (player == 1) 2 else 1
        } else {
            if (config.mode != PlayMode.ONLINE && winner == 1) prefs.recordWin(gameId)
            if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendGameOver(winner!!)
        }
    }

    LaunchedEffect(turn, winner) {
        if (config.mode == PlayMode.SOLO && turn == 2 && winner == null) {
            delay(650)
            val pts = if (isDarts) listOf(20, 25, 40, 60, 15).random() else Random.nextInt(6, 11)
            applyThrow(2, pts, 0.5f + Random.nextFloat() * 0.1f - 0.05f, 0.5f, false)
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                if (ev is OnlineEvent.OpponentMove) {
                    applyThrow(ev.playerIndex, ev.payload.optInt("pts"), ev.payload.optDouble("x", 0.5).toFloat(), ev.payload.optDouble("y", 0.5).toFloat(), true)
                } else if (ev is OnlineEvent.RematchStart) {
                    score1 = if (isDarts) 301 else 0; score2 = if (isDarts) 301 else 0; round = 1; turn = ev.startingPlayer; winner = null
                }
            }
        }
    }

    val canTap = winner == null && (config.mode == PlayMode.LOCAL_2P || (config.mode == PlayMode.SOLO && turn == 1) || (config.mode == PlayMode.ONLINE && turn == config.playerIndex))

    Column(Modifier.fillMaxSize().background(if (isDarts) Color(0xFFF6B98E) else Color(0xFFD5F0EE)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScorePill("Игрок 1", score1, PlayBoxColors.Player1Color, turn == 1)
            ScorePill(if (config.mode == PlayMode.SOLO) "Бот" else "Игрок 2", score2, PlayBoxColors.Player2Color, turn == 2)
        }
        Spacer(Modifier.height(6.dp))
        Text(lastMsg, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))

        Canvas(
            Modifier.fillMaxWidth(0.9f).aspectRatio(1f)
                .pointerInput(canTap, aimX, aimY) {
                    detectTapGestures {
                        if (!canTap) return@detectTapGestures
                        val dist = hypot(aimX - 0.5f, aimY - 0.5f)
                        val pts = if (isDarts) {
                            when { dist < 0.06f -> 50; dist < 0.12f -> 25; dist < 0.25f -> 20; dist < 0.36f -> 60; else -> 10 }
                        } else {
                            (10 - (dist * 22).toInt()).coerceIn(1, 10)
                        }
                        applyThrow(turn, pts, aimX, aimY, false)
                    }
                }
        ) {
            val r = size.minDimension * 0.45f
            if (isDarts) {
                drawCircle(Color(0xFF14181F), r, center)
                drawCircle(Color(0xFFEF476F), r * 0.85f, center, style = Stroke(8.dp.toPx()))
                drawCircle(Color(0xFF06D6A0), r * 0.55f, center, style = Stroke(8.dp.toPx()))
                drawCircle(Color(0xFF06D6A0), r * 0.18f, center)
                drawCircle(Color(0xFFEF476F), r * 0.08f, center)
            } else {
                drawCircle(Color.White, r, center)
                drawCircle(Color(0xFF0096C7), r * 0.75f, center)
                drawCircle(Color(0xFFEF476F), r * 0.50f, center)
                drawCircle(Color(0xFFFFCA3A), r * 0.22f, center)
            }
            lastHit?.let { h -> drawCircle(Color.Black, 6.dp.toPx(), Offset(h.x * size.width, h.y * size.height)) }
            drawCircle(Color.Magenta, 12.dp.toPx(), Offset(aimX * size.width, aimY * size.height), style = Stroke(3.dp.toPx()))
        }
        Spacer(Modifier.weight(1f))
        if (winner != null) {
            GameResultBanner("Победил Игрок $winner!", "Счёт: $score1 — $score2", config.mode, roomState,
                onRematch = { score1 = if (isDarts) 301 else 0; score2 = if (isDarts) 301 else 0; round = 1; turn = 1; winner = null },
                onExit = onExit
            )
        }
    }
}

// 39. BILLIARDS & 40. SOCCER POOL (2D Elastic Slingshot Physics Engine)
private data class PoolBall(val id: Int, val team: Int, var x: Float, var y: Float, var vx: Float, var vy: Float, val active: Boolean = true)

@Composable
fun BilliardsScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    SlingshotTableGame(GameId.BILLIARDS, isSoccer = false, config, prefs, onlineClient, roomState, resetSignal, onExit)
}

@Composable
fun SoccerPoolScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    SlingshotTableGame(GameId.SOCCER_POOL, isSoccer = true, config, prefs, onlineClient, roomState, resetSignal, onExit)
}

@Composable
private fun SlingshotTableGame(
    gameId: GameId,
    isSoccer: Boolean,
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    fun initBalls(): List<PoolBall> = if (isSoccer) {
        listOf(
            PoolBall(0, 0, 0.5f, 0.5f, 0f, 0f), // soccer ball
            PoolBall(1, 1, 0.3f, 0.75f, 0f, 0f), PoolBall(2, 1, 0.7f, 0.75f, 0f, 0f), PoolBall(3, 1, 0.5f, 0.86f, 0f, 0f),
            PoolBall(4, 2, 0.3f, 0.25f, 0f, 0f), PoolBall(5, 2, 0.7f, 0.25f, 0f, 0f), PoolBall(6, 2, 0.5f, 0.14f, 0f, 0f)
        )
    } else {
        listOf(
            PoolBall(0, 0, 0.5f, 0.78f, 0f, 0f), // cue ball
            PoolBall(1, 1, 0.5f, 0.32f, 0f, 0f), PoolBall(2, 1, 0.44f, 0.26f, 0f, 0f), PoolBall(3, 2, 0.56f, 0.26f, 0f, 0f),
            PoolBall(4, 2, 0.38f, 0.20f, 0f, 0f), PoolBall(8, 8, 0.50f, 0.20f, 0f, 0f), PoolBall(5, 1, 0.62f, 0.20f, 0f, 0f)
        )
    }

    var balls by remember(resetSignal) { mutableStateOf(initBalls()) }
    var turn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var score1 by remember(resetSignal) { mutableIntStateOf(0) }
    var score2 by remember(resetSignal) { mutableIntStateOf(0) }
    var dragStart by remember(resetSignal) { mutableStateOf<Offset?>(null) }
    var dragEnd by remember(resetSignal) { mutableStateOf<Offset?>(null) }
    var selectedId by remember(resetSignal) { mutableIntStateOf(0) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    val isMoving = balls.any { it.active && (abs(it.vx) > 0.0006f || abs(it.vy) > 0.0006f) }

    LaunchedEffect(resetSignal, winner) {
        var wasMoving = false
        while (winner == null) {
            delay(16L)
            val list = balls.map { it.copy() }.toMutableList()
            val anyMove = list.any { it.active && (abs(it.vx) > 0.0006f || abs(it.vy) > 0.0006f) }
            if (anyMove) {
                wasMoving = true
                val r = 0.042f
                for (b in list) {
                    if (!b.active) continue
                    b.x += b.vx; b.y += b.vy
                    b.vx *= 0.975f; b.vy *= 0.975f
                    if (abs(b.vx) < 0.0005f) b.vx = 0f
                    if (abs(b.vy) < 0.0005f) b.vy = 0f

                    if (isSoccer && b.team == 0 && b.x in 0.32f..0.68f) {
                        if (b.y < 0.04f) {
                            score1 += 1; balls = initBalls()
                            if (score1 >= 3) winner = 1
                            break
                        } else if (b.y > 0.96f) {
                            score2 += 1; balls = initBalls()
                            if (score2 >= 3) winner = 2
                            break
                        }
                    }

                    if (b.x < r) { b.x = r; b.vx = abs(b.vx) * 0.85f }
                    if (b.x > 1f - r) { b.x = 1f - r; b.vx = -abs(b.vx) * 0.85f }
                    if (b.y < r) { b.y = r; b.vy = abs(b.vy) * 0.85f }
                    if (b.y > 1f - r) { b.y = 1f - r; b.vy = -abs(b.vy) * 0.85f }
                }

                // Ball-ball elastic collisions
                for (i in list.indices) for (j in i + 1 until list.size) {
                    val a = list[i]; val b = list[j]
                    if (!a.active || !b.active) continue
                    val dx = b.x - a.x; val dy = b.y - a.y
                    val d = hypot(dx, dy).coerceAtLeast(0.0001f)
                    if (d < 2 * r) {
                        val nx = dx / d; val ny = dy / d
                        val overlap = (2 * r - d) * 0.5f
                        a.x -= nx * overlap; a.y -= ny * overlap
                        b.x += nx * overlap; b.y += ny * overlap
                        val dvx = a.vx - b.vx; val dvy = a.vy - b.vy
                        val dot = dvx * nx + dvy * ny
                        if (dot > 0) {
                            a.vx -= dot * nx; a.vy -= dot * ny
                            b.vx += dot * nx; b.vy += dot * ny
                        }
                    }
                }

                // Billiards corner pockets check
                if (!isSoccer) {
                    val pockets = listOf(0.05f to 0.05f, 0.95f to 0.05f, 0.05f to 0.95f, 0.95f to 0.95f)
                    for (idx in list.indices) {
                        val b = list[idx]
                        if (b.active && pockets.any { (px, py) -> hypot(b.x - px, b.y - py) < 0.068f }) {
                            if (b.team == 0) { list[idx] = b.copy(x = 0.5f, y = 0.78f, vx = 0f, vy = 0f) }
                            else if (b.team == 8) { winner = turn }
                            else {
                                list[idx] = b.copy(active = false)
                                if (turn == 1) score1++ else score2++
                            }
                        }
                    }
                }
                balls = list
            } else if (wasMoving) {
                wasMoving = false
                if (winner == null) turn = if (turn == 1) 2 else 1
            }
        }
    }

    LaunchedEffect(turn, isMoving, winner) {
        if (config.mode == PlayMode.SOLO && turn == 2 && !isMoving && winner == null) {
            delay(600)
            val botBall = if (isSoccer) balls.filter { it.team == 2 }.random() else balls.first { it.team == 0 }
            val target = if (isSoccer) balls.first { it.team == 0 } else balls.firstOrNull { it.active && it.team != 0 } ?: botBall
            val dx = target.x - botBall.x; val dy = target.y - botBall.y
            val len = hypot(dx, dy).coerceAtLeast(0.01f)
            balls = balls.map { if (it.id == botBall.id) it.copy(vx = (dx / len) * 0.032f, vy = (dy / len) * 0.032f) else it }
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                if (ev is OnlineEvent.OpponentMove) {
                    val id = ev.payload.optInt("id")
                    val vx = ev.payload.optDouble("vx").toFloat()
                    val vy = ev.payload.optDouble("vy").toFloat()
                    balls = balls.map { if (it.id == id) it.copy(vx = vx, vy = vy) else it }
                } else if (ev is OnlineEvent.RematchStart) {
                    balls = initBalls(); score1 = 0; score2 = 0; turn = ev.startingPlayer; winner = null
                }
            }
        }
    }

    val canShoot = !isMoving && winner == null && (config.mode == PlayMode.LOCAL_2P || (config.mode == PlayMode.SOLO && turn == 1) || (config.mode == PlayMode.ONLINE && turn == config.playerIndex))

    Column(Modifier.fillMaxSize().background(Color(0xFF137534)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScorePill("Игрок 1", score1, PlayBoxColors.Player2Color, turn == 1)
            ScorePill(if (config.mode == PlayMode.SOLO) "Бот" else "Игрок 2", score2, PlayBoxColors.Player1Color, turn == 2)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.weight(1f).aspectRatio(0.68f).clip(RoundedCornerShape(18.dp))
                .background(if (isSoccer) Color(0xFF75B73B) else Color(0xFF15803D))
                .border(6.dp, if (isSoccer) Color.White else Color(0xFF5C3A21), RoundedCornerShape(18.dp))
                .pointerInput(canShoot, balls) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (!canShoot) return@detectDragGestures
                            val nx = pos.x / size.width; val ny = pos.y / size.height
                            val candidate = balls.filter { it.active && (if (isSoccer) it.team == turn else it.team == 0) }
                                .minByOrNull { hypot(it.x - nx, it.y - ny) }
                            if (candidate != null && hypot(candidate.x - nx, candidate.y - ny) < 0.16f) {
                                selectedId = candidate.id
                                dragStart = Offset(candidate.x * size.width, candidate.y * size.height)
                                dragEnd = pos
                            }
                        },
                        onDrag = { ch, _ -> ch.consume(); if (dragStart != null) dragEnd = ch.position },
                        onDragEnd = {
                            val s = dragStart; val e = dragEnd
                            if (s != null && e != null) {
                                val vx = ((s.x - e.x) / size.width * 0.14f).coerceIn(-0.045f, 0.045f)
                                val vy = ((s.y - e.y) / size.height * 0.14f).coerceIn(-0.045f, 0.045f)
                                balls = balls.map { if (it.id == selectedId) it.copy(vx = vx, vy = vy) else it }
                                if (config.mode == PlayMode.ONLINE) {
                                    onlineClient.sendMove(JSONObject().put("id", selectedId).put("vx", vx.toDouble()).put("vy", vy.toDouble()))
                                }
                            }
                            dragStart = null; dragEnd = null
                        },
                        onDragCancel = { dragStart = null; dragEnd = null }
                    )
                }
        ) {
            if (isSoccer) {
                drawLine(Color.White, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 2.dp.toPx())
                drawRect(Color.White, Offset(size.width * 0.32f, 0f), Size(size.width * 0.36f, 10.dp.toPx()))
                drawRect(Color.White, Offset(size.width * 0.32f, size.height - 10.dp.toPx()), Size(size.width * 0.36f, 10.dp.toPx()))
            } else {
                listOf(0.05f to 0.05f, 0.95f to 0.05f, 0.05f to 0.95f, 0.95f to 0.95f).forEach { (px, py) ->
                    drawCircle(Color.Black, 16.dp.toPx(), Offset(px * size.width, py * size.height))
                }
            }
            if (dragStart != null && dragEnd != null) {
                drawLine(PlayBoxColors.BoltGold, dragStart!!, dragEnd!!, 4.dp.toPx())
            }
            balls.filter { it.active }.forEach { b ->
                val col = when (b.team) {
                    0 -> Color.White
                    1 -> PlayBoxColors.Player2Color
                    2 -> PlayBoxColors.Player1Color
                    else -> Color(0xFF111827)
                }
                val c = Offset(b.x * size.width, b.y * size.height)
                drawCircle(col, 0.042f * size.width, c)
                drawCircle(Color.Black, 0.042f * size.width, c, style = Stroke(2.dp.toPx()))
            }
        }
        if (winner != null) {
            GameResultBanner("Победил Игрок $winner!", "Счёт: $score1 — $score2", config.mode, roomState,
                onRematch = { balls = initBalls(); score1 = 0; score2 = 0; turn = 1; winner = null },
                onExit = onExit
            )
        }
    }
}

// 41. TAP MATCH (Triple tile match Zen/Mahjong)
private val TAP_ICONS = listOf("🫐", "🍉", "🍋", "🍓", "🥝", "🍇")

@Composable
fun TapMatchScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    var board by remember(seed) {
        val rng = Random(seed)
        val pool = (0 until 6).flatMap { t -> List(3) { t } }.shuffled(rng)
        mutableStateOf(pool) // 18 tiles (6 triples)
    }
    var tray by remember(seed) { mutableStateOf<List<Int>>(emptyList()) }
    var lost by remember(seed) { mutableStateOf(false) }

    val won = board.all { it < 0 } && tray.isEmpty()

    LaunchedEffect(won) {
        if (won) {
            prefs.recordWin(GameId.TAP_MATCH)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFE05D56)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Нажмите 3 одинаковые плитки, чтобы убрать их из лотка (макс. 7 мест).", color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))

        Column(Modifier.fillMaxWidth(0.9f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0..5) {
                        val idx = r * 6 + c
                        val t = board[idx]
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (t < 0) Color.Transparent else Color.White)
                                .clickable(enabled = t >= 0 && !lost) {
                                    board = board.mapIndexed { i, old -> if (i == idx) -1 else old }
                                    val nextTray = (tray + t).toMutableList()
                                    val matchType = nextTray.groupingBy { it }.eachCount().entries.firstOrNull { it.value >= 3 }?.key
                                    if (matchType != null) {
                                        var removed = 0
                                        nextTray.removeAll { if (it == matchType && removed < 3) { removed++; true } else false }
                                    }
                                    tray = nextTray
                                    if (nextTray.size >= 7) lost = true
                                },
                            contentAlignment = Alignment.Center
                        ) { if (t >= 0) Text(TAP_ICONS[t], fontSize = 24.sp) }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF401F1C)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0..6) {
                val item = tray.getOrNull(i)
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = if (item != null) 1f else 0.15f)), Alignment.Center) {
                    if (item != null) Text(TAP_ICONS[item], fontSize = 22.sp)
                }
            }
        }
        if (won || lost) {
            GameResultBanner(
                title = if (won) "Все тройки собраны!" else "Лоток переполнен!",
                subtitle = if (won) "Чистая победа!" else "Попробуйте заново",
                mode = config.mode,
                roomState = roomState,
                onRematch = { seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}
