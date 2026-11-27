# HANDCRAFT SHOP — Multi-Seller Handmade Marketplace

This is an extension of the existing HandcraftShopJava project. It preserves the original kinetic dark artisan storefront, orbital carousel, moving craft ribbons, product gallery, 360° quick view, cart drawer, craft story, cart API and order checkout flow. It adds customer, artisan-seller and admin workflows around the existing products and order tables.

## Implemented modules

### Storefront and marketplace
- Original animated homepage and current eight sample products retained.
- Multi-category catalogue: ceramics, textiles, woodwork, brass and copper, silver, resin art, crochet and macrame, bags and purses, fashion accessories, home decor, jewellery, paper crafts, paintings, leatherwork and other handmade crafts.
- Search/autocomplete by product, origin, category, material and artisan; category filtering; advanced material and price range filters, minimum rating filter; sorting by price or rating.
- Seller-submitted products appear in the catalogue after seller approval.
- Product origin and seller location are displayed as marketplace metadata.
- Product quick view supports seller-uploaded multiple photos.
- Wishlist is stored per signed-in customer; guest wishlist is local to the browser session.

### Customer accounts
- Customer registration, login and logout.
- Password hashing using PBKDF2WithHmacSHA256.
- Forgot-password flow with one-time, 20-minute reset token.
- Customer profile editing and saved delivery addresses (add, edit and delete).
- Order history and cancellation while an order is still in an eligible pre-shipping status.
- Product ratings and written reviews.

### Artisan seller accounts
- Seller registration with shop name, owner, contact, craft speciality and location.
- Seller accounts begin as `PENDING`; an admin must approve the seller before products can be published.
- Seller dashboard with product, order and sales counts, plus editable shop profile and location.
- Add products with category, material, price, stock, product origin, maker, description and 1–4 JPG/PNG/WebP photos. A photo is required for seller listings.
- Edit product details/stock, deactivate products and view seller-specific order lines.
- Update seller fulfilment status for their own sub-orders only.

### Admin
- Admin login and dashboard counts for customers, sellers, pending seller applications, products, orders and sales.
- Approve/reject seller applications.
- Create, edit and activate/hide product categories. Active admin-created categories appear in the storefront filter and seller product form.
- View and enable/disable customer accounts; view marketplace orders and payment records; review monthly revenue, category sales and payment summaries.
- Update order statuses and activate/hide product listings.

### Orders, payments, email and assistant
- One customer order can contain products from multiple sellers. The marketplace stores one parent order and creates a seller-specific sub-order for each participating seller.
- Inventory is checked and reduced within the checkout transaction.
- Delivery name, phone, address, city, state and PIN code are captured. Customers can save addresses to their account.
- Cash on delivery and a labelled simulated test-payment mode are included. Test mode creates a local test transaction record only; it does **not** charge a real card, UPI account or bank account.
- Order confirmation, account, seller application, password-reset, cancellation and status notifications use SMTP when configured. Without SMTP, the site still works and writes a notification note to the Tomcat console.
- Floating Craft Assistant chatbot answers questions about all seeded craft categories, product discovery, seller approval, delivery, orders, cancellations, reviews and the test-payment mode.

## Technology
- Java 17
- Jakarta Servlet 6.1
- Apache Tomcat 11
- Maven WAR project
- MySQL 8+
- HTML, CSS and JavaScript (original storefront styling and Tailwind CDN retained)
- Gson, MySQL Connector/J and Jakarta Mail

## Setup in Eclipse

1. Extract `HandcraftShopJava_Final.zip`.
2. In Eclipse, choose **File → Import → Maven → Existing Maven Projects** and select the extracted `HandcraftShopJava` folder.
3. Wait for Maven dependencies to finish downloading.
4. Make sure the project is configured for Java 17 or newer and Tomcat 11.
5. Open MySQL Workbench and run the database script as described below.
6. Open `src/main/java/com/handcraftshop/config/DBConnection.java` and set the correct MySQL username/password, or configure the environment variables below.
7. Right-click the project → **Maven → Update Project**.
8. Right-click the project → **Run As → Run on Server**.
9. Open `http://localhost:8080/HandcraftShop/`.

## Database instructions

**For your existing database** (the one that already contains `products`, `orders`, `order_items`, `customers` and `sellers`): run only `database/handcraft_shop_upgrade.sql`. It adds the missing columns and marketplace tables without dropping the existing tables or deleting your records. The script is designed to be repeatable.

**For a brand-new database:** run `database/handcraft_shop.sql` first to create and seed the original sample catalogue, then run `database/handcraft_shop_upgrade.sql`.

