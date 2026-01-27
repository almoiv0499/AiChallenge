package org.example.config

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Интерактивный мастер настройки профиля пользователя.
 * Задаёт вопросы и заполняет user_profile.json на основе ответов.
 */
class ProfileSetupWizard {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private var currentProfile = UserProfileConfig.loadProfile()
    private var currentStep = 0
    private var isActive = false

    // Шаги мастера настройки
    private val steps = listOf(
        SetupStep.NAME,
        SetupStep.NICKNAME,
        SetupStep.PROFESSION,
        SetupStep.TECHNICAL_LEVEL,
        SetupStep.LOCATION,
        SetupStep.INTERESTS,
        SetupStep.HABITS,
        SetupStep.TECH_STACK,
        SetupStep.GOALS,
        SetupStep.COMMUNICATION_STYLE,
        SetupStep.RESPONSE_LENGTH,
        SetupStep.USE_EMOJI,
        SetupStep.FORMAL_ADDRESS,
        SetupStep.WORK_HOURS,
        SetupStep.FAVORITE_TOPICS,
        SetupStep.AVOID_TOPICS,
        SetupStep.CUSTOM_INSTRUCTIONS,
        SetupStep.CONFIRM
    )

    enum class SetupStep {
        NAME,
        NICKNAME,
        PROFESSION,
        TECHNICAL_LEVEL,
        LOCATION,
        INTERESTS,
        HABITS,
        TECH_STACK,
        GOALS,
        COMMUNICATION_STYLE,
        RESPONSE_LENGTH,
        USE_EMOJI,
        FORMAL_ADDRESS,
        WORK_HOURS,
        FAVORITE_TOPICS,
        AVOID_TOPICS,
        CUSTOM_INSTRUCTIONS,
        CONFIRM
    }

    /**
     * Запускает мастер настройки профиля
     */
    fun start(): String {
        isActive = true
        currentStep = 0
        currentProfile = UserProfile() // Начинаем с чистого профиля
        return getQuestionForCurrentStep()
    }

    /**
     * Продолжает настройку с текущего профиля
     */
    fun continueSetup(): String {
        isActive = true
        currentStep = 0
        currentProfile = UserProfileConfig.loadProfile()
        return getQuestionForCurrentStep()
    }

    /**
     * Проверяет, активен ли мастер настройки
     */
    fun isActive(): Boolean = isActive

    /**
     * Обрабатывает ответ пользователя и возвращает следующий вопрос или завершение
     */
    fun processAnswer(answer: String): String {
        if (!isActive) {
            return "❌ Мастер настройки не активен. Используйте /profile setup для начала."
        }

        val trimmedAnswer = answer.trim()

        // Проверка на команду пропуска
        if (trimmedAnswer.lowercase() in listOf("пропустить", "skip", "-", "")) {
            return skipCurrentStep()
        }

        // Проверка на команду отмены
        if (trimmedAnswer.lowercase() in listOf("отмена", "cancel", "выход", "exit")) {
            isActive = false
            return "❌ Настройка профиля отменена. Изменения не сохранены."
        }

        // Обрабатываем ответ для текущего шага
        val result = processStepAnswer(trimmedAnswer)

        if (result.success) {
            currentStep++

            // Сохраняем промежуточный результат
            saveProfile()

            if (currentStep >= steps.size) {
                isActive = false
                return """
                    |✅ Настройка профиля завершена!
                    |
                    |${getProfileSummary()}
                    |
                    |💡 Профиль сохранён в user_profile.json
                    |   Используйте /profile для просмотра
                    |   Используйте /profile setup для повторной настройки
                """.trimMargin()
            }

            return result.message + "\n\n" + getQuestionForCurrentStep()
        } else {
            return result.message + "\n\n" + getQuestionForCurrentStep()
        }
    }

    /**
     * Пропускает текущий шаг
     */
    private fun skipCurrentStep(): String {
        currentStep++

        if (currentStep >= steps.size) {
            isActive = false
            saveProfile()
            return """
                |✅ Настройка профиля завершена!
                |
                |${getProfileSummary()}
            """.trimMargin()
        }

        return "⏭️ Пропущено.\n\n" + getQuestionForCurrentStep()
    }

