package com.playbox.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import com.playbox.games.ui.components.PlayBoxButton
import com.playbox.games.ui.components.PlayBoxOutlinedButton
import com.playbox.games.ui.theme.PlayBoxColors
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private const val BS_SIZE = 10
private val FLEET_LENGTHS = listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1)
private val BS_COLS_LABELS = listOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К")

private enum class BsPhase {
    PLACE_P1, PASS_TO_P2, PLACE_P2, WAIT_ONLINE_OPP, PASS_TURN_LOCAL, BATTLE, GAME_OVER
}

@Composable
fun BattleshipScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var p1Ships by remember(resetSignal) { mutableStateOf(generateRandomFleet()) }
    var p2Ships by remember(resetSignal) { mutableStateOf(generateRandomFleet()) }
    var p1Shots by remember(resetSignal) { mutableStateOf(IntArray(BS_SIZE * BS_SIZE)) }
    var p2Shots by remember(resetSignal) { mutableStateOf(IntArray(BS_SIZE * BS_SIZE)) }

    var phase by remember(resetSignal) { mutableStateOf(BsPhase.PLACE_P1) }
    var battleTurn by remember(resetSignal) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(resetSignal) { mutableStateOf<Int?>(null) }
    var onlineOppReady by remember(resetSignal) { mutableStateOf(false) }
    var onlineLocalReady by remember(resetSignal) { mutableStateOf(false) }
    var awaitingOnlineReply by remember(resetSignal) { mutableStateOf(false) }
    var lastShotMsg by remember(resetSignal) { mutableStateOf("Расставьте флот и нажмите «В бой!»") }

    fun reset(starter: Int = 1) {
        p1Ships = generateRandomFleet(); p2Ships = generateRandomFleet()
        p1Shots = IntArray(BS_SIZE * BS_SIZE); p2Shots = IntArray(BS_SIZE * BS_SIZE)
        phase = BsPhase.PLACE_P1; battleTurn = starter; winner = null
        onlineOppReady = false; onlineLocalReady = false; awaitingOnlineReply = false
        lastShotMsg = "Расставьте флот и нажмите «В бой!»"
    }

    fun myOnlineShips(): IntArray = if (config.playerIndex == 1) p1Ships else p2Ships
    fun myOnlineShots(): IntArray = if (config.playerIndex == 1) p1Shots else p2Shots
    fun oppOnlineShotsOnMe(): IntArray = if (config.playerIndex == 1) p2Shots else p1Shots

    fun applyLocalShot(attacker: Int, idx: Int) {
        if (phase != BsPhase.BATTLE || winner != null) return
        val targetShips = if (attacker == 1) p2Ships else p1Ships
        val shotGrid = (if (attacker == 1) p1Shots else p2Shots).copyOf()
        if (shotGrid[idx] != 0) return

        val shipId = targetShips[idx]
        val hit = shipId > 0
        shotGrid[idx] = if (hit) 2 else 1

        if (hit) {
            if (isShipSunk(targetShips, shotGrid, shipId)) {
                markSunkSurrounding(targetShips, shotGrid, shipId)
                lastShotMsg = "🔥 Корабль потоплен!"
            } else lastShotMsg = "💥 Попадание! Стреляйте ещё"
        } else lastShotMsg = "💦 Мимо!"

        if (attacker == 1) p1Shots = shotGrid else p2Shots = shotGrid
        if (prefs.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        val allSunk = targetShips.indices.all { i -> targetShips[i] == 0 || shotGrid[i] >= 2 }
        if (allSunk) {
            winner = attacker; phase = BsPhase.GAME_OVER
            if (attacker == 1) prefs.recordWin(GameId.BATTLESHIP)
        } else if (!hit) {
            battleTurn = if (attacker == 1) 2 else 1
            if (config.mode == PlayMode.LOCAL_2P) phase = BsPhase.PASS_TURN_LOCAL
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentMove -> {
                        when (event.payload.optString("action")) {
                            "ready" -> {
                                onlineOppReady = true
                                if (onlineLocalReady) {
                                    phase = BsPhase.BATTLE
                                    lastShotMsg = if (battleTurn == config.playerIndex) "Ваш выстрел!" else "Выстрел соперника..."
                                }
                            }
                            "shot" -> {
                                val idx = event.payload.optInt("idx", -1)
                                if (idx in 0 until BS_SIZE * BS_SIZE) {
                                    val myShips = myOnlineShips()
                                    val incoming = oppOnlineShotsOnMe().copyOf()
                                    val shipId = myShips[idx]
                                    val hit = shipId > 0
                                    incoming[idx] = if (hit) 2 else 1
                                    val sunkCells = mutableListOf<Int>()
                                    if (hit && isShipSunk(myShips, incoming, shipId)) {
                                        for (i in myShips.indices) if (myShips[i] == shipId) { incoming[i] = 3; sunkCells.add(i) }
                                    }
                                    if (config.playerIndex == 1) p2Shots = incoming else p1Shots = incoming
                                    val allSunk = myShips.indices.all { i -> myShips[i] == 0 || incoming[i] >= 2 }
                                    onlineClient.sendMove(
                                        JSONObject().put("action", "shot_result").put("idx", idx).put("hit", hit)
                                            .put("sunkCells", JSONArray(sunkCells)).put("allSunk", allSunk)
                                    )
                                    if (allSunk) { winner = event.playerIndex; phase = BsPhase.GAME_OVER }
                                    else if (!hit) { battleTurn = config.playerIndex; lastShotMsg = "Соперник промахнулся — ваш ход!" }
                                    else lastShotMsg = "Соперник попал!"
                                }
                            }
                            "shot_result" -> {
                                awaitingOnlineReply = false
                                val idx = event.payload.optInt("idx", -1)
                                val hit = event.payload.optBoolean("hit", false)
                                val allSunk = event.payload.optBoolean("allSunk", false)
                                val sunkArr = event.payload.optJSONArray("sunkCells")
                                val shots = myOnlineShots().copyOf()
                                if (idx in shots.indices) shots[idx] = if (hit) 2 else 1
                                if (sunkArr != null && sunkArr.length() > 0) {
                                    val cells = (0 until sunkArr.length()).map { sunkArr.getInt(it) }
                                    cells.forEach { if (it in shots.indices) shots[it] = 3 }
                                    markSurroundingForSunkList(shots, cells)
                                    lastShotMsg = "🔥 Корабль потоплен!"
                                } else lastShotMsg = if (hit) "💥 Попадание! Стреляйте ещё" else "💦 Мимо!"
                                if (config.playerIndex == 1) p1Shots = shots else p2Shots = shots
                                if (allSunk) {
                                    winner = config.playerIndex; phase = BsPhase.GAME_OVER
                                    prefs.recordWin(GameId.BATTLESHIP); onlineClient.sendGameOver(config.playerIndex)
                                } else if (!hit) battleTurn = if (config.playerIndex == 1) 2 else 1
                            }
                        }
                    }
                    is OnlineEvent.RematchStart -> reset(event.startingPlayer)
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(phase, battleTurn, p2Shots) {
        if (config.mode == PlayMode.SOLO && phase == BsPhase.BATTLE && battleTurn == 2 && winner == null) {
            delay(460)
            val targetIdx = chooseBattleshipShot(p2Shots, config.difficulty)
            if (targetIdx >= 0) applyLocalShot(2, targetIdx)
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(14.dp), color = PlayBoxColors.HeaderSurface, shadowElevation = 2.dp) {
            Text(
                text = lastShotMsg,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PlayBoxColors.DarkInk,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        when (phase) {
            BsPhase.PLACE_P1, BsPhase.PLACE_P2 -> {
                val placingP2 = phase == BsPhase.PLACE_P2
                val fleet = if (placingP2) p2Ships else p1Ships
                Text(if (placingP2) "Расстановка: Игрок 2" else "Расстановка флота", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                BattleshipGrid(ships = fleet, shots = IntArray(BS_SIZE * BS_SIZE), showShips = true, showLabels = true, onCellTap = {})
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PlayBoxOutlinedButton(
                        onClick = { if (placingP2) p2Ships = generateRandomFleet() else p1Ships = generateRandomFleet() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) { Text("🎲 Перемешать", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
                    PlayBoxButton(
                        onClick = {
                            when (config.mode) {
                                PlayMode.SOLO -> { p2Ships = generateRandomFleet(); phase = BsPhase.BATTLE; lastShotMsg = "Ваш выстрел!" }
                                PlayMode.LOCAL_2P -> {
                                    if (!placingP2) phase = BsPhase.PASS_TO_P2
                                    else { phase = BsPhase.BATTLE; lastShotMsg = "Ход Игрока 1" }
                                }
                                PlayMode.ONLINE -> {
                                    if (config.playerIndex == 2) p2Ships = p1Ships
                                    onlineLocalReady = true
                                    onlineClient.sendMove(JSONObject().put("action", "ready"))
                                    if (onlineOppReady) {
                                        phase = BsPhase.BATTLE
                                        lastShotMsg = if (battleTurn == config.playerIndex) "Ваш выстрел!" else "Выстрел соперника..."
                                    } else {
                                        phase = BsPhase.WAIT_ONLINE_OPP
                                        lastShotMsg = "Ждём расстановку соперника..."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) { Text("В бой!", fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false) }
                }
            }

            BsPhase.PASS_TO_P2, BsPhase.PASS_TURN_LOCAL -> {
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(22.dp), color = PlayBoxColors.HeaderSurface, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (phase == BsPhase.PASS_TO_P2) "Передайте телефон Игроку 2" else "Передайте телефон Игроку $battleTurn",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Нажмите кнопку ниже, когда будете готовы", color = PlayBoxColors.MutedInk)
                        Spacer(Modifier.height(18.dp))
                        PlayBoxButton(
                            onClick = {
                                phase = if (phase == BsPhase.PASS_TO_P2) BsPhase.PLACE_P2 else BsPhase.BATTLE
                                lastShotMsg = if (phase == BsPhase.BATTLE) "Ход Игрока $battleTurn" else "Расстановка: Игрок 2"
                            },
                            containerColor = PlayBoxColors.Player2Color
                        ) { Text("Я готов", fontWeight = FontWeight.Bold, maxLines = 1) }
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            BsPhase.WAIT_ONLINE_OPP -> {
                Spacer(Modifier.weight(1f))
                Text("Вы готовы! Ожидаем флот соперника...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
            }

            BsPhase.BATTLE, BsPhase.GAME_OVER -> {
                val myTurn = when (config.mode) {
                    PlayMode.SOLO -> battleTurn == 1 && phase == BsPhase.BATTLE
                    PlayMode.LOCAL_2P -> phase == BsPhase.BATTLE
                    PlayMode.ONLINE -> battleTurn == config.playerIndex && phase == BsPhase.BATTLE && !awaitingOnlineReply
                }
                val attackShots = when (config.mode) {
                    PlayMode.ONLINE -> myOnlineShots()
                    PlayMode.LOCAL_2P -> if (battleTurn == 1) p1Shots else p2Shots
                    PlayMode.SOLO -> p1Shots
                }
                val myShipsDisplay = when (config.mode) {
                    PlayMode.ONLINE -> myOnlineShips()
                    PlayMode.LOCAL_2P -> if (battleTurn == 1) p1Ships else p2Ships
                    PlayMode.SOLO -> p1Ships
                }
                val incomingShotsDisplay = when (config.mode) {
                    PlayMode.ONLINE -> oppOnlineShotsOnMe()
                    PlayMode.LOCAL_2P -> if (battleTurn == 1) p2Shots else p1Shots
                    PlayMode.SOLO -> p2Shots
                }

                Text("Поле соперника (${if (myTurn) "Стреляйте!" else "Ждите..."})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                BattleshipGrid(
                    ships = IntArray(BS_SIZE * BS_SIZE),
                    shots = attackShots,
                    showShips = false,
                    showLabels = true,
                    modifier = Modifier.fillMaxWidth(0.88f),
                    onCellTap = { idx ->
                        if (!myTurn || attackShots[idx] != 0) return@BattleshipGrid
                        if (config.mode == PlayMode.ONLINE) {
                            awaitingOnlineReply = true
                            onlineClient.sendMove(JSONObject().put("action", "shot").put("idx", idx))
                        } else applyLocalShot(battleTurn, idx)
                    }
                )

                Spacer(Modifier.height(8.dp))
                Text("Ваш флот", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PlayBoxColors.MutedInk)
                Spacer(Modifier.height(4.dp))
                BattleshipGrid(
                    ships = myShipsDisplay,
                    shots = incomingShotsDisplay,
                    showShips = true,
                    showLabels = false,
                    modifier = Modifier.fillMaxWidth(0.42f),
                    onCellTap = {}
                )
            }
        }

        if (winner != null) {
            val title = when (config.mode) {
                PlayMode.SOLO -> if (winner == 1) "Флот врага потоплен!" else "Ваш флот потоплен!"
                PlayMode.ONLINE -> if (winner == config.playerIndex) "Победа!" else "Поражение!"
                PlayMode.LOCAL_2P -> "Победил Игрок $winner!"
            }
            GameResultBanner(
                title = title,
                subtitle = "Морское сражение завершено",
                mode = config.mode,
                roomState = roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else reset(1) },
                onExit = onExit
            )
        }
    }
}

@Composable
private fun BattleshipGrid(
    ships: IntArray,
    shots: IntArray,
    showShips: Boolean,
    showLabels: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(0.92f),
    onCellTap: (Int) -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (showLabels) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, bottom = 2.dp)) {
                BS_COLS_LABELS.forEach { l ->
                    Box(Modifier.weight(1f), Alignment.Center) {
                        Text(l, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PlayBoxColors.MutedInk)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            if (showLabels) {
                Column(Modifier.width(18.dp).aspectRatio(0.1f), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                    for (r in 1..BS_SIZE) Text("$r", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PlayBoxColors.MutedInk)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E607A))
                    .border(3.dp, PlayBoxColors.DarkInk, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (r in 0 until BS_SIZE) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (c in 0 until BS_SIZE) {
                            val idx = r * BS_SIZE + c
                            val shot = shots[idx]
                            val hasShip = showShips && ships[idx] > 0
                            val bg = when (shot) {
                                3 -> Color(0xFF991B1B)
                                2 -> Color(0xFFEF476F)
                                1 -> Color(0xFF64748B)
                                else -> if (hasShip) Color(0xFF334155) else Color(0xFF287C9C)
                            }
                            Box(
                                modifier = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(bg).clickable { onCellTap(idx) },
                                contentAlignment = Alignment.Center
                            ) {
                                when (shot) {
                                    1 -> Text("•", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    2, 3 -> Text("✕", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun generateRandomFleet(): IntArray {
    val grid = IntArray(BS_SIZE * BS_SIZE)
    FLEET_LENGTHS.forEachIndexed { index, len ->
        val shipId = index + 1
        var placed = false
        while (!placed) {
            val horiz = Random.nextBoolean()
            val r = Random.nextInt(BS_SIZE); val c = Random.nextInt(BS_SIZE)
            if (canPlaceShip(grid, r, c, len, horiz)) {
                for (k in 0 until len) {
                    val nr = if (horiz) r else r + k; val nc = if (horiz) c + k else c
                    grid[nr * BS_SIZE + nc] = shipId
                }
                placed = true
            }
        }
    }
    return grid
}

private fun canPlaceShip(grid: IntArray, r: Int, c: Int, len: Int, horiz: Boolean): Boolean {
    for (k in 0 until len) {
        val nr = if (horiz) r else r + k; val nc = if (horiz) c + k else c
        if (nr !in 0 until BS_SIZE || nc !in 0 until BS_SIZE) return false
        for (dr in -1..1) for (dc in -1..1) {
            val ar = nr + dr; val ac = nc + dc
            if (ar in 0 until BS_SIZE && ac in 0 until BS_SIZE && grid[ar * BS_SIZE + ac] != 0) return false
        }
    }
    return true
}

private fun isShipSunk(ships: IntArray, shots: IntArray, shipId: Int): Boolean =
    ships.indices.none { i -> ships[i] == shipId && shots[i] < 2 }

private fun markSunkSurrounding(ships: IntArray, shots: IntArray, shipId: Int) {
    val cells = ships.indices.filter { ships[it] == shipId }
    cells.forEach { shots[it] = 3 }
    markSurroundingForSunkList(shots, cells)
}

private fun markSurroundingForSunkList(shots: IntArray, cells: List<Int>) {
    for (idx in cells) {
        val r = idx / BS_SIZE; val c = idx % BS_SIZE
        for (dr in -1..1) for (dc in -1..1) {
            val nr = r + dr; val nc = c + dc
            if (nr in 0 until BS_SIZE && nc in 0 until BS_SIZE) {
                val nIdx = nr * BS_SIZE + nc
                if (shots[nIdx] == 0) shots[nIdx] = 1
            }
        }
    }
}

private fun chooseBattleshipShot(shots: IntArray, difficulty: BotDifficulty): Int {
    val unknown = shots.indices.filter { shots[it] == 0 }
    if (unknown.isEmpty()) return -1
    if (difficulty != BotDifficulty.EASY) {
        val hits = shots.indices.filter { shots[it] == 2 }
        for (h in hits) {
            val r = h / BS_SIZE; val c = h % BS_SIZE
            val neighbors = listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)
                .filter { (nr, nc) -> nr in 0 until BS_SIZE && nc in 0 until BS_SIZE }
                .map { (nr, nc) -> nr * BS_SIZE + nc }
                .filter { shots[it] == 0 }
            if (neighbors.isNotEmpty()) return neighbors.random()
        }
        val checkerboard = unknown.filter { ((it / BS_SIZE) + (it % BS_SIZE)) % 2 == 0 }
        if (checkerboard.isNotEmpty() && difficulty == BotDifficulty.HARD) return checkerboard.random()
    }
    return unknown.random()
}
