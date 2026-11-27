package com.playbox.games.model

import androidx.compose.ui.graphics.Color

enum class GameId(val id: String) {
    // Screenshot 1
    NUTS_BOLTS("nuts_bolts"),
    PETDOKU("petdoku"),
    MULTI_SNAKES("multi_snakes"),
    ARROWS_PUZZLE("arrows_puzzle"),
    PAINT_MAZE("paint_maze"),
    SHIKAKU("shikaku"),
    // Screenshot 2
    NUMBER_MATCH("number_match"),
    COLOR_BLOCKS("color_blocks"),
    GUESS_WHO("guess_who"),
    FRUIT_MERGE("fruit_merge"),
    ESCAPE_PARKING("escape_parking"),
    BALL_RUN("ball_run"),
    // Screenshot 3
    MULTI_COLOR_FILL("multi_color_fill"),
    TOWER_STACK("tower_stack"),
    WATER_SORT("water_sort"),
    ANIMAL_STACK("animal_stack"),
    DOMINO("domino"),
    FALLING_SAND("falling_sand"),
    // Screenshot 4
    WORD_SEARCH("word_search"),
    ARCHERY("archery"),
    DARTS("darts"),
    DROP_MERGE("drop_merge"),
    CROSS_SUMS("cross_sums"),
    CONNECT_2248("connect_2248"),
    // Screenshot 5
    COLOR_CARDS("color_cards"),
    FLOW_COLORS("flow_colors"),
    FREECELL("freecell"),
    LUDO("ludo"),
    SOLITAIRE("solitaire"),
    SUDOKU("sudoku"),
    // Screenshot 6
    HEARTS("hearts"),
    REVERSI("reversi"),
    TAP_MATCH("tap_match"),
    BATTLESHIP("battleship"),
    SOCCER_POOL("soccer_pool"),
    GAME_2048("game_2048"),
    // Screenshot 7
    CONNECT4("connect4"),
    TICTACTOE("tictactoe"),
    BACKGAMMON("backgammon"),
    WORDLE("wordle"),
    MINESWEEPER("minesweeper"),
    HANGMAN("hangman"),
    // Screenshot 8
    SOUND_MEMORY("sound_memory"),
    SNAKE("snake"),
    CHESS("chess"),
    CHESS_PUZZLES("chess_puzzles"),
    CHECKERS("checkers"),
    BILLIARDS("billiards"),
    // Screenshot 9
    MEMORY_CARDS("memory_cards"),
    SLIDING_PUZZLE("sliding_puzzle"),
    PING_PONG("ping_pong"),
    WOOD_BLOCKS("wood_blocks"),
    DOTS_BOXES("dots_boxes"),
    MANCALA("mancala");

    companion object {
        fun fromId(raw: String): GameId? = entries.firstOrNull { it.id == raw }
    }
}

enum class GameCategory(val label: String) {
    ALL("Все (54)"),
    TWO_PLAYER("На двоих"),
    BOARD_CARDS("Настолки и карты"),
    LOGIC("Логика"),
    WORDS("Слова и память"),
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
    val badgeColor: Color = Color(0xFF0096D6)
)

object GameCatalog {
    val all: List<GameDefinition> = listOf(
        // Screenshot 1
        GameDefinition(GameId.NUTS_BOLTS, "ГАЙКИ И\nБОЛТЫ", "Отсортируй гайки по цвету", GameCategory.LOGIC, false, false, true, false, Color(0xFF221E2A), Color(0xFFE0526D), Color(0xFFE0526D)),
        GameDefinition(GameId.PETDOKU, "PETDOKU", "Питомцы без повторов 4×4", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFFFAF5E8), Color(0xFFD65A7D), Color(0xFFD65A7D), "САМОЕ ПОПУЛЯРНОЕ!", Color(0xFF00A8E8)),
        GameDefinition(GameId.MULTI_SNAKES, "ЗМЕЙКИ", "Радужная арена против змеек", GameCategory.ARCADE, true, false, true, false, Color(0xFF1A1E24), Color(0xFFF6B756), Color(0xFFF6B756)),
        GameDefinition(GameId.ARROWS_PUZZLE, "УКАЗАТЕЛИ", "Освободи все стрелки с поля", GameCategory.LOGIC, false, false, true, false, Color(0xFFD8EFF7), Color(0xFF0096C7), Color(0xFF0096C7)),
        GameDefinition(GameId.PAINT_MAZE, "ЗАКРАСЬ\nЛАБИРИНТ", "Закрась весь лабиринт шаром", GameCategory.LOGIC, false, false, true, false, Color(0xFF23262E), Color(0xFFE0526D), Color(0xFFE0526D)),
        GameDefinition(GameId.SHIKAKU, "ШИКАКУ", "Разбей сетку на прямоугольники", GameCategory.LOGIC, false, false, true, false, Color(0xFFFAF5E8), Color(0xFFC86D85), Color(0xFFC86D85), "НОВОЕ!", Color(0xFFE63946)),

