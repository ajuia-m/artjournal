# Architecture Decision Records

**[Project overview](../../README.md) · [Русский обзор](../../README.ru.md)**

ADR фиксируют значимые архитектурные решения Art Journal вместе с контекстом, последствиями и рассмотренными альтернативами.

| ADR | Статус | Реализация в `main` | Решение |
|---|---|---|---|
| [0001](0001-server-backed-architecture.md) | Accepted | Backend и Android session bootstrap реализованы; серверные данные журнала ещё не подключены | Перейти от локальной Room-базы к backend на Django/DRF и PostgreSQL. |
| [0002](0002-android-server-integration.md) | Accepted | `Local legacy`, server workspace, JWT-клиент и выбор школы реализованы; Room replica и миграция ожидаются | Подключать Android через явную data boundary, server-authoritative режим и контролируемую миграцию legacy-данных. |
| [0003](0003-offline-write-synchronization.md) | Accepted | Backend slice для `StudentLessonState` реализован; Android outbox/worker и snapshot ожидаются | Разрешить offline-записи через Room outbox, идемпотентные команды, версии, tombstone и серверный change feed. |

Статусы:

- `Proposed` — решение обсуждается;
- `Accepted` — решение принято и направляет дальнейшую разработку;
- `Superseded` — заменено более новым ADR;
- `Deprecated` — больше не рекомендуется, но не обязательно заменено.

`Accepted` означает, что решение принято, а не что каждый его этап уже
реализован. Фактический статус указан отдельно и синхронизирован с roadmap
главного README.
