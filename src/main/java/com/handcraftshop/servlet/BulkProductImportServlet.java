
package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.util.ApiUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imports multiple products using a CSV file containing HTTPS image URLs.
 * Only signed-in and approved sellers can import products.
 */
@WebServlet("/api/seller/bulk-products")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 12L * 1024 * 1024,
        maxRequestSize = 14L * 1024 * 1024
)
public class BulkProductImportServlet extends HttpServlet {

    private static final int MAX_PRODUCTS = 200;
    private static final int MAX_IMAGES_PER_PRODUCT = 4;
    private static final int MAX_CSV_BYTES = 4 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 40;
    private static final int MAX_IMAGE_URL_LENGTH = 500;

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException, ServletException {

        if (!approvedSeller(request, response)) {
            return;
        }

        try {

            Part zipPart = request.getPart("bulkZip");

            if (zipPart == null || zipPart.getSize() == 0) {
                ApiUtil.error(
                        response,
                        400,
                        "Choose a ZIP file containing products.csv."
                );
                return;
            }

            String submittedName = zipPart.getSubmittedFileName();

            if (submittedName == null ||
                    !submittedName.toLowerCase(Locale.ROOT).endsWith(".zip")) {

                ApiUtil.error(response, 400, "Upload a .zip file.");
                return;
            }

            String csv = readCsvFromZip(zipPart);

            List<BulkProduct> products = parseProducts(csv);

            if (products.isEmpty()) {
                ApiUtil.error(
                        response,
                        400,
                        "products.csv does not contain any product rows."
                );
                return;
            }

            List<Integer> createdProductIds = new ArrayList<>();

            int savedImageCount = 0;

            try (Connection connection = DBConnection.getConnection()) {

                connection.setAutoCommit(false);

                try {

                    Map<String, String> activeCategories =
                            loadActiveCategories(connection);

                    // Validate all categories before inserting products.
                    for (BulkProduct product : products) {

                        if (!activeCategories.containsKey(
                                product.category().toLowerCase(Locale.ROOT))) {

                            throw new IllegalArgumentException(
                                    "CSV row " + product.csvRow()
                                    + ": category '" + product.category()
                                    + "' is not an active marketplace category."
                            );
                        }
                    }

                    String insertProduct =
                            "INSERT INTO products(" +
                            "name,category,origin,artisan,price_inr," +
                            "price_usd,dimensions,badge,description," +
                            "active,seller_id,material,stock_quantity,featured" +
                            ") VALUES(?,?,?,?,?,?,?,?,?,1,?,?,?,0)";

                    String insertImage =
                            "INSERT INTO product_images(" +
                            "product_id,image_path,is_primary,sort_order" +
                            ") VALUES(?,?,?,?)";

                    try (
                            PreparedStatement productStatement =
                                    connection.prepareStatement(
                                            insertProduct,
                                            Statement.RETURN_GENERATED_KEYS
                                    );

                            PreparedStatement imageStatement =
                                    connection.prepareStatement(insertImage)
                    ) {

                        for (BulkProduct product : products) {

                            productStatement.setString(
                                    1, product.name()
                            );

                            productStatement.setString(
                                    2,
                                    activeCategories.get(
                                            product.category().toLowerCase(Locale.ROOT)
                                    )
                            );

                            productStatement.setString(
                                    3, product.origin()
                            );

                            productStatement.setString(
                                    4, product.artisan()
                            );

                            productStatement.setBigDecimal(
                                    5, product.priceInr()
                            );

                            productStatement.setBigDecimal(
                                    6,
                                    product.priceInr().divide(
                                            new BigDecimal("80"),
                                            2,
                                            RoundingMode.HALF_UP
                                    )
                            );

                            productStatement.setString(
                                    7, product.dimensions()
                            );

                            productStatement.setString(
                                    8, product.badge()
                            );

                            productStatement.setString(
                                    9, product.description()
                            );

                            productStatement.setInt(
                                    10, ApiUtil.sessionId(request)
                            );

                            productStatement.setString(
                                    11, product.material()
                            );

                            productStatement.setInt(
                                    12, product.stockQuantity()
                            );

                            productStatement.executeUpdate();

                            int productId;

                            try (
                                    ResultSet keys =
                                            productStatement.getGeneratedKeys()
                            ) {

                                if (!keys.next()) {
                                    throw new SQLException(
                                            "MySQL did not return an ID for product '"
                                            + product.name() + "'."
                                    );
                                }

                                productId = keys.getInt(1);
                            }

                            createdProductIds.add(productId);

                            // Save image URLs in product_images.
                            for (
                                    int imageIndex = 0;
                                    imageIndex < product.imageUrls().size();
                                    imageIndex++
                            ) {

                                String imageUrl =
                                        product.imageUrls().get(imageIndex);

                                imageStatement.setInt(1, productId);

                                // Store the HTTPS URL as image_path.
                                imageStatement.setString(2, imageUrl);

                                // First image is the main product image.
                                imageStatement.setBoolean(
                                        3, imageIndex == 0
                                );

                                imageStatement.setInt(
                                        4, imageIndex
                                );

                                imageStatement.addBatch();

                                savedImageCount++;
                            }

                            imageStatement.executeBatch();
                        }
                    }

                    connection.commit();

                } catch (Exception e) {

                    try {
                        connection.rollback();
                    } catch (SQLException rollbackError) {
                        e.addSuppressed(rollbackError);
                    }

                    throw e;
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();

            result.put("success", true);

            result.put("createdCount", createdProductIds.size());

            result.put("imageCount", savedImageCount);

            result.put("productIds", createdProductIds);

            result.put(
                    "message",
                    "Bulk upload completed: " +
                    createdProductIds.size() +
                    " products and " +
                    savedImageCount +
                    " image URLs were added to your shop."
            );

            ApiUtil.send(response, 201, result);

        } catch (IllegalArgumentException e) {

            ApiUtil.error(response, 400, e.getMessage());

        } catch (Exception e) {

            getServletContext().log(
                    "Handcraft Shop bulk product import failed", e
            );

            ApiUtil.error(
                    response,
                    500,
                    "Bulk upload failed: " + safeMessage(e)
            );
        }
    }

    // CHECK SELLER LOGIN AND APPROVAL
    private boolean approvedSeller(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        if (!ApiUtil.requireRole(request, response, "seller")) {
            return false;
        }

        Integer sellerId = ApiUtil.sessionId(request);

        if (sellerId == null) {

            ApiUtil.error(
                    response,
                    401,
                    "Please sign in again as a seller."
            );

            return false;
        }

        try (
                Connection connection = DBConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT approval_status FROM sellers WHERE seller_id=?"
                        )
        ) {

            statement.setInt(1, sellerId);

            try (ResultSet result = statement.executeQuery()) {

                if (!result.next() ||
                        !"APPROVED".equalsIgnoreCase(result.getString(1))) {

                    ApiUtil.error(
                            response,
                            403,
                            "Your seller account must be approved before bulk uploading products."
                    );

                    return false;
                }
            }

        } catch (SQLException e) {

            ApiUtil.error(
                    response,
                    500,
                    "Could not verify seller approval: " + safeMessage(e)
            );

            return false;
        }

        return true;
    }

    // READ PRODUCTS.CSV FROM THE ZIP
    private String readCsvFromZip(Part part) throws IOException {

        byte[] csvBytes = null;

        int entries = 0;

        try (
                ZipInputStream zip =
                        new ZipInputStream(part.getInputStream())
        ) {

            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {

                entries++;

                if (entries > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException(
                            "The ZIP contains too many entries."
                    );
                }

                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }

                String entryName =
                        entry.getName().replace('\\', '/');

                if (entryName.startsWith("/") ||
                        entryName.contains("../") ||
                        entryName.equals("..")) {

                    throw new IllegalArgumentException(
                            "The ZIP contains an unsafe file path."
                    );
                }

                String baseName =
                        entryName.substring(
                                entryName.lastIndexOf('/') + 1
                        );

                if (baseName.equalsIgnoreCase("products.csv")) {

                    if (csvBytes != null) {
                        throw new IllegalArgumentException(
                                "The ZIP must contain only one products.csv file."
                        );
                    }

                    csvBytes = readBounded(zip, MAX_CSV_BYTES);

                } else if (
                        !baseName.equalsIgnoreCase("README.txt") &&
                        !baseName.equalsIgnoreCase("README.md") &&
                        !baseName.equalsIgnoreCase(".DS_Store") &&
                        !baseName.equalsIgnoreCase("Thumbs.db") &&
                        !baseName.startsWith("._")
                ) {

                    throw new IllegalArgumentException(
                            "This URL-based template needs only products.csv "
                            + "and README files. Remove image files/folders from the ZIP."
                    );
                }

                zip.closeEntry();
            }
        }

        if (csvBytes == null) {

            throw new IllegalArgumentException(
                    "The ZIP must contain a file named products.csv."
            );
        }

        return new String(csvBytes, StandardCharsets.UTF_8);
    }

