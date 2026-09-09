# Питч: метрики, скорость, масштаб

## Что уже меряем (GET /api/metrics)

- Диалог: `turns`, `hints`, `screens`, `avgLatencyMs`
- Пайплайн СП: `specDiffDurationMs`, `impactAnalysisDurationMs`, `taskGenerationDurationMs`
- Результат: `changesDetected`, `highImpactChanges`, `affectedFiles`, `tasksGenerated`
- Самоулучшение: циклы / ревизии / docs в knowledge

Эти цифры - материал для питча «под капотом»: не только ответ LLM, а измеримый контур требование > diff > impact > задача.

## Оптимизации «чтобы было супер быстро» (план)

Бинарный индекс **ещё не внедрён**. Сейчас версии/diff - JSON в `data/spec-versions/`.

1. Бинарный индекс секций и кода (MessagePack / FlatBuffers / свой pack):
   - держим: нормализованные секции СП + code chunks (текст в blob);
   - читаем по индексу: id, pathHash, contentHash, offset/length + флаги контекста;
   - diff по hash без повторного парсинга всего текста;
   - флаги чата (`NEED_SPEC`, `HINT`, `IMPACT`, `TASK`) выбирают какие куски mmap-ить.
2. Контекстные флаги разговора:
   - не тащить Doom/interview RAG в режим TopSP;
   - для `#hint` - tiny prompt, fast model, 0 history;
   - для impact - только секции с `significance=high|critical`.
3. Инкрементальный code index: watch + перехеш файла, не полный rescan.
4. Кэш embedding/символов на диск; hot set в памяти.
5. Лимиты: historyMessages, max impact 8, clip текстов в API.
6. Демо-срез вместо полной Confluence-СП (уже в `demo/`).
7. Параллель: diff секций и поиск символов на virtual threads.

## Масштаб большого проекта

- Хранить нормализованные секции и code chunks отдельно от «сырого» документа.
- Шардировать индекс по `documentId` / репозиторию.
- Холодный слой (объектное хранилище blob) + тёплый (локальный mmap) + горячий (RAM LRU по сессии).
- LLM только на уточнение смысла top-N изменений; детерминированный diff всегда первый.
- Для команды: один draft task на пакет связанных изменений, не N шума.

## Демо-скрипт на 2 минуты

1. Открыть UI, в чат `#sp1`.
2. `#sp2` - увидеть что изменилось и какие файлы front/back потенциально затронуты.
3. Спросить «что аффектит на код».
4. `#task` - тема + описание в чат и файл в `demo/tasks`.
5. Показать `/api/metrics`: latency diff/impact и счётчики.
