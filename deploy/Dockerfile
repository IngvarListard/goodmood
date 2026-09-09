# goodmood: self-contained образ с uberjar.
# Сборка образа предполагает, что uberjar уже собран (clj -T:build uberjar) —
# это делает bin/deploy.sh. Переменные окружения передаются через compose
# env_file (см. deploy/compose.yaml); .env внутрь образа не кладётся.
FROM eclipse-temurin:25-jre

# curl для HEALTHCHECK (в temurin его нет)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# non-root
RUN useradd --system --uid 10001 goodmood

WORKDIR /app
COPY target/goodmood-*.jar /app/app.jar

USER goodmood

# Дефолты для контейнера: БД в volume, порт наружу через reverse proxy DSM.
# GOODMOOD_SESSION_SECRET / GOODMOOD_ADMIN_* / OPENROUTER_API_KEY приходят
# из env_file.
ENV GOODMOOD_DB_PATH=/data/goodmood.db \
    GOODMOOD_PORT=3000

VOLUME /data
EXPOSE 3000

# 2 ГБ RAM на NAS: heap ограничен жёстко (дефолтная ¼ RAM недопустима).
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD curl -fsS http://localhost:3000/ || exit 1

ENTRYPOINT ["java", "-Xmx384m", "-jar", "/app/app.jar"]
