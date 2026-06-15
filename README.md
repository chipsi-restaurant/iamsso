# IAM SSO

Система единого входа (SSO) с поддержкой OAuth 2.0 / OpenID Connect, ролевого (RBAC) и атрибутного (ABAC) контроля доступа.

Выпускная квалификационная работа.

## Архитектура

Микросервисное приложение на Spring Boot 4 / Kotlin. Каждый сервис имеет собственную базу данных (PostgreSQL), общение между сервисами — через Kafka и HTTP.

### Сервисы

| Сервис | Порт | Назначение |
|---|---|---|
| gateway-service | 8090 | API-шлюз, JWT-валидация, применение политик |
| auth-service | 8080 | OAuth 2.0 / OIDC: авторизация, выдача токенов |
| user-service | 8081 | Управление пользователями, верификация email |
| policy-service | 8082 | Хранение и оценка RBAC/ABAC политик |
| mfa-service | 8083 | TOTP и Email OTP факторы |
| notification-service | 8084 | Отправка email через Kafka |
| audit-service | 8085 | Журнал событий безопасности |
| session-service | 8086 | SSO-сессии в Redis |
| vacation-backend | 8087 | Демо-приложение (портал отпусков) |

### Инфраструктура

- **PostgreSQL 16** — база данных каждого сервиса (отдельные схемы в одном инстансе)
- **Redis 7** — SSO-сессии, кэш сессий на шлюзе, MFA-челленджи, семейства refresh-токенов
- **Apache Kafka 3.9** — события аутентификации, сессий, уведомлений, аудита
- **HashiCorp Vault** — хранение секретов (в dev-режиме)

### Фронтенды

- **frontend** (порт 3000) — административная панель (управление пользователями, политиками, аудит)
- **vacation-frontend** (порт 3001) — демо-портал, клиент OAuth 2.0 через SSO

## Ключевые возможности

**Аутентификация**
- Authorization Code Flow + PKCE (S256)
- Refresh token rotation с revocation по семействам (обнаружение повторного использования)
- Многофакторная аутентификация: TOTP (Google Authenticator и совместимые) и Email OTP
- Сброс пароля через email

**Контроль доступа**
- RBAC: политики привязаны к роли пользователя (`admin`, `user`)
- ABAC: условия на полях субъекта и ресурса (например, `resource.owner_id == subject.user_id`)
- Deny-by-default: запрос отклоняется, если ни одна политика не дала ALLOW
- Конфликты: при одинаковом приоритете DENY побеждает ALLOW

**Сессии**
- SSO-сессия хранится в Redis, шлюз проверяет её существование при каждом запросе
- Кэш на шлюзе (Caffeine, TTL 5 сек) — near-real-time отзыв access-токенов без базы данных
- Отзыв отдельной сессии, отзыв всех сессий пользователя

## Запуск

Требования: Docker, Docker Compose, JDK 21+, Gradle.

```bash
# Сборка JAR-файлов
./gradlew bootJar

# Запуск инфраструктуры
docker compose -f docker-compose.yaml up -d

# Запуск сервисов
docker compose -f docker-compose.yaml -f docker-compose.services.yaml up -d
```

После запуска:
- Административный фронтенд: http://localhost:3000
- Портал отпусков: http://localhost:3001
- Kafka UI: http://localhost:9000

### Дефолтный администратор

```
Email:    admin@kolyshkin.space
Username: admin
```

Пароль задаётся в миграции `004-seed-admin-user.yaml` (bcrypt).

## Структура репозитория

```
libs/
  contracts/   — Kafka-события (CloudEvent envelope + доменные DTO)
  common/      — общие утилиты

services/
  auth-service/
  user-service/
  policy-service/
  mfa-service/
  notification-service/
  audit-service/
  session-service/
  gateway-service/

frontend/          — React + Vite, административный UI
vacation-portal/   — демо-приложение (Spring Boot backend + React frontend)
infra/             — init-скрипты PostgreSQL
```

## Переменные окружения (продакшн)

Notification-service требует SMTP-кредлов:

```
MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
```

Без них сервис запускается нормально, но письма не отправляются.
