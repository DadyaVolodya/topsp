# Что уже сделано: напарник (бэкенд и ручки для фронта)

Документ для улучшения продукта. Срез на 2026-09-02. Живой UI сейчас лежит в этом же репозитории (`src/main/resources/static`), но контракт ниже рассчитан и на отдельный фронт.

Конкурсный Copilot (версии СП, semantic diff, impact, задачи): ручки и экраны для нового фронта в `docs/FRONTEND-BINDINGS.md`.

База: `http://localhost:8080`  
Сервис: Spring Boot 4.1, Java 25, пакет `ru.infereco.demo.barista`  
LLM: Infereco `https://api.infereco.ru/v1`  
Стенд ПТО: `https://dev.aisto.local` (API `https://dev.aisto.local/api`)

Секреты только в `config/application-local.yml` (git ignore): ключ Infereco, пароль `pto-pp`, токен Telegram.

---

## Зачем это

Три режима в одном сервисе:

1. **Созвон (`interview`)** - подсказки только по голосу. Кадр экрана не берём и не просим шарить окно.
2. **ПТО (`pto-site`)** - обучалка и действия по сайту «Творческое образование». Правда из СП, кода aisto-front / aisto-pto-back и живого API стенда.
3. **Doom (`doom`)** - скрытая рация. Кадр игры снимает сам js-dos, не `getDisplayMedia`.

Роутер скила: `SkillRouter` по триггерам из `src/main/resources/skills/*.md`. При равенстве очков остаётся текущий скил (старт: `interview`). Doom включается только явным `POST /api/sessions/{id}/doom`.

---

## Что создано на бэке

### Ядро чата

| Класс | Роль |
| --- | --- |
| `ChatService` | сессия, ответ модели, RAG, поиск школы, выбор страницы |
| `ConversationStore` | сессии в памяти (после рестарта пусто) |
| `InferecoClient` | chat completions и vision |
| `SkillCatalog` / `SkillRouter` | загрузка скилов, скоринг триггеров |
| `KnowledgeCatalog` | RAG: seed, spec, code, upload, learned |

Ответ ассистента может нести ссылки для фронта:

- `cardUrl` - карточка учреждения на aisto, `/institutions/{id}`
- `openUrl` - что сделать дальше: та же карточка, другая страница aisto, или маршрут (Яндекс `rtext=~lat,lon`, как кнопка «Постройте маршрут» в aisto-front)

Фронт обязан: если есть `cardUrl`, сначала открыть карточку; если `openUrl` это Яндекс/`rtext` и он не равен карточке, через ~1.4 с открыть маршрут в той же вкладке. Так эмулируется клик по вашей кнопке без правок aisto-front.

### ПТО

| Класс | Роль |
| --- | --- |
| `PtoClient` | те же ручки стенда, что у aisto-front |
| `PtoQuery` | алиасы STT: несенных / гнесенных / гнесиных = Гнесины |
| `PtoPages` | страницы из `APP_ROUTES` без поиска школы |
| `PtoPlaces` | выбор корпуса, URL карточки и маршрута |
| `PtoHttp` | TLS для `*.local` |

Внешние вызовы (не наши, стенд):

- `POST {api}/pto/institutions/search` body `{ query, page: 0, size: 20 }`
- `GET {api}/pto/institutions/suggest?query=`
- Basic auth: логин `pto-pp`, пароль из local-конфига

Маршрут собирается как в aisto-front:

`https://yandex.ru/maps/?rtext=~{lat},{lon}`

Головной корпус предпочитается филиалу. Для Гнесиных при прочих равных берётся Знаменка, если она есть в выдаче.

### СП и задачи разработчику

| Класс | Роль |
| --- | --- |
| `SpecLibrary` | читает `/Users/sparrow/Documents/pto/СП`, кладёт в RAG, watch на create/modify |
| `SpecText` | docx (zip XML), doc (textutil или MIME Confluence HTML), txt/md |
| `DevTaskWriter` | markdown-задача в `/Users/sparrow/Documents/pto/задачи/авто` |
| `TelegramNotifier` | long poll `getUpdates`, рассылка в сохранённые chat id |

При **изменении** файла СП (не при старте):

1. текст перекладывается в RAG (`source=spec`)
2. пишется файл задачи `yyyyMMdd-HHmmss-{имя}.md`
3. в Telegram уходит оповещение (нужен `/start` боту `@assistentPTObot`)