    /**
     * Возвращает вопрос для текущего шага
     */
    private fun getQuestionForCurrentStep(): String {
        if (currentStep >= steps.size) {
            return "Настройка завершена!"
        }

        val step = steps[currentStep]
        val progress = "📊 Шаг ${currentStep + 1}/${steps.size}"
        val skipHint = "\n💡 Введите 'пропустить' чтобы пропустить вопрос"

        return when (step) {
            SetupStep.NAME -> """
                |$progress
                |
                |👤 Как тебя зовут?
                |
                |Введи своё имя (например: Александр, Маша, Дмитрий)
                |$skipHint
            """.trimMargin()

            SetupStep.NICKNAME -> """
                |$progress
                |
                |🏷️ Как мне к тебе обращаться?
                |
                |Введи короткое имя или никнейм (например: Саша, Маш, Дима)
                |Текущее имя: ${currentProfile.name}
                |$skipHint
            """.trimMargin()

            SetupStep.PROFESSION -> """
                |$progress
                |
                |💼 Кем ты работаешь / чем занимаешься?
                |
                |Например: Android разработчик, студент, менеджер проектов
                |$skipHint
            """.trimMargin()

            SetupStep.TECHNICAL_LEVEL -> """
                |$progress
                |
                |🎓 Какой у тебя технический уровень?
                |
                |Выбери один из вариантов:
                |  1. Начинающий (BEGINNER) - нужны подробные объяснения
                |  2. Средний (INTERMEDIATE) - базовые концепции понятны
                |  3. Продвинутый (ADVANCED) - можно использовать технический язык
                |  4. Эксперт (EXPERT) - минимум объяснений, максимум деталей
                |
                |Введи номер (1-4) или название уровня
                |$skipHint
            """.trimMargin()

            SetupStep.LOCATION -> """
                |$progress
                |
                |📍 Где ты находишься?
                |
                |Укажи город (например: Москва, Санкт-Петербург, Новосибирск)
                |Это поможет с погодой и временем
                |$skipHint
            """.trimMargin()

            SetupStep.INTERESTS -> """
                |$progress
                |
                |🎯 Какие у тебя интересы?
                |
                |Перечисли через запятую (например: программирование, музыка, спорт, книги)
                |$skipHint
            """.trimMargin()

            SetupStep.HABITS -> """
                |$progress
                |
                |📋 Расскажи о своих привычках в работе
                |
                |Перечисли через запятую (например: пью кофе утром, работаю в тишине, делаю перерывы каждый час)
                |$skipHint
            """.trimMargin()

            SetupStep.TECH_STACK -> """
                |$progress
                |
                |💻 Какие технологии и инструменты ты используешь?
                |
                |Перечисли через запятую (например: Kotlin, Python, Git, VS Code, Docker)
                |$skipHint
            """.trimMargin()

            SetupStep.GOALS -> """
                |$progress
                |
                |🎯 Какие у тебя текущие цели?
                |
                |Перечисли через запятую (например: выучить Kotlin, сдать экзамен, запустить проект)
                |$skipHint
            """.trimMargin()

            SetupStep.COMMUNICATION_STYLE -> """
                |$progress
                |
                |🗣️ Какой стиль общения тебе нравится?
                |
                |Выбери:
                |  1. Формальный (formal) - вежливый, деловой стиль
                |  2. Повседневный (casual) - обычный разговорный
                |  3. Дружелюбный (friendly) - тёплый, с юмором
                |
                |Введи номер (1-3) или название стиля
                |$skipHint
            """.trimMargin()

            SetupStep.RESPONSE_LENGTH -> """
                |$progress
                |
                |📝 Какую длину ответов предпочитаешь?
                |
                |Выбери:
                |  1. Краткие (short) - только суть
                |  2. Средние (medium) - сбалансированные ответы
                |  3. Подробные (detailed) - максимум деталей
                |
                |Введи номер (1-3) или название
                |$skipHint
            """.trimMargin()

            SetupStep.USE_EMOJI -> """
                |$progress
                |
                |😀 Использовать эмодзи в ответах?
                |
                |Введи: да / нет
                |$skipHint
            """.trimMargin()

            SetupStep.FORMAL_ADDRESS -> """
                |$progress
                |
                |👔 Как мне к тебе обращаться?
                |
                |  1. На "ты" - неформально
                |  2. На "вы" - формально
                |
                |Введи: ты / вы (или 1 / 2)
                |$skipHint
            """.trimMargin()

            SetupStep.WORK_HOURS -> """
                |$progress
                |
                |⏰ Во сколько ты обычно работаешь?
                |
                |Введи в формате: 09:00-18:00
                |Или просто время начала и конца через дефис
                |$skipHint
            """.trimMargin()

            SetupStep.FAVORITE_TOPICS -> """
                |$progress
                |
                |💬 Какие темы тебе интересно обсуждать?
                |
                |Перечисли через запятую (например: архитектура кода, новые технологии, оптимизация)
                |$skipHint
            """.trimMargin()

            SetupStep.AVOID_TOPICS -> """
                |$progress
                |
                |🚫 Какие темы лучше избегать?
                |
                |Перечисли через запятую (например: политика, религия)
                |$skipHint
            """.trimMargin()

            SetupStep.CUSTOM_INSTRUCTIONS -> """
                |$progress
                |
                |📌 Есть ли особые инструкции для меня?
                |
                |Например: "показывай примеры на Kotlin", "напоминай о перерывах", "предлагай оптимизации кода"
                |Перечисли через запятую
                |$skipHint
            """.trimMargin()

            SetupStep.CONFIRM -> """
                |$progress
                |
                |✅ Давай проверим твой профиль:
                |
                |${getProfileSummary()}
                |
                |Всё верно? Введи 'да' для сохранения или 'нет' для отмены
            """.trimMargin()
        }
    }

