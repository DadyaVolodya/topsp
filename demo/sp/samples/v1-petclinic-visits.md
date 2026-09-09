# Системная постановка (демо): PetClinic visits

Документ: petclinic-visits.md
Продукт: Spring PetClinic (урезанный срез).

## 1. Контекст

Владелец (Owner) записывает питомца (Pet) на визит к ветеринару (Visit).
UI: страница Pet visits (`PetVisitsPage`, компонент `VisitRow`).
API: `VisitController` `/api/pets/{petId}/visits`, правила в `VisitService`.

## 3. Создание визита

Владелец может создать визит: дата и описание. Статус после создания: SCHEDULED.

## 3.2 Отмена и правка описания визита

После создания визита владелец не может отменить визит.
После создания визита описание визита нельзя редактировать.
Кнопка Cancel visit на фронте всегда disabled.
Методы `VisitService.canCancel` и `VisitService.canEditDescription` возвращают false.
`PUT /api/pets/{petId}/visits/{visitId}` и `DELETE` отвечают конфликтом, если визит уже создан.

## 4. Просмотр

Владелец видит список визитов питомца на `PetVisitsPage`.
