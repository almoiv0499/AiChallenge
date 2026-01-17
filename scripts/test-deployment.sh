#!/bin/bash

# Скрипт для проверки работы деплоя
# Использование: ./scripts/test-deployment.sh [URL]

set -e

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# URL по умолчанию (можно передать как аргумент)
DEPLOYMENT_URL="${1:-http://localhost:8080}"

echo -e "${YELLOW}🧪 Тестирование деплоя на ${DEPLOYMENT_URL}${NC}"
echo ""

# Функция для проверки endpoint
check_endpoint() {
    local endpoint=$1
    local description=$2
    
    echo -n "Проверка $description... "
    
    response=$(curl -s -w "\n%{http_code}" "${DEPLOYMENT_URL}${endpoint}" || echo -e "\n000")
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}✅ OK${NC}"
        echo "   Ответ: $body" | head -c 100
        echo ""
        return 0
    else
        echo -e "${RED}❌ FAILED (HTTP $http_code)${NC}"
        return 1
    fi
}

# Проверка health endpoint
check_endpoint "/api/health" "Health Check"

# Проверка тестового endpoint деплоя
check_endpoint "/api/deployment/test" "Deployment Test"

# Проверка версии
check_endpoint "/api/version" "Version Info"

echo ""
echo -e "${GREEN}✅ Все проверки завершены!${NC}"
