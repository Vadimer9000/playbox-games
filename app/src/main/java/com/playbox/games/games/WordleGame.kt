package com.playbox.games.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import com.playbox.games.ui.theme.bounceClick
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

private val RU_WORDS_5 = listOf(
    "АРБУЗ", "БАНАН", "БЕРЕГ", "БИЛЕТ", "БУКВА", "ВАГОН", "ВЕДРО", "ВЕСНА", "ВЕТЕР", "ВИЛКА",
    "ВОЙНА", "ВОЛНА", "ВОРОН", "ВРЕМЯ", "ВЫБОР", "ГАЗОН", "ГЕРОЙ", "ГЛАЗА", "ГЛИНА", "ГОЛОС",
    "ГОРОД", "ГРОЗА", "ГРУША", "ДВЕРЬ", "ДИВАН", "ДОЖДЬ", "ДОМИК", "ДРАКА", "ЖИЗНЬ", "ЗАВОД",
    "ЗАМОК", "ЗАПАХ", "ЗВЕРЬ", "ЗЕМЛЯ", "ЗЕРНО", "ИГРОК", "КАМНИ", "КАРТА", "КАТЕР", "КИСТЬ",
    "КНИГА", "КОВЕР", "КОМАР", "КОШКА", "КРЫЛО", "КРЫСА", "КУБОК", "ЛАМПА", "ЛИМОН", "ЛИНИЯ",
    "ЛОДКА", "ЛОЖКА", "МАЙОР", "МАСЛО", "МЕСТО", "МЕТРО", "МЕЧТА", "МИНУС", "МОЛОТ", "МОРОЗ",
    "МОТОР", "МЫШКА", "НАРОД", "НОМЕР", "НОСОК", "НОЧКА", "ОБРАЗ", "ОГОНЬ", "ОКЕАН", "ОЛЕНЬ",
    "ОПЕРА", "ОСЕНЬ", "ПАЛЕЦ", "ПАЛКА", "ПАРАД", "ПАРУС", "ПЕСНЯ", "ПЕСОК", "ПИРАТ", "ПЛИТА",
    "ПОЕЗД", "ПОИСК", "ПОЛЕТ", "ПОЛКА", "ПОЧТА", "ПРАВО", "ПТИЦА", "ПУДРА", "ПУШКА", "ПЯТНО",
    "РАДИО", "РАМКА", "РАНЕЦ", "РЕЧКА", "РОБОТ", "РОЛИК", "РУБЛЬ", "РУКАВ", "РУЧКА", "РЫБАК",
    "САПОГ", "САХАР", "СВЕЧА", "СЕВЕР", "СЕМЬЯ", "СИЛАЧ", "СКАЛА", "СКЛАД", "СЛОВО", "СМЕНА",
    "СОВЕТ", "СОСЕД", "СПИНА", "СПОРТ", "СРЕДА", "СТАРТ", "СТЕНА", "СТОЛБ", "СТРАХ", "СУМКА",
    "СУПЕР", "СЦЕНА", "ТАЙНА", "ТАНЕЦ", "ТЕАТР", "ТЕКСТ", "ТЕПЛО", "ТОВАР", "ТОПОР", "ТРАВА",
    "ТРОПА", "ТРУБА", "ТУМАН", "ТУФЛЯ", "ТЫКВА", "УЛИЦА", "УСПЕХ", "ФАКЕЛ", "ФИЛЬМ", "ФОКУС",
    "ФОРМА", "ФРУКТ", "ХОЛОД", "ЦИФРА", "ЧАШКА", "ЧЕРТА", "ЧИСЛО", "ШАПКА", "ШКОЛА", "ШЛЯПА",
    "ШТОРМ", "ШУТКА", "ЭКРАН", "ЮНОША", "ЯКОРЬ"
)

