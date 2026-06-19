# AGENTS.md

## Назначение проекта

`db-examples` - учебный Maven multi-module проект с примерами работы с PostgreSQL из Java 21.
Проект показывает несколько подходов к чтению и записи данных:

- низкоуровневый JDBC через `DriverManager`, `Connection`, `Statement`, `PreparedStatement` и `ResultSet`;
- Spring JDBC через `JdbcTemplate`, `NamedParameterJdbcTemplate` и `JdbcClient`;
- простой repository-подход с ленивым постраничным чтением пользователей через `Iterator`;
- transactional outbox для атомарного сохранения заказа и события;
- интеграционные тесты с PostgreSQL в Testcontainers.

Основная предметная модель проекта - пользователь (`users`), заказ (`orders`) и outbox-событие (`outbox_events`).
Подключение к локальной БД задано в `common`: `jdbc:postgresql://localhost:5433/testdb`, пользователь `postgres`, пароль `postgres`.

## Структура проекта

Корневой `pom.xml` описывает родительский проект `ru.gold.ordance:db-examples:1.0-SNAPSHOT`
с упаковкой `pom`, Java 21 и пятью модулями:

- `common` - общие классы, модели и SQL-ресурсы.
- `testcontainers-support` - общая конфигурация PostgreSQL Testcontainers и Flyway для тестов.
- `native-jdbc-examples` - примеры на чистом JDBC.
- `spring-jdbc-examples` - примеры на Spring JDBC.
- `repository-examples` - примеры repository-слоя поверх Spring `JdbcClient`, включая iterator и outbox.

### `common`

Общий модуль без внешних зависимостей проекта.

Важные файлы:

- `common/src/main/java/ru/gold/ordance/jdbc/examples/common/db/DbProps.java` - константы подключения к PostgreSQL.
- `common/src/main/java/ru/gold/ordance/jdbc/examples/common/db/RowMapper.java` - минимальный интерфейс маппера строк.
- `common/src/main/java/ru/gold/ordance/jdbc/examples/common/db/UserRowMapper.java` - маппинг `ResultSet` в `User`.
- `common/src/main/java/ru/gold/ordance/jdbc/examples/common/db/model/User.java` - модель пользователя.
- `common/src/main/java/ru/gold/ordance/jdbc/examples/common/db/model/Order.java` - модель заказа.
- `common/src/main/java/ru/gold/ordance/jdbc/examples/common/db/model/OutboxEvent.java` - модель outbox-события.
- `common/src/main/java/ru/gold/ordance/jdbc/examples/common/db/generator/UserGenerator.java` - генератор данных пользователей.
- `common/src/main/resources/db/migration/V1.0.0__create_users.sql` - создание таблицы `users`.
- `common/src/main/resources/db/migration/V1.0.1__create_orders.sql` - создание таблицы `orders`.
- `common/src/main/resources/db/migration/V1.0.2__create_outbox_events.sql` - создание таблицы `outbox_events`.
- `common/src/main/resources/db/manual/data_users.sql` - тестовые данные пользователей.

### `testcontainers-support`

Модуль с общей тестовой инфраструктурой.

Ключевой класс:

- `Containers.java` - поднимает PostgreSQL `postgres:17-alpine`, регистрирует адрес контейнера и настраивает Flyway на миграции из `classpath:db/migration`.

### `native-jdbc-examples`

Модуль с примерами прямой работы с JDBC и PostgreSQL-драйвером.

Пакет:

- `ru.gold.ordance.jdbc.examples.ntv`

Примеры покрывают:

- создание пользователя;
- поиск пользователя по `id`;
- чтение всех пользователей;
- пагинацию через `LIMIT/OFFSET`;
- курсорный обход;
- улучшенный курсорный обход;
- ограничения `Statement#setMaxRows`;
- размер выборки `Statement#setFetchSize`;
- таймауты `Statement#setQueryTimeout`;
- уровень изоляции `Connection#setTransactionIsolation`.

### `spring-jdbc-examples`

Модуль с аналогичными сценариями на Spring JDBC.

Пакеты:

- `ru.gold.ordance.jdbc.examples.spring.simple` - примеры с позиционными параметрами.
- `ru.gold.ordance.jdbc.examples.spring.named` - примеры с именованными параметрами.
- `ru.gold.ordance.jdbc.examples.spring.fluent` - примеры с fluent API `JdbcClient`.

Вспомогательный класс:

- `spring-jdbc-examples/src/main/java/ru/gold/ordance/jdbc/examples/spring/DbUtils.java` - создание `DataSource` из `DbProps`.

### `repository-examples`

Модуль с примерами выделения доступа к данным в repository.

Пакеты:

- `ru.gold.ordance.repository.examples.iterator` - ленивое постраничное чтение пользователей через `Iterator<User>`.
- `ru.gold.ordance.repository.examples.outbox` - transactional outbox для заказа и связанных событий.

Ключевые классы iterator-примера:

- `UserRepository.java` - контракт репозитория, возвращающий `Iterator<User>`.
- `UserRepositoryImpl.java` - постраничная загрузка пользователей батчами через `JdbcClient`.
- `iterator/spring/Main.java` - пример использования repository и логирования найденных пользователей.

Ключевые классы outbox-примера:

- `OrderService.java` - сохраняет заказ и outbox-событие в одной транзакции.
- `OrderRepositoryImpl.java` - запись заказов.
- `OutboxEventRepositoryImpl.java` - запись, claim и смена статусов outbox-событий.
- `OutboxEventRelay.java` - забирает пачку событий, вызывает consumer и отмечает результат.
- `outbox/spring/Main.java` - Spring-пример запуска outbox-сценария.

## Сборка и запуск

Сборка всех модулей:

```bash
mvn clean package
```

Компиляция без упаковки:

```bash
mvn compile
```

Запуск тестов:

```bash
mvn test
```

Интеграционные тесты используют Testcontainers, поэтому для них нужен доступный Docker.

Запуск примеров выполняется через `main`-классы в соответствующих модулях из IDE или Maven/Java CLI.
Перед запуском локальных примеров нужна доступная PostgreSQL БД по настройкам из `DbProps`; схему и данные можно подготовить SQL-файлами из `common/src/main/resources/db`.

## Правила для агентов

- Перед каждой сессией использовать `/caveman ultra`.
- Использовать `/ponytail full`.
- Для сложных фич и значимых рефакторингов использовать ExecPlan по `PLANS.md`.
- ExecPlan писать на русском языке.
- Не менять реализацию учебных примеров без явного запроса.
- При изменениях учитывать multi-module структуру Maven и общие классы из `common`.
- При изменении SQL-скриптов менять существующие скрипты, если изменение относится к ним.
- После каждого изменения запускать тесты на install-фазе, если это применимо к задаче.
- Не создавать отдельные ветки Git при реализации задач.
- Не коммитить пароли из реальных окружений; текущие значения в `DbProps` являются локальными учебными настройками.
