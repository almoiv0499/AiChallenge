package org.example.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Конфигурация профиля пользователя для персонализации агента.
 * Загружается из файла user_profile.json в корне проекта.
 */
@Serializable
data class UserProfile(
    /** Имя пользователя */
    val name: String = "",

    /** Как агент должен обращаться к пользователю */
    val nickname: String = "",

    /** Профессия или род занятий */
    val profession: String = "",

    /** Уровень экспертизы в технических темах */
    val technicalLevel: TechnicalLevel = TechnicalLevel.INTERMEDIATE,

    /** Интересы пользователя */
    val interests: List<String> = emptyList(),

    /** Привычки пользователя */
    val habits: List<String> = emptyList(),

    /** Предпочтения в общении */
    val communicationPreferences: CommunicationPreferences = CommunicationPreferences(),

    /** Местоположение (город) */
    val location: String = "",

    /** Часовой пояс (например: Europe/Moscow) */
    val timezone: String = "",

    /** Рабочее расписание */
    val workSchedule: WorkSchedule = WorkSchedule(),

    /** Текущие цели и задачи */
    val currentGoals: List<String> = emptyList(),

    /** Технологии и инструменты, которые использует */
    val techStack: List<String> = emptyList(),

    /** Любимые темы для обсуждения */
    val favoriteTopics: List<String> = emptyList(),

    /** Темы, которые лучше избегать */
    val avoidTopics: List<String> = emptyList(),

    /** Дополнительные инструкции для агента */
    val customInstructions: List<String> = emptyList(),

    /** Контакты и аккаунты */
    val contacts: UserContacts = UserContacts(),

    /** Важные даты */
    val importantDates: List<ImportantDate> = emptyList()
)

@Serializable
enum class TechnicalLevel {
    BEGINNER,      // Начинающий - нужны подробные объяснения
    INTERMEDIATE,  // Средний - базовые концепции понятны
    ADVANCED,      // Продвинутый - можно использовать технический язык
    EXPERT         // Эксперт - минимум объяснений, максимум деталей
}

@Serializable
data class CommunicationPreferences(
    /** Предпочитаемый язык общения */
    val language: String = "ru",

    /** Стиль общения: formal, casual, friendly */
    val style: String = "friendly",

    /** Предпочитаемая длина ответов: short, medium, detailed */
    val responseLength: String = "medium",

    /** Использовать эмодзи в ответах */
    val useEmoji: Boolean = true,

    /** Использовать юмор */
    val useHumor: Boolean = true,

    /** Обращаться на "ты" или на "вы" */
    val formalAddress: Boolean = false
)

@Serializable
data class WorkSchedule(
    /** Начало рабочего дня (формат HH:mm) */
    val workStartTime: String = "09:00",

    /** Конец рабочего дня (формат HH:mm) */
    val workEndTime: String = "18:00",

    /** Рабочие дни (1 = понедельник, 7 = воскресенье) */
    val workDays: List<Int> = listOf(1, 2, 3, 4, 5),

    /** Предпочитаемое время для сложных задач */
    val productiveHours: String = "morning"
)

@Serializable
data class UserContacts(
    val github: String = "",
    val telegram: String = "",
    val email: String = ""
)

@Serializable
data class ImportantDate(
    val name: String,
    val date: String,  // формат: MM-DD или YYYY-MM-DD
    val recurring: Boolean = true
)

/**
 * Загрузчик и менеджер профиля пользователя
 */
object UserProfileConfig {
    private const val PROFILE_FILE = "user_profile.json"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private var cachedProfile: UserProfile? = null

    /**
     * Загружает профиль пользователя из файла.
     * Если файл не существует, создаёт пример и возвращает пустой профиль.
     */
    fun loadProfile(): UserProfile {
        cachedProfile?.let { return it }

        val file = File(PROFILE_FILE)

        if (!file.exists()) {
            println("📝 Файл профиля не найден. Создаю пример: $PROFILE_FILE")
            createExampleProfile(file)
            return UserProfile().also { cachedProfile = it }
        }

        return try {
            val content = file.readText()
            json.decodeFromString<UserProfile>(content).also {
                cachedProfile = it
                if (it.name.isNotEmpty()) {
                    println("👤 Профиль пользователя загружен: ${it.name}")
                }
            }
        } catch (e: Exception) {
            println("⚠️ Ошибка загрузки профиля: ${e.message}")
            println("   Используется профиль по умолчанию")
            UserProfile().also { cachedProfile = it }
        }
    }

