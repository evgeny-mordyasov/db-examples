# db-examples

Учебный Maven multi-module проект с примерами работы с PostgreSQL из Java 21.

Проект показывает:

- чистый JDBC через `DriverManager`, `Connection`, `Statement`, `PreparedStatement` и `ResultSet`;
- Spring JDBC через `JdbcTemplate`, `NamedParameterJdbcTemplate` и `JdbcClient`;
- repository-подход с ленивым постраничным чтением пользователей через `Iterator`;
- transactional outbox для атомарного сохранения заказа и события;
- интеграционные тесты с PostgreSQL в Testcontainers.

## Модули

- `common` - общие модели, мапперы, генераторы и SQL-миграции.
- `testcontainers-support` - общая конфигурация PostgreSQL Testcontainers и Flyway для тестов.
- `native-jdbc-examples` - примеры на чистом JDBC.
- `spring-jdbc-examples` - примеры на Spring JDBC.
- `repository-examples` - repository-примеры: iterator и outbox.

## Модель данных

- `users` - пользователи.
- `orders` - заказы.
- `outbox_events` - события transactional outbox.

Миграции лежат в `common/src/main/resources/db/migration`.
Тестовые данные пользователей лежат в `common/src/main/resources/db/manual/data_users.sql`.

## Сборка

```bash
mvn clean package
```

Компиляция:

```bash
mvn compile
```

Тесты:

```bash
mvn test
```

Интеграционные тесты используют Testcontainers, поэтому нужен доступный Docker.

## Локальный запуск примеров

`DbProps` ожидает PostgreSQL по адресу:

```text
jdbc:postgresql://localhost:5433/testdb
user: postgres
password: postgres
```

Примеры запускаются через `main`-классы соответствующих модулей из IDE или Maven/Java CLI.
