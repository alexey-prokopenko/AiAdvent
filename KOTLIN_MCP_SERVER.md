# Kotlin MCP Server для NewsAPI

Этот документ описывает Kotlin реализацию MCP сервера для NewsAPI.

## Описание

MCP сервер реализован на Kotlin и предоставляет те же инструменты, что и JavaScript версия:
- Поиск новостных статей по ключевым словам
- Получение топ новостных заголовков
- Получение списка источников новостей

## Преимущества Kotlin версии

- **Типобезопасность**: Статическая типизация Kotlin обеспечивает безопасность типов
- **Производительность**: JVM обеспечивает высокую производительность
- **Единый язык**: Весь проект на Kotlin (Android приложение + MCP сервер)
- **Лучшая интеграция**: Легче интегрировать с Android приложением

## Структура проекта

```
mcp-server/
├── build.gradle.kts          # Конфигурация Gradle
└── src/main/kotlin/
    └── com/example/aiadvent/mcp/
        ├── models.kt          # JSON-RPC и MCP модели
        ├── NewsApiClient.kt   # HTTP клиент для NewsAPI
        └── McpServer.kt       # Основной MCP сервер
```

## Зависимости

- **Kotlinx Serialization** - для работы с JSON
- **Ktor Client** - для HTTP запросов к NewsAPI
- **Kotlinx Coroutines** - для асинхронных операций

## Сборка

### Сборка JAR файла

```bash
./gradlew :mcp-server:build :mcp-server:jar
```

JAR файл будет создан в `mcp-server/build/libs/mcp-server-1.0.0.jar`

### Запуск напрямую

```bash
./gradlew :mcp-server:run
```

## Запуск через HTTP Proxy

Для подключения с Android устройства используйте скрипт:

```bash
./start-newsapi-mcp-kotlin.sh [PORT]
```

По умолчанию используется порт 3001.

Скрипт автоматически:
1. Собирает JAR файл
2. Запускает HTTP прокси
3. Запускает Kotlin MCP сервер через stdio

## Использование

После запуска сервер работает точно так же, как JavaScript версия:

- **Для эмулятора Android**: `http://10.0.2.2:3001`
- **Для реального устройства**: `http://YOUR_IP:3001`

## Сравнение с JavaScript версией

| Характеристика | JavaScript | Kotlin |
|----------------|------------|--------|
| Язык | JavaScript/Node.js | Kotlin/JVM |
| Типизация | Динамическая | Статическая |
| Производительность | Хорошая | Отличная |
| Размер | Малый | Средний (JAR) |
| Зависимости | Node.js | JVM |
| Интеграция с Android | Через HTTP | Нативная |

## API ключ

API ключ NewsAPI встроен в код: `07fab6c9eca5436ba1b7f939c5528e1e`

Для production использования рекомендуется вынести API ключ в переменные окружения или конфигурационный файл.

## Логирование

Все логи выводятся в `stderr`, чтобы не мешать JSON-RPC протоколу (который использует `stdout`).

Логи включают:
- Запросы к NewsAPI
- Ответы от NewsAPI (превью и метаданные)
- Вызовы инструментов
- Ошибки с полным стеком

## Отладка

Для отладки можно запустить сервер напрямую:

```bash
./gradlew :mcp-server:run
```

Или использовать JAR:

```bash
java -jar mcp-server/build/libs/mcp-server-1.0.0.jar
```

Затем отправляйте JSON-RPC запросы через stdin.

## Запуск

Для запуска сервера используйте:

```bash
./start-newsapi-mcp-kotlin.sh
```

Или через npm:

```bash
npm run start:newsapi:kotlin
```

