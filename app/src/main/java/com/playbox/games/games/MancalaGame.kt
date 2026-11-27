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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

// Indices: 0..5 = P1 pits (bottom left->right), 6 = P1 store,
//          7..12 = P2 pits (top right->left), 13 = P2 store
private fun initialMancalaPits(): IntArray = IntArray(14) { i ->
    if (i == 6 || i == 13) 0 else 4
}

@Composable
fun MancalaScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var pits by remember(resetSignal) { mutableStateOf(initialMancalaPits()) }
    var currentPlayer by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var infoMsg by remember(resetSignal) { mutableStateOf<String?>(null) }

    fun reset(starter: Int = 1) {
        pits = initialMancalaPits()
        currentPlayer = starter
        winner = null
        infoMsg = null
    }

    fun sowPit(pitIdx: Int, player: Int, fromRemote: Boolean) {
        if (winner != null) return
        val ownRange = if (player == 1) 0..5 else 7..12
        if (pitIdx !in ownRange || pits[pitIdx] <= 0) return

        val (next, extraTurn) = simulateMancalaMove(pits, pitIdx, player)
        pits = next
        if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("pit", pitIdx))
        }

        val p1Empty = (0..5).all { next[it] == 0 }
        val p2Empty = (7..12).all { next[it] == 0 }
        if (p1Empty || p2Empty) {
            val finalized = next.copyOf()
            for (i in 0..5) { finalized[6] += finalized[i]; finalized[i] = 0 }
            for (i in 7..12) { finalized[13] += finalized[i]; finalized[i] = 0 }
            pits = finalized
            val w = when {
                finalized[6] > finalized[13] -> 1
                finalized[13] > finalized[6] -> 2
                else -> 0
            }
            winner = w
            infoMsg = null
            if (config.mode != PlayMode.ONLINE && w == 1) prefs.recordWin(GameId.MANCALA)
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendGameOver(w)
                if (w == config.playerIndex) prefs.recordWin(GameId.MANCALA)
            }
        } else if (extraTurn) {
            infoMsg = "Камень в калахе — дополнительный ход!"
        } else {
            infoMsg = null
            currentPlayer = if (player == 1) 2 else 1
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentMove -> {
                        val pit = event.payload.optInt("pit", -1)
                        sowPit(pit, event.playerIndex, fromRemote = true)
                    }
                    is OnlineEvent.RematchStart -> reset(event.startingPlayer)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(currentPlayer, winner, pits) {
        if (config.mode == PlayMode.SOLO && currentPlayer == 2 && winner == null) {
            delay(480)
            val botPit = chooseMancalaMove(pits, config.difficulty)
            if (botPit >= 0) sowPit(botPit, 2, false)
        }
    }

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
            ScorePill("Игрок 1", pits[6], PlayBoxColors.Player1Color, currentPlayer == 1 && winner == null)
            ScorePill(if (config.mode == PlayMode.SOLO) "Бот" else "Игрок 2", pits[13], PlayBoxColors.Player2Color, currentPlayer == 2 && winner == null)
        }
        if (infoMsg != null) {
            Spacer(Modifier.height(6.dp))
            Text(infoMsg!!, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PlayBoxColors.OnlinePurple)
        }
        Spacer(Modifier.height(24.dp))

        // Wooden Mancala board
        Surface(
            modifier = Modifier.fillMaxWidth().height(230.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFE7B66B),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .border(4.dp, PlayBoxColors.DarkInk, RoundedCornerShape(28.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // P2 Store (left)
                MancalaStore(count = pits[13], color = PlayBoxColors.Player2Color, modifier = Modifier.weight(1f).fillMaxHeight())

                // 6 columns of pits (top: P2 12 down to 7; bottom: P1 0 up to 5)
                Column(
                    modifier = Modifier.weight(4.5f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (pit in 12 downTo 7) {
                            val active = canTap && currentPlayer == 2 && pits[pit] > 0
                            MancalaPit(
                                count = pits[pit],
                                color = PlayBoxColors.Player2Color,
                                active = active,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { if (active) sowPit(pit, 2, false) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (pit in 0..5) {
                            val active = canTap && currentPlayer == 1 && pits[pit] > 0
                            MancalaPit(
                                count = pits[pit],
                                color = PlayBoxColors.Player1Color,
                                active = active,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { if (active) sowPit(pit, 1, false) }
                            )
                        }
                    }
                }

                // P1 Store (right)
                MancalaStore(count = pits[6], color = PlayBoxColors.Player1Color, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Нижний ряд — Игрок 1 (ход вправо). Верхний — Игрок 2 (ход влево).",
            fontSize = 12.sp,
            color = PlayBoxColors.MutedInk
        )

        Spacer(Modifier.weight(1f))
        if (winner != null) {
            val title = when {
                winner == 0 -> "Ничья ${pits[6]} : ${pits[13]}!"
                config.mode == PlayMode.SOLO -> if (winner == 1) "Вы победили!" else "Бот победил!"
                config.mode == PlayMode.ONLINE -> if (winner == config.playerIndex) "Вы победили!" else "Соперник победил!"
                else -> "Победил Игрок $winner!"
            }
            GameResultBanner(
                title = title,
                subtitle = "Камни: ${pits[6]} — ${pits[13]}",
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
private fun MancalaStore(count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.88f))
            .border(2.dp, PlayBoxColors.DarkInk, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("$count", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    }
}

@Composable
private fun MancalaPit(
    count: Int,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = if (active) 0.95f else 0.72f))
            .border(if (active) 3.dp else 1.5.dp, if (active) Color.White else PlayBoxColors.DarkInk, CircleShape)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val dots = count.coerceAtMost(8)
            for (i in 0 until dots) {
                val angle = (i * 2.0 * Math.PI / dots.coerceAtLeast(1))
                val r = if (dots == 1) 0f else size.minDimension * 0.25f
                val cx = center.x + (r * kotlin.math.cos(angle)).toFloat()
                val cy = center.y + (r * kotlin.math.sin(angle)).toFloat()
                drawCircle(Color(0xFFFFF8EE).copy(alpha = 0.65f), 3.dp.toPx(), Offset(cx, cy))
            }
        }
        Text("$count", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}

private fun simulateMancalaMove(pits: IntArray, pitIdx: Int, player: Int): Pair<IntArray, Boolean> {
    val next = pits.copyOf()
    var stones = next[pitIdx]
    next[pitIdx] = 0
    var cursor = pitIdx
    val oppStore = if (player == 1) 13 else 6
    val myStore = if (player == 1) 6 else 13
    val myRange = if (player == 1) 0..5 else 7..12

    while (stones > 0) {
        cursor = (cursor + 1) % 14
        if (cursor == oppStore) continue
        next[cursor] += 1
        stones -= 1
    }

    if (cursor == myStore) return next to true

    if (cursor in myRange && next[cursor] == 1) {
        val opposite = 12 - cursor
        if (next[opposite] > 0) {
            next[myStore] += next[opposite] + 1
            next[opposite] = 0
            next[cursor] = 0
        }
    }
    return next to false
}

private fun chooseMancalaMove(pits: IntArray, difficulty: BotDifficulty): Int {
    val valid = (7..12).filter { pits[it] > 0 }
    if (valid.isEmpty()) return -1
    if (difficulty == BotDifficulty.EASY && Random.nextFloat() < 0.6f) return valid.random()

    var bestPit = valid.first()
    var bestScore = Int.MIN_VALUE
    for (pit in valid) {
        val (after, extra) = simulateMancalaMove(pits, pit, 2)
        val score = (after[13] - after[6]) * 3 + (if (extra) 8 else 0)
        if (score > bestScore) {
            bestScore = score
            bestPit = pit
        }
    }
    return bestPit
}
