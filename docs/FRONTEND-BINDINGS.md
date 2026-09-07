# Фронт: куда подвязывать AI Copilot

База: `http://localhost:8080`  
JSON, ошибки `{ "error": "текст" }`. CORS не настроен: либо тот же origin, либо прокси.

Старые ручки чата / голоса / Doom не ломались. Новые экраны конкурса читают блок **Copilot** ниже.

Демо без правки большой СП:

```http
POST /api/copilot/demo/seed
```

Появится документ `demo-application-edit`: v1 «после подачи нельзя редактировать», v2 «можно до начала проверки».

---

## Экраны и ручки

| Экран | Что показать | Откуда брать |
| --- | --- | --- |
| Overview | последнее изменение СП, счётчики, затронутый код, CTA | `GET /api/copilot/overview` |
| СП | список документов, версия, число изменений | `GET /api/specs` |
| СП / версии | v(n-1) и v(n), разделы | `GET /api/specs/{documentId}/versions` |
| Изменения | карточки semantic diff, фильтры | `GET /api/specs/{documentId}/changes` |
| Изменение | полный текст + impact | `GET /api/specs/{documentId}/changes/{changeId}` |
| Impact | «потенциально затронут», confidence, exclude | то же + `POST .../exclude` |
| Код | корни репозиториев + символы | `GET /api/code`, `GET /api/code/symbols?q=` |
| Задачи | markdown draft | `GET /api/tasks` |
| Chat | уточнения, `cardUrl` / `openUrl` | `POST /api/sessions`, `POST /api/sessions/{id}/messages` |
| Live Product | открыть стенд и подсветить | `change.navigation` или `GET /api/navigation?q=` |
| Metrics | время пайплайна и счётчики | `GET /api/metrics` |
| Telegram | статус бота, текст последнего notice | `GET /api/telegram` + `overview.lastNotice` |

`documentId` не имя файла, а slug. Пример: файл `заявка.md` > `заявка-md`. Берите поле `documentId` из `GET /api/specs` и `GET /api/copilot/overview`.

---

## Overview

`GET /api/copilot/overview`  
`GET /api/copilot/overview?documentId=demo-application-edit`

```json
{
  "documentId": "demo-application-edit",
  "fileName": "demo-редактирование-заявки.md",
  "fromVersion": 1,
  "toVersion": 2,
  "updatedAt": "2026-09-02T17:00:00Z",
  "sections": 2,
  "summary": {
    "total": 1,
    "critical": 0,
    "high": 1,
    "medium": 0,
    "low": 0,
    "cosmetic": 0
  },
  "affectedFrontend": 0,
  "affectedBackend": 0,
  "affectedFiles": 0,
  "lastNotice": "СП «...» обновлена...",
  "links": 0
}
```

CTA «Посмотреть изменения»:

`GET /api/specs/{documentId}/changes`

Поллить Overview каждые 8-15 с, пока идёт загрузка СП (после старта бэка первая версия появляется не сразу).

---

## Список СП

`GET /api/specs`

```json
{
  "dir": "/Users/sparrow/Documents/pto/СП",
  "chunks": 184,
  "lastChange": "2026-09-02T17:00:00Z",
  "files": [
    {
      "file": "Системная+постановка_....doc",
      "documentId": "системная-постановка-...",
      "bytes": 123456,
      "fingerprint": 1,
      "version": 1,
      "changes": 0
    }
  ]
}
```

Действия на карточке документа:

- открыть разделы: `GET /api/specs/{documentId}/versions`
- сравнить: `GET /api/specs/{documentId}/changes`
- переанализировать: `POST /api/specs/{documentId}/reanalyze`

`version == 0` и пустой `documentId` в overview значат: пайплайн ещё не сохранил snapshot.

---

## Версии

`GET /api/specs/{documentId}/versions`

Две последние версии. Поля секции: `sectionPath`, `heading`, `text` (обрезан до 600), `hash`.  
Полный raw файл на фронт не отдаём.

