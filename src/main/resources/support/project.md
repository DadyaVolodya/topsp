# Doom-рация на Infereco

Shareware Doom в том же окне, что и LLM-напарник. Рация в реальном времени говорит куда идти и может вбить чит в эмулятор.

## Стек

- Java 25 LTS
- Spring Boot 4.1.0 (Spring Framework 7)
- Spring AI 2.0, Infereco `https://api.infereco.ru/v1`
- DeepSeek V4 Flash DSpark (чат и рация), GLM 5.2 (критик)
- js-dos: shareware Doom в браузере
- Vosk: локальный русский STT, без Google

## Запуск

```bash
cd barista-rag
cp config/application-local.yml.example config/application-local.yml
# впишите ключ Infereco в api-key
./mvnw spring-boot:run
```

Откройте `http://localhost:8080`. Нужен интернет до `v8.js-dos.com` (движок). Бандл `static/doom/doom.jsdos` лежит локально.

Ключ не коммитьте. `config/application-local.yml` в `.gitignore`.

## Что смотреть на демо

1. Кликни кадр Doom, походи, постреляй.
2. Рация сама пишет куда идти на Hangar.
3. Спроси «где дробовик» или «дай бога».
4. Кнопка «вбить iddqd» печатает чит в игру.
5. Справа GLM может выучить факт про ваш стиль.

## Этап 2 (ещё не в коде)

Саморазвёртывание этого сервиса и техподдержка репозитория через чат. Детали в `PLAN.md`.