private val EN_WORDS_5 = listOf(
    "APPLE", "BEACH", "BRAIN", "BREAD", "BRUSH", "CHAIR", "CHEST", "CHORD", "CLICK", "CLOCK",
    "CLOUD", "DANCE", "DIARY", "DRINK", "EARTH", "FEAST", "FIELD", "FLAME", "FLOOR", "FRUIT",
    "GHOST", "GRAPE", "GREEN", "HEART", "HOUSE", "JUICE", "LIGHT", "LUCKY", "MAGIC", "MONEY",
    "MUSIC", "NIGHT", "OCEAN", "PARTY", "PEACE", "PHONE", "PIANO", "PILOT", "PIZZA", "PLANE",
    "PLANT", "PLATE", "POWER", "PRIZE", "RADIO", "RIVER", "ROBOT", "SCALE", "SCORE", "SHARK",
    "SHEEP", "SHIRT", "SHOES", "SMILE", "SNAKE", "SPACE", "SPICE", "SPOON", "SPORT", "STAGE",
    "STAMP", "STONE", "STORM", "SUGAR", "SUNNY", "SWEET", "TABLE", "TASTE", "TIGER", "TOAST",
    "TOUCH", "TOWER", "TRACK", "TRAIN", "TRUCK", "VOICE", "WATER", "WHALE", "WHEEL", "WORLD",
    "WRITE", "YOUTH", "ZEBRA"
)

private val RU_KEYBOARD = listOf(
    listOf("Й", "Ц", "У", "К", "Е", "Н", "Г", "Ш", "Щ", "З", "Х"),
    listOf("Ф", "Ы", "В", "А", "П", "Р", "О", "Л", "Д", "Ж", "Э"),
    listOf("ENTER", "Я", "Ч", "С", "М", "И", "Т", "Ь", "Б", "Ю", "DEL")
)

private val EN_KEYBOARD = listOf(
    listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
    listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
    listOf("ENTER", "Z", "X", "C", "V", "B", "N", "M", "DEL")
)