Шапка экрана diff: `{fileName}  v{from} > v{to}`.

---

## Изменения (главный экран)

`GET /api/specs/{documentId}/changes`  
`GET /api/specs/{documentId}/changes?includeUnchanged=true`

```json
{
  "document": "demo-редактирование-заявки.md",
  "documentId": "demo-application-edit",
  "fromVersion": 1,
  "toVersion": 2,
  "summary": { "total": 1, "critical": 0, "high": 1, "medium": 0, "low": 0, "cosmetic": 0 },
  "changes": [
    {
      "id": "uuid",
      "type": "modified",
      "significance": "high",
      "sectionPath": "4.2",
      "heading": "Редактирование заявки",
      "summary": "Изменён момент, до которого разрешено редактирование",
      "oldBehavior": "нельзя после подачи",
      "newBehavior": "можно до начала проверки",
      "businessImpact": "...",
      "requiresCodeChange": true,
      "oldText": "...",
      "newText": "...",
      "excluded": false,
      "affected": [],
      "navigation": {
        "route": "/grant-program",
        "target": "application-edit-button",
        "action": "highlight",
        "openUrl": "https://dev.aisto.local/grant-program"
      }
    }
  ]
}
```

`type`: `added` | `removed` | `modified` | `unchanged` | `moved`  
`significance`: `critical` | `high` | `medium` | `low` | `cosmetic`

Фильтры на UI: significance, type, `requiresCodeChange === true`.

Карточка:

- бейдж `[HIGH]` + раздел `sectionPath`
- summary
- Было / Стало (`oldBehavior` / `newBehavior`, запасной вариант `oldText` / `newText`)
- business impact
- кнопки: «Показать влияние на код» (`affected`), «Открыть в продукте» (`navigation.openUrl`)

Детали без обрезки: `GET /api/specs/{documentId}/changes/{changeId}`

Пересчёт: `POST /api/specs/{documentId}/reanalyze` (тот же JSON, что у GET changes).

---

## Impact и исключение ложных

В `affected[]`:

| Поле | Куда |
| --- | --- |
| `repo` | колонка Frontend / Backend |
| `path` | файл |
| `symbol` | метод / компонент |
| `symbolType` | подпись: method, component, controller... |
| `reason` | «почему потенциально затронут» |
| `confidence` | 0..1, в UI проценты |
| `startLine` / `endLine` | якорь в файле |

Формулировка только: **потенциально затронут** или **высокая вероятность влияния**. Не писать «этот файл точно надо менять».

Исключить один символ:

```http
POST /api/specs/{documentId}/changes/{changeId}/exclude
{ "path": "aisto-front/src/...", "symbol": "canEditApplication" }
```

Исключить всё изменение:

```http
POST /api/specs/{documentId}/changes/{changeId}/exclude
{}
```

Вернуть изменение в выдачу:

```http
POST /api/specs/{documentId}/changes/{changeId}/include
```

Связи СП-код: `GET /api/specs/{documentId}/links`

```json
[{ "changeId": "...", "specVersion": 2, "repo": "...", "file": "...", "symbol": "...", "reason": "...", "confidence": 0.8 }]
```

---

## Код

`GET /api/code`

```json
{
  "files": 862,
  "chunks": 862,
  "symbols": 1200,
  "lastChange": "...",
  "roots": [{ "name": "aisto-front", "dir": "...", "exists": true }]
}
```

Поиск символов для экрана кода / impact:

`GET /api/code/symbols?q=редактир заявк&side=front&limit=20`

`side`: `front` | `back` | пусто.

Ответ: массив `{ repo, path, language, symbol, symbolType, startLine, endLine, text }`.

---

## Задачи

`GET /api/tasks` - до 20 последних markdown.

```json
[{ "file": "20260902-181800-заявка.md", "title": "Изменить: Редактирование заявки", "body": "# ..." }]
```

