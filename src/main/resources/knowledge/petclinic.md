# PetClinic (демо-срез TopSP)

Продукт: ветеринарная клиника Spring PetClinic.
Сущности: Owner, Pet, Visit.
Backend: petclinic-back (`VisitController`, `VisitService`, `OwnerController`).
Frontend: petclinic-front (`PetVisitsPage`, `VisitRow`, `visitsApi`).

Сценарий демо:
1. v1 СП: после создания визита нельзя отменить и нельзя править описание.
2. v2 СП: отмена за >=24ч до визита; описание редактируется до дня визита.
3. TopSP показывает diff, impact и собирает #task.
