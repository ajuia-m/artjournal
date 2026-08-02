# OpenAPI-контракт Art Journal

Backend генерирует OpenAPI 3.0.3 из DRF serializers, URL patterns и явных
`extend_schema`-аннотаций. Генератор зафиксирован на конкретной версии:
обновление `drf-spectacular` требует отдельной проверки изменения схемы.

## Endpoints документации

| URL | Назначение |
|---|---|
| `/api/v1/schema/` | OpenAPI-схема; YAML по умолчанию |
| `/api/v1/schema/?format=json` | та же схема в JSON |
| `/api/v1/docs/` | интерактивный Swagger UI |

Схема и Swagger UI публичны: они описывают контракт, но не дают доступа к
данным. Защищённые операции требуют `Authorization: Bearer <access-token>`.
Swagger UI не сохраняет токен между перезагрузками страницы.

## Локальная генерация и проверка

Из каталога `backend`:

```bash
python manage.py spectacular \
  --file /tmp/artjournal-openapi.yaml \
  --validate \
  --fail-on-warn
```

Эта команда выполняется в Backend CI. Warning считается ошибкой: новый view не
может незаметно попасть в схему с угаданным serializer или нестабильным
`operationId`.

## Правила для новых endpoints

Каждый новый endpoint обязан:

1. использовать DRF serializer для request и response;
2. иметь стабильный уникальный `operation_id`;
3. иметь короткие `summary` и доменный tag;
4. явно указывать `auth=[]`, если endpoint публичный;
5. показывать UUID path parameters и фактические HTTP status codes;
6. проходить генерацию с `--validate --fail-on-warn`;
7. добавляться в проверку ожидаемых paths.

Роль, школа и назначения преподавателя не являются полями JWT security
scheme. OpenAPI описывает Bearer-аутентификацию, а фактическую авторизацию
сервер проверяет по PostgreSQL.

## Формат ошибок

DRF-ошибки нормализуются глобальным exception handler и возвращаются в одном
формате:

```json
{
  "error": {
    "code": "validation_error",
    "message": "Request validation failed.",
    "fields": {"date_to": ["date_to must be on or after date_from."]}
  }
}
```

Journal endpoints ссылаются на компонент `ApiError` для ответов `400`, `401`,
`403` и `404`. Необработанные серверные ошибки не преобразуются в публичные
подробности и остаются стандартными ответами `500` инфраструктуры.

## Sync contract

OpenAPI описывает transport endpoints `/sync/commands/` и `/sync/changes/`, а
JSON Schema `backend/apps/sync/schemas/sync-protocol-v1.schema.json` фиксирует
канонический offline-command envelope. Эти контракты дополняют друг друга:
OpenAPI задаёт HTTP request/response, JSON Schema используется для проверки
совместимости будущих Kotlin DTO и Python serializers.
