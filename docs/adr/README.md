# Architecture Decision Records

ADR фиксируют значимые архитектурные решения Art Journal вместе с контекстом, последствиями и рассмотренными альтернативами.

| ADR | Статус | Решение |
|---|---|---|
| [0001](0001-server-backed-architecture.md) | Accepted | Перейти от локальной Room-базы к backend на Django/DRF и PostgreSQL, оставив Room в роли кеша Android-клиента. |

Статусы:

- `Proposed` — решение обсуждается;
- `Accepted` — решение принято и направляет дальнейшую разработку;
- `Superseded` — заменено более новым ADR;
- `Deprecated` — больше не рекомендуется, но не обязательно заменено.
