#!/bin/bash

# Скрипт для развертывания чат-сервера на VPS Ubuntu
# Использование: ./deploy-chat-server.sh

set -e

echo "🚀 Развертывание чат-сервера на VPS..."

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Проверка, что скрипт запущен от root или с sudo
if [ "$EUID" -ne 0 ]; then 
    echo -e "${RED}Пожалуйста, запустите скрипт с sudo${NC}"
    exit 1
fi

# 1. Установка Java 17
echo -e "${GREEN}📦 Установка Java 17...${NC}"
apt-get update
apt-get install -y openjdk-17-jdk

# 2. Установка Ollama
echo -e "${GREEN}📦 Установка Ollama...${NC}"
if ! command -v ollama &> /dev/null; then
    curl -fsSL https://ollama.ai/install.sh | sh
else
    echo -e "${YELLOW}Ollama уже установлен${NC}"
fi

# 3. Установка легкой модели (для 1GB RAM)
echo -e "${GREEN}📦 Установка модели phi-2 (легкая модель для 1GB RAM)...${NC}"
ollama pull phi-2

# 4. Создание пользователя для сервиса
echo -e "${GREEN}👤 Создание пользователя для сервиса...${NC}"
if ! id -u aichat &>/dev/null; then
    useradd -r -s /bin/bash -m -d /home/aichat aichat
    echo -e "${GREEN}Пользователь aichat создан${NC}"
else
    echo -e "${YELLOW}Пользователь aichat уже существует${NC}"
fi

# 5. Создание директорий
echo -e "${GREEN}📁 Создание директорий...${NC}"
mkdir -p /opt/aichat
mkdir -p /var/log/aichat
mkdir -p /home/aichat/.ollama
chown -R aichat:aichat /opt/aichat
chown -R aichat:aichat /var/log/aichat
chown -R aichat:aichat /home/aichat/.ollama

# 6. Копирование файлов (предполагается, что JAR уже собран)
echo -e "${GREEN}📋 Копирование файлов...${NC}"
# Если JAR файл существует в текущей директории
if [ -f "build/libs/AiChallenge-1.0-SNAPSHOT.jar" ]; then
    cp build/libs/AiChallenge-1.0-SNAPSHOT.jar /opt/aichat/chat-server.jar
    chown aichat:aichat /opt/aichat/chat-server.jar
    echo -e "${GREEN}JAR файл скопирован${NC}"
else
    echo -e "${YELLOW}JAR файл не найден. Соберите проект: ./gradlew jar${NC}"
fi

# 7. Создание systemd service для Ollama
echo -e "${GREEN}⚙️ Создание systemd service для Ollama...${NC}"
cat > /etc/systemd/system/ollama.service << 'EOF'
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
EOF

# 8. Создание systemd service для чат-сервера
echo -e "${GREEN}⚙️ Создание systemd service для чат-сервера...${NC}"
cat > /etc/systemd/system/aichat-server.service << 'EOF'
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
Environment="OLLAMA_MODEL=phi-2"
Environment="DB_PATH=/opt/aichat/data"
ExecStart=/usr/bin/java -jar /opt/aichat/chat-server.jar org.example.chat.ChatServerMainKt
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# 9. Создание директории для данных
mkdir -p /opt/aichat/data
chown aichat:aichat /opt/aichat/data

# 10. Настройка firewall (если установлен ufw)
if command -v ufw &> /dev/null; then
    echo -e "${GREEN}🔥 Настройка firewall...${NC}"
    ufw allow 8080/tcp
    ufw allow 11434/tcp
fi

# 11. Запуск сервисов
echo -e "${GREEN}🚀 Запуск сервисов...${NC}"
systemctl daemon-reload
systemctl enable ollama.service
systemctl enable aichat-server.service
systemctl start ollama.service
sleep 5  # Ждем запуска Ollama
systemctl start aichat-server.service

# 12. Проверка статуса
echo -e "${GREEN}✅ Проверка статуса сервисов...${NC}"
sleep 3
systemctl status ollama.service --no-pager -l
echo ""
systemctl status aichat-server.service --no-pager -l

echo ""
echo -e "${GREEN}✅ Развертывание завершено!${NC}"
echo ""
echo "📋 Полезные команды:"
echo "   • Проверить статус: systemctl status aichat-server"
echo "   • Посмотреть логи: journalctl -u aichat-server -f"
echo "   • Перезапустить: systemctl restart aichat-server"
echo "   • Остановить: systemctl stop aichat-server"
echo ""
echo "🌐 Веб-интерфейс будет доступен по адресу: http://YOUR_SERVER_IP:8080"
echo "📡 API endpoint: http://YOUR_SERVER_IP:8080/api"
