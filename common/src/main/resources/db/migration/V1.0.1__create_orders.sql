CREATE TABLE orders (
    order_id     SERIAL PRIMARY KEY,
    user_id      INT NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    amount       NUMERIC(10,2) NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user
        FOREIGN KEY (user_id)
            REFERENCES users(user_id)
            ON DELETE CASCADE
);