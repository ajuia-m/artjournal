# Art Journal backend

**[Main project overview](../README.md) · [Русский обзор проекта](../README.ru.md)**

Серверный фундамент Art Journal. Он запускается отдельно от Android-приложения
и умеет безопасно импортировать локальную резервную копию, но Android пока не
читает и не изменяет серверные данные.

## Что входит

- Python 3.13;
- Django 5.2 LTS;
- Django REST Framework;
- PostgreSQL 17;
- Docker Compose;
- readiness endpoint `GET /api/v1/health/`;
- Pytest и Ruff;
- кастомный `User`, JWT access/refresh tokens и отзыв refresh-токенов;
- школы, членства с ролями `admin`/`teacher` и назначения преподавателей;
- серверная матрица доступа с проверкой членства при каждом запросе;
- OpenAPI 3.0.3, публичная схема, Swagger UI и CI-валидация контракта;
- защищённый journal API для занятий, тем занятия, посещаемости и оценок;
- sync protocol v1 для состояний учеников: идемпотентные команды, optimistic
  concurrency, tombstone и cursor-based change feed;
- нормализованная серверная модель учебной структуры, расписания, тем,
  занятий, прогресса и оплат;
- UUID, внешние ключи, индексы, PostgreSQL constraints и начальные миграции;
- проверка Art Journal JSON v1 по JSON Schema и предметным правилам;
- транзакционный импорт, `--dry-run`, SHA-256 и идемпотентность по `exportId`;
- изолированное хранение legacy-аудита и отчёты `ImportBatch`.

Доступны API аутентификации, чтение доступных школ, управление членствами и
назначениями преподавателей, основной журнал занятий и первый sync vertical
slice. API прогресса по зачётным темам, snapshot sync, авторизация импорта и
подключение Android пока не входят в этот этап. Импорт по-прежнему доступен
только через management-команду.

Модель описана в [целевой серверной модели](../docs/server-data-model.md):
темы и критерии отделены от фактических занятий, а связь `LessonTopic`
поддерживает несколько тем в одном занятии и несколько занятий для одной
темы. Матрица ролей и API описаны в
[документе контроля доступа](../docs/access-control.md), правила OpenAPI — в
[документе API-контракта](../docs/openapi.md).

## Запуск через Docker Compose

Из корня репозитория:

```bash
cp .env.example .env
docker compose up --build
```

Compose дождётся готовности PostgreSQL, применит Django migrations и запустит
сервер на `http://localhost:8000`.

Если вы уже запускали раннюю версию backend до появления кастомного `User`,
пересоздайте только локальную development-базу:

```bash
docker compose down --volumes
docker compose up --build
```

Первая команда безвозвратно удаляет данные локального PostgreSQL volume. Для
базы с нужными данными вместо этого требуется отдельный план миграции.

Проверка:

```bash
curl http://localhost:8000/api/v1/health/
```

Ожидаемый ответ:

```json
{"status":"ok","database":"ok"}
```

Остановить контейнеры:

```bash
docker compose down
```

Удалить также локальный PostgreSQL volume:

```bash
docker compose down --volumes
```

Последняя команда безвозвратно удаляет данные development-базы.

## Локальный запуск без Docker

Если PostgreSQL не настроен, backend использует SQLite только для быстрых
локальных проверок:

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements/dev.txt
python manage.py migrate
python manage.py runserver
```

Для подключения к PostgreSQL задайте `POSTGRES_HOST` и остальные переменные из
корневого `.env.example`.

## Пользователи, роли и JWT

Сначала создайте суперпользователя:

```bash
docker compose exec web python manage.py createsuperuser
```

Через `/admin/` создайте школу, пользователя и его первое активное членство с
ролью `admin`. После bootstrap администратор школы может управлять членствами
и назначениями через API.

Получить пару токенов:

```bash
curl -X POST http://localhost:8000/api/v1/auth/token/ \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"your-password"}'
```

Access-токен живёт 15 минут, refresh-токен — 7 дней. При обновлении refresh
ротируется, а использованный токен попадает в blacklist:

```bash
curl -X POST http://localhost:8000/api/v1/auth/token/refresh/ \
  -H 'Content-Type: application/json' \
  -d '{"refresh":"REFRESH_TOKEN"}'
```

Проверить пользователя и его активные членства:

```bash
curl http://localhost:8000/api/v1/auth/me/ \
  -H 'Authorization: Bearer ACCESS_TOKEN'
```

Завершить сессию:

```bash
curl -X POST http://localhost:8000/api/v1/auth/token/logout/ \
  -H 'Content-Type: application/json' \
  -d '{"refresh":"REFRESH_TOKEN"}'
