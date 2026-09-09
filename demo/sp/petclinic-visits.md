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

После создания визита владелец может отменить визит, если до даты визита осталось не меньше 24 часов.
Описание визита можно редактировать, пока дата визита ещё в будущем (до наступления дня визита).
На фронте `VisitRow`: кнопка Cancel visit и поле description управляются флагами `canCancel` и `canEditDescription`.
На backend `VisitService.canCancel` и `VisitService.canEditDescription` реализуют эти правила; `VisitController` вызывает update/cancel.
Если правило нарушено, API отвечает конфликтом (HTTP 409).

## 4. Просмотр

Владелец видит список визитов питомца на `PetVisitsPage`.
