package com.playbox.games.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.playbox.games.ui.theme.PlayBoxColors
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.random.Random

private const val MS_SIZE = 9
private const val MS_MINES = 10

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MinesweeperScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var mines by remember(resetSignal) { mutableStateOf<BooleanArray?>(null) }
    var revealed by remember(resetSignal) { mutableStateOf(BooleanArray(MS_SIZE * MS_SIZE)) }
    var flagged by remember(resetSignal) { mutableStateOf(BooleanArray(MS_SIZE * MS_SIZE)) }
    var flagMode by remember(resetSignal) { mutableStateOf(false) }
    var elapsedSec by remember(resetSignal) { mutableIntStateOf(0) }
    var finishedState by remember(resetSignal) { mutableStateOf<String?>(null) } // "WIN" | "LOSE"
    var oppProgress by remember(resetSignal) { mutableIntStateOf(0) }
    var currentSeed by remember(resetSignal) { mutableIntStateOf(config.seed) }

    fun reset(seed: Int) {
        currentSeed = seed
        mines = null
        revealed = BooleanArray(MS_SIZE * MS_SIZE)
        flagged = BooleanArray(MS_SIZE * MS_SIZE)
        flagMode = false
        elapsedSec = 0
        finishedState = null
        oppProgress = 0
    }

    LaunchedEffect(mines, finishedState) {
        if (mines != null && finishedState == null) {
            while (finishedState == null) {
                delay(1000)
                elapsedSec += 1
            }
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentState -> {
                        oppProgress = event.payload.optInt("pct", oppProgress)
                        if (event.payload.optBoolean("won", false) && finishedState == null) {
                            finishedState = "LOSE"
                        }
                    }
                    is OnlineEvent.RematchStart -> reset(event.seed)
                    else -> Unit
                }
            }
        }
    }

    fun toggleFlag(idx: Int) {
        if (finishedState != null || revealed[idx]) return
        val next = flagged.copyOf()
        next[idx] = !next[idx]
        flagged = next
        if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun revealCell(idx: Int) {
        if (finishedState != null || flagged[idx] || revealed[idx]) return
        val activeMines = mines ?: generateMines(idx, currentSeed).also { mines = it }

        if (activeMines[idx]) {
            val allRev = BooleanArray(MS_SIZE * MS_SIZE) { i -> revealed[i] || activeMines[i] }
            revealed = allRev
            finishedState = "LOSE"
            if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        val nextRev = revealed.copyOf()
        floodReveal(idx, activeMines, nextRev)
        revealed = nextRev
        if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        val safeTotal = MS_SIZE * MS_SIZE - MS_MINES
        val safeOpen = nextRev.indices.count { nextRev[it] && !activeMines[it] }
        val pct = (safeOpen * 100) / safeTotal

        if (safeOpen == safeTotal) {
            finishedState = "WIN"
            prefs.recordWin(GameId.MINESWEEPER)
            prefs.updateBestTime(GameId.MINESWEEPER, elapsedSec)
            if (config.mode == PlayMode.ONLINE) {
                onlineClient.sendState(JSONObject().put("pct", 100).put("won", true))
                onlineClient.sendGameOver(config.playerIndex)
            }
        } else if (config.mode == PlayMode.ONLINE) {
            onlineClient.sendState(JSONObject().put("pct", pct).put("won", false))
        }
    }

    val flagsLeft = MS_MINES - flagged.count { it }

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("💣", flagsLeft, Color(0xFF38424D), true)
            ScorePill("⏱", elapsedSec, PlayBoxColors.Player2Color, false)
            if (config.mode == PlayMode.ONLINE) {
                ScorePill("Соп.", oppProgress, PlayBoxColors.OnlinePurple, false)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = !flagMode,
                onClick = { flagMode = false },
                label = { Text("⛏️ Открывать", fontWeight = FontWeight.Bold) }
            )
            FilterChip(
                selected = flagMode,
                onClick = { flagMode = true },
                label = { Text("🚩 Флажок", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2B333C))
                .border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(16.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (r in 0 until MS_SIZE) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (c in 0 until MS_SIZE) {
                        val idx = r * MS_SIZE + c
                        val isOpen = revealed[idx]
                        val isFlag = flagged[idx]
                        val isMine = mines?.get(idx) == true
                        val count = if (isOpen && !isMine && mines != null) countAdjMines(idx, mines!!) else 0

                        val bg = when {
                            isOpen && isMine -> Color(0xFFEF476F)
                            isOpen -> Color(0xFF3A4450)
                            else -> Color(0xFFE2E8F0)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .combinedClickable(
                                    onClick = { if (flagMode) toggleFlag(idx) else revealCell(idx) },
                                    onLongClick = { toggleFlag(idx) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isOpen && isMine -> Text("💣", fontSize = 16.sp)
                                isOpen && count > 0 -> Text(
                                    "$count",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = mineNumberColor(count)
                                )
                                !isOpen && isFlag -> Text("🚩", fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (finishedState != null) {
            val won = finishedState == "WIN"
            GameResultBanner(
                title = if (won) "Поле разминировано!" else if (config.mode == PlayMode.ONLINE && oppProgress >= 100) "Соперник опередил!" else "Бум! Мина!",
                subtitle = "Время: ${elapsedSec} сек.",
                mode = config.mode,
                roomState = roomState,
                onRematch = {
                    if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch()
                    else reset(Random.nextInt(100000))
                },
                onExit = onExit
            )
        }
    }
}

private fun mineNumberColor(n: Int): Color = when (n) {
    1 -> Color(0xFF38BDF8)
    2 -> Color(0xFF4ADE80)
    3 -> Color(0xFFF87171)
    4 -> Color(0xFFA78BFA)
    else -> Color(0xFFFBBF24)
}

private fun generateMines(safeIdx: Int, seed: Int): BooleanArray {
    val arr = BooleanArray(MS_SIZE * MS_SIZE)
    val rng = Random(seed + safeIdx)
    val candidates = (0 until MS_SIZE * MS_SIZE).filter { it != safeIdx }.shuffled(rng)
    for (i in 0 until MS_MINES) arr[candidates[i]] = true
    return arr
}

private fun countAdjMines(idx: Int, mines: BooleanArray): Int {
    val r = idx / MS_SIZE
    val c = idx % MS_SIZE
    var cnt = 0
    for (dr in -1..1) for (dc in -1..1) {
        if (dr == 0 && dc == 0) continue
        val nr = r + dr
        val nc = c + dc
        if (nr in 0 until MS_SIZE && nc in 0 until MS_SIZE && mines[nr * MS_SIZE + nc]) cnt++
    }
    return cnt
}

private fun floodReveal(start: Int, mines: BooleanArray, revealed: BooleanArray) {
    val queue = ArrayDeque<Int>()
    queue.add(start)
    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        if (revealed[cur]) continue
        revealed[cur] = true
        if (countAdjMines(cur, mines) == 0) {
            val r = cur / MS_SIZE
            val c = cur % MS_SIZE
            for (dr in -1..1) for (dc in -1..1) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until MS_SIZE && nc in 0 until MS_SIZE) {
                    val ni = nr * MS_SIZE + nc
                    if (!revealed[ni] && !mines[ni]) queue.add(ni)
                }
            }
        }
    }
}