Старые `.doc` из Confluence это MIME, не Word. Текст берётся из HTML-части, иначе получались сотни пустых кусков.

### Код проекта в RAG

| Класс | Роль |
| --- | --- |
| `CodeLibrary` | индекс при старте и каждые 2 минуты |
| `CodeIndex` | ts/tsx/js/jsx/java/kt, без node_modules, test, bin, generated |

Корни по умолчанию:

- `/Users/sparrow/Documents/pto/projects/aisto-front`
- `/Users/sparrow/Documents/pto/projects/aisto-pto-back`

Лимит: 400 файлов, приоритет `app-routes`, `page.tsx`, `*-api.ts`, контроллеры. Один документ на файл, обрезка ~2200 символов. `source=code`.

Retrieve для ПТО: сначала spec, потом code, потом upload/seed.

### Голос (STT)

| Класс | Роль |
| --- | --- |
| `VoskSttService` | модель `vosk-model-small-ru-0.22` (~50–90 МБ на диске, ~300 МБ RAM, CPU) |
| `SttWebSocketHandler` | поток PCM |
| `SttLexicon` | слова встречи и ПТО для Vosk |
| `PcmWav` | разбор wav в PCM 16-bit LE |

На созвоне только микрофон. Шаринг экрана для интервью выключен. Кадр принимает только Doom (`POST .../screen` кидает 400 вне doom).

### Самоулучшение

`SelfImproveService` + `MetricsRegistry`. Критик может выучить факт (`source=learned`). Сейчас `meet.improve.max-revisions: 0`, правки ответа выключены.

---

## Ручки для фронта

База JSON. Ошибки: `{ "error": "текст" }`.

| HTTP | Код |
| --- | --- |
| нет сессии | 404 |
| плохой запрос | 400 |
| Vosk ещё грузится | 503 |
| Infereco / прочее | 502 |

### Сессия и чат

#### `POST /api/sessions`

Создать сессию. Скил по умолчанию `interview`.

Ответ:

```json
{
  "id": "uuid",
  "topic": "Напарник: эфир, СП ПТО, Doom",
  "skill": "interview",
  "createdAt": "2026-09-02T15:00:00Z",
  "messages": []
}
```

#### `GET /api/sessions/{id}`

Сессия и история.

```json
{
  "id": "uuid",
  "skill": "pto-site",
  "createdAt": "2026-09-02T15:00:00Z",
  "messages": []
}
```

#### `POST /api/sessions/{id}/messages`

Основной ход. Тело:

```json
{
  "text": "как доехать до гнесиных",
  "source": "voice"
}
```

`source`: `text` или `voice` (как пометить реплику). `text` обязателен.

Ответ одного сообщения:

```json
{
  "id": "uuid",
  "role": "assistant",
  "text": "Это МССМШ им. Гнесиных, улица Знаменка, д. 12/2. Открываю карточку, затем Постройте маршрут.",
  "source": "chat",
  "at": "2026-09-02T15:00:01Z",
  "latencyMs": 1200,
  "skill": "pto-site",
  "openUrl": "https://yandex.ru/maps/?rtext=~55.75,37.60",
  "cardUrl": "https://dev.aisto.local/institutions/{id}"
}
```

Поля для UI:

| Поле | Когда |
| --- | --- |
| `skill` | текущий скил после роутера |
| `cardUrl` | нашли учреждение |
| `openUrl` | страница aisto или маршрут (если просили доехать) |
| `latencyMs` | время ответа модели |

Роли в ленте: `user`, `assistant`, `hint`, `system`.

Контракт вкладок ПТО (как в `app.js`):

1. По жесту пользователя открыть `about:blank` (Safari режет popup).
2. Если есть `cardUrl`, поставить его в вкладку.
3. Если `openUrl` содержит `yandex` или `rtext=` и не равен `cardUrl`, через 1400 мс заменить URL вкладки на `openUrl`.
4. Кнопки в сообщении: «открыть карточку» и «Постройте маршрут».

#### `POST /api/sessions/{id}/doom`

Включить Doom. Дальше `skill=doom`, кадр экрана разрешён.

#### `POST /api/sessions/{id}/screen`

Только Doom. Тело:

```json
{ "image": "data:image/jpeg;base64,..." }
```

Вне Doom: 400 `{ "error": "кадр экрана только в Doom" }`.

#### `POST /api/sessions/{id}/hints`