```

Роли не записываются в JWT. Сервер повторно читает активное членство и
назначения из PostgreSQL при каждом защищённом запросе, поэтому деактивация или
смена роли применяется немедленно и не ждёт истечения access-токена.

Основные endpoints:

| Метод и путь | Доступ |
|---|---|
| `GET /api/v1/schools/` | активные школы текущего пользователя |
| `GET /api/v1/schools/{schoolId}/` | активный участник школы |
| `GET/POST /api/v1/schools/{schoolId}/memberships/` | администратор школы |
| `GET/PATCH/DELETE /api/v1/schools/{schoolId}/memberships/{id}/` | администратор школы |
| `GET/POST /api/v1/schools/{schoolId}/teaching-assignments/` | администратор школы |
| `GET/PUT/PATCH/DELETE /api/v1/schools/{schoolId}/teaching-assignments/{id}/` | администратор школы |

OpenAPI-схема доступна по `/api/v1/schema/`, интерактивный Swagger UI — по
`/api/v1/docs/`. Оба endpoint публичны, но защищённые операции по-прежнему
требуют Bearer JWT.

## Journal API

Журнал вложен в школу и группу. Сервер не доверяет идентификаторам из URL:
группа должна принадлежать школе, а преподаватель — иметь активное назначение
на группу и дисциплину занятия.

| Метод и путь | Назначение |
|---|---|
| `GET/POST /api/v1/schools/{schoolId}/groups/{groupId}/lessons/` | список и создание занятий |
| `GET/PATCH/DELETE .../lessons/{lessonId}/` | чтение, изменение и удаление занятия |
| `GET/POST .../lessons/{lessonId}/states/` | состояния учеников на занятии |
| `GET/PATCH/DELETE .../states/{stateId}/` | посещаемость, оценка, домашние баллы и заметки |

Список занятий поддерживает `date_from`, `date_to` и `subject_id`.
`topic_ids` задаёт упорядоченный список тем: одна тема может использоваться в
нескольких занятиях, а одно занятие — включать несколько тем. Тема должна
соответствовать дисциплине и быть назначена группе. Состояние ученика можно
создать только при наличии зачисления в группу на дату занятия.

Администратор школы видит весь журнал. Преподаватель с назначением на всю
группу видит все дисциплины; назначение на отдельную дисциплину фильтрует
списки и запрещает чтение или изменение остальных занятий. Ошибки API имеют
единый вид `{"error":{"code":"...","message":"...","fields":{}}}`.

## Sync API v1

Первый вертикальный срез синхронизирует `StudentLessonState`: посещаемость,
оценку, домашние баллы, комментарий и заметку. Каждая команда содержит
постоянный `operationId`, `clientSequence`, `entityId` и последнюю известную
`baseVersion`.

| Метод и путь | Назначение |
|---|---|
| `POST /api/v1/schools/{schoolId}/sync/commands/` | применить до 100 offline-команд общей величиной до 1 MiB |
| `GET /api/v1/schools/{schoolId}/sync/changes/?cursor=...` | получить доступные изменения после cursor |

Команда возвращает один из статусов `applied`, `duplicate`, `conflict`,
`rejected` или `blocked`. Повтор идентичной команды не создаёт вторую запись;
повтор `operationId` с другим checksum отклоняется. Устаревшая `baseVersion` не
перезаписывает серверное значение и возвращает каноническое текущее состояние.

Обычные REST create/update/delete состояния используют тот же application
service, увеличивают `version` и создают `ChangeEvent`. Поэтому change feed
видит изменения независимо от того, через какой endpoint они были выполнены.
Преподаватель получает только события назначенных ему группы и дисциплины.

Формальная JSON Schema находится в
`apps/sync/schemas/sync-protocol-v1.schema.json`. Snapshot recovery и остальные
типы сущностей будут добавляться отдельными вертикальными срезами.

В production обязательно задайте разные длинные случайные значения
`DJANGO_SECRET_KEY` и `JWT_SIGNING_KEY`.

## Импорт Art Journal JSON v1

Сначала создайте целевую школу через Django Admin либо Django shell. Пример для
локальной development-базы:

```bash
docker compose exec web python manage.py shell -c \
  "from apps.schools.models import School; print(School.objects.create(name='Художественная школа', slug='art-school', default_currency='RUB').id)"
```

Команда напечатает UUID школы. До импорта выполните dry run, передавая файл
через стандартный ввод, чтобы не копировать персональные данные в репозиторий
или контейнер:

```bash
docker compose exec -T web python manage.py import_artjournal_backup \
  /dev/stdin --school SCHOOL_UUID --dry-run < backup.json
```

Если отчёт не содержит ошибок, запустите импорт без `--dry-run`:

```bash
docker compose exec -T web python manage.py import_artjournal_backup \
  /dev/stdin --school SCHOOL_UUID < backup.json
```

Повтор тех же байтов с тем же `exportId` возвращает сохранённый успешный
результат и не создаёт записей. Тот же `exportId` с другим SHA-256 считается
конфликтом. Новый экспорт той же Room-базы имеет новый `exportId`, поэтому JSON
v1 не защищает от смысловых дублей между разными экспортами.

Не помещайте реальные backup-файлы в Git, issue, CI artifacts или логи.

## Проверки

Из каталога `backend`:

```bash
ruff check .
ruff format --check .
python manage.py check
python manage.py makemigrations --check --dry-run
python manage.py spectacular \
  --file /tmp/artjournal-openapi.yaml \
  --validate --fail-on-warn
pytest
```

`Backend CI` выполняет эти проверки с PostgreSQL 17, валидирует Docker Compose,
запускает полный стек и проверяет database-backed health endpoint.
