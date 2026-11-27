package com.playbox.games.model

import androidx.compose.ui.graphics.Color

enum class GameId(val id: String) {
    TICTACTOE("tictactoe"),
    CONNECT4("connect4"),
    BATTLESHIP("battleship"),
    CHECKERS("checkers"),
    REVERSI("reversi"),
    MANCALA("mancala"),
    DOTS_BOXES("dots_boxes"),
    PING_PONG("ping_pong"),
    GAME_2048("game_2048"),
    MINESWEEPER("minesweeper"),
    SNAKE("snake"),
    WORDLE("wordle");

    companion object {
        fun fromId(raw: String): GameId? = entries.firstOrNull { it.id == raw }
    }
}

enum class GameCategory(val label: String) {
    ALL("Все"),
    TWO_PLAYER("На двоих"),
    LOGIC("Логика"),
    WORDS("Слова"),
    ARCADE("Аркады")
}

enum class PlayMode(val label: String) {
    SOLO("Одиночная"),
    LOCAL_2P("На одном экране"),
    ONLINE("Онлайн")
}

enum class BotDifficulty(val label: String) {
    EASY("Лёгкий"),
    MEDIUM("Средний"),
    HARD("Сложный")
}

data class GameDefinition(
    val id: GameId,
    val title: String,
    val subtitle: String,
    val category: GameCategory,
    val supportsBot: Boolean,
    val supportsLocal2P: Boolean,
    val supportsOnline: Boolean,
    val isDuelOnline: Boolean,
    val cardBg: Color,
    val titleColor: Color,
    val accentColor: Color,
    val badge: String? = null,
    val badgeColor: Color = Color(0xFF00A8E8)
)