@Composable
fun WordleScreen(
    config: LaunchConfig,
    prefs: PlayerPrefs,
    onlineClient: OnlineClient,
    roomState: OnlineRoomState?,
    resetSignal: Int,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var useRussian by remember { mutableStateOf(true) }
    var seed by remember(resetSignal) { mutableIntStateOf(config.seed) }
    val wordList = if (useRussian) RU_WORDS_5 else EN_WORDS_5
    val targetWord = remember(useRussian, seed) { wordList[abs(seed) % wordList.size] }

    var guesses by remember(useRussian, seed) { mutableStateOf<List<String>>(emptyList()) }
    var currentInput by remember(useRussian, seed) { mutableStateOf("") }
    var outcome by remember(useRussian, seed) { mutableStateOf<String?>(null) }
    var oppRows by remember(useRussian, seed) { mutableIntStateOf(0) }
    var oppWon by remember(useRussian, seed) { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }

    fun reset(newSeed: Int) {
        seed = newSeed; guesses = emptyList(); currentInput = ""; outcome = null; oppRows = 0; oppWon = false
    }

    LaunchedEffect(config.mode) {
        if (config.mode == PlayMode.ONLINE) {
            onlineClient.events.collect { event ->
                when (event) {
                    is OnlineEvent.OpponentState -> {
                        oppRows = event.payload.optInt("rows", oppRows)
                        oppWon = event.payload.optBoolean("won", false)
                    }
                    is OnlineEvent.RematchStart -> reset(event.seed)
                    else -> Unit
                }
            }
        }
    }

    fun triggerShake() {
        scope.launch {
            listOf(-14f, 14f, -10f, 10f, 0f).forEach { target ->
                shakeOffset.animateTo(target, tween(45))
            }
        }
    }

    fun onKey(key: String) {
        if (outcome != null) return
        when (key) {
            "DEL" -> if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
            "ENTER" -> {
                if (currentInput.length < 5) { triggerShake(); return }
                val updated = guesses + currentInput
                guesses = updated
                val won = currentInput == targetWord
                currentInput = ""
                if (won) {
                    outcome = "WIN"
                    prefs.recordWin(GameId.WORDLE)
                    if (config.mode == PlayMode.ONLINE) {
                        onlineClient.sendState(JSONObject().put("rows", updated.size).put("won", true))
                        onlineClient.sendGameOver(config.playerIndex)
                    }
                } else {
                    if (config.mode == PlayMode.ONLINE) onlineClient.sendState(JSONObject().put("rows", updated.size).put("won", false))
                    if (updated.size >= 6) outcome = "LOSE"
                }
            }
            else -> if (currentInput.length < 5) currentInput += key
        }
    }

    val keyColors = remember(guesses, targetWord) {
        val map = mutableMapOf<Char, Int>()
        guesses.forEach { g ->
            val eval = evaluateWordleGuess(g, targetWord)
            g.forEachIndexed { i, ch ->
                val prev = map[ch] ?: 0
                if (eval[i] > prev) map[ch] = eval[i]
            }
        }
        map
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = useRussian, onClick = { useRussian = true }, label = { Text("RU", fontWeight = FontWeight.Bold, maxLines = 1) })
                FilterChip(selected = !useRussian, onClick = { useRussian = false }, label = { Text("EN", fontWeight = FontWeight.Bold, maxLines = 1) })
            }
            if (config.mode == PlayMode.ONLINE) {
                Text(
                    text = if (oppWon) "Соперник угадал за $oppRows!" else "Соперник: попыток $oppRows/6",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PlayBoxColors.OnlinePurple
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Column(Modifier.weight(1f).fillMaxWidth(0.82f), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            for (row in 0 until 6) {
                val word = when {
                    row < guesses.size -> guesses[row]
                    row == guesses.size -> currentInput.padEnd(5, ' ')
                    else -> "     "
                }
                val eval = if (row < guesses.size) evaluateWordleGuess(word, targetWord) else IntArray(5)
                val rowShake = if (row == guesses.size) shakeOffset.value.roundToInt() else 0
                Row(
                    modifier = Modifier.fillMaxWidth().offset { IntOffset(rowShake, 0) },
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (col in 0 until 5) {
                        val ch = word[col]; val state = eval[col]
                        val bg by animateColorAsState(
                            when (state) {
                                3 -> Color(0xFF40916C); 2 -> Color(0xFFF4A261); 1 -> Color(0xFF4B5563); else -> Color.White
                            },
                            label = "w_${row}_$col"
                        )
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                                .border(2.dp, if (state == 0) Color(0xFFCBD5E1) else bg, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (ch == ' ') "" else ch.toString(),
                                color = if (state == 0) PlayBoxColors.DarkInk else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp
                            )
                        }
                    }
                }
            }
        }

        if (outcome != null) {
            GameResultBanner(
                title = if (outcome == "WIN") "Слово угадано!" else "Слово: $targetWord",
                subtitle = if (outcome == "WIN") "Попыток: ${guesses.size}/6" else "Попробуйте ещё раз!",
                mode = config.mode,
                roomState = roomState,
                onRematch = { if (config.mode == PlayMode.ONLINE) onlineClient.requestRematch() else reset(Random.nextInt(100000)) },
                onExit = onExit
            )
        } else {
            val kb = if (useRussian) RU_KEYBOARD else EN_KEYBOARD
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                kb.forEach { rowKeys ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        rowKeys.forEach { key ->
                            val isSpecial = key == "ENTER" || key == "DEL"
                            val state = if (!isSpecial) keyColors[key[0]] ?: 0 else 0
                            val bg = when (state) {
                                3 -> Color(0xFF40916C); 2 -> Color(0xFFF4A261); 1 -> Color(0xFF6B7280)
                                else -> if (isSpecial) PlayBoxColors.DarkInk else Color.White
                            }
                            Box(
                                modifier = Modifier
                                    .weight(if (isSpecial) 1.6f else 1f)
                                    .height(45.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(8.dp))
                                    .bounceClick { onKey(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (key) { "ENTER" -> "ОК"; "DEL" -> "⌫"; else -> key },
                                    color = if (state == 0 && !isSpecial) PlayBoxColors.DarkInk else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = if (isSpecial) 12.sp else 14.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun evaluateWordleGuess(guess: String, target: String): IntArray {
    val result = IntArray(5) { 1 }
    val remaining = target.toCharArray()
    for (i in 0 until 5) if (guess[i] == target[i]) { result[i] = 3; remaining[i] = '*' }
    for (i in 0 until 5) {
        if (result[i] == 3) continue
        val idx = remaining.indexOf(guess[i])
        if (idx >= 0) { result[i] = 2; remaining[idx] = '*' }
    }
    return result
}
