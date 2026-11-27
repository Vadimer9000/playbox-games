package com.playbox.games.games

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// 28. HANGMAN
private val HANGMAN_WORDS = listOf(
    "КОСМОНАВТ", "БИБЛИОТЕКА", "ПРАВИТЕЛЬ", "КОМПЬЮТЕР", "ПРИКЛЮЧЕНИЕ",
    "ШОКОЛАД", "ТЕЛЕФОН", "АВТОМОБИЛЬ", "ПРОГРАММА", "ДИНОЗАВР",
    "ВЕРТОЛЕТ", "КРОКОДИЛ", "МАТЕМАТИКА", "УНИВЕРСИТЕТ", "МОРОЖЕНОЕ",
    "ПАРАШЮТ", "АКВАРИУМ", "ПИРАМИДА", "СВЕТОФОР", "ПУТЕШЕСТВИЕ"
)

private val RU_ALPHA = listOf(
    listOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "Й", "К"),
    listOf("Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х"),
    listOf("Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э", "Ю", "Я")
)

@Composable
fun HangmanScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    var customWordInput by remember(resetSignal) { mutableStateOf("") }
    var secretWord by remember(seed) {
        mutableStateOf(if (config.mode == PlayMode.LOCAL_2P) "" else HANGMAN_WORDS[abs(seed) % HANGMAN_WORDS.size])
    }
    var guessed by remember(seed, secretWord) { mutableStateOf(emptySet<Char>()) }
    val wrongCount = guessed.count { it !in secretWord }
    val won = secretWord.isNotEmpty() && secretWord.all { it in guessed }
    val lost = secretWord.isNotEmpty() && wrongCount >= 6

    LaunchedEffect(won) {
        if (won) {
            prefs.recordWin(GameId.HANGMAN)
            if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(config.playerIndex)
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { if (it is OnlineEvent.RematchStart) seed = it.seed }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF6A570)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (secretWord.isEmpty()) {
            Spacer(Modifier.weight(1f))
            Text("Игрок 1, загадайте слово для Игрока 2:", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = customWordInput,
                onValueChange = { customWordInput = it.uppercase().filter { c -> c in 'А'..'Я' }.take(12) },
                label = { Text("Слово на русском (3–12 букв)") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Button(enabled = customWordInput.length >= 3, onClick = { secretWord = customWordInput; customWordInput = "" }) {
                Text("Загадать и передать телефон")
            }
            Spacer(Modifier.weight(1f))
        } else {
            ScorePill("Ошибки", wrongCount, Color(0xFFD62828), true)
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.size(160.dp)) {
                val stroke = 5.dp.toPx()
                val gallows = Color(0xFFD62828)
                val body = Color(0xFF3A1F0D)
                drawLine(gallows, Offset(20f, size.height - 10f), Offset(size.width * 0.7f, size.height - 10f), stroke)
                drawLine(gallows, Offset(size.width * 0.25f, size.height - 10f), Offset(size.width * 0.25f, 16f), stroke)
                drawLine(gallows, Offset(size.width * 0.25f, 16f), Offset(size.width * 0.65f, 16f), stroke)
                drawLine(gallows, Offset(size.width * 0.65f, 16f), Offset(size.width * 0.65f, size.height * 0.24f), stroke)
                if (wrongCount >= 1) drawCircle(body, 22f, Offset(size.width * 0.65f, size.height * 0.34f), style = Stroke(stroke))
                if (wrongCount >= 2) drawLine(body, Offset(size.width * 0.65f, size.height * 0.44f), Offset(size.width * 0.65f, size.height * 0.68f), stroke)
                if (wrongCount >= 3) drawLine(body, Offset(size.width * 0.65f, size.height * 0.50f), Offset(size.width * 0.50f, size.height * 0.60f), stroke)
                if (wrongCount >= 4) drawLine(body, Offset(size.width * 0.65f, size.height * 0.50f), Offset(size.width * 0.80f, size.height * 0.60f), stroke)
                if (wrongCount >= 5) drawLine(body, Offset(size.width * 0.65f, size.height * 0.68f), Offset(size.width * 0.52f, size.height * 0.85f), stroke)
                if (wrongCount >= 6) drawLine(body, Offset(size.width * 0.65f, size.height * 0.68f), Offset(size.width * 0.78f, size.height * 0.85f), stroke)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = secretWord.map { if (it in guessed || lost) it else '_' }.joinToString(" "),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PlayBoxColors.DarkInk
            )
            Spacer(Modifier.weight(1f))
            if (won || lost) {
                GameResultBanner(
                    title = if (won) "Слово отгадано!" else "Попытки закончились!",
                    subtitle = "Загаданное слово: $secretWord",
                    mode = config.mode,
                    roomState = roomState,
                    onRematch = {
                        if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch()
                        else if (config.mode == PlayMode.LOCAL_2P) secretWord = ""
                        else seed = Random.nextInt(100000)
                    },
                    onExit = onExit
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    RU_ALPHA.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            row.forEach { l ->
                                val ch = l[0]
                                val used = ch in guessed
                                Box(
                                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(8.dp))
                                        .background(when { !used -> Color.White; ch in secretWord -> Color(0xFF40916C); else -> Color(0xFF6B7280) })
                                        .clickable(enabled = !used) { guessed = guessed + ch },
                                    contentAlignment = Alignment.Center
                                ) { Text(l, fontWeight = FontWeight.ExtraBold, color = if (used) Color.White else PlayBoxColors.DarkInk) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 29. WORD SEARCH WHEEL (Letter Wheel Word Connect)
private data class WheelLevel(val letters: List<Char>, val targets: List<String>)

private val WHEEL_LEVELS = listOf(
    WheelLevel(listOf('С', 'Т', 'О', 'Л', 'А'), listOf("СТОЛ", "СОТ", "ЛАТ", "ТОЛ", "СТАЛ")),
    WheelLevel(listOf('К', 'О', 'Р', 'А', 'Б'), listOf("КОРА", "РОК", "РАК", "БАК", "БРАК")),
    WheelLevel(listOf('М', 'О', 'Р', 'Е', 'К'), listOf("МОРЕ", "РОМ", "КОМ", "КРЕМ", "МОРК"))
)

@Composable
fun WordSearchWheelScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var lvl by remember(resetSignal) { mutableIntStateOf(abs(config.seed) % WHEEL_LEVELS.size) }
    val puz = WHEEL_LEVELS[lvl % WHEEL_LEVELS.size]
    var found by remember(lvl, resetSignal) { mutableStateOf(emptySet<String>()) }
    var pickedIndices by remember(lvl, resetSignal) { mutableStateOf<List<Int>>(emptyList()) }
    var msg by remember(lvl, resetSignal) { mutableStateOf("Собирайте слова нажимая буквы в колесе") }

    val currentWord = pickedIndices.map { puz.letters[it] }.joinToString("")
    val won = found.size == puz.targets.size

    LaunchedEffect(won) { if (won) prefs.recordWin(GameId.WORD_SEARCH) }

    Column(Modifier.fillMaxSize().background(Color(0xFF7B5780)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Найдено слов", found.size, PlayBoxColors.BoltGold, true)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            puz.targets.forEach { w ->
                val ok = w in found
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (ok) Color(0xFF40916C) else Color(0xFF5D3F61)).padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (ok) w else "•".repeat(w.length), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(if (currentWord.isEmpty()) msg else currentWord, color = PlayBoxColors.BoltGold, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Spacer(Modifier.weight(1f))

        // Letter Wheel
        Box(Modifier.size(220.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            puz.letters.forEachIndexed { idx, ch ->
                val angle = (idx * 2.0 * Math.PI / puz.letters.size) - Math.PI / 2
                val dx = (72 * cos(angle)).dp
                val dy = (72 * sin(angle)).dp
                val sel = idx in pickedIndices
                Box(
                    Modifier
                        .padding(start = if (dx > 0.dp) dx * 2 else 0.dp, end = if (dx < 0.dp) (-dx) * 2 else 0.dp, top = if (dy > 0.dp) dy * 2 else 0.dp, bottom = if (dy < 0.dp) (-dy) * 2 else 0.dp)
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (sel) PlayBoxColors.BoltGold else Color.White)
                        .clickable(enabled = !won && !sel) { pickedIndices = pickedIndices + idx },
                    contentAlignment = Alignment.Center
                ) { Text("$ch", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = PlayBoxColors.DarkInk) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pickedIndices = emptyList() }) { Text("Сброс", color = Color.White) }
            Button(enabled = pickedIndices.size >= 2, onClick = {
                if (currentWord in puz.targets && currentWord !in found) {
                    found = found + currentWord
                    msg = "Слово $currentWord найдено!"
                } else {
                    msg = "Нет такого слова"
                }
                pickedIndices = emptyList()
            }) { Text("Проверить") }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner("Все слова найдены!", "Отличный словарный запас", config.mode, roomState, onRematch = { lvl += 1 }, onExit = onExit)
        }
    }
}

// 30. SOUND MEMORY (Simon Says with real audio tones!)
@Composable
fun SoundMemoryScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    val toneGen = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 75) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { runCatching { toneGen?.release() } } }

    val toneCodes = listOf(ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_4, ToneGenerator.TONE_DTMF_7, ToneGenerator.TONE_DTMF_9)
    val quadColors = listOf(Color(0xFF38BDF8), Color(0xFF70E000), Color(0xFFEF476F), Color(0xFFFFCA3A))

    var sequence by remember(resetSignal) { mutableStateOf(listOf(Random.nextInt(4))) }
    var userStep by remember(resetSignal) { mutableIntStateOf(0) }
    var litIdx by remember(resetSignal) { mutableIntStateOf(-1) }
    var playingBack by remember(resetSignal) { mutableStateOf(true) }
    var lost by remember(resetSignal) { mutableStateOf(false) }

    fun beep(idx: Int) {
        runCatching { toneGen?.startTone(toneCodes[idx], 240) }
    }

    LaunchedEffect(sequence, resetSignal) {
        if (!lost) {
            playingBack = true
            delay(350)
            for (q in sequence) {
                litIdx = q
                beep(q)
                delay(380)
                litIdx = -1
                delay(180)
            }
            playingBack = false
            userStep = 0
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF2E4D63)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScorePill("Раунд", sequence.size, PlayBoxColors.BoltGold, true)
        Spacer(Modifier.height(10.dp))
        Text(if (playingBack) "Слушайте и запоминайте..." else "Ваш черёд! Повторите последовательность", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))

        Column(Modifier.size(270.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (r in 0..1) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (c in 0..1) {
                        val idx = r * 2 + c
                        val isLit = litIdx == idx
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(28.dp))
                                .background(if (isLit) Color.White else quadColors[idx])
                                .border(4.dp, Color(0xFF1E293B), RoundedCornerShape(28.dp))
                                .clickable(enabled = !playingBack && !lost) {
                                    beep(idx)
                                    if (sequence[userStep] == idx) {
                                        if (userStep + 1 == sequence.size) {
                                            prefs.updateBestScore(GameId.SOUND_MEMORY, sequence.size)
                                            if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("score", sequence.size))
                                            sequence = sequence + Random.nextInt(4)
                                        } else {
                                            userStep += 1
                                        }
                                    } else {
                                        lost = true
                                    }
                                }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (lost) {
            GameResultBanner("Ошибка!", "Достигнут раунд ${sequence.size}", config.mode, roomState,
                onRematch = { sequence = listOf(Random.nextInt(4)); lost = false; userStep = 0 },
                onExit = onExit
            )
        }
    }
}

// 31. MEMORY CARDS (Pairs matching — Solo, 2P Local, Online)
private val MEMORY_ICONS = listOf("🌸", "🦊", "🍉", "🚀", "💎", "🎵", "⚡", "👑")

@Composable
fun MemoryCardsScreen(config: LaunchConfig, prefs: PlayerPrefs, onlineClient: OnlineClient, roomState: OnlineRoomState?, resetSignal: Int, onExit: () -> Unit) {
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    val cards = remember(seed) { (MEMORY_ICONS + MEMORY_ICONS).shuffled(Random(seed)) }
    var matched by remember(seed) { mutableStateOf(BooleanArray(16)) }
    var flipped by remember(seed) { mutableStateOf<List<Int>>(emptyList()) }
    var turn by remember(seed) { mutableIntStateOf(config.startingPlayer) }
    var p1Pairs by remember(seed) { mutableIntStateOf(0) }
    var p2Pairs by remember(seed) { mutableIntStateOf(0) }
    var locked by remember(seed) { mutableStateOf(false) }

    val won = matched.all { it }

    fun flipCard(idx: Int, player: Int, fromRemote: Boolean) {
        if (locked || matched[idx] || idx in flipped) return
        val nf = flipped + idx
        flipped = nf
        if (config.mode == PlayMode.ONLINE && !fromRemote) {
            onlineClient.sendMove(JSONObject().put("idx", idx))
        }
        if (nf.size == 2) {
            locked = true
        }
    }

    LaunchedEffect(flipped) {
        if (flipped.size == 2) {
            delay(650)
            val a = flipped[0]; val b = flipped[1]
            if (cards[a] == cards[b]) {
                matched = matched.copyOf().apply { this[a] = true; this[b] = true }
                if (turn == 1) p1Pairs++ else p2Pairs++
                if (matched.all { it }) {
                    val w = if (p1Pairs > p2Pairs) 1 else if (p2Pairs > p1Pairs) 2 else 0
                    if (config.mode != PlayMode.ONLINE && w == 1) prefs.recordWin(GameId.MEMORY_CARDS)
                    if (config.mode == PlayMode.ONLINE) onlineClient.sendGameOver(w)
                }
            } else if (config.mode != PlayMode.SOLO) {
                turn = if (turn == 1) 2 else 1
            }
            flipped = emptyList()
            locked = false
        }
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { ev ->
                when (ev) {
                    is OnlineEvent.OpponentMove -> flipCard(ev.payload.optInt("idx"), ev.playerIndex, true)
                    is OnlineEvent.RematchStart -> seed = ev.seed
                    else -> Unit
                }
            }
        }
    }

    val canTap = !locked && !won && (config.mode != PlayMode.ONLINE || turn == config.playerIndex)

    Column(Modifier.fillMaxSize().background(Color(0xFF4E3B42)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScorePill("Игрок 1", p1Pairs, PlayBoxColors.Player1Color, turn == 1)
            if (config.mode != PlayMode.SOLO) ScorePill("Игрок 2", p2Pairs, PlayBoxColors.Player2Color, turn == 2)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(0.92f).aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..3) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0..3) {
                        val idx = r * 4 + c
                        val open = matched[idx] || idx in flipped
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(14.dp))
                                .background(if (open) Color.White else Color(0xFFFFCA3A))
                                .border(3.dp, Color.White, RoundedCornerShape(14.dp))
                                .clickable(enabled = canTap && !open) { flipCard(idx, turn, false) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (open) Text(cards[idx], fontSize = 30.sp) else Text("?", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (won) {
            GameResultBanner(
                title = if (config.mode == PlayMode.SOLO) "Все пары найдены!" else if (p1Pairs > p2Pairs) "Победил Игрок 1!" else if (p2Pairs > p1Pairs) "Победил Игрок 2!" else "Ничья!",
                subtitle = "Пары: $p1Pairs — $p2Pairs",
                mode = config.mode,
                roomState = roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else seed = Random.nextInt(100000) },
                onExit = onExit
            )
        }
    }
}
