# Art Journal backend

Минимальный серверный фундамент Art Journal. Он запускается отдельно от
Android-приложения и пока не является источником пользовательских данных.

## Что входит

- Python 3.13;
- Django 5.2 LTS;
- Django REST Framework;
- PostgreSQL 17;
- Docker Compose;
- readiness endpoint `GET /api/v1/health/`;
- Pytest и Ruff;
- заготовленные границы `accounts`, `journal` и `imports`.

Авторизация, серверная предметная модель, импорт JSON v1 и подключение Android
намеренно не входят в этот этап.

Контракт следующего этапа описан в
[целевой серверной модели](../docs/server-data-model.md): темы и критерии
отделены от фактических занятий, а безопасный импорт сначала будет доступен
через management-команду без публичного endpoint.

## Запуск через Docker Compose

Из корня репозитория:

```bash
cp .env.example .env
docker compose up --build
```

Compose дождётся готовности PostgreSQL, применит Django migrations и запустит
сервер на `http://localhost:8000`.

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

## Проверки

Из каталога `backend`:

```bash
ruff check .
ruff format --check .
python manage.py check
python manage.py makemigrations --check --dry-run
pytest
```

Эти же проверки выполняет отдельный workflow `Backend CI`.
