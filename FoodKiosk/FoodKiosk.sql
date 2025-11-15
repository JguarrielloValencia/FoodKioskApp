/******************************************************************
Food Kiosk Database Initialization Script
 Author: Joey Guarriello
 Description:
   Creates the `foodkiosk` database, ensures the `products` table
   exists with a `sold` column, and optionally resets data.
******************************************************************/

-- ==============================================================
-- 1 Create and select the database
-- ==============================================================
CREATE DATABASE IF NOT EXISTS foodkiosk;
USE foodkiosk;

-- ==============================================================
-- 2️ Create the `products` table if it doesn't already exist
-- ==============================================================
CREATE TABLE IF NOT EXISTS products (
    id INT PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL
);

-- ==============================================================
-- 3️  Add the `sold` column if it’s missing
-- ==============================================================
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'products'
      AND COLUMN_NAME = 'sold'
);

SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE products ADD COLUMN sold INT NOT NULL DEFAULT 0 AFTER stock;',
    'SELECT "✅ Column `sold` already exists" AS info;'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==============================================================
-- 4️ (Optional) Clear out old data before reimporting
-- ==============================================================
-- TRUNCATE TABLE products;

-- ==============================================================
-- 5️ Verify structure and contents
-- ==============================================================
DESCRIBE products;       -- show table structure
SELECT * FROM products   -- show sample data
LIMIT 10;

-- +++++++==========================================
-- Thats all folks!!
