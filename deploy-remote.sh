#!/bin/bash
set -e

SERVER_IP="${1:-83.166.246.106}"
MODEL="${2:-llama3.2}"

echo "🚀 Начало развертывания на $SERVER_IP..."

# 1. Обновление системы
echo "📦 Обновление системы..."
apt-get update -qq

# 2. Установка Java 17
echo "📦 Установка Java 17..."
if ! command -v java &> /dev/null || ! java -version 2>&1 | grep -q "17"; then
    apt-get install -y openjdk-17-jdk
else
    echo "✅ Java 17 уже установлена"
fi

# 3. Установка Ollama
echo "📦 Установка Ollama..."
if ! command -v ollama &> /dev/null; then
    curl -fsSL https://ollama.ai/install.sh | sh
else
    echo "✅ Ollama уже установлен"
fi

# 4. Установка модели
echo "📦 Установка модели $MODEL..."
ollama pull "$MODEL" || echo "⚠️ Модель уже установлена или произошла ошибка"

# 5. Создание пользователя
echo "👤 Создание пользователя..."
if ! id -u aichat &>/dev/null; then
    useradd -r -s /bin/bash -m -d /home/aichat aichat
    echo "✅ Пользователь aichat создан"
else
    echo "✅ Пользователь aichat уже существует"
fi

# 6. Создание директорий
echo "📁 Создание директорий..."
mkdir -p /opt/aichat/data
mkdir -p /var/log/aichat
chown -R aichat:aichat /opt/aichat
chown -R aichat:aichat /var/log/aichat

# 7. Копирование JAR файла
echo "📋 Копирование JAR файла..."
if [ -f /tmp/AiChallenge-1.0-SNAPSHOT.jar ]; then
    cp /tmp/AiChallenge-1.0-SNAPSHOT.jar /opt/aichat/chat-server.jar
    chown aichat:aichat /opt/aichat/chat-server.jar
    echo "✅ JAR файл скопирован"
else
    echo "⚠️ JAR файл не найден в /tmp/"
fi

# 8. Создание systemd service для Ollama
echo "⚙️ Создание systemd service для Ollama..."
cat > /etc/systemd/system/ollama.service << 'EOFSERVICE'
[Unit]
Description=Ollama Service
After=network.target

[Service]
Type=simple
User=aichat
Environment="HOME=/home/aichat"
Environment="OLLAMA_HOST=0.0.0.0:11434"
ExecStart=/usr/local/bin/ollama serve
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOFSERVICE

# 9. Создание systemd service для чат-сервера
echo "⚙️ Создание systemd service для чат-сервера..."
cat > /etc/systemd/system/aichat-server.service << EOFSERVICE
[Unit]
Description=AI Chat Server
After=network.target ollama.service
Requires=ollama.service

[Service]
Type=simple
User=aichat
WorkingDirectory=/opt/aichat
Environment="PORT=8080"
Environment="OLLAMA_BASE_URL=http://localhost:11434/api"
Environment="OLLAMA_MODEL=$MODEL"
Environment="DB_PATH=/opt/aichat/data"
ExecStart=/usr/bin/java -jar /opt/aichat/chat-server.jar org.example.chat.ChatServerMainKt
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOFSERVICE

# 10. Настройка firewall (если установлен ufw)
if command -v ufw &> /dev/null; then
    echo "🔥 Настройка firewall..."
    ufw allow 8080/tcp || true
    ufw allow 11434/tcp || true
fi

# 11. Запуск сервисов
echo "🚀 Запуск сервисов..."
systemctl daemon-reload
systemctl enable ollama.service
systemctl enable aichat-server.service
systemctl restart ollama.service
sleep 5
systemctl restart aichat-server.service

echo "✅ Развертывание завершено!"
echo ""
echo "📋 Полезные команды:"
echo "   • Проверить статус: systemctl status aichat-server"
echo "   • Посмотреть логи: journalctl -u aichat-server -f"
echo "   • Перезапустить: systemctl restart aichat-server"
echo ""
echo "🌐 Веб-интерфейс: http://$SERVER_IP:8080"