        // Screenshot 2
        GameDefinition(GameId.NUMBER_MATCH, "СОВПАДЕНИЕ\nЧИСЕЛ", "Одинаковые или в сумме 10", GameCategory.LOGIC, false, false, true, false, Color(0xFF00A8E8), Color(0xFFFFFFFF), Color(0xFFFFFFFF), "САМОЕ ПОПУЛЯРНОЕ!", Color(0xFF0096D6)),
        GameDefinition(GameId.COLOR_BLOCKS, "ЦВЕТОВЫЕ\nБЛОКИ", "Падающие фигуры и линии", GameCategory.ARCADE, false, false, true, false, Color(0xFF2B2438), Color(0xFFF6B756), Color(0xFFF6B756)),
        GameDefinition(GameId.GUESS_WHO, "УГАДАЙ\nЧЕЛОВЕКА", "Задавай вопросы о внешности", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFF6A4C73), Color(0xFFF6B756), Color(0xFFF6B756)),
        GameDefinition(GameId.FRUIT_MERGE, "СЛИЯНИЕ\nФРУКТОВ", "Собирай фрукты до арбуза", GameCategory.ARCADE, false, false, true, false, Color(0xFFFCEBC9), Color(0xFFE6533C), Color(0xFFE6533C)),
        GameDefinition(GameId.ESCAPE_PARKING, "ПОБЕГ", "Выведи красную машину", GameCategory.LOGIC, false, false, true, false, Color(0xFF6B4C3B), Color(0xFFE0526D), Color(0xFFE0526D)),
        GameDefinition(GameId.BALL_RUN, "БЕГ ШАРА", "Неоновый тоннель и преграды", GameCategory.ARCADE, false, false, true, false, Color(0xFF141830), Color(0xFFC77DFF), Color(0xFFC77DFF)),

        // Screenshot 3
        GameDefinition(GameId.MULTI_COLOR_FILL, "МНОГОЦВЕТНОЕ\nЗАПОЛНЕНИЕ", "Заполни клетки линиями", GameCategory.LOGIC, false, false, true, false, Color(0xFF2A3236), Color(0xFFE2E8F0), Color(0xFFE2E8F0)),
        GameDefinition(GameId.TOWER_STACK, "БАШНЯ", "Построй самый высокий небоскрёб", GameCategory.ARCADE, false, false, true, false, Color(0xFFF2D4E6), Color(0xFF2B2D42), Color(0xFF2B2D42)),
        GameDefinition(GameId.WATER_SORT, "СОРТИРОВКА\nВОДЫ", "Переливай цвета по пробиркам", GameCategory.LOGIC, false, false, true, false, Color(0xFF43304A), Color(0xFFC792DF), Color(0xFFC792DF)),
        GameDefinition(GameId.ANIMAL_STACK, "ЖИВОТНЫЕ В\nСТОПКЕ", "Удержи баланс башни зверей", GameCategory.ARCADE, false, false, true, false, Color(0xFF26B7E8), Color(0xFF1E293B), Color(0xFF1E293B)),
        GameDefinition(GameId.DOMINO, "ДОМИНО", "Классический козёл на костяшках", GameCategory.BOARD_CARDS, true, true, true, true, Color(0xFF0A4F44), Color(0xFFFFFFFF), Color(0xFFFFFFFF)),
        GameDefinition(GameId.FALLING_SAND, "СЫПУЧИЙ\nПЕСОК", "Песчаный тетрис по цветам", GameCategory.ARCADE, false, false, true, false, Color(0xFF141F38), Color(0xFFF6B756), Color(0xFFF6B756)),

        // Screenshot 4
        GameDefinition(GameId.WORD_SEARCH, "ПОИСК СЛОВ", "Соединяй буквы в круглом колесе", GameCategory.WORDS, false, false, true, false, Color(0xFF7B5780), Color(0xFFF6B756), Color(0xFFF6B756)),
        GameDefinition(GameId.ARCHERY, "СТРЕЛЬБА ИЗ\nЛУКА", "Попади в яблочко с ветром", GameCategory.ARCADE, true, true, true, true, Color(0xFFD5F0EE), Color(0xFF2D4654), Color(0xFF2D4654)),
        GameDefinition(GameId.DARTS, "ДАРТС", "Режим 301 с удвоением и центром", GameCategory.ARCADE, true, true, true, true, Color(0xFFF6B98E), Color(0xFF4A2511), Color(0xFF4A2511)),
        GameDefinition(GameId.DROP_MERGE, "СЛИЯНИЕ ЧИСЕЛ", "Бросай блоки и объединяй 2048", GameCategory.LOGIC, false, false, true, false, Color(0xFF33363F), Color(0xFF6BCB77), Color(0xFF6BCB77)),
        GameDefinition(GameId.CROSS_SUMS, "ПЕРЕКРЕСТНЫЕ\nСУММЫ", "Подбери числа под суммы строк", GameCategory.LOGIC, false, false, true, false, Color(0xFF00B4E6), Color(0xFF1E293B), Color(0xFF1E293B)),
        GameDefinition(GameId.CONNECT_2248, "СОЕДИНЕНИЕ\nЦИФР", "Тяни цепочки 2→4→8→2248", GameCategory.LOGIC, false, false, true, false, Color(0xFF283B4A), Color(0xFF2EC4B6), Color(0xFF2EC4B6)),

        // Screenshot 5
        GameDefinition(GameId.COLOR_CARDS, "ЦВЕТНЫЕ\nКАРТЫ", "UNO-дуэль с +2, Сменой и Стоп", GameCategory.BOARD_CARDS, true, true, true, true, Color(0xFF7D4932), Color(0xFFF6B756), Color(0xFFF6B756)),
        GameDefinition(GameId.FLOW_COLORS, "СОЕДИНЕНИЯ\nЦВЕТОВ", "Свяжи все парные точки", GameCategory.LOGIC, false, false, true, false, Color(0xFF4D384F), Color(0xFFF6B756), Color(0xFFF6B756)),
        GameDefinition(GameId.FREECELL, "СВОБОДНАЯ\nЯЧЕЙКА", "Классический карточный пасьянс", GameCategory.BOARD_CARDS, false, false, true, false, Color(0xFF0E4D75), Color(0xFFA9D6E5), Color(0xFFA9D6E5)),
        GameDefinition(GameId.LUDO, "ЛУДО", "Гонка фишек с кубиком 2–4 игрока", GameCategory.BOARD_CARDS, true, true, true, true, Color(0xFF2A4759), Color(0xFFFFFFFF), Color(0xFFFFFFFF)),
        GameDefinition(GameId.SOLITAIRE, "ПАСЬЯНС", "Косынка (Клондайк)", GameCategory.BOARD_CARDS, false, false, true, false, Color(0xFF137534), Color(0xFF141B24), Color(0xFF141B24)),
        GameDefinition(GameId.SUDOKU, "СУДОКУ", "9×9 с заметками и проверкой", GameCategory.LOGIC, false, false, true, false, Color(0xFF1AA7E3), Color(0xFFFFFFFF), Color(0xFFFFFFFF)),

        // Screenshot 6
        GameDefinition(GameId.HEARTS, "ЧЕРВЫ", "Не бери черви и Даму пик", GameCategory.BOARD_CARDS, true, false, true, false, Color(0xFFE95C5C), Color(0xFF4A1818), Color(0xFF4A1818)),
        GameDefinition(GameId.REVERSI, "РЕВЕРСИ", "Переворачивай фишки соперника", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFF335266), Color(0xFF8ECAE6), Color(0xFF8ECAE6)),
        GameDefinition(GameId.TAP_MATCH, "НАЖМИТЕ НА\nМАТЧ", "Собери тройки одинаковых плиток", GameCategory.LOGIC, false, false, true, false, Color(0xFFE05D56), Color(0xFF401F1C), Color(0xFF401F1C)),
        GameDefinition(GameId.BATTLESHIP, "МОРСКОЙ БОЙ", "Флот 10×10 и туман войны", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFF17A5D4), Color(0xFF1E293B), Color(0xFF1E293B)),
        GameDefinition(GameId.SOCCER_POOL, "ФУТБОЛЬНЫЙ\nПУЛ", "Щелчковый футбол на двоих", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFF75B73B), Color(0xFF1B4332), Color(0xFF1B4332)),
        GameDefinition(GameId.GAME_2048, "ЧИСЛОВОЙ\nСЛАЙД", "Собери 2048 свайпами", GameCategory.LOGIC, false, false, true, false, Color(0xFF6A515E), Color(0xFFFFBC42), Color(0xFFFFBC42)),

        // Screenshot 7
        GameDefinition(GameId.CONNECT4, "4 В РЯД", "Собери 4 фишки в линию", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFF2B4657), Color(0xFF4CC9F0), Color(0xFF4CC9F0)),
        GameDefinition(GameId.TICTACTOE, "КРЕСТИКИ-\nНОЛИКИ", "3×3 и 5×5 (4 в ряд)", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFF1BB1E7), Color(0xFF1B1F23), Color(0xFF1B1F23)),
        GameDefinition(GameId.BACKGAMMON, "НАРДЫ", "Классическая доска 24 пункта", GameCategory.BOARD_CARDS, true, true, true, true, Color(0xFF2D4C61), Color(0xFF90CAF9), Color(0xFF90CAF9)),
        GameDefinition(GameId.WORDLE, "УГАДАЙ СЛОВО", "Слово из 5 букв (RU / EN)", GameCategory.WORDS, false, false, true, false, Color(0xFFF3E7CF), Color(0xFF343A40), Color(0xFF343A40)),
        GameDefinition(GameId.MINESWEEPER, "САПЁР", "Разминируй поле без ошибок", GameCategory.LOGIC, false, false, true, false, Color(0xFF38424D), Color(0xFFFFFFFF), Color(0xFFFFFFFF)),
        GameDefinition(GameId.HANGMAN, "ВИСЕЛЬНИК", "Отгадай слово по буквам", GameCategory.WORDS, false, true, true, false, Color(0xFFF6A570), Color(0xFFD62828), Color(0xFFD62828)),

        // Screenshot 8
        GameDefinition(GameId.SOUND_MEMORY, "ЗВУКОВАЯ\nПАМЯТЬ", "Повтори мелодию и цвета", GameCategory.WORDS, false, false, true, false, Color(0xFF2E4D63), Color(0xFF4CC9F0), Color(0xFF4CC9F0)),
        GameDefinition(GameId.SNAKE, "ЗМЕЙКА", "Классика соло, 2P и гонка", GameCategory.ARCADE, false, true, true, false, Color(0xFFFCE6B8), Color(0xFFE76F51), Color(0xFFE76F51)),
        GameDefinition(GameId.CHESS, "ШАХМАТЫ", "Полные правила, бот, 2P и онлайн", GameCategory.BOARD_CARDS, true, true, true, true, Color(0xFF9E5A47), Color(0xFFFDF0E6), Color(0xFFFDF0E6)),
        GameDefinition(GameId.CHESS_PUZZLES, "ШАХМАТНЫЕ\nГОЛОВОЛОМКИ", "Найди лучший тактический ход", GameCategory.BOARD_CARDS, false, false, true, false, Color(0xFF29485C), Color(0xFF70E000), Color(0xFF70E000)),
        GameDefinition(GameId.CHECKERS, "ШАШКИ", "Классика 8×8 с дамками", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFF6B535A), Color(0xFFF3E5D8), Color(0xFFF3E5D8)),
        GameDefinition(GameId.BILLIARDS, "БИЛЬЯРД", "Восьмёрка с физикой шаров", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFF137534), Color(0xFFFFFFFF), Color(0xFFFFFFFF)),

        // Screenshot 9
        GameDefinition(GameId.MEMORY_CARDS, "ПАМЯТЬ", "Найди все парные карточки", GameCategory.WORDS, false, true, true, true, Color(0xFF4E3B42), Color(0xFFF6B756), Color(0xFFF6B756)),
        GameDefinition(GameId.SLIDING_PUZZLE, "ГОЛОВОЛОМКА\nСЛАЙДЕР", "Пятнашки 3×3 и 4×4", GameCategory.LOGIC, false, false, true, false, Color(0xFF2F5368), Color(0xFFFFFFFF), Color(0xFFFFFFFF)),
        GameDefinition(GameId.PING_PONG, "ПИНГ-ПОНГ", "Дуэль в реальном времени", GameCategory.ARCADE, true, true, true, true, Color(0xFFF6C244), Color(0xFF2F3542), Color(0xFF2F3542)),
        GameDefinition(GameId.WOOD_BLOCKS, "ДЕРЕВЯННЫЕ\nБЛОКИ", "Собери линии на поле 9×9", GameCategory.LOGIC, false, false, true, false, Color(0xFF704E58), Color(0xFFF3C68F), Color(0xFFF3C68F)),
        GameDefinition(GameId.DOTS_BOXES, "ТОЧКИ И\nКОРОБОЧКИ", "Захватывай квадраты линиями", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFFDFE2F7), Color(0xFFE05263), Color(0xFFE05263)),
        GameDefinition(GameId.MANCALA, "МАНКАЛА", "Древняя стратегия с камнями", GameCategory.TWO_PLAYER, true, true, true, true, Color(0xFF2D4C61), Color(0xFFF6B756), Color(0xFFF6B756))
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