object GameCatalog {
    val all: List<GameDefinition> = listOf(
        GameDefinition(
            id = GameId.TICTACTOE,
            title = "КРЕСТИКИ-\nНОЛИКИ",
            subtitle = "3×3 и 5×5 (4 в ряд)",
            category = GameCategory.TWO_PLAYER,
            supportsBot = true,
            supportsLocal2P = true,
            supportsOnline = true,
            isDuelOnline = true,
            cardBg = Color(0xFF1BB1E7),
            titleColor = Color(0xFF1B1F23),
            accentColor = Color(0xFF1B1F23),
            badge = "САМОЕ ПОПУЛЯРНОЕ!",
            badgeColor = Color(0xFF0096D6)
        ),
        GameDefinition(
            id = GameId.CONNECT4,
            title = "4 В РЯД",
            subtitle = "Собери линию из 4 фишек",
            category = GameCategory.TWO_PLAYER,
            supportsBot = true,
            supportsLocal2P = true,
            supportsOnline = true,
            isDuelOnline = true,
            cardBg = Color(0xFF2B4657),
            titleColor = Color(0xFF4CC9F0),
            accentColor = Color(0xFF4CC9F0),
            badge = "2 ИГРОКА",
            badgeColor = Color(0xFFE63946)
        ),
        GameDefinition(
            id = GameId.BATTLESHIP,
            title = "МОРСКОЙ БОЙ",
            subtitle = "Флот 10×10 и туман войны",
            category = GameCategory.TWO_PLAYER,
            supportsBot = true,
            supportsLocal2P = true,
            supportsOnline = true,
            isDuelOnline = true,
            cardBg = Color(0xFF17A5D4),
            titleColor = Color(0xFF1E293B),
            accentColor = Color(0xFF1E293B),
            badge = "ХИТ!",
            badgeColor = Color(0xFFFF6B35)
        ),
        GameDefinition(
            id = GameId.PING_PONG,
            title = "ПИНГ-ПОНГ",
            subtitle = "Дуэль в реальном времени",
            category = GameCategory.ARCADE,
            supportsBot = true,
            supportsLocal2P = true,
            supportsOnline = true,
            isDuelOnline = true,
            cardBg = Color(0xFFF6C244),
            titleColor = Color(0xFF2F3542),
            accentColor = Color(0xFF2F3542),
            badge = "2 ИГРОКА",
            badgeColor = Color(0xFF2EC4B6)
        ),
        GameDefinition(
            id = GameId.CHECKERS,
            title = "ШАШКИ",
            subtitle = "Классика 8×8 с дамками",
            category = GameCategory.TWO_PLAYER,
            supportsBot = true,
            supportsLocal2P = true,
            supportsOnline = true,
            isDuelOnline = true,
            cardBg = Color(0xFF6B535A),
            titleColor = Color(0xFFF3E5D8),
            accentColor = Color(0xFFF3E5D8)
        ),
        GameDefinition(
            id = GameId.REVERSI,
            title = "РЕВЕРСИ",
            subtitle = "Переворачивай фишки",
            category = GameCategory.TWO_PLAYER,
            supportsBot = true,
            supportsLocal2P = true,
            supportsOnline = true,
            isDuelOnline = true,
            cardBg = Color(0xFF335266),
            titleColor = Color(0xFF8ECAE6),
            accentColor = Color(0xFF8ECAE6)
        ),
        GameDefinition(
            id = GameId.MANCALA,
            title = "МАНКАЛА",
            subtitle = "Древняя стратегия с камнями",
            category = GameCategory.TWO_PLAYER,
            supportsBot = true,
            supportsLocal2P = true,
            supportsOnline = true,
            isDuelOnline = true,
            cardBg = Color(0xFF2D4C61),
            titleColor = Color(0xFFF6B756),
            accentColor = Color(0xFFF6B756)
        ),
        GameDefinition(
            id = GameId.DOTS_BOXES,
            title = "ТОЧКИ И\nКОРОБОЧКИ",
            subtitle = "Захватывай квадраты",
            category = GameCategory.TWO_PLAYER,
            supportsBot = true,
            supportsLocal2P = true,
            supportsOnline = true,
            isDuelOnline = true,
            cardBg = Color(0xFFDFE2F7),
            titleColor = Color(0xFFE05263),
            accentColor = Color(0xFFE05263)
        ),
        GameDefinition(
            id = GameId.GAME_2048,
            title = "ЧИСЛОВОЙ\nСЛАЙД",
            subtitle = "Собери 2048 или обгони в онлайне",
            category = GameCategory.LOGIC,
            supportsBot = false,
            supportsLocal2P = false,
            supportsOnline = true,
            isDuelOnline = false,
            cardBg = Color(0xFF6A515E),
            titleColor = Color(0xFFFFBC42),
            accentColor = Color(0xFFFFBC42),
            badge = "САМОЕ ПОПУЛЯРНОЕ!",
            badgeColor = Color(0xFF0096D6)
        ),
        GameDefinition(
            id = GameId.MINESWEEPER,
            title = "САПЁР",
            subtitle = "Разминируй поле быстрее всех",
            category = GameCategory.LOGIC,
            supportsBot = false,
            supportsLocal2P = false,
            supportsOnline = true,
            isDuelOnline = false,
            cardBg = Color(0xFF38424D),
            titleColor = Color(0xFFFFFFFF),
            accentColor = Color(0xFFFFFFFF)
        ),
        GameDefinition(
            id = GameId.WORDLE,
            title = "УГАДАЙ СЛОВО",
            subtitle = "Слово из 5 букв (RU / EN)",
            category = GameCategory.WORDS,
            supportsBot = false,
            supportsLocal2P = false,
            supportsOnline = true,
            isDuelOnline = false,
            cardBg = Color(0xFFF3E7CF),
            titleColor = Color(0xFF343A40),
            accentColor = Color(0xFF343A40),
            badge = "НОВОЕ!",
            badgeColor = Color(0xFFE63946)
        ),
        GameDefinition(
            id = GameId.SNAKE,
            title = "ЗМЕЙКА",
            subtitle = "Классика соло, 2P и гонка",
            category = GameCategory.ARCADE,
            supportsBot = false,
            supportsLocal2P = true,
            supportsOnline = true,
            isDuelOnline = false,
            cardBg = Color(0xFFFCE6B8),
            titleColor = Color(0xFFE76F51),
            accentColor = Color(0xFFE76F51)
        )
    )

    fun get(id: GameId): GameDefinition = all.first { it.id == id }
}

data class LaunchConfig(
    val gameId: GameId,
    val mode: PlayMode,
    val difficulty: BotDifficulty = BotDifficulty.MEDIUM,
    val roomCode: String? = null,
    val playerIndex: Int = 1,
    val seed: Int = 42,
    val startingPlayer: Int = 1
)
