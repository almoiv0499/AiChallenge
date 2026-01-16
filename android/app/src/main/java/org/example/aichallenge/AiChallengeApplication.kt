package org.example.aichallenge

import android.app.Application
import org.example.aichallenge.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Application класс для инициализации Koin DI.
 * Инициализация здесь гарантирует, что Koin запустится только один раз за весь жизненный цикл приложения.
 */
class AiChallengeApplication : Application() {
    
    /**
     * Читает API ключ из BuildConfig (который заполняется из local.properties во время сборки)
     */
    private fun readApiKeyFromLocalProperties(): String? {
        return try {
            BuildConfig.OPENROUTER_API_KEY.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Инициализация Koin в Application (выполняется только один раз)
        startKoin {
            androidLogger()
            androidContext(this@AiChallengeApplication)
            modules(listOf(appModule))
            // Устанавливаем API ключ из local.properties
            val apiKey = readApiKeyFromLocalProperties()
                ?: throw IllegalStateException(
                    "OPENROUTER_API_KEY не найден в local.properties. " +
                    "Добавьте в local.properties: OPENROUTER_API_KEY=sk-or-v1-ваш_ключ"
                )
            properties(
                mapOf(
                    "OPENROUTER_API_KEY" to apiKey
                )
            )
        }
    }
}
