
package com.handcraftshop.util;

import com.google.gson.*;

import java.net.*;
import java.net.http.*;
import java.net.http.HttpHeaders;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class ProductImageDownloader {

    static Path oldFolder =
        Paths.get("src/main/webapp/images/products");

    static Path newFolder =
        Paths.get("src/main/webapp/images/products-new");

    static final String USER_AGENT =
        "HandcraftShop/1.0 (contact: chaithrakcsr@gmail.com)";

    static final long MIN_REQUEST_INTERVAL = 1600;

    static long lastRequestTime = 0;

    static HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    static Gson gson = new Gson();

    static Set<String> hashes = new HashSet<>();

    static Set<Long> visualHashes = new HashSet<>();

    static class RateLimitException extends IOException {

        RateLimitException(String message) {
            super(message);
        }
    }

    static class ImageCandidate {

        String category;
        int id;
        String productName;

        String title;
        String imageUrl;
        String pageUrl;

        String licence;
        String licenceUrl;
        String artist;
        String attribution;
        String description;

        String handmadeEvidence;

        int score;
    }

    // ONLY 96 PRODUCTS
    // 16 CATEGORIES x 6 PRODUCTS

    static String[][] products = {

        // 1. CERAMICS

        {"Ceramics","1","Terracotta Amphora"},
        {"Ceramics","5","Glazed Celadon Teapot"},
        {"Ceramics","11","Blue Pottery Vase"},
        {"Ceramics","12","Terracotta Serving Bowl"},
        {"Ceramics","13","Celadon Tea Set"},
        {"Ceramics","18","Hand-painted Ceramic Plate"},

        // 2. TEXTILES

        {"Textiles","2","Hand-Loomed Tapestry"},
        {"Textiles","7","Bagru Mud-Resist Cushions"},
        {"Textiles","21","Kutch Handloom Throw"},
        {"Textiles","23","Indigo Table Runner"},
        {"Textiles","24","Handwoven Cotton Stole"},
        {"Textiles","27","Kantha Quilt"},

        // 3. WOODWORK

        {"Woodwork","4","Carved Rosewood Box"},
        {"Woodwork","8","Kashmiri Walnut Bowl"},
        {"Woodwork","31","Carved Rosewood Keepsake Box"},
        {"Woodwork","33","Teak Wood Candle Holders"},
        {"Woodwork","34","Carved Wooden Wall Panel"},
        {"Woodwork","35","Wooden Desk Organizer"},

        // 4. BRASS & COPPER

        {"Brass & Copper","3","Brass Singing Bowl"},
        {"Brass & Copper","6","Bastar Dhokra Totem"},
        {"Brass & Copper","41","Hand-hammered Brass Singing Bowl"},
        {"Brass & Copper","42","Brass Oil Lamp"},
        {"Brass & Copper","43","Copper Water Bottle"},
        {"Brass & Copper","44","Brass Incense Holder"},

        // 5. SILVER JEWELLERY

        {"Silver Jewellery","51","Oxidized Silver Jhumkas"},
        {"Silver Jewellery","52","Silver Filigree Pendant"},
        {"Silver Jewellery","53","Silver Toe Rings"},
        {"Silver Jewellery","54","Engraved Silver Cuff"},
        {"Silver Jewellery","55","Silver Chain Necklace"},
        {"Silver Jewellery","56","Silver Anklet Pair"},

        // 6. RESIN ART

        {"Resin Art","61","Ocean Resin Serving Tray"},
        {"Resin Art","62","Botanical Resin Coasters"},
        {"Resin Art","63","Resin Geode Wall Art"},
        {"Resin Art","64","Resin Jewellery Dish"},
        {"Resin Art","65","Marbled Resin Desk Clock"},
        {"Resin Art","67","Resin Cheese Board"},

        // 7. CROCHET & MACRAME

        {"Crochet & Macrame","72","Macrame Plant Hanger"},
        {"Crochet & Macrame","73","Crochet Table Runner"},
        {"Crochet & Macrame","74","Crochet Bucket Hat"},
        {"Crochet & Macrame","75","Macrame Wall Hanging"},
        {"Crochet & Macrame","76","Crochet Coaster Set"},
        {"Crochet & Macrame","77","Crochet Baby Blanket"},

        // 8. BAGS & PURSES

        {"Bags & Purses","71","Crochet Market Tote"},
        {"Bags & Purses","79","Crochet Sling Bag"},
        {"Bags & Purses","81","Handwoven Jute Tote"},
        {"Bags & Purses","82","Block-print Cotton Sling Bag"},
        {"Bags & Purses","83","Embroidered Clutch"},
        {"Bags & Purses","85","Patchwork Weekend Bag"},

        // 9. FASHION ACCESSORIES

        {"Fashion Accessories","91","Hand-painted Silk Scarf"},
        {"Fashion Accessories","92","Beaded Hairband"},
        {"Fashion Accessories","93","Block-print Scrunchie Set"},
        {"Fashion Accessories","94","Embroidered Brooch"},
        {"Fashion Accessories","95","Handwoven Belt"},
        {"Fashion Accessories","96","Fabric Bow Clip Set"},

        // 10. HOME DECOR

        {"Home Decor","101","Hand-painted Wall Plate"},
        {"Home Decor","102","Terracotta Table Lamp"},
        {"Home Decor","103","Block-print Table Mats"},
        {"Home Decor","104","Handwoven Storage Basket"},
        {"Home Decor","105","Madhubani Wooden Frame"},
        {"Home Decor","106","Brass Flower Urli"},

        // 11. ARTISAN JEWELLERY

        {"Artisan Jewellery","111","Madhubani Pendant Necklace"},
        {"Artisan Jewellery","112","Terracotta Bead Necklace"},
        {"Artisan Jewellery","113","Blue Pottery Stud Earrings"},
        {"Artisan Jewellery","114","Dhokra Tribal Earrings"},
        {"Artisan Jewellery","115","Silk Thread Necklace"},
        {"Artisan Jewellery","116","Wooden Bead Bracelet"},

        // 12. PAPER CRAFTS

        {"Paper Crafts","121","Handmade Deckle-edge Journal"},
        {"Paper Crafts","122","Block-printed Gift Tags"},
        {"Paper Crafts","123","Papier-mache Decorative Bowl"},
        {"Paper Crafts","124","Handmade Greeting Card Set"},
        {"Paper Crafts","125","Paper Quilling Wall Art"},
        {"Paper Crafts","126","Hand-bound Sketchbook"},

        // 13. PAINTINGS

        {"Paintings","131","Madhubani Fish Painting"},
        {"Paintings","132","Warli Village Scene"},
        {"Paintings","133","Miniature Floral Painting"},
        {"Paintings","134","Pattachitra Mythology Panel"},
        {"Paintings","135","Landscape Watercolour"},
        {"Paintings","136","Madhubani Tree of Life"},

        // 14. LEATHERWORK

        {"Leatherwork","141","Hand-stitched Leather Cardholder"},
        {"Leatherwork","142","Leather Journal Cover"},
        {"Leatherwork","143","Leather Key Fob"},
        {"Leatherwork","144","Leather Belt"},
        {"Leatherwork","145","Leather Coin Pouch"},
        {"Leatherwork","146","Leather Passport Sleeve"},

        // 15. OTHER HANDMADE CRAFTS

        {"Other Handmade Crafts","151","Coconut Shell Serving Set"},
        {"Other Handmade Crafts","152","Hand-painted Festive Mask"},
        {"Other Handmade Crafts","153","Bamboo Desk Caddy"},
        {"Other Handmade Crafts","154","Lacquered Wooden Toy Set"},
        {"Other Handmade Crafts","155","Palm-leaf Basket"},
        {"Other Handmade Crafts","156","Stone-carved Incense Stand"},

        // 16. KITCHEN & DINING

        {"Kitchen & Dining","16","Speckled Coffee Mugs"},
        {"Kitchen & Dining","19","Textured Water Pitcher"},
        {"Kitchen & Dining","28","Loom-woven Placemats"},
        {"Kitchen & Dining","30","Handloom Tablecloth"},
        {"Kitchen & Dining","32","Walnut Serving Bowl"},
        {"Kitchen & Dining","37","Brass-inlay Wooden Tray"}
    };

    public static void main(String[] args) throws Exception {

        Files.createDirectories(newFolder);

        System.out.println("TOTAL PRODUCTS: " + products.length);

        loadHashes(oldFolder);
        loadHashes(newFolder);

        int downloaded = 0;
        int failed = 0;
        int skipped = 0;

        for (String[] product : products) {

            String category = product[0];

            int id = Integer.parseInt(product[1]);

            String name = product[2];

            if (alreadyExists(id)) {

                System.out.println(
                    "Already exists: " + id + " - " + name
                );

                skipped++;
                continue;
            }

            System.out.println("\n================================");

            System.out.println("Category: " + category);

            System.out.println("Product ID: " + id);

            System.out.println("Product: " + name);

            try {

                if (searchAndDownload(category, id, name)) {

                    downloaded++;

                } else {

                    failed++;
                }

            } catch (RateLimitException e) {

                System.out.println(e.getMessage());

                System.out.println(
                    "Stopping safely because of API rate limiting."
                );

                break;

            } catch (Exception e) {

                failed++;

                System.out.println(
                    "Failed: " + e.getMessage()
                );
            }
        }

        System.out.println("\n================================");
        System.out.println("FINAL RESULT");
        System.out.println("================================");

        System.out.println("Total selected products: " + products.length);

        System.out.println("Downloaded candidates: " + downloaded);

        System.out.println("Products without candidates: " + failed);

        System.out.println("Already existing: " + skipped);

        System.out.println(
            "Folder: " + newFolder.toAbsolutePath()
        );

        System.out.println(
            "Source file: image_sources_96.csv"
        );

        System.out.println("================================");

        System.out.println(
            "Review images before adding them to the website."
        );
    }

    // SEARCH PRODUCT IMAGES

    static boolean searchAndDownload(
            String category,
            int id,
            String name) throws Exception {

        String api =
            "https://commons.wikimedia.org/w/api.php"
            + "?action=query"
            + "&generator=search"
            + "&gsrnamespace=6"
            + "&gsrlimit=30"
            + "&gsrsearch="
            + URLEncoder.encode(name, StandardCharsets.UTF_8)
            + "&prop=imageinfo"
            + "&iiprop=url%7Cextmetadata"
            + "&iiextmetadatafilter="
            + "LicenseShortName%7CLicenseUrl%7CArtist%7CAttribution%7CImageDescription"
            + "&iiurlwidth=1200"
            + "&maxlag=5"
            + "&format=json";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(api))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();

        HttpResponse<String> response =
            sendTextWithRetry(request);

        if (response.statusCode() != 200) {

            throw new IOException(
                "Search HTTP error: " + response.statusCode()
            );
        }

        JsonObject root = gson.fromJson(
            response.body(),
            JsonObject.class
        );

        if (root.has("error")) {

            System.out.println(
                "API message: "
                + root.getAsJsonObject("error")
                       .get("info").getAsString()
            );

            return false;
        }

        if (!root.has("query")) {

            System.out.println(
                "No search results: " + name
            );

            return false;
        }

        JsonObject pages = root.getAsJsonObject("query")
                               .getAsJsonObject("pages");

        if (pages == null) {
            return false;
        }

        List<ImageCandidate> candidates =
            new ArrayList<>();

        Set<String> seenTitles = new HashSet<>();

        for (Map.Entry<String, JsonElement> entry
                : pages.entrySet()) {

            JsonObject page =
                entry.getValue().getAsJsonObject();

            if (!page.has("imageinfo")) {
                continue;
            }

            JsonArray imageInfo =
                page.getAsJsonArray("imageinfo");

            if (imageInfo.size() == 0) {
                continue;
            }

            JsonObject info =
                imageInfo.get(0).getAsJsonObject();

            if (!info.has("extmetadata")) {
                continue;
            }

            JsonObject metadata =
                info.getAsJsonObject("extmetadata");

            String licence =
                metadataValue(metadata, "LicenseShortName");

            String licenceUrl =
                metadataValue(metadata, "LicenseUrl");

            if (!allowedLicence(licence)) {
                continue;
            }

            String title = page.has("title")
                ? page.get("title").getAsString()
                : "";

            if (!seenTitles.add(title.toLowerCase(Locale.ROOT))) {
                continue;
            }

            String description =
                metadataValue(metadata, "ImageDescription");

            int score = relevanceScore(
                name,
                title,
                description
            );

            if (score < 5) {
                continue;
            }

            ImageCandidate candidate =
                new ImageCandidate();

            candidate.category = category;
            candidate.id = id;
            candidate.productName = name;

            candidate.title = title;

            candidate.imageUrl = info.has("thumburl")
                ? info.get("thumburl").getAsString()
                : info.has("url")
                    ? info.get("url").getAsString()
                    : "";

            candidate.pageUrl = info.has("descriptionurl")
                ? info.get("descriptionurl").getAsString()
                : "https://commons.wikimedia.org/wiki/"
                    + URLEncoder.encode(
                        title.replace(" ", "_"),
                        StandardCharsets.UTF_8
                    );

            candidate.licence = licence;
            candidate.licenceUrl = licenceUrl;

            candidate.artist =
                metadataValue(metadata, "Artist");

            candidate.attribution =
                metadataValue(metadata, "Attribution");

            candidate.description = description;

            candidate.handmadeEvidence =
                hasHandmadeEvidence(title + " " + description)
                    ? "Craft-related wording found - verify manually"
                    : "Not stated - verify manually";

            candidate.score = score;

            if (candidate.handmadeEvidence.startsWith("Craft")) {
                candidate.score += 3;
            }

            if (!candidate.imageUrl.isBlank()) {
                candidates.add(candidate);
            }
        }

        candidates.sort(
            Comparator.comparingInt(
                (ImageCandidate c) -> c.score
            ).reversed()
        );

        System.out.println(
            "Relevant licensed candidates: " + candidates.size()
        );

        for (ImageCandidate candidate : candidates) {

            System.out.println(
                "Checking: " + candidate.title
                + " | Score: " + candidate.score
            );

            try {

                HttpRequest imageRequest =
                    HttpRequest.newBuilder()
                        .uri(URI.create(candidate.imageUrl))
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();

                HttpResponse<byte[]> imageResponse =
                    sendBytesWithRetry(imageRequest);

                if (imageResponse.statusCode() != 200) {
                    continue;
                }

                byte[] bytes = imageResponse.body();

                if (bytes.length < 10000) {
                    continue;
                }

                BufferedImage image = ImageIO.read(
                    new ByteArrayInputStream(bytes)
                );

                if (image == null) {
                    continue;
                }

                if (image.getWidth() < 400 ||
                    image.getHeight() < 400) {

                    continue;
                }

                String hash = sha256(bytes);

                if (hashes.contains(hash)) {

                    System.out.println(
                        "Exact duplicate rejected."
                    );

                    continue;
                }

                long visualHash =
                    calculateVisualHash(image);

                if (isVisualDuplicate(visualHash)) {

                    System.out.println(
                        "Visually similar image rejected."
                    );

                    continue;
                }

                String format = detectFormat(bytes);

                if (format == null) {
                    continue;
                }

                Path output = newFolder.resolve(
                    "new-product-" + id + "." + format
                );

                if (Files.exists(output)) {
                    continue;
                }

                Files.write(
                    output,
                    bytes,
                    StandardOpenOption.CREATE_NEW
                );

                hashes.add(hash);
                visualHashes.add(visualHash);

                saveSourceRecord(candidate);

                System.out.println(
                    "DOWNLOADED: " + id
                    + " - " + candidate.title
                );

                return true;

            } catch (RateLimitException e) {

                throw e;

            } catch (Exception e) {

                System.out.println(
                    "Candidate skipped: " + e.getMessage()
                );
            }
        }

        System.out.println(
            "No suitable licensed image found: " + id
        );

        return false;
    }

    // PRODUCT NAME MATCHING

    static int relevanceScore(
            String productName,
            String title,
            String description) {

        String titleText = normalize(title);

        String descriptionText = normalize(description);

        String[] words = normalize(productName).split(" ");

        Set<String> ignored = Set.of(
            "hand",
            "handmade",
            "handcrafted",
            "traditional",
            "artisan",
            "decorative",
            "small",
            "large",
            "pair",
            "set",
            "with",
            "for",
            "the",
            "and",
            "of",
            "by"
        );

        int score = 0;

        int titleMatches = 0;

        int descriptionMatches = 0;

        for (String word : words) {

            if (word.length() < 3 ||
                ignored.contains(word)) {

                continue;
            }

            if (containsWord(titleText, word)) {

                score += 5;

                titleMatches++;

            } else if (containsWord(descriptionText, word)) {

                score += 2;

                descriptionMatches++;
            }
        }

        if (titleMatches == 0 && descriptionMatches < 2) {
            return 0;
        }

        return score;
    }

    static boolean containsWord(
            String text,
            String word) {

        String[] words = text.split(" ");

        for (String current : words) {

            if (current.equals(word) ||
                current.equals(word + "s") ||
                current.equals(word + "es") ||
                word.equals(current + "s")) {

                return true;
            }
        }

        return false;
    }

    static String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", " ")
                    .trim();
    }

    // CHECK FOR HANDMADE / CRAFT-RELATED WORDING

    static boolean hasHandmadeEvidence(String value) {

        String text = normalize(value);

        String[] words = {
            "handmade",
            "hand crafted",
            "handcrafted",
            "hand woven",
            "handloom",
            "hand painted",
            "handcarved",
            "hand carved",
            "hand thrown",
            "hand hammered",
            "hand stitched",
            "hand embroidered",
            "artisan",
            "craft",
            "traditional art",
            "crochet",
            "macrame",
            "block print",
            "dhokra",
            "madhubani",
            "warli",
            "pattachitra",
            "filigree",
            "channapatna",
            "kantha"
        };

        for (String word : words) {

            if (text.contains(normalize(word))) {
                return true;
            }
        }

        return false;
    }

    // COMMERCIAL LICENCE CHECK

    static boolean allowedLicence(String licence) {

        if (licence == null || licence.isBlank()) {
            return false;
        }

        String value =
            licence.toLowerCase(Locale.ROOT);

        if (value.contains("non-commercial") ||
            value.contains("noncommercial") ||
            value.matches(".*\\b(nc|nd)\\b.*")) {

            return false;
        }

        return value.contains("cc0")
            || value.contains("public domain")
            || value.contains("cc by");
    }

    // READ WIKIMEDIA METADATA

    static String metadataValue(
            JsonObject metadata,
            String key) {

        if (metadata == null || !metadata.has(key)) {
            return "";
        }

        JsonElement element = metadata.get(key);

        if (element == null || element.isJsonNull()) {
            return "";
        }

        if (element.isJsonObject()) {

            JsonObject object =
                element.getAsJsonObject();

            if (!object.has("value")) {
                return "";
            }

            element = object.get("value");
        }

        if (element.isJsonNull()) {
            return "";
        }

        return cleanHtml(element.getAsString());
    }

    static String cleanHtml(String value) {

        if (value == null) {
            return "";
        }

        return value.replaceAll(
                        "(?i)<br\\s*/?>", " "
                    )
                    .replaceAll("<[^>]*>", " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replaceAll("\\s+", " ")
                    .trim();
    }

    // HTTP REQUEST WITH RATE-LIMIT HANDLING

    static void waitForRequestSlot() throws InterruptedException {

        long now = System.currentTimeMillis();

        long elapsed = now - lastRequestTime;

        long remaining = MIN_REQUEST_INTERVAL - elapsed;

        if (remaining > 0) {
            Thread.sleep(remaining);
        }

        lastRequestTime = System.currentTimeMillis();
    }

    static HttpResponse<String> sendTextWithRetry(
            HttpRequest request) throws Exception {

        for (int attempt = 1; attempt <= 3; attempt++) {

            waitForRequestSlot();

            HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            int status = response.statusCode();

            if (status != 429 && status != 503) {
                return response;
            }

            long wait = retryDelaySeconds(
                response.headers(), attempt
            );

            if (attempt == 3) {

                throw new RateLimitException(
                    "Wikimedia rate limit continues. "
                    + "Please try again later."
                );
            }

            System.out.println(
                "Rate limit received. Waiting "
                + wait + " seconds."
            );

            Thread.sleep(wait * 1000L);
        }

        throw new RateLimitException(
            "Search request stopped."
        );
    }

    static HttpResponse<byte[]> sendBytesWithRetry(
            HttpRequest request) throws Exception {

        for (int attempt = 1; attempt <= 3; attempt++) {

            waitForRequestSlot();

            HttpResponse<byte[]> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
            );

            int status = response.statusCode();

            if (status != 429 && status != 503) {
                return response;
            }

            long wait = retryDelaySeconds(
                response.headers(), attempt
            );

            if (attempt == 3) {

                throw new RateLimitException(
                    "Image download rate limit continues."
                );
            }

            System.out.println(
                "Image rate limit received. Waiting "
                + wait + " seconds."
            );

            Thread.sleep(wait * 1000L);
        }

        throw new RateLimitException(
            "Image download stopped."
        );
    }

    static long retryDelaySeconds(
            HttpHeaders headers,
            int attempt) {

        String value = headers.firstValue("Retry-After")
                              .orElse("");

        try {

            return Math.max(
                5,
                Long.parseLong(value)
            );

        } catch (NumberFormatException ignored) {
        }

        try {

            Instant retryTime = ZonedDateTime.parse(
                value,
                DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant();

            return Math.max(
                5,
                Duration.between(
                    Instant.now(), retryTime
                ).getSeconds()
            );

        } catch (Exception ignored) {
        }

        return 5L * attempt;
    }

    // UNIQUE IMAGE CHECKING

    static long calculateVisualHash(
            BufferedImage image) {

        long hash = 0;

        for (int y = 0; y < 8; y++) {

            for (int x = 0; x < 8; x++) {

                int left = grayValue(image, x, y);

                int right = grayValue(image, x + 1, y);

                hash = (hash << 1)
                    | (left > right ? 1 : 0);
            }
        }

        return hash;
    }

    static int grayValue(
            BufferedImage image,
            int x,
            int y) {

        int px =
            (x * (image.getWidth() - 1)) / 8;

        int py =
            (y * (image.getHeight() - 1)) / 7;

        int rgb = image.getRGB(px, py);

        int red = (rgb >> 16) & 255;

        int green = (rgb >> 8) & 255;

        int blue = rgb & 255;

        return (red + green + blue) / 3;
    }

    static boolean isVisualDuplicate(long newHash) {

        for (long oldHash : visualHashes) {

            int difference = Long.bitCount(
                newHash ^ oldHash
            );

            if (difference <= 3) {
                return true;
            }
        }

        return false;
    }

    // SAVE IMAGE SOURCE AND LICENCE DETAILS

    static void saveSourceRecord(
            ImageCandidate candidate) throws IOException {

        Path csv = newFolder.resolve(
            "image_sources_96.csv"
        );

        if (!Files.exists(csv)) {

            Files.writeString(
                csv,
                "Category,Product ID,Product Name,"
                + "Image Title,Image Page URL,"
                + "Direct Image URL,Licence,Licence URL,"
                + "Creator,Attribution,Description,"
                + "Handmade Evidence,Match Score\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE
            );
        }

        String row =
            csvCell(candidate.category) + ","
            + csvCell(String.valueOf(candidate.id)) + ","
            + csvCell(candidate.productName) + ","
            + csvCell(candidate.title) + ","
            + csvCell(candidate.pageUrl) + ","
            + csvCell(candidate.imageUrl) + ","
            + csvCell(candidate.licence) + ","
            + csvCell(candidate.licenceUrl) + ","
            + csvCell(candidate.artist) + ","
            + csvCell(candidate.attribution) + ","
            + csvCell(candidate.description) + ","
            + csvCell(candidate.handmadeEvidence) + ","
            + csvCell(String.valueOf(candidate.score))
            + "\n";

        Files.writeString(
            csv,
            row,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
    }

    static String csvCell(String value) {

        if (value == null) {
            value = "";
        }

        return "\""
            + value.replace("\"", "\"\"")
            + "\"";
    }

    // CHECK EXISTING PRODUCT IMAGE

    static boolean alreadyExists(int id) {

        return Files.exists(
            newFolder.resolve("new-product-" + id + ".jpg")
        ) || Files.exists(
            newFolder.resolve("new-product-" + id + ".png")
        );
    }

    // LOAD IMAGE HASHES FROM EXISTING AND NEW FOLDERS

    static void loadHashes(Path folder) throws Exception {

        if (!Files.exists(folder)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(folder)) {

            List<Path> files = stream
                .filter(Files::isRegularFile)
                .toList();

            for (Path file : files) {

                try {

                    BufferedImage image =
                        ImageIO.read(file.toFile());

                    if (image != null) {

                        byte[] bytes =
                            Files.readAllBytes(file);

                        hashes.add(sha256(bytes));

                        visualHashes.add(
                            calculateVisualHash(image)
                        );
                    }

                } catch (Exception e) {

                    System.out.println(
                        "Could not inspect: "
                        + file.getFileName()
                    );
                }
            }
        }
    }

    // SHA-256 DUPLICATE CHECK

    static String sha256(byte[] bytes)
            throws Exception {

        byte[] hash = MessageDigest
            .getInstance("SHA-256")
            .digest(bytes);

        StringBuilder result =
            new StringBuilder();

        for (byte b : hash) {

            result.append(
                String.format("%02x", b)
            );
        }

        return result.toString();
    }

    // IDENTIFY IMAGE FORMAT

    static String detectFormat(byte[] bytes) {

        if (bytes.length > 3 &&
            (bytes[0] & 255) == 255 &&
            (bytes[1] & 255) == 216) {

            return "jpg";
        }

        if (bytes.length > 8 &&
            bytes[0] == (byte) 137 &&
            bytes[1] == 80 &&
            bytes[2] == 78 &&
            bytes[3] == 71) {

            return "png";
        }

        return null;
    }
}