The upgrade adds categories, admins, product images, customer addresses, seller sub-orders, payments, wishlists, product reviews and password-reset tokens. It also extends the existing customer, seller, product, order and order-item tables.

## Local admin account

On first application startup, the app creates this administrator account if it does not already exist:

- Account type: `Admin`
- Username/email: `admin@handcraft.local`
- Password: `Admin@123`

Change this immediately for any shared or deployed environment. You can set `HANDCRAFT_ADMIN_USER` and `HANDCRAFT_ADMIN_PASSWORD` before the first start to choose another initial admin login. The seed admin is created once; changing the environment variable later does not change an existing admin password.

## Database connection configuration

`DBConnection.java` supports these environment variables:

- `HANDCRAFT_DB_URL` — defaults to `jdbc:mysql://localhost:3306/handcraft_shop?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- `HANDCRAFT_DB_USER` — defaults to `root`
- `HANDCRAFT_DB_PASSWORD` — defaults to `root` in the starter file; change it to your MySQL password.

## Optional email configuration

Set these environment variables in the Tomcat server launch configuration to send real emails:

- `SMTP_HOST`
- `SMTP_PORT` (default `587`)
- `SMTP_USER`
- `SMTP_PASSWORD`
- `SMTP_FROM`
- `HANDCRAFT_PUBLIC_URL` (defaults to `http://localhost:8080/HandcraftShop` for password-reset links)

Use an application password or SMTP credential; do not commit secrets into the ZIP or source code. If SMTP is not configured, the app continues to work. Password-reset links are printed to the Tomcat console in local development so the reset flow can still be tested.

## Important pages

- `index.html` — storefront
- `login.html` — customer, seller and admin sign-in
- `register.html` — customer or seller registration
- `forgot-password.html` and `reset-password.html` — password recovery
- `checkout.html` — delivery and payment selection
- `customer.html` — profile, saved addresses, orders, cancellations, wishlist and reviews
- `seller.html` — product upload, stock and seller orders
- `admin.html` — seller approval, product moderation and order management

## Main API endpoints

- `GET /api/products` — catalogue; supports `category`, `q`, `material`, `minPrice`, `maxPrice`, `minRating` and `sort` query parameters.
- `GET /api/categories` — all active craft categories.
- `GET /api/cart`, `POST /api/cart`, `PUT /api/cart`, `DELETE /api/cart` — session shopping cart.
- `POST /api/auth/register`, `/login`, `/logout`, `/forgot-password`, `/reset-password`; `GET /api/auth/me`.
- `GET /api/profile`, `PUT /api/profile` — customer profile.
- `GET /api/addresses`, `POST /api/addresses`, `PUT /api/addresses`, `DELETE /api/addresses?id=...` — saved delivery addresses.
- `GET /api/wishlist`, `POST /api/wishlist`, `DELETE /api/wishlist?productId=...` — customer wishlist.
- `GET /api/reviews?productId=...`, `POST /api/reviews` — product reviews and ratings.
- `GET /api/orders`, `POST /api/orders`, `POST /api/orders/cancel` — customer orders and cancellation.
- `GET /api/seller/dashboard`, `/profile`, `/products`, `/orders`; seller profile PUT, product POST/PUT/DELETE and seller order PUT.
- `GET /api/admin/dashboard`, `/sellers`, `/orders`, `/products`, `/customers`, `/categories`, `/payments`, `/reports`; admin can POST/PUT categories and PUT sellers/orders/products/customers.
- `GET /api/chatbot`, `POST /api/chatbot` — category-aware craft assistant.

## Product photographs

Sellers can upload multiple product photos from Seller Studio. Accepted image formats are JPG/JPEG, PNG and WebP, up to 8 MB per file, up to 4 photos per product upload (32 MB per upload request). Files are placed in the web application's `uploads` folder and their paths are stored in `product_images`; the database stores image paths, not image binary blobs. Back up this folder along with the database. Do not upload private or copyrighted images without permission.

The existing eight demonstration products retain the original frontend's illustrated artwork until you add real photographs for those listings. Seller-added products can show their uploaded photographs immediately after the product record is available.

## Production notes

This is a full-stack learning project. The payment flow is intentionally simulated and must be connected to a real payment provider before accepting money. Configure SMTP to send actual emails. For public deployment, also configure HTTPS, secure cookie settings, stronger admin credentials, server-side validation, image scanning/size policies, backups, privacy notices and appropriate return/refund/legal policies.
