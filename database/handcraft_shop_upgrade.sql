-- HANDCRAFT SHOP: additive upgrade for an existing handcraft_shop database.
-- Safe to run on the starter database: existing products, orders and order_items are retained.
CREATE DATABASE IF NOT EXISTS handcraft_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE handcraft_shop;

-- Starter tables (created only when they do not already exist).
CREATE TABLE IF NOT EXISTS products (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  category VARCHAR(80) NOT NULL,
  origin VARCHAR(160), artisan VARCHAR(150),
  price_inr DECIMAL(10,2) NOT NULL,
  price_usd DECIMAL(10,2) NOT NULL DEFAULT 0,
  dimensions VARCHAR(100), badge VARCHAR(80), description TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_number VARCHAR(40) NOT NULL UNIQUE,
  total_amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PLACED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id INT NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

-- Customer and seller tables already created in the setup steps are reused.
CREATE TABLE IF NOT EXISTS customers (
  customer_id INT PRIMARY KEY AUTO_INCREMENT,
  full_name VARCHAR(100) NOT NULL,
  email VARCHAR(150) UNIQUE NOT NULL,
  phone VARCHAR(15),
  password VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS sellers (
  seller_id INT PRIMARY KEY AUTO_INCREMENT,
  shop_name VARCHAR(150) NOT NULL,
  owner_name VARCHAR(100) NOT NULL,
  email VARCHAR(150) UNIQUE NOT NULL,
  phone VARCHAR(15),
  password VARCHAR(255) NOT NULL,
  seller_location VARCHAR(255),
  approval_status VARCHAR(20) DEFAULT 'PENDING',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Idempotent column migration helper for existing MySQL 8 databases.
DROP PROCEDURE IF EXISTS handcraft_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE handcraft_add_column_if_missing(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column
  ) THEN
    SET @handcraft_ddl = CONCAT('ALTER TABLE `', REPLACE(p_table,'`',''), '` ADD COLUMN `', REPLACE(p_column,'`',''), '` ', p_definition);
    PREPARE handcraft_stmt FROM @handcraft_ddl;
    EXECUTE handcraft_stmt;
    DEALLOCATE PREPARE handcraft_stmt;
  END IF;
END$$
DELIMITER ;

CALL handcraft_add_column_if_missing('customers','address_line','VARCHAR(255) NULL');
CALL handcraft_add_column_if_missing('customers','city','VARCHAR(100) NULL');
CALL handcraft_add_column_if_missing('customers','state','VARCHAR(100) NULL');
CALL handcraft_add_column_if_missing('customers','pincode','VARCHAR(12) NULL');
CALL handcraft_add_column_if_missing('customers','updated_at','TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL handcraft_add_column_if_missing('customers','active','BOOLEAN NOT NULL DEFAULT 1');
CALL handcraft_add_column_if_missing('sellers','shop_description','TEXT NULL');
CALL handcraft_add_column_if_missing('sellers','craft_speciality','VARCHAR(150) NULL');
CALL handcraft_add_column_if_missing('sellers','verification_note','VARCHAR(500) NULL');
CALL handcraft_add_column_if_missing('sellers','updated_at','TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL handcraft_add_column_if_missing('products','seller_id','INT NULL');
CALL handcraft_add_column_if_missing('products','material','VARCHAR(100) NULL');
CALL handcraft_add_column_if_missing('products','stock_quantity','INT NOT NULL DEFAULT 25');
CALL handcraft_add_column_if_missing('products','rating_avg','DECIMAL(3,2) NOT NULL DEFAULT 0');
CALL handcraft_add_column_if_missing('products','rating_count','INT NOT NULL DEFAULT 0');
CALL handcraft_add_column_if_missing('products','featured','BOOLEAN NOT NULL DEFAULT 0');
CALL handcraft_add_column_if_missing('products','updated_at','TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL handcraft_add_column_if_missing('orders','customer_id','INT NULL');
CALL handcraft_add_column_if_missing('orders','delivery_address','VARCHAR(255) NULL');
CALL handcraft_add_column_if_missing('orders','delivery_city','VARCHAR(100) NULL');
CALL handcraft_add_column_if_missing('orders','delivery_state','VARCHAR(100) NULL');
CALL handcraft_add_column_if_missing('orders','delivery_pincode','VARCHAR(12) NULL');
CALL handcraft_add_column_if_missing('orders','delivery_phone','VARCHAR(20) NULL');
CALL handcraft_add_column_if_missing('orders','payment_method','VARCHAR(30) NOT NULL DEFAULT ''TEST_PAYMENT''');
CALL handcraft_add_column_if_missing('orders','payment_status','VARCHAR(30) NOT NULL DEFAULT ''PENDING''');
CALL handcraft_add_column_if_missing('orders','guest_name','VARCHAR(120) NULL');
CALL handcraft_add_column_if_missing('orders','guest_email','VARCHAR(180) NULL');
CALL handcraft_add_column_if_missing('orders','cancel_reason','VARCHAR(500) NULL');
CALL handcraft_add_column_if_missing('order_items','seller_id','INT NULL');
CALL handcraft_add_column_if_missing('order_items','seller_order_id','BIGINT NULL');
CALL handcraft_add_column_if_missing('order_items','item_status','VARCHAR(30) NOT NULL DEFAULT ''PLACED''');

DROP PROCEDURE handcraft_add_column_if_missing;

-- Product categories available in browsing and in the all-category chatbot.
CREATE TABLE IF NOT EXISTS categories (
  category_id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL UNIQUE,
  description VARCHAR(300),
  image_url VARCHAR(500),
  sort_order INT NOT NULL DEFAULT 100,
  active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;
INSERT INTO categories(name,description,sort_order) VALUES
('Ceramics','Hand-shaped pottery, tableware and kiln-fired objects',1),
('Textiles','Handloom fabrics, cushions, rugs and woven pieces',2),
('Woodwork','Carved, turned and joinery-made wood objects',3),
('Brass & Copper','Hammered brass, copperware and metal craft',4),
('Silver','Handcrafted silver objects and jewellery',5),
('Resin Art','Resin trays, keepsakes and decorative art',6),
('Crochet & Macrame','Handmade yarn, crochet and knotted fibre work',7),
('Bags & Purses','Handcrafted bags, clutches and purses',8),
('Fashion Accessories','Wearable accessories and artisan details',9),
('Home Decor','Handmade accents for living spaces',10),
('Jewellery','Artisan necklaces, earrings, bracelets and rings',11),
('Paper Crafts','Handmade paper goods, journals and stationery',12),
('Paintings','Original paintings and hand-painted objects',13),
('Leatherwork','Hand-stitched leather accessories and utility goods',14),
('Other Handmade Crafts','Unique work from independent makers',15)
ON DUPLICATE KEY UPDATE description=VALUES(description),sort_order=VALUES(sort_order),active=1;

CREATE TABLE IF NOT EXISTS admins (
  admin_id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product_images (
  image_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id INT NOT NULL,
  image_path VARCHAR(500) NOT NULL,
  is_primary BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_product_images_product FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS customer_addresses (
  address_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id INT NOT NULL,
  label VARCHAR(40) NOT NULL DEFAULT 'Home',
  recipient_name VARCHAR(120) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  address_line VARCHAR(255) NOT NULL,
  city VARCHAR(100) NOT NULL,
  state VARCHAR(100) NOT NULL,
  pincode VARCHAR(12) NOT NULL,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_customer_addresses_customer FOREIGN KEY(customer_id) REFERENCES customers(customer_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS seller_orders (
  seller_order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  seller_id INT NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PLACED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_seller_orders_order FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_seller_orders_seller FOREIGN KEY(seller_id) REFERENCES sellers(seller_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payments (
  payment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  provider VARCHAR(60) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(30) NOT NULL,
  transaction_reference VARCHAR(100),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_payments_order FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS wishlist_items (
  wishlist_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id INT NOT NULL,
  product_id INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_wishlist_customer_product(customer_id,product_id),
  CONSTRAINT fk_wishlist_customer FOREIGN KEY(customer_id) REFERENCES customers(customer_id) ON DELETE CASCADE,
  CONSTRAINT fk_wishlist_product FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product_reviews (
  review_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id INT NOT NULL,
  customer_id INT NOT NULL,
  rating TINYINT NOT NULL,
  review_text TEXT NOT NULL,
  is_visible BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_product_review_customer(product_id,customer_id),
  CONSTRAINT fk_reviews_product FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE,
  CONSTRAINT fk_reviews_customer FOREIGN KEY(customer_id) REFERENCES customers(customer_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Give the original 8 starter products a safe inventory and material label.
UPDATE products SET material=COALESCE(NULLIF(material,''),category),stock_quantity=GREATEST(stock_quantity,1),featured=CASE WHEN id<=8 THEN 1 ELSE featured END;
ALTER TABLE products MODIFY id INT NOT NULL AUTO_INCREMENT;

-- Helpful indexes for common marketplace and dashboard lookups.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  reset_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_type VARCHAR(20) NOT NULL,
  email VARCHAR(180) NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_password_reset_email(account_type,email,expires_at)
) ENGINE=InnoDB;

-- Make index creation safe when this upgrade script is run more than once.
DROP PROCEDURE IF EXISTS handcraft_add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE handcraft_add_index_if_missing(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(255))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name=p_table AND index_name=p_index
  ) THEN
    SET @handcraft_idx_ddl=CONCAT('CREATE INDEX `',REPLACE(p_index,'`',''),'` ON `',REPLACE(p_table,'`',''),'` (',p_columns,')');
    PREPARE handcraft_idx_stmt FROM @handcraft_idx_ddl;
    EXECUTE handcraft_idx_stmt;
    DEALLOCATE PREPARE handcraft_idx_stmt;
  END IF;
END$$
DELIMITER ;
CALL handcraft_add_index_if_missing('products','idx_products_category_active','category,active');
CALL handcraft_add_index_if_missing('products','idx_products_seller','seller_id');
CALL handcraft_add_index_if_missing('orders','idx_orders_customer_created','customer_id,created_at');
CALL handcraft_add_index_if_missing('order_items','idx_order_items_seller_suborder','seller_order_id');
CALL handcraft_add_index_if_missing('seller_orders','idx_seller_orders_seller_status','seller_id,status');
DROP PROCEDURE handcraft_add_index_if_missing;

SELECT 'Handcraft Shop database upgrade completed. Existing records were preserved.' AS result;
