# TopSP — AI Copilot жизненного цикла требований

Бэкенд: изменение системной постановки > semantic diff > impact по коду > draft задачи разработчику > ответы в чате и навигация на страницу продукта.

Фронт в этом репозитории демо-прототип. Боевой UI подключается к API ниже (CORS на `/api/**` открыт).

Telegram: бот `@assistentPTObot` (токен в `config/application-local.yml`). Напишите боту `/start` - chat id сохранится в `config/telegram-chats.txt`. При изменении СП (пара файлов, `#sp2`, `#sp`, `#task`) notice уходит в Telegram.

## Запуск

Java 25+, Maven Wrapper.

```bash
cp config/application-local.yml.example config/application-local.yml
# ключ Infereco: spring.ai.openai.api-key
./mvnw spring-boot:run
```

База: `http://localhost:8080`

Демо-контур: Spring PetClinic в `demo/` (СП visits + front/back). Концепция: [docs/CONCEPT.md](docs/CONCEPT.md). Метрики: [docs/PITCH-METRICS.md](docs/PITCH-METRICS.md).

Чат-команды: `#sp1` `#sp2` `#task` `#hint` `#doom` `#sp`.

Пути к СП и коду: `meet.specs` / `meet.code` (по умолчанию `demo/sp` и `demo/code/petclinic-*`).

## Ручки для фронта

Ошибки: `{ "error": "текст" }`.

Полный контракт с полями карточек: [docs/FRONTEND-BINDINGS.md](docs/FRONTEND-BINDINGS.md).

### Copilot

| Метод | Путь | Зачем |
| --- | --- | --- |
| GET | `/api/copilot/overview` | дашборд: версии, счётчики, lastNotice |
| POST | `/api/copilot/demo/seed` | демо v1/v2 «редактирование заявки» |
| GET | `/api/specs` | список СП, `documentId`, version, changes |
| GET | `/api/specs/{documentId}/versions` | две последние версии, разделы |
| GET | `/api/specs/{documentId}/changes` | semantic diff |
| GET | `/api/specs/{documentId}/changes/{changeId}` | одно изменение + affected code |
| POST | `/api/specs/{documentId}/reanalyze` | пересчитать diff/impact/задачу |
| POST | `/api/specs/{documentId}/changes/{changeId}/exclude` | исключить ложный код или всё изменение |
| POST | `/api/specs/{documentId}/changes/{changeId}/include` | вернуть изменение |
| GET | `/api/specs/{documentId}/links` | связи требование-код |
| POST | `/api/specs/upload` | загрузить `.md`/`.txt` СП в watcher + pipeline |
| GET | `/api/code` | индекс репозиториев |
| GET | `/api/code/symbols?q=&side=front\|back` | символы для impact |
| GET | `/api/tasks` | markdown draft задач |
| GET | `/api/navigation?q=` | `{ route, target, action, openUrl }` |
| GET | `/api/metrics` | `specDiffDurationMs`, `changesDetected`, … |

`documentId` берите из API, не собирайте из имени файла.

Формулировка impact только: **потенциально затронут**, не «файл точно менять».

Навигация: бэк говорит что открыть (`openUrl`) и что подсветить (`target`, `action=highlight`). Подсветку рисует фронт.

### Чат и голос

| Метод | Путь |
| --- | --- |
| POST | `/api/sessions` |
| GET | `/api/sessions/{id}` |
| POST | `/api/sessions/{id}/messages` body `{ "text", "source": "typed" }` |
| WS | `/ws/stt` PCM 16 kHz |

В сообщении: `text`, `skill`, `cardUrl`, `openUrl`.

Если есть `cardUrl`, сначала карточка учреждения. Если `openUrl` это Яндекс `rtext=` и он не равен карточке, через ~1.4 с открыть маршрут.

### Демо жюри без правки большой СП

```http
POST /api/copilot/demo/seed
GET  /api/copilot/overview?documentId=demo-application-edit
GET  /api/specs/demo-application-edit/changes
```

Лицензия Apache-2.0.