Блоки в `body`: Причина, Было, Стало, Бизнес-смысл, frontend, backend, Acceptance Criteria, Риски.

Кнопки: показать markdown, скопировать `body`. Сохранение на диск уже делает бэк при изменении СП.

---

## Навигация по живому продукту

Бэк говорит **что открыть и что подсветить**. Подсветку рисует фронт (aisto-front сам не меняем).

Из изменения:

```json
"navigation": {
  "route": "/grant-program",
  "target": "application-edit-button",
  "action": "highlight",
  "openUrl": "https://dev.aisto.local/grant-program"
}
```

Свободный запрос: `GET /api/navigation?q=редактировать заявку`

Как открывать:

1. `window.open(navigation.openUrl)` или та же вкладка.
2. Если умеете встроить стенд / extension: найти элемент по `data-hint` / id ≈ `target` и подсветить.
3. Если подсветки нет, достаточно открыть `openUrl`.

Чат по-прежнему отдаёт отдельно:

- `cardUrl` - карточка учреждения `/institutions/{id}`
- `openUrl` - страница или Яндекс-маршрут `rtext=~lat,lon`

Правило чата: если есть `cardUrl`, сначала карточка; если `openUrl` это Яндекс и он не равен карточке, через ~1.4 с открыть маршрут в той же вкладке.

---

## Chat и голос (как было)

```http
POST /api/sessions
GET  /api/sessions/{id}
POST /api/sessions/{id}/messages
{ "text": "что изменилось в редактировании заявки", "source": "typed" }
```

Ответ сообщения: `text`, `skill`, `cardUrl`, `openUrl`, `latencyMs`.

Голос: `ws://localhost:8080/ws/stt` PCM 16 kHz, либо `POST /api/transcribe` wav.  
Интервью без шаринга экрана. Doom: `POST /api/sessions/{id}/doom`.

---

## Метрики конкурса

`GET /api/metrics` - старые поля плюс:

| Поле | Смысл |
| --- | --- |
| `specDiffDurationMs` | semantic diff |
| `impactAnalysisDurationMs` | поиск кода |
| `taskGenerationDurationMs` | markdown задачи |
| `changesDetected` | число изменений последнего прогона |
| `highImpactChanges` | critical + high |
| `affectedFiles` | уникальные файлы |
| `tasksGenerated` | сколько draft создано с старта процесса |

Для слайда: ручной разбор 20-40 мин, пайплайн обычно десятки секунд.

---

## Telegram

`GET /api/telegram` > `{ enabled, chats, hint }`. Telegram **не используется**: сеть не дергаем. Текст для тоста: `overview.lastNotice`.

---

## Поллинг

| Что | Интервал |
| --- | --- |
| Overview, specs, tasks, telegram | 10 с |
| changes после seed / reanalyze | один раз сразу, потом 10 с пока `toVersion` не вырос |
| code / symbols | 30 с (индекс кода обновляется раз в 2 мин) |
| metrics | 15 с |

Не дёргайте `reanalyze` в цикле.

---

## Демо-сценарий для жюри

1. Старт бэка, подождать индекс (СП + код).
2. `POST /api/copilot/demo/seed`
3. Overview: 1 high change, v1 > v2.
4. Экран изменений: раздел 4.2, было / стало.
5. Impact: символы, если код уже в индексе.
6. Задачи: новый `.md`.
7. Кнопка Live Product: `openUrl` грантовой программы.
8. Чат: «что поменять в коде после правки СП».

Альтернатива seed: заменить файл из `meet.specs.file` в папке СП. Watcher сам снимет v2 и прогонит пайплайн.

---

## Чего бэк не делает

- Не подсвечивает DOM на `dev.aisto.local` (другой origin).
- Не пишет в Jira.
- Interview / Doom не удалены, но это не главный сюжет.
- Токен бота на фронт не отдаём.
