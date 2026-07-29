# Art Journal backend

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
- минимальный кастомный `User` и граница данных `School`;
- нормализованная серверная модель учебной структуры, расписания, тем,
  занятий, прогресса и оплат;
- UUID, внешние ключи, индексы, PostgreSQL constraints и начальные миграции;
- проверка Art Journal JSON v1 по JSON Schema и предметным правилам;
- транзакционный импорт, `--dry-run`, SHA-256 и идемпотентность по `exportId`;
- изолированное хранение legacy-аудита и отчёты `ImportBatch`.

Авторизация, роли, публичные предметные API и подключение Android намеренно не
входят в этот этап. Наличие модели `User` не означает, что вход или выдача
токенов уже доступны. Импорт доступен только через management-команду.

Модель описана в [целевой серверной модели](../docs/server-data-model.md):
темы и критерии отделены от фактических занятий, а связь `LessonTopic`
поддерживает несколько тем в одном занятии и несколько занятий для одной
темы.

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
pytest
```

Эти же проверки выполняет отдельный workflow `Backend CI`.