    /**
     * Обрабатывает ответ для конкретного шага
     */
    private fun processStepAnswer(answer: String): StepResult {
        val step = steps[currentStep]

        return when (step) {
            SetupStep.NAME -> {
                if (answer.length < 2) {
                    StepResult(false, "❌ Имя слишком короткое. Введи хотя бы 2 символа.")
                } else {
                    currentProfile = currentProfile.copy(name = answer)
                    StepResult(true, "✅ Отлично, ${answer}!")
                }
            }

            SetupStep.NICKNAME -> {
                currentProfile = currentProfile.copy(nickname = answer)
                StepResult(true, "✅ Буду обращаться к тебе: $answer")
            }

            SetupStep.PROFESSION -> {
                currentProfile = currentProfile.copy(profession = answer)
                StepResult(true, "✅ Записал: $answer")
            }

            SetupStep.TECHNICAL_LEVEL -> {
                val level = parseTechnicalLevel(answer)
                if (level != null) {
                    currentProfile = currentProfile.copy(technicalLevel = level)
                    StepResult(true, "✅ Технический уровень: $level")
                } else {
                    StepResult(false, "❌ Не понял. Введи число от 1 до 4 или название уровня.")
                }
            }

            SetupStep.LOCATION -> {
                currentProfile = currentProfile.copy(location = answer)
                StepResult(true, "✅ Локация: $answer")
            }

            SetupStep.INTERESTS -> {
                val interests = parseCommaSeparatedList(answer)
                currentProfile = currentProfile.copy(interests = interests)
                StepResult(true, "✅ Записал ${interests.size} интересов")
            }

            SetupStep.HABITS -> {
                val habits = parseCommaSeparatedList(answer)
                currentProfile = currentProfile.copy(habits = habits)
                StepResult(true, "✅ Записал ${habits.size} привычек")
            }

            SetupStep.TECH_STACK -> {
                val techStack = parseCommaSeparatedList(answer)
                currentProfile = currentProfile.copy(techStack = techStack)
                StepResult(true, "✅ Записал ${techStack.size} технологий")
            }

            SetupStep.GOALS -> {
                val goals = parseCommaSeparatedList(answer)
                currentProfile = currentProfile.copy(currentGoals = goals)
                StepResult(true, "✅ Записал ${goals.size} целей")
            }

            SetupStep.COMMUNICATION_STYLE -> {
                val style = parseCommunicationStyle(answer)
                if (style != null) {
                    currentProfile = currentProfile.copy(
                        communicationPreferences = currentProfile.communicationPreferences.copy(style = style)
                    )
                    StepResult(true, "✅ Стиль общения: $style")
                } else {
                    StepResult(false, "❌ Не понял. Введи число от 1 до 3 или название стиля.")
                }
            }

            SetupStep.RESPONSE_LENGTH -> {
                val length = parseResponseLength(answer)
                if (length != null) {
                    currentProfile = currentProfile.copy(
                        communicationPreferences = currentProfile.communicationPreferences.copy(responseLength = length)
                    )
                    StepResult(true, "✅ Длина ответов: $length")
                } else {
                    StepResult(false, "❌ Не понял. Введи число от 1 до 3 или название.")
                }
            }

            SetupStep.USE_EMOJI -> {
                val useEmoji = parseYesNo(answer)
                if (useEmoji != null) {
                    currentProfile = currentProfile.copy(
                        communicationPreferences = currentProfile.communicationPreferences.copy(useEmoji = useEmoji)
                    )
                    StepResult(true, if (useEmoji) "✅ Буду использовать эмодзи 😊" else "✅ Без эмодзи")
                } else {
                    StepResult(false, "❌ Не понял. Введи 'да' или 'нет'.")
                }
            }

            SetupStep.FORMAL_ADDRESS -> {
                val formal = parseFormalAddress(answer)
                if (formal != null) {
                    currentProfile = currentProfile.copy(
                        communicationPreferences = currentProfile.communicationPreferences.copy(formalAddress = formal)
                    )
                    StepResult(true, if (formal) "✅ Буду обращаться на \"вы\"" else "✅ Буду обращаться на \"ты\"")
                } else {
                    StepResult(false, "❌ Не понял. Введи 'ты' или 'вы'.")
                }
            }

            SetupStep.WORK_HOURS -> {
                val hours = parseWorkHours(answer)
                if (hours != null) {
                    currentProfile = currentProfile.copy(
                        workSchedule = currentProfile.workSchedule.copy(
                            workStartTime = hours.first,
                            workEndTime = hours.second
                        )
                    )
                    StepResult(true, "✅ Рабочие часы: ${hours.first} - ${hours.second}")
                } else {
                    StepResult(false, "❌ Не понял формат. Введи в формате: 09:00-18:00")
                }
            }

            SetupStep.FAVORITE_TOPICS -> {
                val topics = parseCommaSeparatedList(answer)
                currentProfile = currentProfile.copy(favoriteTopics = topics)
                StepResult(true, "✅ Записал ${topics.size} любимых тем")
            }

            SetupStep.AVOID_TOPICS -> {
                val topics = parseCommaSeparatedList(answer)
                currentProfile = currentProfile.copy(avoidTopics = topics)
                StepResult(true, "✅ Записал ${topics.size} тем для избегания")
            }

            SetupStep.CUSTOM_INSTRUCTIONS -> {
                val instructions = parseCommaSeparatedList(answer)
                currentProfile = currentProfile.copy(customInstructions = instructions)
                StepResult(true, "✅ Записал ${instructions.size} инструкций")
            }

            SetupStep.CONFIRM -> {
                val confirmed = parseYesNo(answer)
                if (confirmed == true) {
                    StepResult(true, "")
                } else if (confirmed == false) {
                    isActive = false
                    StepResult(false, "❌ Настройка отменена. Изменения не сохранены.")
                } else {
                    StepResult(false, "❌ Введи 'да' для сохранения или 'нет' для отмены.")
                }
            }
        }
    }