    /**
     * Проверяет, настроен ли профиль пользователя
     */
    fun isProfileConfigured(): Boolean {
        val profile = loadProfile()
        return profile.name.isNotEmpty()
    }

    /**
     * Генерирует текст для системного промпта на основе профиля
     */
    fun generateSystemPromptAddition(): String {
        val profile = loadProfile()

        if (profile.name.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()
        sb.appendLine("\n\n=== ПЕРСОНАЛИЗАЦИЯ ===")
        sb.appendLine("Информация о пользователе, с которым ты общаешься:")

        // Основная информация
        sb.appendLine("\n👤 ОСНОВНОЕ:")
        sb.appendLine("- Имя: ${profile.name}")
        if (profile.nickname.isNotEmpty()) {
            sb.appendLine("- Обращайся к нему: ${profile.nickname}")
        }
        if (profile.profession.isNotEmpty()) {
            sb.appendLine("- Профессия: ${profile.profession}")
        }
        if (profile.location.isNotEmpty()) {
            sb.appendLine("- Местоположение: ${profile.location}")
        }

        // Технический уровень
        val levelDesc = when (profile.technicalLevel) {
            TechnicalLevel.BEGINNER -> "начинающий - давай подробные объяснения"
            TechnicalLevel.INTERMEDIATE -> "средний - базовые концепции понятны"
            TechnicalLevel.ADVANCED -> "продвинутый - можно использовать технический язык"
            TechnicalLevel.EXPERT -> "эксперт - минимум объяснений, максимум деталей"
        }
        sb.appendLine("- Технический уровень: $levelDesc")

        // Стек технологий
        if (profile.techStack.isNotEmpty()) {
            sb.appendLine("\n💻 ТЕХНОЛОГИИ:")
            sb.appendLine("- Использует: ${profile.techStack.joinToString(", ")}")
        }

        // Интересы
        if (profile.interests.isNotEmpty()) {
            sb.appendLine("\n🎯 ИНТЕРЕСЫ:")
            profile.interests.forEach { sb.appendLine("- $it") }
        }

        // Привычки
        if (profile.habits.isNotEmpty()) {
            sb.appendLine("\n📋 ПРИВЫЧКИ:")
            profile.habits.forEach { sb.appendLine("- $it") }
        }

        // Текущие цели
        if (profile.currentGoals.isNotEmpty()) {
            sb.appendLine("\n🎯 ТЕКУЩИЕ ЦЕЛИ:")
            profile.currentGoals.forEach { sb.appendLine("- $it") }
        }

        // Любимые темы
        if (profile.favoriteTopics.isNotEmpty()) {
            sb.appendLine("\n💬 ЛЮБИМЫЕ ТЕМЫ ДЛЯ ОБСУЖДЕНИЯ:")
            sb.appendLine("- ${profile.favoriteTopics.joinToString(", ")}")
        }

        // Темы для избегания
        if (profile.avoidTopics.isNotEmpty()) {
            sb.appendLine("\n🚫 ИЗБЕГАЙ ТЕМ:")
            sb.appendLine("- ${profile.avoidTopics.joinToString(", ")}")
        }

        // Предпочтения общения
        sb.appendLine("\n🗣️ СТИЛЬ ОБЩЕНИЯ:")
        val prefs = profile.communicationPreferences
        sb.appendLine("- Стиль: ${when(prefs.style) {
            "formal" -> "формальный"
            "casual" -> "повседневный"
            "friendly" -> "дружелюбный"
            else -> prefs.style
        }}")
        sb.appendLine("- Длина ответов: ${when(prefs.responseLength) {
            "short" -> "краткие"
            "medium" -> "средние"
            "detailed" -> "подробные"
            else -> prefs.responseLength
        }}")
        sb.appendLine("- Эмодзи: ${if (prefs.useEmoji) "да" else "нет"}")
        sb.appendLine("- Юмор: ${if (prefs.useHumor) "уместен" else "не использовать"}")
        sb.appendLine("- Обращение: ${if (prefs.formalAddress) "на \"вы\"" else "на \"ты\""}")

