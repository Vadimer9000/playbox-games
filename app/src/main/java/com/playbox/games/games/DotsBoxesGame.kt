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
import androidx.compose.ui.graphics.StrokeCap
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

private const val DB_DOTS = 5 // 5x5 dots = 4x4 boxes
private const val DB_BOXES = DB_DOTS - 1

@Composable
fun DotsBoxesScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // hEdges: 5 rows x 4 cols; vEdges: 4 rows x 5 cols; boxes: 4x4
    var hEdges by remember(resetSignal) { mutableStateOf(IntArray(DB_DOTS * DB_BOXES)) }
    var vEdges by remember(resetSignal) { mutableStateOf(IntArray(DB_BOXES * DB_DOTS)) }
    var boxes by remember(resetSignal) { mutableStateOf(IntArray(DB_BOXES * DB_BOXES)) }
    var currentPlayer by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }

    fun reset(starter: Int = 1) {
        hEdges = IntArray(DB_DOTS * DB_BOXES)
        vEdges = IntArray(DB_BOXES * DB_DOTS)
        boxes = IntArray(DB_BOXES * DB_BOXES)
        currentPlayer = starter
        winner = null
    }

    fun placeEdge(isHorizontal: Boolean, r: Int, c: Int, player: Int, fromRemote: Boolean) {
        if (winner != null) return
        val nextH = hEdges.copyOf()
        val nextV = vEdges.copyOf()
        if (isHorizontal) {
            val idx = r * DB_BOXES + c
            if (r !in 0 until DB_DOTS || c !in 0 until DB_BOXES || nextH[idx] != 0) return
            nextH[idx] = player
        } else {
            val idx = r * DB_DOTS + c
            if (r !in 0 until DB_BOXES || c !in 0 until DB_DOTS || nextV[idx] != 0) return
            nextV[idx] = player
        }

        val nextBoxes = boxes.copyOf()
        var closedAny = false
        for (br in 0 until DB_BOXES) {
            for (bc in 0 until DB_BOXES) {
                val bIdx = br * DB_BOXES + bc
                if (nextBoxes[bIdx] == 0) {
                    val top = nextH[br * DB_BOXES + bc] != 0
                    val bottom = nextH[(br + 1) * DB_BOXES + bc] != 0
                    val left = nextV[br * DB_DOTS + bc] != 0
                    val right = nextV[br * DB_DOTS + bc + 1] != 0
                    if (top && bottom && left && right) {
                        nextBoxes[bIdx] = player
                        closedAny = true
                    }
                }
            }
        }

        hEdges = nextH
        vEdges = nextV
        boxes = nextBoxes
        if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("h", isHorizontal).put("r", r).put("c", c))
        }

        if (nextBoxes.none { it == 0 }) {
            val p1 = nextBoxes.count { it == 1 }
            val p2 = nextBoxes.count { it == 2 }
            val w = when {
                p1 > p2 -> 1
                p2 > p1 -> 2
                else -> 0
            }
            winner = w
            if (config.mode != PlayMode.ONLINE && w == 1) prefs.recordWin(GameId.DOTS_BOXES)
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendGameOver(w)
                if (w == config.playerIndex) prefs.recordWin(GameId.DOTS_BOXES)
            }
        } else if (!closedAny) {
            currentPlayer = if (player == 1) 2 else 1
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentMove -> {
                        placeEdge(
                            isHorizontal = event.payload.optBoolean("h"),
                            r = event.payload.optInt("r"),
                            c = event.payload.optInt("c"),
                            player = event.playerIndex,
                            fromRemote = true
                        )
                    }
                    is OnlineEvent.RematchStart -> reset(event.startingPlayer)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(currentPlayer, winner, hEdges, vEdges) {
        if (config.mode == PlayMode.SOLO && currentPlayer == 2 && winner == null) {
            delay(360)
            val move = chooseDotsBoxesMove(hEdges, vEdges, config.difficulty)
            if (move != null) {
                placeEdge(move.first, move.second, move.third, 2, fromRemote = false)
            }
        }
    }

    val p1Count = boxes.count { it == 1 }
    val p2Count = boxes.count { it == 2 }
    val canTap = when (config.mode) {
        PlayMode.SOLO -> currentPlayer == 1 && winner == null
        PlayMode.LOCAL_2P -> winner == null
        PlayMode.ONLINE -> currentPlayer == config.playerIndex && winner == null && (roomState?.bothConnected == true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ScorePill("Игрок 1", p1Count, PlayBoxColors.Player1Color, currentPlayer == 1 && winner == null)
            ScorePill(if (config.mode == PlayMode.SOLO) "Бот" else "Игрок 2", p2Count, PlayBoxColors.Player2Color, currentPlayer == 2 && winner == null)
        }

        Spacer(Modifier.height(18.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(24.dp))
                .padding(24.dp)
                .pointerInput(canTap, hEdges, vEdges) {
                    detectTapGestures { pos ->
                        if (!canTap) return@detectTapGestures
                        val stepX = size.width / DB_BOXES
                        val stepY = size.height / DB_BOXES
                        val colFloat = pos.x / stepX
                        val rowFloat = pos.y / stepY

                        var bestDist = Float.MAX_VALUE
                        var bestMove: Triple<Boolean, Int, Int>? = null

                        // Check horizontal edges
                        for (r in 0 until DB_DOTS) {
                            for (c in 0 until DB_BOXES) {
                                if (hEdges[r * DB_BOXES + c] != 0) continue
                                val dist = abs(rowFloat - r) + abs(colFloat - (c + 0.5f)) * 0.45f
                                if (abs(rowFloat - r) < 0.38f && colFloat in (c.toFloat() - 0.1f)..(c + 1.1f) && dist < bestDist) {
                                    bestDist = dist
                                    bestMove = Triple(true, r, c)
                                }
                            }
                        }
                        // Check vertical edges
                        for (r in 0 until DB_BOXES) {
                            for (c in 0 until DB_DOTS) {
                                if (vEdges[r * DB_DOTS + c] != 0) continue
                                val dist = abs(colFloat - c) + abs(rowFloat - (r + 0.5f)) * 0.45f
                                if (abs(colFloat - c) < 0.38f && rowFloat in (r.toFloat() - 0.1f)..(r + 1.1f) && dist < bestDist) {
                                    bestDist = dist
                                    bestMove = Triple(false, r, c)
                                }
                            }
                        }
                        bestMove?.let { placeEdge(it.first, it.second, it.third, currentPlayer, false) }
                    }
                }
        ) {
            val step = size.minDimension / DB_BOXES
            // Boxes
            for (r in 0 until DB_BOXES) {
                for (c in 0 until DB_BOXES) {
                    val owner = boxes[r * DB_BOXES + c]
                    if (owner != 0) {
                        val color = if (owner == 1) PlayBoxColors.Player1Color.copy(alpha = 0.28f) else PlayBoxColors.Player2Color.copy(alpha = 0.28f)
                        drawRect(color, Offset(c * step + 4.dp.toPx(), r * step + 4.dp.toPx()), Size(step - 8.dp.toPx(), step - 8.dp.toPx()))
                    }
                }
            }
            // Guide grid & placed edges
            for (r in 0 until DB_DOTS) {
                for (c in 0 until DB_BOXES) {
                    val owner = hEdges[r * DB_BOXES + c]
                    val col = when (owner) {
                        1 -> PlayBoxColors.Player1Color
                        2 -> PlayBoxColors.Player2Color
                        else -> Color(0xFFE5E7EB)
                    }
                    drawLine(
                        col,
                        Offset(c * step, r * step),
                        Offset((c + 1) * step, r * step),
                        strokeWidth = (if (owner == 0) 3.dp else 7.dp).toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            for (r in 0 until DB_BOXES) {
                for (c in 0 until DB_DOTS) {
                    val owner = vEdges[r * DB_DOTS + c]
                    val col = when (owner) {
                        1 -> PlayBoxColors.Player1Color
                        2 -> PlayBoxColors.Player2Color
                        else -> Color(0xFFE5E7EB)
                    }
                    drawLine(
                        col,
                        Offset(c * step, r * step),
                        Offset(c * step, (r + 1) * step),
                        strokeWidth = (if (owner == 0) 3.dp else 7.dp).toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            // Dots
            for (r in 0 until DB_DOTS) {
                for (c in 0 until DB_DOTS) {
                    drawCircle(PlayBoxColors.DarkInk, 6.5.dp.toPx(), Offset(c * step, r * step))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (winner != null) {
            val title = when {
                winner == 0 -> "Ничья $p1Count : $p2Count!"
                config.mode == PlayMode.SOLO -> if (winner == 1) "Вы победили!" else "Бот победил!"
                config.mode == PlayMode.ONLINE -> if (winner == config.playerIndex) "Вы победили!" else "Соперник победил!"
                else -> "Победил Игрок $winner!"
            }
            GameResultBanner(
                title = title,
                subtitle = "Квадраты: $p1Count — $p2Count",
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

@Composable
fun ScorePill(label: String, score: Int, color: Color, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) color else PlayBoxColors.HeaderSurface,
        shadowElevation = if (active) 4.dp else 1.dp
    ) {
        Text(
            text = "$label: $score",
            color = if (active) Color.White else PlayBoxColors.DarkInk,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

private fun chooseDotsBoxesMove(hEdges: IntArray, vEdges: IntArray, difficulty: BotDifficulty): Triple<Boolean, Int, Int>? {
    val all = mutableListOf<Triple<Boolean, Int, Int>>()
    for (r in 0 until DB_DOTS) for (c in 0 until DB_BOXES) if (hEdges[r * DB_BOXES + c] == 0) all.add(Triple(true, r, c))
    for (r in 0 until DB_BOXES) for (c in 0 until DB_DOTS) if (vEdges[r * DB_DOTS + c] == 0) all.add(Triple(false, r, c))
    if (all.isEmpty()) return null
    if (difficulty == BotDifficulty.EASY && Random.nextFloat() < 0.6f) return all.random()

    fun boxEdgesCount(br: Int, bc: Int, h: IntArray, v: IntArray): Int {
        var cnt = 0
        if (h[br * DB_BOXES + bc] != 0) cnt++
        if (h[(br + 1) * DB_BOXES + bc] != 0) cnt++
        if (v[br * DB_DOTS + bc] != 0) cnt++
        if (v[br * DB_DOTS + bc + 1] != 0) cnt++
        return cnt
    }

    // 1. Complete any 3-sided box
    for (m in all) {
        val nh = hEdges.copyOf()
        val nv = vEdges.copyOf()
        if (m.first) nh[m.second * DB_BOXES + m.third] = 2 else nv[m.second * DB_DOTS + m.third] = 2
        val completes = (0 until DB_BOXES).any { br ->
            (0 until DB_BOXES).any { bc ->
                boxEdgesCount(br, bc, hEdges, vEdges) == 3 && boxEdgesCount(br, bc, nh, nv) == 4
            }
        }
        if (completes) return m
    }

    // 2. Avoid creating 3-sided box
    val safe = all.filter { m ->
        val nh = hEdges.copyOf()
        val nv = vEdges.copyOf()
        if (m.first) nh[m.second * DB_BOXES + m.third] = 2 else nv[m.second * DB_DOTS + m.third] = 2
        (0 until DB_BOXES).none { br ->
            (0 until DB_BOXES).any { bc ->
                boxEdgesCount(br, bc, hEdges, vEdges) == 2 && boxEdgesCount(br, bc, nh, nv) == 3
            }
        }
    }
    return if (safe.isNotEmpty()) safe.random() else all.random()
}
