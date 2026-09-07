# ПТО карта и страницы aisto-front

База правды: СП в /Users/sparrow/Documents/pto/СП и код /Users/sparrow/Documents/pto/projects/aisto-front плюс aisto-pto-back. При правке СП напарник пишет в Telegram и ставит задачу разработчику.

Сайт демо: https://dev.aisto.local. Пути как в aisto-front APP_ROUTES: / главная, /login вход, /institutions карта организаций, /institutions/{id} карточка, /compare сравнение, /grant-program гранты, /grant-program/application заявка, /profile профиль, /questionnaire анкета, /events события.

Логин: поля Логин и Пароль, кнопка Войти. Не MOS.ID и не СУДИР. Логин pto-pp.

Шапка: пункт «Организации» ведёт на /institutions. Поиск на карте: поле «Поиск по названию» / «Поиск по названию учебного заведения». API как у фронта: GET /api/pto/institutions/suggest?query= и POST /api/pto/institutions/search на https://dev.aisto.local/api.

Гнесины: МССМШ им. Гнесиных, головное улица Знаменка, д. 12/2. Есть и МГДМШ им. Гнесиных, Большая Филевская, д. 29. Направления: Музыкальные инструменты, Театр, Архитектура и дизайн. Маршрут только с карточки: «Постройте маршрут». Ссылка как в aisto-front: Яндекс rtext=~широта,долгота.

Главная: блок «Популярные направления». Карточка ведёт на /institutions?direction=КОД: DRAWING_PAINTING рисунок и живопись, CHOREOGRAPHY хореография, DECORATIVE_APPLIED_ART декоративно-прикладное, MUSICAL_INSTRUMENTS музыкальные инструменты, ARCHITECTURE_AND_DESIGN архитектура и дизайн, THEATRE театр, VOCAL вокал.

Напарник открывает страницу на dev.aisto.local. Не веди в «Рисунок и живопись», если назвали конкретную школу.