    // LIMIT CSV FILE SIZE
    private byte[] readBounded(
            InputStream input,
            int maximum
    ) throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];

        int read;

        int total = 0;

        while ((read = input.read(buffer)) != -1) {

            total += read;

            if (total > maximum) {

                throw new IllegalArgumentException(
                        "products.csv is larger than 4 MB."
                );
            }

            output.write(buffer, 0, read);
        }

        return output.toByteArray();
    }

    // PARSE CSV AND PRODUCT IMAGE URLS
    private List<BulkProduct> parseProducts(String csv) {

        List<List<String>> records = parseCsv(csv);

        if (records.isEmpty()) {
            throw new IllegalArgumentException(
                    "products.csv is empty."
            );
        }

        Map<String, Integer> header = new HashMap<>();

        List<String> headerRow = records.get(0);

        for (int i = 0; i < headerRow.size(); i++) {

            String name = headerRow.get(i)
                    .trim()
                    .replace("\uFEFF", "")
                    .toLowerCase(Locale.ROOT);

            if (!name.isEmpty()) {
                header.put(name, i);
            }
        }

        // Required product columns.
        for (String required :
                List.of("name", "category", "priceinr")) {

            if (!header.containsKey(required)) {

                throw new IllegalArgumentException(
                        "products.csv is missing the required column: "
                        + required + "."
                );
            }
        }

        // Accept imageUrl OR images column.
        if (!header.containsKey("imageurl") &&
                !header.containsKey("images")) {

            throw new IllegalArgumentException(
                    "Add an imageUrl column containing direct HTTPS image addresses."
            );
        }

        List<BulkProduct> products = new ArrayList<>();

        for (
                int rowIndex = 1;
                rowIndex < records.size();
                rowIndex++
        ) {

            List<String> row = records.get(rowIndex);

            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }

            int csvRow = rowIndex + 1;

            if (products.size() >= MAX_PRODUCTS) {

                throw new IllegalArgumentException(
                        "The bulk uploader accepts up to "
                        + MAX_PRODUCTS + " products per ZIP."
                );
            }

            String name = field(row, header, "name");

            String category = field(row, header, "category");

            String priceText = field(row, header, "priceinr");

            String imagesText =
                    header.containsKey("imageurl")
                    ? field(row, header, "imageurl")
                    : field(row, header, "images");

            if (
                    name.isBlank() ||
                    category.isBlank() ||
                    priceText.isBlank() ||
                    imagesText.isBlank()
            ) {

                throw new IllegalArgumentException(
                        "CSV row " + csvRow +
                        ": name, category, priceINR and imageUrl are required."
                );
            }

            if (name.length() > 150 || category.length() > 80) {

                throw new IllegalArgumentException(
                        "CSV row " + csvRow +
                        ": product name or category is too long."
                );
            }

            // PRICE VALIDATION
            BigDecimal price;

            try {

                price = new BigDecimal(priceText.trim())
                        .setScale(2, RoundingMode.HALF_UP);

            } catch (NumberFormatException e) {

                throw new IllegalArgumentException(
                        "CSV row " + csvRow +
                        ": priceINR must be a valid number."
                );
            }

            if (
                    price.compareTo(BigDecimal.ZERO) <= 0 ||
                    price.precision() > 10
            ) {

                throw new IllegalArgumentException(
                        "CSV row " + csvRow +
                        ": priceINR must be greater than zero and fit DECIMAL(10,2)."
                );
            }

            // STOCK VALIDATION
            int stock = 1;

            String stockText =
                    field(row, header, "stockquantity");

            if (!stockText.isBlank()) {

                try {

                    stock = Integer.parseInt(stockText);

                } catch (NumberFormatException e) {

                    throw new IllegalArgumentException(
                            "CSV row " + csvRow +
                            ": stockQuantity must be a whole number."
                    );
                }
            }

            if (stock < 0) {

                throw new IllegalArgumentException(
                        "CSV row " + csvRow +
                        ": stockQuantity cannot be negative."
                );
            }

            // IMAGE URL VALIDATION
            List<String> imageUrls = new ArrayList<>();

            Set<String> distinct = new HashSet<>();

            for (String rawUrl : imagesText.split("\\|")) {

                String url = validateImageUrl(rawUrl, csvRow);

                if (!distinct.add(url)) {

                    throw new IllegalArgumentException(
                            "CSV row " + csvRow +
                            ": the same image URL is listed more than once."
                    );
                }

                imageUrls.add(url);
            }

            if (
                    imageUrls.isEmpty() ||
                    imageUrls.size() > MAX_IMAGES_PER_PRODUCT
            ) {

                throw new IllegalArgumentException(
                        "CSV row " + csvRow +
                        ": add 1 to 4 HTTPS image URLs separated by |."
                );
            }

            // OPTIONAL PRODUCT DETAILS
            String material = field(row, header, "material");

            String origin = field(row, header, "origin");

            String artisan = field(row, header, "artisan");

            String dimensions = field(row, header, "dimensions");

            String badge = field(row, header, "badge");

            String description = field(row, header, "description");

            checkLength(csvRow, "material", material, 100);

            checkLength(csvRow, "origin", origin, 160);

            checkLength(csvRow, "artisan", artisan, 150);

            checkLength(csvRow, "dimensions", dimensions, 100);

            checkLength(csvRow, "badge", badge, 80);

            checkLength(csvRow, "description", description, 60000);

            products.add(
                    new BulkProduct(
                            csvRow,
                            name,
                            category,
                            material,
                            price,
                            stock,
                            origin,
                            artisan,
                            dimensions,
                            badge,
                            description,
                            imageUrls
                    )
            );
        }

        return products;
    }

    // VALIDATE HTTPS IMAGE ADDRESS
    private String validateImageUrl(
            String rawUrl,
            int csvRow
    ) {

        String value = rawUrl.trim();

        if (value.isEmpty()) {

            throw new IllegalArgumentException(
                    "CSV row " + csvRow +
                    ": imageUrl cannot be blank."
            );
        }

        if (value.length() > MAX_IMAGE_URL_LENGTH) {

            throw new IllegalArgumentException(
                    "CSV row " + csvRow +
                    ": image URL exceeds 500 characters."
            );
        }

        try {

            URI uri = URI.create(value);

            if (
                    !"https".equalsIgnoreCase(uri.getScheme()) ||
                    uri.getHost() == null ||
                    uri.getUserInfo() != null
            ) {

                throw new IllegalArgumentException(
                        "CSV row " + csvRow +
                        ": use a direct HTTPS image URL (not a search page link)."
                );
            }

            return value;

        } catch (IllegalArgumentException e) {

            if (
                    e.getMessage() != null &&
                    e.getMessage().startsWith("CSV row ")
            ) {
                throw e;
            }

            throw new IllegalArgumentException(
                    "CSV row " + csvRow +
                    ": imageUrl is not a valid HTTPS address."
            );
        }
    }

    // LOAD ACTIVE MARKETPLACE CATEGORIES
    private Map<String, String> loadActiveCategories(
            Connection connection
    ) throws SQLException {

        Map<String, String> categories = new HashMap<>();

        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT name FROM categories WHERE active=1"
                        );

                ResultSet result = statement.executeQuery()
        ) {

            while (result.next()) {

                String name = result.getString(1);

                categories.put(
                        name.toLowerCase(Locale.ROOT),
                        name
                );
            }
        }

        return categories;
    }

    // CSV PARSER
    private List<List<String>> parseCsv(String text) {

        List<List<String>> records = new ArrayList<>();

        List<String> row = new ArrayList<>();

        StringBuilder field = new StringBuilder();

        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {

            char ch = text.charAt(i);

            if (quoted) {

                if (ch == '"') {

                    if (
                            i + 1 < text.length() &&
                            text.charAt(i + 1) == '"'
                    ) {

                        field.append('"');
                        i++;

                    } else {

                        quoted = false;
                    }

                } else {

                    field.append(ch);
                }

            } else if (ch == '"' && field.length() == 0) {

                quoted = true;

            } else if (ch == ',') {

                row.add(field.toString());
                field.setLength(0);

            } else if (ch == '\n' || ch == '\r') {

                if (
                        ch == '\r' &&
                        i + 1 < text.length() &&
                        text.charAt(i + 1) == '\n'
                ) {
                    i++;
                }

                row.add(field.toString());

                field.setLength(0);

                if (!(row.size() == 1 && row.get(0).isBlank())) {
                    records.add(row);
                }

                row = new ArrayList<>();

            } else {

                field.append(ch);
            }
        }

        if (quoted) {

            throw new IllegalArgumentException(
                    "products.csv has an unclosed quotation mark."
            );
        }

        if (field.length() > 0 || !row.isEmpty()) {

            row.add(field.toString());

            if (!(row.size() == 1 && row.get(0).isBlank())) {
                records.add(row);
            }
        }

        return records;
    }

    // GET A CSV COLUMN VALUE
    private String field(
            List<String> row,
            Map<String, Integer> header,
            String name
    ) {

        Integer index = header.get(name);

        return index == null || index >= row.size()
                ? ""
                : row.get(index).trim();
    }

    // VALIDATE FIELD LENGTH
    private void checkLength(
            int row,
            String field,
            String value,
            int maximum
    ) {

        if (value.length() > maximum) {

            throw new IllegalArgumentException(
                    "CSV row " + row +
                    ": " + field +
                    " exceeds " + maximum + " characters."
            );
        }
    }

    // SAFE ERROR MESSAGE
    private String safeMessage(Exception e) {

        String message = e.getMessage();

        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : message;
    }

    // PRODUCT DATA
    private record BulkProduct(

            int csvRow,

            String name,

            String category,

            String material,

            BigDecimal priceInr,

            int stockQuantity,

            String origin,

            String artisan,

            String dimensions,

            String badge,

            String description,

            List<String> imageUrls

    ) { }

}
