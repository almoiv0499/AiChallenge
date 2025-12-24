package org.example.embedding

/**
 * Разбивает текст на чанки для индексации.
 * Использует стратегию разбивки по предложениям и абзацам.
 */
class TextChunker(
    private val chunkSize: Int = 100,
    private val chunkOverlap: Int = 200
) {
    /**
     * Разбивает текст на чанки.
     * @param text Исходный текст
     * @param metadata Метаданные для чанков (например, источник документа)
     * @return Список текстовых чанков с метаданными
     */
    fun chunkText(text: String, metadata: ChunkMetadata = ChunkMetadata()): List<TextChunk> {
        if (text.isBlank()) return emptyList()
        
        // Разбиваем на абзацы
        val paragraphs = text.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        val chunks = mutableListOf<TextChunk>()
        var currentChunk = StringBuilder()
        var chunkIndex = 0
        
        for (paragraph in paragraphs) {
            // Если абзац сам по себе больше chunkSize, разбиваем его на предложения
            if (paragraph.length > chunkSize) {
                // Сохраняем текущий чанк, если он не пустой
                if (currentChunk.isNotEmpty()) {
                    chunks.add(createChunk(currentChunk.toString(), chunkIndex++, metadata))
                    currentChunk.clear()
                }
                
                // Разбиваем большой абзац на предложения
                val sentences = splitIntoSentences(paragraph)
                for (sentence in sentences) {
                    if (currentChunk.length + sentence.length + 1 > chunkSize) {
                        if (currentChunk.isNotEmpty()) {
                            chunks.add(createChunk(currentChunk.toString(), chunkIndex++, metadata))
                            // Добавляем overlap
                            val overlapText = getOverlapText(currentChunk.toString())
                            currentChunk = StringBuilder(overlapText)
                        }
                    }
                    if (currentChunk.isNotEmpty()) {
                        currentChunk.append(" ")
                    }
                    currentChunk.append(sentence)
                }
            } else {
                // Если добавление абзаца не превышает лимит, добавляем его
                if (currentChunk.length + paragraph.length + 2 > chunkSize) {
                    if (currentChunk.isNotEmpty()) {
                        chunks.add(createChunk(currentChunk.toString(), chunkIndex++, metadata))
                        // Добавляем overlap
                        val overlapText = getOverlapText(currentChunk.toString())
                        currentChunk = StringBuilder(overlapText)
                    }
                }
                if (currentChunk.isNotEmpty()) {
                    currentChunk.append("\n\n")
                }
                currentChunk.append(paragraph)
            }
        }
        
        // Добавляем последний чанк
        if (currentChunk.isNotEmpty()) {
            chunks.add(createChunk(currentChunk.toString(), chunkIndex, metadata))
        }
        
        return chunks
    }
    
    private fun splitIntoSentences(text: String): List<String> {
        // Простое разбиение по знакам препинания
        val sentences = mutableListOf<String>()
        val sentenceEndings = listOf(". ", "! ", "? ", ".\n", "!\n", "?\n")
        
        var currentPos = 0
        while (currentPos < text.length) {
            var nextPos = text.length
            for (ending in sentenceEndings) {
                val pos = text.indexOf(ending, currentPos)
                if (pos != -1 && pos < nextPos) {
                    nextPos = pos + ending.length
                }
            }
            
            if (nextPos < text.length) {
                sentences.add(text.substring(currentPos, nextPos).trim())
                currentPos = nextPos
            } else {
                sentences.add(text.substring(currentPos).trim())
                break
            }
        }
        
        return sentences.filter { it.isNotBlank() }
    }
    
    private fun getOverlapText(text: String): String {
        if (text.length <= chunkOverlap) return text
        // Берем последние chunkOverlap символов, начиная с границы слова
        val startPos = text.length - chunkOverlap
        val overlapStart = text.indexOf(' ', startPos).let { 
            if (it == -1) startPos else it + 1
        }
        return text.substring(overlapStart)
    }
    
    private fun createChunk(text: String, index: Int, metadata: ChunkMetadata): TextChunk {
        return TextChunk(
            text = text,
            index = index,
            source = metadata.source,
            metadata = metadata.additionalMetadata
        )
    }
}

/**
 * Метаданные для чанка текста.
 */
data class ChunkMetadata(
    val source: String = "unknown",
    val additionalMetadata: Map<String, String> = emptyMap()
)

/**
 * Представляет один чанк текста.
 */
data class TextChunk(
    val text: String,
    val index: Int,
    val source: String,
    val metadata: Map<String, String> = emptyMap()
)


