CREATE DATABASE IF NOT EXISTS handcraft_shop;
USE handcraft_shop;

CREATE TABLE IF NOT EXISTS products (
  id INT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  category VARCHAR(50) NOT NULL,
  origin VARCHAR(120),
  artisan VARCHAR(150),
  price_inr DECIMAL(10,2) NOT NULL,
  price_usd DECIMAL(10,2) NOT NULL,
  dimensions VARCHAR(100),
  badge VARCHAR(80),
  description TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_number VARCHAR(40) NOT NULL UNIQUE,
  total_amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PLACED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id INT NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
);

INSERT INTO products(id,name,category,origin,artisan,price_inr,price_usd,dimensions,badge,description) VALUES
(1,'Terracotta Amphora','Ceramics','Kutch, Gujarat','Rameshbhai Smoke-Kiln Guild',2850,36,'H: 30 cm × D: 17 cm','Kiln-Fired','Wheel-thrown red river clay fired in a sawdust reduction kiln for an obsidian sheen and hand-inlaid with molten gold mineral slip.'),
(2,'Hand-Loomed Tapestry','Textiles','Bhujodi, Kutch','Vankar Shamji Weaver Guild',3950,49,'135 cm × 190 cm','Pit-Loomed','Hand-spun organic Kala cotton dyed in iron-black and madder terracotta, woven on heritage pit looms with raw antique Zari threads.'),
(3,'Brass Singing Bowl','Brass','Moradabad, UP','Thathera Master Smiths',3400,43,'D: 22 cm × H: 11 cm','Hand-Hammered','Forged from a seven-metal bell alloy and hand-hammered over charcoal fires to produce deep harmonic resonance and a burnished gold patina.'),
(4,'Carved Rosewood Box','Woodwork','Saharanpur, UP','Zafar Heritage Joinery',3150,40,'26 cm × 16 cm × 11 cm','Chiseled Relief','Seasoned dark Indian rosewood hand-carved with botanical jaali filigree and inlaid with antique brass corners and velvet interior.'),
(5,'Glazed Celadon Teapot','Ceramics','Jaipur, Rajasthan','Sanganer Studio Kiln',2650,34,'H: 16 cm × W: 21 cm','Crackle Glaze','High-fired stoneware finished in deep jade-celadon ash glaze with a hand-bent oxidized brass handle and precision drip-free spout.'),
(6,'Bastar Dhokra Totem','Brass','Bastar, Chhattisgarh','Ghadwa Lost-Wax Guild',3650,46,'H: 24 cm × W: 14 cm','Lost-Wax Cast','Cast using the 4,000-year-old lost-wax process, capturing hand-rolled beeswax filigree permanently in raw antique bronze.'),
(7,'Bagru Mud-Resist Cushions','Textiles','Bagru, Rajasthan','Chhipa Dabu Printers',1950,25,'45 cm × 45 cm (Pair)','Dabu Indigo','Hand-stamped using carved teak blocks and river-mud resist paste before immersion in fermented natural indigo vats.'),
(8,'Kashmiri Walnut Bowl','Woodwork','Srinagar, Kashmir','Mir Woodcarving Atelier',2950,38,'D: 25 cm × H: 10 cm','Single-Block','Turned and chiseled from a single block of aged Himalayan walnut with deep chinar-leaf relief and organic beeswax luster.')
ON DUPLICATE KEY UPDATE name=VALUES(name), price_inr=VALUES(price_inr), description=VALUES(description);
