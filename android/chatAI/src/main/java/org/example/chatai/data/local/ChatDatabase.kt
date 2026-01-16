package org.example.chatai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import org.example.chatai.data.model.ChatMessage
import org.example.chatai.data.model.MessageRole
import org.example.chatai.data.model.DocumentEntity
import org.example.chatai.data.model.DocumentChunkEntity

/**
 * Room база данных для хранения истории чата и RAG индекса.
 * Использует миграции для обновления схемы.
 */
@Database(
    entities = [
        ChatMessage::class,
        DocumentEntity::class,
        DocumentChunkEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(MessageRoleConverter::class)
abstract class ChatDatabase : RoomDatabase() {
    
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun documentDao(): DocumentDao
    abstract fun documentChunkDao(): DocumentChunkDao
    
    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null
        
        private const val DATABASE_NAME = "chat_database"
        
        /**
         * Получить или создать экземпляр базы данных.
         * Использует паттерн Singleton для обеспечения единственного экземпляра.
         */
        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // Для разработки - удаляет данные при миграции
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Очистить экземпляр базы данных (для тестирования)
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}

/**
 * Конвертер для преобразования MessageRole в String для Room
 */
object MessageRoleConverter {
    @TypeConverter
    fun fromRole(role: MessageRole): String {
        return role.name
    }
    
    @TypeConverter
    fun toRole(roleString: String): MessageRole {
        return MessageRole.valueOf(roleString)
    }
}
