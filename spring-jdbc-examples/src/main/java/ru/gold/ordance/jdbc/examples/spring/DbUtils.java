package ru.gold.ordance.jdbc.examples.spring;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_PASSWORD;
import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_URL;
import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_USERNAME;

public final class DbUtils {

    private DbUtils() {
    }

    public static DataSource createDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(DB_URL);
        dataSource.setUsername(DB_USERNAME);
        dataSource.setPassword(DB_PASSWORD);
        return dataSource;
    }
}
