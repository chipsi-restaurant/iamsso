# IAM SSO

Система единого входа с OAuth 2.0 / OpenID Connect, ролевым (RBAC) и атрибутным (ABAC) контролем доступа.

## Архитектура

Микросервисы на Kotlin и Spring Boot 4. У каждого сервиса своя база в общем инстансе PostgreSQL, синхронное общение идёт
по HTTP через шлюз, асинхронное — через Kafka.

| Сервис               | Порт | Назначение                                          |
|----------------------|------|-----------------------------------------------------|
| gateway-service      | 8090 | Единая точка входа, валидация JWT, проверка политик |
| auth-service         | 8080 | OAuth 2.0 / OIDC, выдача и отзыв токенов            |
| user-service         | 8081 | Пользователи, верификация email                     |
| policy-service       | 8082 | Хранение и оценка политик                           |
| mfa-service          | 8083 | TOTP и Email OTP                                    |
| notification-service | 8084 | Отправка писем по событиям из Kafka                 |
| audit-service        | 8085 | Журнал событий безопасности                         |
| session-service      | 8086 | SSO-сессии в Redis                                  |
| vacation-backend     | 8087 | Демо-приложение, портал отпусков                    |

Фронтенды: админка на порту 3000 (пользователи, политики, аудит) и портал отпусков на 3001 — обычный OAuth-клиент,
который логинится через SSO.

Инфраструктура: PostgreSQL 16, Redis 7, Kafka 3.9 (KRaft, без ZooKeeper), Vault 1.15 в dev-режиме для секретов. Kafka UI
поднимается на 9000.

### Как проходит запрос

Всё внешнее идёт на шлюз (8090). Он смотрит путь: `/oauth2/**`, `/.well-known/**`, `/login`, `/forgot-password`,
`/reset-password`, `/mfa-challenge/**`, `/userinfo` считаются публичными и просто проксируются в auth-service. Для
остального шлюз сначала проверяет подпись access-токена по JWKS, потом спрашивает у session-service, жива ли SSO-сессия,
и только затем отправляет запрос в policy-service на решение allow/deny. Маршруты матчатся по порядку, поэтому
`/api/v1/sessions/logout` объявлен до общего `/api/v1/sessions/**` — иначе логаут ушёл бы не в тот сервис.

Ответы session-service шлюз кэширует в Caffeine на 5 секунд. Отзыв токена срабатывает почти сразу, но каждый запрос при
этом не идёт в базу.

## Что реализовано

Аутентификация — Authorization Code Flow с PKCE (S256), ротация refresh-токенов с отзывом по семейству при повторном
использовании, второй фактор через TOTP (Google Authenticator и совместимые) или одноразовый код на почту, сброс пароля
по email.

Авторизация — политики привязываются к роли (`admin`, `user`) либо описывают условие на атрибутах субъекта и ресурса,
например `resource.owner_id == subject.user_id`. Работает deny-by-default: если ни одна политика не вернула ALLOW,
запрос отклоняется. При равном приоритете DENY выигрывает у ALLOW.

Сессии — лежат в Redis, можно отозвать одну сессию или сразу все сессии пользователя.

## Запуск

Нужны Docker с Compose, JDK 21+ и Gradle.

```bash
./gradlew bootJar
docker compose -f docker-compose.yaml up -d
docker compose -f docker-compose.yaml -f docker-compose.services.yaml up -d
```

Первая команда собирает JAR-ы, вторая поднимает инфраструктуру, третья — сами сервисы и фронтенды.

После старта: админка на http://localhost:3000, портал отпусков на http://localhost:3001, Kafka UI
на http://localhost:9000.

Дефолтный админ — `admin` / `admin@kolyshkin.space`. Пароль лежит в миграции `004-seed-admin-user.yaml` в виде
bcrypt-хэша.

## Структура

```
libs/contracts/      Kafka-события: CloudEvent-конверт и доменные DTO
libs/common/         общие утилиты
services/            восемь бэкенд-сервисов из таблицы выше
frontend/            админка, React + Vite
vacation-portal/     демо: backend на Spring Boot + frontend на React
infra/postgres/      init-скрипт, создаёт базы под каждый сервис
```

## Переменные окружения

Notification-service берёт SMTP-настройки из `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`. Без них сервис
поднимется, но письма отправляться не будут — верификация email, OTP и сброс пароля работать не будут тоже.
