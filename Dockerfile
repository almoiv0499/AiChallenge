# Многоэтапная сборка для оптимизации размера образа
FROM gradle:8.5-jdk17 AS build

# Установка рабочей директории
WORKDIR /app

# Копирование файлов конфигурации Gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/ ./gradle/

# Копирование исходного кода
COPY jvm/build.gradle.kts ./jvm/
COPY src/ ./src/

# Сборка приложения (загрузка зависимостей и компиляция)
RUN gradle build --no-daemon -x test

# Финальный образ для запуска
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Копирование собранного JAR файла с манифестом
COPY --from=build /app/build/libs/*.jar app.jar

# Копирование VERSION файла для endpoints
COPY VERSION VERSION

# Создание директории для данных с правами записи (до создания пользователя)
RUN mkdir -p /app/data /tmp/data && chmod 777 /app/data /tmp/data

# Создание пользователя для безопасности
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# Даем пользователю права на запись в директории данных и /tmp
RUN chown -R appuser:appgroup /app/data /tmp/data
USER appuser

# Переменные окружения
ENV PORT=8080
ENV JAVA_OPTS="-Xmx512m -Xms256m"
# Переменная окружения для пути к базам данных (использует /tmp по умолчанию)
ENV DB_PATH=/tmp

# Экспонирование порта
EXPOSE $PORT

# Health check (если wget недоступен, используем curl или отключаем)
# HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
#   CMD wget --no-verbose --tries=1 --spider http://localhost:$PORT/health || exit 1

# Запуск приложения
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
