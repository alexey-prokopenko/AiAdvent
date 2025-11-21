#!/bin/bash

# Скрипт для запуска OpenWeatherMap MCP Server (Kotlin) через HTTP Proxy

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Запуск OpenWeatherMap MCP Server (Kotlin)${NC}"

# Проверка наличия Gradle
if ! command -v ./gradlew &> /dev/null && ! command -v gradle &> /dev/null; then
    echo -e "${RED}❌ Ошибка: Gradle не найден${NC}"
    echo "Убедитесь, что gradlew доступен в корне проекта"
    exit 1
fi

# Проверка наличия Node.js для HTTP прокси
if ! command -v node &> /dev/null; then
    echo -e "${RED}❌ Ошибка: Node.js не установлен${NC}"
    echo "Установите Node.js: https://nodejs.org/"
    exit 1
fi

# Порт (можно передать как аргумент, по умолчанию 3002)
PORT=${1:-3002}

echo -e "${GREEN}✅ Gradle найден${NC}"
echo -e "${GREEN}✅ Node.js установлен${NC}"
echo ""
echo -e "Порт: ${YELLOW}${PORT}${NC}"
echo -e "Для эмулятора Android используйте: ${YELLOW}http://10.0.2.2:${PORT}${NC}"
echo -e "Для реального устройства используйте: ${YELLOW}http://$(ipconfig getifaddr en0 2>/dev/null || echo 'YOUR_IP'):${PORT}${NC}"
echo ""
echo -e "${GREEN}Сборка Kotlin MCP сервера...${NC}"

# Собираем JAR файл (исключаем тесты, так как они отключены)
if command -v ./gradlew &> /dev/null; then
    ./gradlew :mcp-server:jar -x test -x check
else
    gradle :mcp-server:jar -x test -x check
fi

JAR_FILE="mcp-server/build/libs/mcp-server-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}❌ Ошибка: JAR файл не найден: $JAR_FILE${NC}"
    exit 1
fi

echo -e "${GREEN}✅ JAR файл собран: $JAR_FILE${NC}"
echo ""
echo -e "${GREEN}Запуск сервера...${NC}"
echo ""

# Запуск HTTP прокси с Kotlin MCP сервером для погоды
# Используем java с указанием главного класса WeatherMcpServerKt
# JAR файл уже содержит все зависимости, используем -cp для указания нужного main класса
node mcp-http-proxy.js "$PORT" java -cp "$JAR_FILE" com.example.aiadvent.mcp.WeatherMcpServerKt

