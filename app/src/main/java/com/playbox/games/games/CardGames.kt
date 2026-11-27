package com.playbox.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.playbox.games.ui.components.PlayBoxButton
import com.playbox.games.ui.components.PlayBoxOutlinedButton
import com.playbox.games.ui.theme.PlayBoxColors
import com.playbox.games.ui.theme.bounceClick
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.random.Random

data class PlayingCard(val suit: Int, val rank: Int, val faceUp: Boolean = true) {
    val isRed: Boolean get() = suit == 1 || suit == 3
    val suitSymbol: String get() = listOf("♠", "♥", "♣", "♦")[suit]
    val rankLabel: String get() = when (rank) {
        1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> "$rank"
    }
    val label: String get() = "$rankLabel$suitSymbol"
}

private fun shuffledDeck(seed: Int): List<PlayingCard> =
    (0..3).flatMap { s -> (1..13).map { r -> PlayingCard(s, r, true) } }.shuffled(Random(seed))

@Composable
private fun CardChip(
    card: PlayingCard?,
    selected: Boolean = false,
    faceDown: Boolean = false,
    placeholder: String = "",
    modifier: Modifier = Modifier.size(width = 46.dp, height = 64.dp),
    onClick: () -> Unit = {}
) {
    val bg = when {
        card == null -> Color.White.copy(alpha = 0.20f)
        faceDown || !card.faceUp -> Color(0xFF1D3557)
        else -> Color(0xFFFFFDF8)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(
                if (selected) 3.dp else 1.5.dp,
                if (selected) PlayBoxColors.BoltGold else Color(0xFF1E293B),
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        when {
            card == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(placeholder, color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            }
            faceDown || !card.faceUp -> Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(
                    Brush.linearGradient(listOf(Color(0xFF1D3557), Color(0xFF457B9D)))
                ),
                Alignment.Center
            ) {
                Text("❖", color = Color(0xFFA8DADC), fontSize = 18.sp)
            }
            else -> {
                val ink = if (card.isRed) Color(0xFFD62828) else Color(0xFF1E293B)
                Text(
                    text = card.rankLabel,
                    color = ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                Text(
                    text = card.suitSymbol,
                    color = ink,
                    fontSize = 17.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                Text(
                    text = card.suitSymbol,
                    color = ink,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

// 1. SOLITAIRE (KLONDIKE)
@Composable
fun SolitaireScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    var tableau by remember(seed) {
        val deck = shuffledDeck(seed); var idx = 0
        mutableStateOf(List(7) { c -> List(c + 1) { r -> deck[idx++].copy(faceUp = r == c) } })
    }
    var stock by remember(seed) { mutableStateOf(shuffledDeck(seed).drop(28)) }
    var waste by remember(seed) { mutableStateOf<List<PlayingCard>>(emptyList()) }
    var foundations by remember(seed) { mutableStateOf(List(4) { emptyList<PlayingCard>() }) }
    var selectedSource by remember(seed) { mutableStateOf<Pair<String, Int>?>(null) }
    var oppFoundations by remember(seed) { mutableIntStateOf(0) }

    val totalFound = foundations.sumOf { it.size }
    val won = totalFound == 52

    fun syncOnline(newFound: List<List<PlayingCard>>) {
        val cnt = newFound.sumOf { it.size }
        if (cnt == 52) {
            prefs.recordWin(GameId.SOLITAIRE)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex)
        }
        if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("found", cnt))
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentState -> oppFoundations = ev.payload.optInt("found", oppFoundations)
                    is OnlineEvent.RematchStart -> seed = ev.seed
                    else -> Unit
                }
            }
        }
    }

    fun tryMoveToFoundation(card: PlayingCard): Boolean {
        val f = foundations[card.suit]
        val expected = (f.lastOrNull()?.rank ?: 0) + 1
        if (card.rank == expected) {
            val nextF = foundations.mapIndexed { i, list -> if (i == card.suit) list + card else list }
            foundations = nextF; syncOnline(nextF); return true
        }
        return false
    }

    fun tryMoveCardsToCol(cards: List<PlayingCard>, targetCol: Int): Boolean {
        val dest = tableau[targetCol]; val lead = cards.first()
        val ok = if (dest.isEmpty()) lead.rank == 13 else {
            val top = dest.last()
            top.faceUp && top.isRed != lead.isRed && top.rank == lead.rank + 1
        }
        if (!ok) return false
        tableau = tableau.mapIndexed { idx, col -> if (idx == targetCol) col + cards else col }
        return true
    }

    fun revealTops(cols: List<List<PlayingCard>>): List<List<PlayingCard>> =
        cols.map { col -> if (col.isNotEmpty() && !col.last().faceUp) col.dropLast(1) + col.last().copy(faceUp = true) else col }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF137534)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScorePill("В доме", totalFound, Color(0xFF0A4F22), true)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соп.", oppFoundations, PlayBoxColors.OnlinePurple, false)
            PlayBoxOutlinedButton(
                onClick = {
                    var changed = true
                    while (changed) {
                        changed = false
                        waste.lastOrNull()?.let { c -> if (tryMoveToFoundation(c)) { waste = waste.dropLast(1); changed = true } }
                        for (col in 0..6) {
                            val c = tableau[col].lastOrNull()
                            if (c != null && c.faceUp && tryMoveToFoundation(c)) {
                                tableau = revealTops(tableau.mapIndexed { i, l -> if (i == col) l.dropLast(1) else l })
                                changed = true
                            }
                        }
                    }
                },
                contentColor = Color.White,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("⚡ Автосбор", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CardChip(
                    card = stock.lastOrNull(), faceDown = stock.isNotEmpty(), placeholder = "↻",
                    onClick = {
                        selectedSource = null
                        if (stock.isNotEmpty()) { waste = waste + stock.first(); stock = stock.drop(1) }
                        else if (waste.isNotEmpty()) { stock = waste; waste = emptyList() }
                    }
                )
                CardChip(
                    card = waste.lastOrNull(), selected = selectedSource?.first == "waste",
                    onClick = {
                        val wCard = waste.lastOrNull() ?: return@CardChip
                        if (selectedSource?.first == "waste") {
                            if (tryMoveToFoundation(wCard)) waste = waste.dropLast(1)
                            selectedSource = null
                        } else selectedSource = "waste" to -1
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val suits = listOf("♠", "♥", "♣", "♦")
                for (s in 0..3) {
                    CardChip(
                        card = foundations[s].lastOrNull(), placeholder = suits[s],
                        onClick = {
                            val sel = selectedSource ?: return@CardChip
                            if (sel.first == "waste") {
                                val c = waste.lastOrNull()
                                if (c != null && c.suit == s && tryMoveToFoundation(c)) waste = waste.dropLast(1)
                            } else if (sel.first == "tab") {
                                val col = sel.second; val c = tableau[col].lastOrNull()
                                if (c != null && c.suit == s && tryMoveToFoundation(c)) {
                                    tableau = revealTops(tableau.mapIndexed { i, l -> if (i == col) l.dropLast(1) else l })
                                }
                            }
                            selectedSource = null
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (col in 0..6) {
                val pile = tableau[col]
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize().clickable {
                        val sel = selectedSource ?: return@clickable
                        if (pile.isEmpty()) {
                            if (sel.first == "waste") {
                                val c = waste.lastOrNull()
                                if (c != null && tryMoveCardsToCol(listOf(c), col)) waste = waste.dropLast(1)
                            } else if (sel.first == "tab" && sel.second != col) {
                                val src = tableau[sel.second]; val firstFaceUp = src.indexOfFirst { it.faceUp }
                                if (firstFaceUp >= 0 && tryMoveCardsToCol(src.subList(firstFaceUp, src.size), col)) {
                                    tableau = revealTops(tableau.mapIndexed { i, l -> if (i == sel.second) l.take(firstFaceUp) else l })
                                }
                            }
                            selectedSource = null
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy((-36).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (pile.isEmpty()) {
                        CardChip(null, placeholder = "K", modifier = Modifier.fillMaxWidth().height(56.dp))
                    } else {
                        pile.forEach { card ->
                            val isSelected = selectedSource?.first == "tab" && selectedSource?.second == col && card.faceUp
                            CardChip(
                                card = card, selected = isSelected, modifier = Modifier.fillMaxWidth().height(56.dp),
                                onClick = {
                                    if (!card.faceUp) return@CardChip
                                    val sel = selectedSource
                                    if (sel == null) selectedSource = "tab" to col
                                    else if (sel.first == "tab" && sel.second == col) {
                                        if (tryMoveToFoundation(pile.last())) tableau = revealTops(tableau.mapIndexed { i, l -> if (i == col) l.dropLast(1) else l })
                                        selectedSource = null
                                    } else if (sel.first == "waste") {
                                        val c = waste.lastOrNull()
                                        if (c != null && tryMoveCardsToCol(listOf(c), col)) waste = waste.dropLast(1)
                                        selectedSource = null
                                    } else if (sel.first == "tab") {
                                        val src = tableau[sel.second]
                                        val startIdx = src.indexOfFirst { it.faceUp }.coerceAtLeast(0)
                                        for (sIdx in startIdx until src.size) {
                                            if (tryMoveCardsToCol(src.subList(sIdx, src.size), col)) {
                                                tableau = revealTops(tableau.mapIndexed { i, l -> if (i == sel.second) l.take(sIdx) else l })
                                                break
                                            }
                                        }
                                        selectedSource = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (won) {
            GameResultBanner("Пасьянс сошёлся!", "Все 52 карты собраны", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}

// 2. FREECELL
@Composable
fun FreeCellScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    var cascades by remember(seed) {
        val deck = shuffledDeck(seed)
        val cols = MutableList(8) { mutableListOf<PlayingCard>() }
        deck.forEachIndexed { i, c -> cols[i % 8].add(c) }
        mutableStateOf(cols.map { it.toList() })
    }
    var freeCells by remember(seed) { mutableStateOf<List<PlayingCard?>>(List(4) { null }) }
    var foundations by remember(seed) { mutableStateOf(List(4) { emptyList<PlayingCard>() }) }
    var selected by remember(seed) { mutableStateOf<Pair<String, Int>?>(null) }
    var oppFound by remember(seed) { mutableIntStateOf(0) }

    val totalFound = foundations.sumOf { it.size }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentState -> oppFound = ev.payload.optInt("found", oppFound)
                    is OnlineEvent.RematchStart -> seed = ev.seed
                    else -> Unit
                }
            }
        }
    }

    fun sync(nf: List<List<PlayingCard>>) {
        val cnt = nf.sumOf { it.size }
        if (cnt == 52) {
            prefs.recordWin(GameId.FREECELL)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex)
        }
        if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("found", cnt))
    }

    fun getCard(src: Pair<String, Int>): PlayingCard? = when (src.first) {
        "free" -> freeCells[src.second]; "cas" -> cascades[src.second].lastOrNull(); else -> null
    }

    fun removeCard(src: Pair<String, Int>) {
        when (src.first) {
            "free" -> freeCells = freeCells.mapIndexed { i, c -> if (i == src.second) null else c }
            "cas" -> cascades = cascades.mapIndexed { i, l -> if (i == src.second) l.dropLast(1) else l }
        }
    }

    fun tryToFoundation(src: Pair<String, Int>, suit: Int): Boolean {
        val c = getCard(src) ?: return false
        if (c.suit != suit) return false
        val expected = (foundations[suit].lastOrNull()?.rank ?: 0) + 1
        if (c.rank != expected) return false
        removeCard(src)
        val nf = foundations.mapIndexed { i, l -> if (i == suit) l + c else l }
        foundations = nf; sync(nf); return true
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E4D75)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("В доме", totalFound, Color(0xFF082F49), true)
            if (config.mode == PlayMode.ONLINE) ScorePill("Соп.", oppFound, PlayBoxColors.OnlinePurple, false)
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0..3) {
                    CardChip(
                        card = freeCells[i], selected = selected == ("free" to i), placeholder = "ЯЧ",
                        modifier = Modifier.size(width = 40.dp, height = 56.dp),
                        onClick = {
                            val sel = selected
                            if (sel == null) { if (freeCells[i] != null) selected = "free" to i }
                            else if (sel == ("free" to i)) { getCard(sel)?.let { tryToFoundation(sel, it.suit) }; selected = null }
                            else if (freeCells[i] == null) {
                                getCard(sel)?.let { c -> removeCard(sel); freeCells = freeCells.mapIndexed { idx, old -> if (idx == i) c else old } }
                                selected = null
                            } else selected = "free" to i
                        }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val suits = listOf("♠", "♥", "♣", "♦")
                for (s in 0..3) {
                    CardChip(
                        card = foundations[s].lastOrNull(), placeholder = suits[s],
                        modifier = Modifier.size(width = 40.dp, height = 56.dp),
                        onClick = { selected?.let { tryToFoundation(it, s) }; selected = null }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (col in 0..7) {
                val pile = cascades[col]
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize().clickable {
                        val sel = selected ?: return@clickable
                        if (pile.isEmpty()) {
                            getCard(sel)?.let { c -> removeCard(sel); cascades = cascades.mapIndexed { i, l -> if (i == col) l + c else l } }
                            selected = null
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy((-36).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (pile.isEmpty()) CardChip(null, placeholder = "", modifier = Modifier.fillMaxWidth().height(54.dp))
                    else pile.forEachIndexed { idx, card ->
                        val isTop = idx == pile.lastIndex
                        CardChip(
                            card = card, selected = isTop && selected == ("cas" to col),
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            onClick = {
                                if (!isTop) return@CardChip
                                val sel = selected
                                if (sel == null) selected = "cas" to col
                                else if (sel == ("cas" to col)) { tryToFoundation(sel, card.suit); selected = null }
                                else {
                                    val moving = getCard(sel); val destTop = pile.last()
                                    if (moving != null && moving.isRed != destTop.isRed && moving.rank + 1 == destTop.rank) {
                                        removeCard(sel); cascades = cascades.mapIndexed { i, l -> if (i == col) l + moving else l }; selected = null
                                    } else selected = "cas" to col
                                }
                            }
                        )
                    }
                }
            }
        }

        if (totalFound == 52) {
            GameResultBanner("Победа в Свободной ячейке!", "Все карты в доме", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}

// 3. HEARTS
@Composable
fun HeartsScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    var hands by remember(seed) {
        val deck = shuffledDeck(seed)
        mutableStateOf(List(4) { p -> deck.subList(p * 13, (p + 1) * 13).sortedWith(compareBy({ it.suit }, { it.rank })) })
    }
    var trick by remember(seed) { mutableStateOf<List<Pair<Int, PlayingCard>>>(emptyList()) }
    var points by remember(seed) { mutableStateOf(IntArray(4)) }
    var turn by remember(seed) { mutableIntStateOf(0) }
    var heartsBroken by remember(seed) { mutableStateOf(false) }
    var oppPoints by remember(seed) { mutableIntStateOf(0) }

    val roundDone = hands.all { it.isEmpty() } && trick.isEmpty()

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentState -> oppPoints = ev.payload.optInt("pts", oppPoints)
                    is OnlineEvent.RematchStart -> seed = ev.seed
                    else -> Unit
                }
            }
        }
    }

    fun playCard(player: Int, card: PlayingCard) {
        val nextHands = hands.mapIndexed { i, h -> if (i == player) h - card else h }
        val nextTrick = trick + (player to card)
        if (card.suit == 1) heartsBroken = true
        hands = nextHands; trick = nextTrick

        if (nextTrick.size == 4) {
            val leadSuit = nextTrick.first().second.suit
            val winnerPair = nextTrick.filter { it.second.suit == leadSuit }
                .maxBy { if (it.second.rank == 1) 14 else it.second.rank }
            val trickPts: Int = nextTrick.sumOf { pair ->
                val c = pair.second
                val pts: Int = when { c.suit == 1 -> 1; c.suit == 0 && c.rank == 12 -> 13; else -> 0 }
                pts
            }
            val np = points.copyOf(); np[winnerPair.first] += trickPts
            points = np; trick = emptyList(); turn = winnerPair.first
            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("pts", np[0]))
            if (nextHands.all { it.isEmpty() } && np[0] == np.min()) prefs.recordWin(GameId.HEARTS)
        } else {
            turn = (player + 1) % 4
        }
    }

    LaunchedEffect(turn, trick, hands) {
        if (turn != 0 && !roundDone) {
            delay(450)
            val hand = hands[turn]
            if (hand.isNotEmpty()) {
                val leadSuit = trick.firstOrNull()?.second?.suit
                val valid = if (leadSuit != null && hand.any { it.suit == leadSuit }) hand.filter { it.suit == leadSuit }
                else if (trick.isEmpty() && !heartsBroken && hand.any { it.suit != 1 }) hand.filter { it.suit != 1 }
                else hand
                val chosen = valid.minBy { if (it.suit == 0 && it.rank == 12) 100 else if (it.rank == 1) 14 else it.rank }
                playCard(turn, chosen)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFB93737)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScorePill("Вы", points[0], Color(0xFF1D3557), turn == 0)
            ScorePill("Б1", points[1], Color(0xFF457B9D), turn == 1)
            ScorePill("Б2", points[2], Color(0xFF457B9D), turn == 2)
            ScorePill("Б3", points[3], Color(0xFF457B9D), turn == 3)
        }
        if (config.mode == PlayMode.ONLINE) {
            Spacer(Modifier.height(4.dp))
            Text("Очки онлайн-соперника: $oppPoints", color = Color(0xFFFFCA3A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF8C2525), modifier = Modifier.fillMaxWidth().height(155.dp)) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                if (trick.isEmpty()) {
                    Text(if (turn == 0) "Ваш ход во взятку" else "Ходит Бот $turn...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    trick.forEach { (p, c) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (p == 0) "Вы" else "Б$p", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            CardChip(c)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))

        val leadSuit = trick.firstOrNull()?.second?.suit
        val myHand = hands[0]
        val hasLead = leadSuit != null && myHand.any { it.suit == leadSuit }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            myHand.forEach { card ->
                val legal = turn == 0 && (!hasLead || card.suit == leadSuit) &&
                    !(trick.isEmpty() && !heartsBroken && myHand.any { it.suit != 1 } && card.suit == 1)
                CardChip(card = card, selected = legal, onClick = { if (legal) playCard(0, card) })
            }
        }

        if (roundDone) {
            val won = points[0] == points.min()
            GameResultBanner(
                title = if (won) "Вы выиграли раздачу!" else "Раздача завершена",
                subtitle = "Ваши штрафные очки: ${points[0]} (меньше — лучше)",
                mode = config.mode,
                roomState = roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}

// 4. COLOR CARDS (UNO-style with interactive Wild Color Picker!)
private data class UnoCard(val colorIdx: Int, val value: String)

private val UNO_COLORS = listOf(Color(0xFFEF476F), Color(0xFF118AB2), Color(0xFF06D6A0), Color(0xFFFFCA3A), Color(0xFF2B2D42))
private val UNO_COLOR_NAMES = listOf("Красный", "Синий", "Зелёный", "Жёлтый")

private fun buildUnoDeck(seed: Int): List<UnoCard> {
    val list = mutableListOf<UnoCard>()
    for (c in 0..3) {
        for (n in 1..9) { list.add(UnoCard(c, "$n")); list.add(UnoCard(c, "$n")) }
        list.add(UnoCard(c, "⊘")); list.add(UnoCard(c, "⇄")); list.add(UnoCard(c, "+2"))
    }
    repeat(4) { list.add(UnoCard(4, "W")); list.add(UnoCard(4, "+4")) }
    return list.shuffled(Random(seed))
}

@Composable
fun ColorCardsScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    var deck by remember(seed) { mutableStateOf(buildUnoDeck(seed).drop(15)) }
    var p1Hand by remember(seed) { mutableStateOf(buildUnoDeck(seed).take(7)) }
    var p2Hand by remember(seed) { mutableStateOf(buildUnoDeck(seed).subList(7, 14)) }
    var topCard by remember(seed) { mutableStateOf(buildUnoDeck(seed)[14].let { if (it.colorIdx == 4) UnoCard(0, "5") else it }) }
    var activeColor by remember(seed) { mutableIntStateOf(topCard.colorIdx) }
    var currentPlayer by remember(seed) { mutableIntStateOf(config.startingPlayer) }
    var winner by remember(seed) { mutableStateOf<Int?>(null) }
    var passTurnHide by remember(seed) { mutableStateOf(false) }
    var pendingWildCardIdx by remember(seed) { mutableStateOf<Int?>(null) }
    var actionBanner by remember(seed) { mutableStateOf("Сбросьте карту в цвет или номинал") }

    fun canPlay(c: UnoCard): Boolean = c.colorIdx == 4 || c.colorIdx == activeColor || c.value == topCard.value

    fun drawCards(targetPlayer: Int, count: Int) {
        val currentDeck = if (deck.size < count) buildUnoDeck(Random.nextInt(100000)) else deck
        val drawn = currentDeck.take(count); deck = currentDeck.drop(count)
        if (targetPlayer == 1) p1Hand = p1Hand + drawn else p2Hand = p2Hand + drawn
    }

    fun playCard(player: Int, idx: Int, chosenWildColor: Int, fromRemote: Boolean) {
        if (winner != null) return
        val hand = if (player == 1) p1Hand else p2Hand
        if (idx !in hand.indices) return
        val card = hand[idx]
        if (!canPlay(card)) return

        val nextHand = hand.toMutableList().apply { removeAt(idx) }
        if (player == 1) p1Hand = nextHand else p2Hand = nextHand
        topCard = card
        activeColor = if (card.colorIdx == 4) chosenWildColor else card.colorIdx

        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("action", "play").put("idx", idx).put("wild", activeColor))
        }

        if (nextHand.isEmpty()) {
            winner = player
            if (config.mode != PlayMode.ONLINE && player == 1) prefs.recordWin(GameId.COLOR_CARDS)
            if (config.mode == PlayMode.ONLINE && !fromRemote) {
                onlineClient.sendGameOver(player); if (player == config.playerIndex) prefs.recordWin(GameId.COLOR_CARDS)
            }
            return
        }

        val opp = if (player == 1) 2 else 1
        when (card.value) {
            "+2" -> { drawCards(opp, 2); actionBanner = "Игрок $player кинул +2! Ещё ход" }
            "+4" -> { drawCards(opp, 4); actionBanner = "+4 и цвет ${UNO_COLOR_NAMES[activeColor]}! Ещё ход" }
            "⊘", "⇄" -> { actionBanner = "Пропуск хода соперника! Ещё ход" }
            "W" -> {
                actionBanner = "Выбран цвет: ${UNO_COLOR_NAMES[activeColor]}"
                currentPlayer = opp
                if (config.mode == PlayMode.LOCAL_2P) passTurnHide = true
            }
            else -> {
                actionBanner = "Ход Игрока $opp"
                currentPlayer = opp
                if (config.mode == PlayMode.LOCAL_2P) passTurnHide = true
            }
        }
    }

    fun drawPass(player: Int, fromRemote: Boolean) {
        drawCards(player, 1)
        if (config.mode == PlayMode.ONLINE && !fromRemote) onlineClient.sendMove(JSONObject().put("action", "draw"))
        currentPlayer = if (player == 1) 2 else 1
        actionBanner = "Взята карта из колоды"
        if (config.mode == PlayMode.LOCAL_2P) passTurnHide = true
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentMove -> {
                        if (ev.payload.optString("action") == "draw") drawPass(ev.playerIndex, true)
                        else playCard(ev.playerIndex, ev.payload.optInt("idx", 0), ev.payload.optInt("wild", 0), true)
                    }
                    is OnlineEvent.RematchStart -> seed = ev.seed
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(currentPlayer, winner, p2Hand) {
        if (config.mode == PlayMode.SOLO && currentPlayer == 2 && winner == null) {
            delay(600)
            val idx = p2Hand.indexOfFirst { canPlay(it) }
            if (idx >= 0) {
                val bestColor = (0..3).maxBy { col -> p2Hand.count { it.colorIdx == col } }
                playCard(2, idx, bestColor, false)
            } else drawPass(2, false)
        }
    }

    val myViewer = when (config.mode) {
        PlayMode.ONLINE -> config.playerIndex; PlayMode.LOCAL_2P -> currentPlayer; PlayMode.SOLO -> 1
    }
    val myHand = if (myViewer == 1) p1Hand else p2Hand
    val oppCount = if (myViewer == 1) p2Hand.size else p1Hand.size
    val canAct = when (config.mode) {
        PlayMode.SOLO -> currentPlayer == 1 && winner == null
        PlayMode.LOCAL_2P -> !passTurnHide && winner == null
        PlayMode.ONLINE -> currentPlayer == config.playerIndex && winner == null
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF7D4932)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill(if (config.mode == PlayMode.SOLO) "Карт у бота" else "Карт у соперника", oppCount, UNO_COLORS[1], currentPlayer != myViewer)
        Spacer(Modifier.height(6.dp))
        Text(actionBanner, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))

        if (passTurnHide) {
            PlayBoxButton(
                onClick = { passTurnHide = false },
                containerColor = PlayBoxColors.BoltGold,
                contentColor = PlayBoxColors.DarkInk,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) { Text("Передайте телефон Игроку $currentPlayer → Открыть", fontWeight = FontWeight.ExtraBold, maxLines = 1) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 80.dp, height = 114.dp).clip(RoundedCornerShape(16.dp)).background(UNO_COLORS[activeColor]).border(4.dp, Color.White, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(topCard.value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp) }
                PlayBoxButton(
                    enabled = canAct,
                    onClick = { drawPass(myViewer, false) },
                    containerColor = PlayBoxColors.DarkInk,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) { Text("🃏 Взять карту", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
            }
        }

        Spacer(Modifier.weight(1f))

        if (!passTurnHide) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                myHand.forEachIndexed { idx, card ->
                    val playable = canAct && canPlay(card)
                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(UNO_COLORS[card.colorIdx])
                            .border(if (playable) 3.dp else 1.5.dp, if (playable) Color.White else Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .bounceClick(enabled = playable) {
                                if (card.colorIdx == 4) pendingWildCardIdx = idx
                                else playCard(myViewer, idx, card.colorIdx, false)
                            },
                        contentAlignment = Alignment.Center
                    ) { Text(card.value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
                }
            }
        }

        if (winner != null) {
            val title = when (config.mode) {
                PlayMode.SOLO -> if (winner == 1) "Вы победили!" else "Бот победил!"
                PlayMode.ONLINE -> if (winner == config.playerIndex) "Победа!" else "Соперник сбросил все карты!"
                PlayMode.LOCAL_2P -> "Победил Игрок $winner!"
            }
            GameResultBanner(title, "Рука пуста!", config.mode, roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }

    val wildIdx = pendingWildCardIdx
    if (wildIdx != null) {
        AlertDialog(
            onDismissRequest = { pendingWildCardIdx = null },
            containerColor = PlayBoxColors.HeaderSurface,
            title = { Text("Выберите цвет", fontWeight = FontWeight.ExtraBold) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (c in 0..3) {
                        Box(
                            Modifier.size(54.dp).clip(CircleShape).background(UNO_COLORS[c]).border(3.dp, PlayBoxColors.DarkInk, CircleShape)
                                .bounceClick { pendingWildCardIdx = null; playCard(myViewer, wildIdx, c, false) }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}
