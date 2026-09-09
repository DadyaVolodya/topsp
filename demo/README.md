# Демо TopSP на Spring PetClinic

Рядом с проектом только урезанный PetClinic, без Aisto/ПТО.

```
demo/
  sp/                 # СП visits v1/v2
  code/petclinic-back # VisitController, VisitService, OwnerController
  code/petclinic-front# PetVisitsPage, VisitRow, visitsApi
  tasks/              # draft задач
```

Сценарий: `#sp1` > `#sp2` > «что изменилось / что аффектит» > `#task`.

Смысл изменения: отмена визита и правка description разрешены по правилам 24ч / до дня визита.
