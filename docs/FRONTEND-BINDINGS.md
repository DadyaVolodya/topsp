# Фронт: куда подвязывать TopSP (PetClinic)

База: `http://localhost:8080`  
JSON, ошибки `{ "error": "текст" }`. CORS на `/api/**` открыт.

Демо-продукт: **Spring PetClinic** (`demo/sp`, `demo/code/petclinic-*`).

---

## Бинарный формат: что держим и читаем

**Сейчас бинарного индекса ещё нет.** На диске лежит JSON:

| Что | Где | Формат сейчас |
| --- | --- | --- |
| Версии СП | `data/spec-versions/{documentId}/vN.json` | JSON: fingerprint, sections[], rawText |
| Diff | `.../changes.json` | JSON: SpecChange + affected[] |
| Связи | `.../links.json` | JSON: requirement-code links |
| Код | RAM (`CodeLibrary`) | чанки символов из `.java` / `.tsx` |
| RAG | RAM (`KnowledgeCatalog`) | текстовые куски seed/spec/code/learned |

**План бинарного слоя** (см. `docs/PITCH-METRICS.md`):

- blob секций и code chunks (MessagePack / FlatBuffers / свой pack);
- в индексе только: `id`, `pathHash`, `contentHash`, `offset`, `length`, флаги;
- diff по `contentHash` без повторного парсинга всего текста;
- флаги контекста чата выбирают, какие куски читать:
  - `NEED_SPEC` - секции СП
  - `HINT` - короткий playbook
  - `IMPACT` - только high/critical + символы front/back
  - `TASK` - affected + acceptance

Читать бинарь будут бэкенд-пайплайн и (опционально) фронт только через API, не сырые `.bin`.

---

## Кнопки UI и как их вязать

### 1) Два окна загрузки СП

| Кнопка / контрол | Действие фронта | API |
| --- | --- | --- |
| Файл «Старое СП» | `FormData.append("oldFile", file)` | вместе с новым |
| Файл «Новое СП» | `FormData.append("newFile", file)` | вместе со старым |
| «Сравнить пару» | `POST /api/specs/pair` multipart | ответ = overview |

```js
const body = new FormData();
body.append("oldFile", oldInput.files[0]);
body.append("newFile", newInput.files[0]);
const overview = await fetch("/api/specs/pair", { method: "POST", body }).then(r => r.json());
// overview.documentId, fromVersion, toVersion, summary, affectedFrontend, affectedBackend
```

После успеха:

1. сохранить `documentId` в state;
2. обновить Overview / Changes;
3. показать CTA «Что изменилось» и «Создать задачи».

Альтернатива без файлов (демо): в чат `#sp1` затем `#sp2`.

### 2) Кнопки сценария (чат-скиллы)

Это не отдельные REST-ручки скиллов: фронт шлёт текст в чат. Бэк распознаёт `#…` и отвечает сразу (без «бреда» LLM, кроме обычных вопросов).

| Кнопка на UI | Что отправить в чат | Что придёт в `text` |
| --- | --- | --- |
| «Что изменилось / impact» | `#sp` | diff: было/стало + Frontend (что менять) + Backend (что менять) |
| «Создать задачи» | `#task` | две markdown-задачи: front-… и back-… |
| «Подсказчик» | `#hint` | короткий режим без СП |
| «Doom» | `#doom` | вход в doom |
| Демо v1 / v2 | `#sp1` / `#sp2` | загрузка samples |

```js
async function sendSkill(sessionId, command) {
  const msg = await fetch(`/api/sessions/${sessionId}/messages`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text: command, source: "text" })
  }).then(r => r.json());
  // msg.text, msg.skill ("topsp"|"hint"|"doom"|...), msg.openUrl, msg.cardUrl
  renderAssistant(msg);
  if (command === "#task") await refreshTasks(); // GET /api/tasks
  if (command === "#sp") await refreshChanges(documentId);
}
```

Поле `skill` в ответе - какой режим активен. По нему красьте бейдж («TopSP» / «подсказчик» / «Doom»).

### 3) Кнопки на карточке изменения (не чат)

Данные: `GET /api/specs/{documentId}/changes`