Живая подсказка рации (Doom). Тело необязательно:

```json
{ "elapsedSec": 40, "lastCheat": "нет", "playing": true }
```

### Мета, база, голос

#### `GET /api/health`

`{ "status": "UP" }`  
Есть ещё Actuator: `GET /actuator/health`.

#### `GET /api/models`

```json
{
  "chat": "minimax/minimax-m3",
  "critic": "glm-5.2",
  "fast": "glm-5.2",
  "vision": "openai/gpt-5.4-mini",
  "topic": "Напарник: эфир, СП ПТО, Doom"
}
```

#### `GET /api/voice`

Пауза и лимиты эфира в мс: `pauseMs`, `maxUtteranceMs`, `hintIdleMs`.

#### `GET /api/tuning`

Тема, модели, температура, лимиты токенов, system prompt. Для экрана настроек, не для каждого хода.

#### `GET /api/skills`

```json
[
  { "id": "interview", "title": "Собеседование", "triggers": ["zoom", "кандидат"] },
  { "id": "pto-site", "title": "Обучалка ПТО", "triggers": ["aisto", "гнесин", "маршрут"] },
  { "id": "doom", "title": "Doom", "triggers": [] }
]
```

Doom триггерами из текста не включается.

#### `GET /api/stt`

```json
{ "ready": true, "status": "ready", "error": "", "engine": "vosk" }
```

Не слать PCM, пока `ready=false`.

#### `POST /api/transcribe`

`multipart/form-data`, поле `file`: wav или сырой PCM. Ответ `{ "text": "..." }`. Для разовых файлов. Живой эфир лучше через WebSocket.

#### `GET /api/knowledge`

Группы, не каждый кусок `#1` `#2`:

```json
[
  { "title": "agenda", "source": "seed", "chunks": 1, "chars": 346 },
  { "title": "СП подача заявки.docx", "source": "spec", "chunks": 33, "chars": 28945 },
  { "title": "aisto-front/src/lib/app-routes.ts", "source": "code", "chunks": 1, "chars": 641 }
]
```

`source`: `seed` | `spec` | `code` | `upload` | `learned`.

В сайдбаре имеет смысл:

- `seed` / `learned` / `upload` в «База знаний»
- `spec` в блоке СП
- `code` в блоке Код

#### `POST /api/knowledge/txt`

`multipart`, поле `file`, только `.txt`. Ответ: список созданных кусков `{ id, title, source, chars }` со `source=upload`.

#### `GET /api/specs`

```json
{
  "dir": "/Users/sparrow/Documents/pto/СП",
  "chunks": 353,
  "lastChange": "2026-09-02T15:29:44Z",
  "files": [
    { "file": "СП подача заявки.docx", "bytes": 1565765, "fingerprint": -244587886 }
  ]
}
```

Поллить раз в 10–30 с, если нужен живой список после правки постановки.

#### `GET /api/code`

```json
{
  "files": 400,
  "chunks": 400,
  "lastChange": "2026-09-02T15:29:44Z",
  "roots": [
    { "name": "aisto-front", "dir": "/Users/sparrow/Documents/pto/projects/aisto-front", "exists": true },
    { "name": "aisto-pto-back", "dir": "/Users/sparrow/Documents/pto/projects/aisto-pto-back", "exists": true }
  ]
}
```

#### `GET /api/tasks`

Последние 20 задач разработчику:

```json
[
  {
    "file": "20260902-181802-карта.docx.md",
    "title": "Задача разработчику: обновить реализацию по СП",
    "body": "# Задача..."
  }
]
```

Пока СП не меняли после старта, массив пустой.

#### `GET /api/metrics`

```json
{
  "turns": 0,
  "hints": 0,
  "screens": 0,
  "improvementCycles": 0,
  "revisions": 0,
  "knowledgeDocs": 0,
  "pendingImprove": 0,
  "avgLatencyMs": 0,
  "lastQuality": 0,
  "lastLearned": false,
  "lastFact": "",
  "lastReason": ""
}
```

`GET /api/metrics/history` - точки качества критика.

---

## WebSocket STT

`ws://localhost:8080/ws/stt` (на https соответственно `wss`).

Сервер при открытии:

```json
{ "type": "ready", "engine": "vosk" }
```

или

```json
{ "type": "error", "text": "vosk ещё не готов" }
```

Клиент шлёт:

