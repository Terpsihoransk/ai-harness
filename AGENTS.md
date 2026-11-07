# AGENTS.md

## Обзор

**Харнесс** — кодинг-агент для работы с кодом в локальном репозитории.
Живёт в терминале (CLI), не в вебе.

Стек:

- Java 25
- Spring Boot 4.1.2
- Spring AI 2.0.0 (BOM)
- Lombok
- JUnit 5 + AssertJ
- Модель: локальная Ollama, `http://localhost:11432`, `qwen3.5:4b`, think выключен.

## Команды

Maven запускай через глобальный Maven — он доступен.

```bash
mvn -B verify                                    # сборка + тесты, как в CI
mvn test                                         # unit-тесты, тег ollama исключён
mvn test -Dgroups=ollama -Dexcluded.groups=      # тесты на живой модели, нужна Ollama
mvn spring-boot:run -Dspring-boot.run.arguments="твой запрос"   # запуск с запросом
docker compose up -d                             # поднять Ollama и загрузить модель
```

Группы тестов исключаются через `excluded.groups` в pom.xml (`ollama,mcp,mission`).

## Структура

- `ru.harness` — точка входа [`AiHarnessApplication`](src/main/java/ru/harness/AiHarnessApplication.java:14), стартовый `startupProbe` (профиль `!test`).
- `ru.harness.agent` — ядро: [`Agent`](src/main/java/ru/harness/agent/Agent.java:9) (цикл и вызов модели), [`Conversation`](src/main/java/ru/harness/agent/Conversation.java:15) (история и системный промпт).
- `src/test/java` — тесты; подставная модель [`ScriptedChatModel`](src/test/java/ru/harness/agent/ScriptedChatModel.java:23) для детерминированных прогонов.

## Конвенции и правила

- Ошибка инструмента — это результат, а не исключение: возвращай `ToolResult.error(...)` с текстом, по которому видно, что делать дальше.
- Доступ к файлам — только через `WorkspaceGuard`; за пределы рабочей папки не выходим даже по абсолютному пути.
- Править файл можно только после его чтения — за этим следит `FileStateCache`.
- Deprecated API Spring AI 1.x не используем: проект на 2.0, старые сигнатуры не берём.
- Пакет `hud` — только представление. Ядро агента про него не знает и импортировать не должно.
- Не добавлять зависимости без необходимости: каждая едет в сборку ученика.
- Не писать абстракцию раньше её первого потребителя.

## Тесты

- Unit-тесты — детерминированные, на `ScriptedChatModel`, без сети.
- Контекстные тесты — профиль `test`: он отключает `startupProbe`, чтобы CI не ходил в живую модель.
- Тесты на живой Ollama — `@Tag("ollama")`, исключены из обычного `mvn test`.
- Имена тестовых методов — на русском.