| Кнопка | Откуда данные | Действие |
| --- | --- | --- |
| «Показать влияние на код» | `change.affected[]` | список repo/path/symbol; front если `repo` содержит `front` или path `.tsx` |
| «Задача front» | после `#task` или файл `front-*.md` в `GET /api/tasks` | показать markdown |
| «Задача back» | файл `back-*.md` | показать markdown |
| «Открыть в продукте» | `change.navigation.openUrl` | `window.open(openUrl)` |
| «Подсветить» | `navigation.target` + `action=highlight` | найти DOM по `data-testid` / id = target |

PetClinic demo navigation (если есть): страница visits, target вроде `visit-cancel-button`.

### 4) Кнопки в ответе чата (`openUrl` / `cardUrl`)

Ответ `POST /api/sessions/{id}/messages`:

```json
{
  "id": "...",
  "role": "assistant",
  "text": "...",
  "skill": "topsp",
  "openUrl": "http://localhost:8080/...",
  "cardUrl": null,
  "latencyMs": 12
}
```

| Поле | Кнопка на UI |
| --- | --- |
| `cardUrl` | «Открыть карточку» |
| `openUrl` | «Открыть» / «Перейти» |
| оба пустые | только текст (типично для `#sp` / `#task`) |

Правило: если есть `cardUrl` - сначала он; `openUrl` - второй ход (если нужен маршрут/другая страница).

---

## Минимальный поток экранов

```
[Старое СП] [Новое СП] → POST /api/specs/pair
        ↓
GET /api/copilot/overview?documentId=...
        ↓
Кнопка «Что изменилось» → POST .../messages { "text": "#sp" }
  и/или GET /api/specs/{id}/changes  (карточки)
        ↓
Кнопка «Создать задачи» → POST .../messages { "text": "#task" }
  и GET /api/tasks  (front-*.md + back-*.md)
```

---

## Overview

`GET /api/copilot/overview`  
`GET /api/copilot/overview?documentId=petclinic-visits-md`

Поля: `documentId`, `fileName`, `fromVersion`, `toVersion`, `summary`, `affectedFrontend`, `affectedBackend`, `affectedFiles`, `lastNotice`, `links`.

CTA после пары файлов: «Посмотреть изменения» > `GET /api/specs/{documentId}/changes`.

Демо без файлов:

```http
POST /api/copilot/demo/seed
```

---

## Список СП / версии / changes / impact

Как раньше:

- `GET /api/specs`
- `GET /api/specs/{documentId}/versions`
- `GET /api/specs/{documentId}/changes`
- `GET /api/specs/{documentId}/changes/{changeId}`
- `POST .../exclude` / `.../include`
- `GET /api/specs/{documentId}/links`

`documentId` - slug из API, не имя файла. Пример: `petclinic-visits.md` > `petclinic-visits-md`.

В `affected[]`: `repo`, `path`, `symbol`, `symbolType`, `reason`, `confidence`, `startLine`, `endLine`.  
Текст только: **потенциально затронут**.

---

## Код

`GET /api/code` - корни `petclinic-front`, `petclinic-back`  
`GET /api/code/symbols?q=canCancel&side=front|back`

---

## Задачи

`GET /api/tasks` - до 20 последних.

После `#task` появляются два файла:

- `…-front-petclinic-visits.md` - исполнитель frontend
- `…-back-petclinic-visits.md` - исполнитель backend

На UI две кнопки/вкладки: Front / Back, тело из `body`.

---

## Чат

```http
POST /api/sessions
POST /api/sessions/{id}/messages
{ "text": "#sp", "source": "text" }
```

Ответ: `text`, `skill`, `cardUrl`, `openUrl`, `latencyMs`.

Команды: `#sp`, `#task`, `#sp1`, `#sp2`, `#hint`, `#doom`, `#topsp`.

---

## Metrics

`GET /api/metrics` - `specDiffDurationMs`, `impactAnalysisDurationMs`, `taskGenerationDurationMs`, `changesDetected`, `affectedFiles`, `tasksGenerated`, `avgLatencyMs`, improve-счётчики.

Модели: `GET /api/tuning` - `chatModel` / `criticModel` / `hintModel` (сейчас deepseek-v4-flash-dspark, glm-5.3, glm-5.3-flash).