- binary: PCM 16-bit LE, mono, 16 kHz (как `GET /api/voice` / `meet.stt.sample-rate`)
- text: `flush` - отдать финальную фразу

Сервер отвечает text JSON:

```json
{ "type": "partial", "text": "доехать до" }
```

```json
{ "type": "final", "text": "доехать до гнесиных" }
```

`final` отправлять в `POST /api/sessions/{id}/messages` с `"source": "voice"`.

---

## Скилы и когда что открывать

| Скил | Как попасть | Кадр | Ссылки |
| --- | --- | --- | --- |
| `interview` | старт, слова zoom / кандидат / эфир | нет | нет |
| `pto-site` | aisto, карта, гнесин, маршрут, грант, постановка | нет | `cardUrl` / `openUrl` |
| `doom` | только `POST .../doom` | да, JPEG игры | нет |

Типичный сценарий ПТО «доехать до гнесиных»:

1. `POST /sessions`
2. WebSocket STT или текст
3. `POST /sessions/{id}/messages`
4. В ответе `skill=pto-site`, `cardUrl`, `openUrl` (Яндекс)
5. Вкладка: карточка, затем маршрут

Страницы без поиска школы (логин, гранты, карта направлений) приходят как `openUrl` на `dev.aisto.local`, `cardUrl` пустой.

---

## RAG: что лежит в базе

Не дубли документов. Одна СП режется на куски ~900 символов для поиска. В `GET /api/knowledge` они схлопнуты в один файл.

| source | Откуда |
| --- | --- |
| `seed` | `src/main/resources/knowledge/*.md` (agenda, pto, e1m1, cheats, …) |
| `spec` | папка `pto/СП` |
| `code` | aisto-front + aisto-pto-back |
| `upload` | `POST /api/knowledge/txt` |
| `learned` | факты критика |

---

## Конфиг, который видит рантайм

`src/main/resources/application.yml` + `config/application-local.yml` (профиль `local`).

| Ключ | Смысл |
| --- | --- |
| `meet.models.*` | chat / critic / fast / vision |
| `meet.voice.*` | пауза эфира |
| `meet.demo.pto.url` / `api` / `login` / `password` | стенд |
| `meet.stt.model-dir` | Vosk |
| `meet.specs.dir` | папка СП |
| `meet.specs.tasks-dir` | папка автозадач |
| `meet.telegram.token` / `chat-id` | бот |
| `meet.code.dirs` | корни исходников |

Telegram chat id после `/start` пишется в `config/telegram-chats.txt` (git ignore).

---

## Чего нет (дыры для улучшения)

1. **Подсветка клика на aisto.** С `localhost` нельзя рисовать на `dev.aisto.local`. Нужен `?hint=` во фронте ПТО или расширение. Маршрут без этого уже открывается той же ссылкой, что кнопка.
2. **Нет REST «открой маршрут» отдельно.** Ссылка приезжает внутри сообщения чата. Отдельной ручки `POST /api/pto/route` нет.
3. **Сессии в памяти.** Рестарт сервера обнуляет чат.
4. **Нет авторизации** наших `/api/*`. Это демо на localhost.
5. **Telegram** с этой машины до `api.telegram.org` может не достучаться (таймаут). Бот и токен есть, нужен `/start` и сеть.
6. **Голос и popup Safari.** Вкладку ПТО надо открывать по жесту (клик «старт» / submit). Иначе браузер режет `window.open`.
7. **Экран созвона выключен специально.** `POST .../screen` только Doom.
8. **`docs/TECH.md` устарел:** там ещё шаринг Meet/Zoom и экран на созвоне. Ориентир этот файл, не TECH.
9. **Индекс кода урезан** до 400 файлов и 2200 символов. Большие контроллеры обрезаны.
10. **Нет webhooks СП.** Только watch папки на диске этой машины.

---

## Минимальный клиент

1. `GET /api/stt` пока `ready`
2. `POST /api/sessions` сохранить `id`
3. `WS /ws/stt` слать PCM, на `final` вызвать messages
4. Рендер `text`, `skill`, кнопок из `cardUrl` / `openUrl`
5. Поллить `/api/specs`, `/api/code`, `/api/tasks`, `/api/knowledge`, `/api/metrics` для сайдбара
6. Doom: `POST .../doom`, JPEG на `.../screen`, опционально `.../hints`

Готовый пример: `src/main/resources/static/app.js` и `index.html`.
