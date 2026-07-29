FROM python:3.13-slim-bookworm AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

COPY requirements/base.txt requirements/base.txt
RUN python -m pip install --no-cache-dir -r requirements/base.txt

RUN addgroup --system --gid 10001 artjournal \
    && adduser --system --uid 10001 --ingroup artjournal artjournal

COPY . .
RUN chown -R artjournal:artjournal /app

USER artjournal

ENTRYPOINT ["sh", "/app/docker/entrypoint.sh"]
CMD ["gunicorn", "config.wsgi:application", "--bind", "0.0.0.0:8000", "--workers", "2", "--access-logfile", "-", "--error-logfile", "-"]

FROM runtime AS development

USER root
COPY requirements/dev.txt requirements/dev.txt
RUN python -m pip install --no-cache-dir -r requirements/dev.txt
USER artjournal

CMD ["python", "manage.py", "runserver", "0.0.0.0:8000"]