        // Рабочее расписание
        val schedule = profile.workSchedule
        sb.appendLine("\n⏰ РАСПИСАНИЕ:")
        sb.appendLine("- Рабочие часы: ${schedule.workStartTime} - ${schedule.workEndTime}")
        sb.appendLine("- Продуктивное время: ${when(schedule.productiveHours) {
            "morning" -> "утро"
            "afternoon" -> "день"
            "evening" -> "вечер"
            "night" -> "ночь"
            else -> schedule.productiveHours
        }}")

        // Дополнительные инструкции
        if (profile.customInstructions.isNotEmpty()) {
            sb.appendLine("\n📌 ОСОБЫЕ ИНСТРУКЦИИ:")
            profile.customInstructions.forEach { sb.appendLine("- $it") }
        }

        sb.appendLine("\n=== КОНЕЦ ПЕРСОНАЛИЗАЦИИ ===")

        return sb.toString()
    }

    /**
     * Создаёт пример файла профиля
     */
    private fun createExampleProfile(file: File) {
        val example = UserProfile(
            name = "Иван",
            nickname = "Ваня",
            profession = "Kotlin разработчик",
            technicalLevel = TechnicalLevel.ADVANCED,
            interests = listOf(
                "Программирование на Kotlin",
                "Android разработка",
                "AI и машинное обучение",
                "Чтение технической литературы"
            ),
            habits = listOf(
                "Пью кофе по утрам",
                "Предпочитаю работать в тишине",
                "Делаю перерывы каждые 2 часа"
            ),
            communicationPreferences = CommunicationPreferences(
                language = "ru",
                style = "friendly",
                responseLength = "medium",
                useEmoji = true,
                useHumor = true,
                formalAddress = false
            ),
            location = "Москва",
            timezone = "Europe/Moscow",
            workSchedule = WorkSchedule(
                workStartTime = "09:00",
                workEndTime = "18:00",
                workDays = listOf(1, 2, 3, 4, 5),
                productiveHours = "morning"
            ),
            currentGoals = listOf(
                "Изучить Kotlin Multiplatform",
                "Завершить проект AI Agent",
                "Улучшить навыки в ML"
            ),
            techStack = listOf(
                "Kotlin", "Android", "Ktor", "Jetpack Compose",
                "Git", "IntelliJ IDEA", "Gradle"
            ),
            favoriteTopics = listOf(
                "Архитектура приложений",
                "Clean Code",
                "Новые технологии"
            ),
            avoidTopics = listOf(
                "Политика",
                "Религия"
            ),
            customInstructions = listOf(
                "Если я спрашиваю о коде, предпочитаю видеть примеры на Kotlin",
                "Напоминай о перерывах, если работаем долго",
                "Предлагай оптимизации кода, когда видишь возможность"
            ),
            contacts = UserContacts(
                github = "username",
                telegram = "@username",
                email = "user@example.com"
            ),
            importantDates = listOf(
                ImportantDate("День рождения", "01-15", true),
                ImportantDate("Дедлайн проекта", "2025-03-01", false)
            )
        )

        try {
            file.writeText(json.encodeToString(UserProfile.serializer(), example))
            println("✅ Создан пример профиля: $PROFILE_FILE")
            println("   Отредактируйте файл под себя и перезапустите приложение")
        } catch (e: Exception) {
            println("⚠️ Не удалось создать пример профиля: ${e.message}")
        }
    }

    /**
     * Сбрасывает кэш профиля (для перезагрузки)
     */
    fun reloadProfile() {
        cachedProfile = null
        loadProfile()
    }
}