    // ============ Парсеры ============

    private fun parseCommaSeparatedList(input: String): List<String> {
        return input.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseTechnicalLevel(input: String): TechnicalLevel? {
        return when (input.lowercase().trim()) {
            "1", "beginner", "начинающий", "новичок" -> TechnicalLevel.BEGINNER
            "2", "intermediate", "средний" -> TechnicalLevel.INTERMEDIATE
            "3", "advanced", "продвинутый" -> TechnicalLevel.ADVANCED
            "4", "expert", "эксперт" -> TechnicalLevel.EXPERT
            else -> null
        }
    }

    private fun parseCommunicationStyle(input: String): String? {
        return when (input.lowercase().trim()) {
            "1", "formal", "формальный" -> "formal"
            "2", "casual", "повседневный", "обычный" -> "casual"
            "3", "friendly", "дружелюбный", "дружеский" -> "friendly"
            else -> null
        }
    }

    private fun parseResponseLength(input: String): String? {
        return when (input.lowercase().trim()) {
            "1", "short", "краткие", "кратко", "короткие" -> "short"
            "2", "medium", "средние", "средне" -> "medium"
            "3", "detailed", "подробные", "подробно", "детальные" -> "detailed"
            else -> null
        }
    }

    private fun parseYesNo(input: String): Boolean? {
        return when (input.lowercase().trim()) {
            "да", "yes", "y", "1", "true", "ага", "угу", "конечно" -> true
            "нет", "no", "n", "0", "false", "не", "неа" -> false
            else -> null
        }
    }

    private fun parseFormalAddress(input: String): Boolean? {
        return when (input.lowercase().trim()) {
            "ты", "1", "неформально" -> false
            "вы", "2", "формально" -> true
            else -> null
        }
    }

    private fun parseWorkHours(input: String): Pair<String, String>? {
        val regex = Regex("""(\d{1,2}):?(\d{2})?\s*[-–]\s*(\d{1,2}):?(\d{2})?""")
        val match = regex.find(input) ?: return null

        val startHour = match.groupValues[1].padStart(2, '0')
        val startMin = match.groupValues[2].ifEmpty { "00" }
        val endHour = match.groupValues[3].padStart(2, '0')
        val endMin = match.groupValues[4].ifEmpty { "00" }

        return "$startHour:$startMin" to "$endHour:$endMin"
    }

    // ============ Утилиты ============

    private fun saveProfile() {
        try {
            val file = File("user_profile.json")
            file.writeText(json.encodeToString(UserProfile.serializer(), currentProfile))
        } catch (e: Exception) {
            println("⚠️ Ошибка сохранения профиля: ${e.message}")
        }
    }

    private fun getProfileSummary(): String {
        val p = currentProfile
        val prefs = p.communicationPreferences

        return """
            |👤 ${p.name.ifEmpty { "(не указано)" }} ${if (p.nickname.isNotEmpty()) "(${p.nickname})" else ""}
            |💼 ${p.profession.ifEmpty { "(не указано)" }}
            |📍 ${p.location.ifEmpty { "(не указано)" }}
            |🎓 ${p.technicalLevel}
            |
            |💻 Технологии: ${p.techStack.joinToString(", ").ifEmpty { "(не указано)" }}
            |🎯 Интересы: ${p.interests.joinToString(", ").ifEmpty { "(не указано)" }}
            |🎯 Цели: ${p.currentGoals.joinToString(", ").ifEmpty { "(не указано)" }}
            |
            |🗣️ Стиль: ${prefs.style}, Длина: ${prefs.responseLength}
            |😀 Эмодзи: ${if (prefs.useEmoji) "да" else "нет"}, Обращение: ${if (prefs.formalAddress) "на вы" else "на ты"}
            |⏰ Работа: ${p.workSchedule.workStartTime} - ${p.workSchedule.workEndTime}
        """.trimMargin()
    }

    private data class StepResult(
        val success: Boolean,
        val message: String
    )

    companion object {
        private var instance: ProfileSetupWizard? = null

        fun getInstance(): ProfileSetupWizard {
            if (instance == null) {
                instance = ProfileSetupWizard()
            }
            return instance!!
        }
    }
}
