# Ai harness 🖤

Агент для работы с кодом: Java 25, Spring Boot 4, Spring AI 2.
Модель локальная, Ollama на порту 11432.

Стек и правила работы для AI-агентов — в [`AGENTS.md`](AGENTS.md:1).

## Что уже есть

* агентский цикл «модель → инструменты → модель» с лимитом итераций;
* реестр инструментов и единственный реализованный инструмент чтения файлов `Read`;
* события хода и HUD в стиле T-800, подписанный на них;
* грубая оценка размера контекста в токенах.

## Требования

* JDK 25 и Maven (wrapper в репозиторий не кладём);
* Docker с плагином Compose — чтобы поднять Ollama рядом с проектом;
* NVIDIA Container Toolkit — если пробрасываете GPU (см. раздел ниже).

## Быстрый старт

Поднять Ollama и скачать модель `qwen3.5:4b`:

```bash
docker compose up -d
```

Проверить, что сервер поднялся и модель на месте:

```bash
docker exec harness-ollama ollama list
```

Запустить агента — он стартует по [`DemoRunner`](src/main/java/ru/harness/DemoRunner.java:16)
и сразу задаёт модели вопрос из кода:

```bash
mvn spring-boot:run
```

Рабочую папку и предел итераций задаёт [`application.yaml`](src/main/resources/application.yaml:17):
`harness.agent.workspace-root` и `harness.agent.max-turns`.

## Тесты

Быстрые тесты — без сети и без живой модели, идут секундами:

```bash
mvn test
```

Полная проверка как в CI (то же, что делает [`build.yml`](.github/workflows/build.yml:1)):

```bash
mvn -B verify
```

Тесты на живой модели помечены тегом `ollama` и по умолчанию исключены
(см. `excluded.groups` в [`pom.xml`](pom.xml:21)). Прогнать только их:

```bash
mvn test -Dgroups=ollama -Dexcluded.groups=
```

Эти тесты требуют поднятой Ollama и идут секундами, а не миллисекундами.

## Если указываем в docker-compose видеокарту nvidia

Нужно предварительно установить NVIDIA Container Toolkit

## 🩺 Диагностика

```bash
# A. Что сейчас делает pull-контейнер (последние 30 строк)
docker logs --tail 30 harness-ollama-pull

# B. Смотрим ВСЕ блобы, включая мелкие — видно, чего не хватает
docker exec harness-ollama-pull ls -la /root/.ollama/models/blobs/
# (внутри pull-контейнера тот же volume, что у сервера)

# C. Есть ли манифест-файлы (если появились — модель почти зарегистрирована)
docker exec harness-ollama-pull ls -la /root/.ollama/models/manifests/registry.ollama.ai/library/ 2>/dev/null || echo "манифестов пока нет"

# D. Прогрев модели с метриками
docker exec -it harness-ollama ollama run qwen3.5:4b "hi" --verbose

# E. Проверка, что модель на GPU
docker exec harness-ollama ollama ps
docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi

# F. Логи на предмет ошибок
docker logs harness-ollama 2>&1 | Select-String -Pattern "error|warn|cuda|vram|offload|library=" -CaseSensitive:$false

```

При прогреве:
prompt eval будет медленный потому, что в него включено время инициализации CUDA-контекста и компиляции графа 
(USE_GRAPHS=1 — ollama строит CUDA graph при первом запуске). Плюс модель только что загрузилась, прогрева ещё не было.
Второй и последующие запуски ollama run qwen3.5:4b "hi" --verbose будут другими.

## Договорённости

* Ошибка инструмента — это результат, а не исключение. Инструмент возвращает
  `ToolResult.error(...)` с текстом, по которому видно, что делать дальше.
* Путь от модели проходит через `WorkspaceGuard`. Наружу рабочей папки
  не ходим даже по абсолютному пути.
* Править файл можно только после его чтения — за этим следит `FileStateCache`.
* Deprecated API Spring AI не используем: проект стоит на версии 2.0,
  старые сигнатуры из 1.x не берём.
* Пакет `hud` — только представление. Ядро агента про него не знает
  и импортировать его не должно.

## Чего не делать

* Не добавлять зависимости без необходимости: каждая едет в сборку ученика.
* Не писать абстракцию раньше её первого потребителя.
* Не трогать `demo/TargetPractice.java` — это мишень для занятий, она намеренно недоделана.