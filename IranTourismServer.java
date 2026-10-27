import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Iran Tourism — single-file backend.
 *
 * Requirements: JDK 11+ only (no external dependencies).
 *
 * Run:
 *   javac IranTourismServer.java
 *   java IranTourismServer
 *
 * Options:
 *   --port=8080         override port (also PORT env var)
 *
 * Files expected alongside this .class/.java file:
 *   index.html          the frontend (served on "/")
 *   places-seed.json    optional seed data (array of places)
 *   data/               created automatically for persistence
 *
 * On first run, if data/places.json does not exist and places-seed.json
 * is missing, the server tries to extract the full attraction dataset
 * directly from index.html (RAW_ATTRACTIONS). If that fails, a small
 * built-in seed is used.
 */
public class IranTourismServer {

    // =========================================================
    //  CONFIG
    // =========================================================
    static final int DEFAULT_PORT = 8080;
    static final String PORT_ENV = "PORT";
    static final int MAX_PAGE_SIZE = 50_000;
    static final int DEFAULT_PAGE_SIZE = 1000;

    static final Map<String, String> CATEGORY_COLORS = new LinkedHashMap<>();
    static {
        CATEGORY_COLORS.put("طبیعی", "#43865f");
        CATEGORY_COLORS.put("تاریخی", "#c09142");
        CATEGORY_COLORS.put("زمین‌شناسی", "#6a85b4");
        CATEGORY_COLORS.put("فرهنگی", "#9b6b9c");
        CATEGORY_COLORS.put("خوراک", "#d47c4a");
        CATEGORY_COLORS.put("مسیر", "#428f9a");
    }

    // =========================================================
    //  STATE
    // =========================================================
    static Path baseDir;
    static Path dataDir;
    static Path placesFile;
    static Path passportFile;
    static Path submissionsFile;
    static Path newsletterFile;
    static Path indexHtml;
    static Path placesSeed;

    static final List<Place> places = new CopyOnWriteArrayList<>();
    static final Map<String, Place> placeById = new ConcurrentHashMap<>();
    static final Map<String, Set<String>> savedByClient = new ConcurrentHashMap<>();
    static final Map<String, Set<String>> visitedByClient = new ConcurrentHashMap<>();
    static final Set<String> newsletterEmails = ConcurrentHashMap.newKeySet();
    static final List<Map<String, Object>> submissions = new CopyOnWriteArrayList<>();
    static final AtomicLong submissionCounter = new AtomicLong();
    static final Object saveLock = new Object();

    // =========================================================
    //  MAIN
    // =========================================================
    public static void main(String[] args) throws Exception {
        int port = resolvePort(args);
        resolvePaths();

        loadAll();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/places", IranTourismServer::handlePlaces);
        server.createContext("/api/provinces", IranTourismServer::handleProvinces);
        server.createContext("/api/passport/", IranTourismServer::handlePassport);
        server.createContext("/api/submissions", IranTourismServer::handleSubmissions);
        server.createContext("/api/newsletter", IranTourismServer::handleNewsletter);
        server.createContext("/api/health", IranTourismServer::handleHealth);
        server.createContext("/health", IranTourismServer::handleHealth);
        server.createContext("/", IranTourismServer::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();

        System.out.println("============================================");
        System.out.println("  Iran Tourism Server");
        System.out.println("  URL        : http://localhost:" + port + "/");
        System.out.println("  API        : http://localhost:" + port + "/api");
        System.out.println("  Base dir   : " + baseDir.toAbsolutePath());
        System.out.println("  Data dir   : " + dataDir.toAbsolutePath());
        System.out.println("  Places     : " + places.size());
        System.out.println("  Java       : " + System.getProperty("java.version"));
        System.out.println("============================================");
    }

    static int resolvePort(String[] args) {
        int port = DEFAULT_PORT;
        for (String a : args) {
            if (a != null && a.startsWith("--port=")) {
                try { port = Integer.parseInt(a.substring(7)); } catch (Exception ignored) {}
            }
        }
        if (port == DEFAULT_PORT) {
            String env = System.getenv(PORT_ENV);
            if (env != null && !env.isBlank()) {
                try { port = Integer.parseInt(env.trim()); } catch (Exception ignored) {}
            }
        }
        return port;
    }

    static void resolvePaths() throws Exception {
        Path where;
        try {
            where = Paths.get(IranTourismServer.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            where = Paths.get(".").toAbsolutePath();
        }
        baseDir = Files.isDirectory(where) ? where : where.getParent();
        if (baseDir == null) baseDir = Paths.get(".").toAbsolutePath();

        dataDir = baseDir.resolve("data");
        Files.createDirectories(dataDir);

        placesFile = dataDir.resolve("places.json");
        passportFile = dataDir.resolve("passport.json");
        submissionsFile = dataDir.resolve("submissions.json");
        newsletterFile = dataDir.resolve("newsletter.json");

        indexHtml = baseDir.resolve("index.html");
        if (!Files.exists(indexHtml)) {
            Path alt = baseDir.resolve("public").resolve("index.html");
            if (Files.exists(alt)) indexHtml = alt;
        }
        placesSeed = baseDir.resolve("places-seed.json");
    }

    // =========================================================
    //  DATA LOADING
    // =========================================================
    static void loadAll() {
        loadPlaces();
        loadPassport();
        loadSubmissions();
        loadNewsletter();
    }

    static void loadPlaces() {
        try {
            if (Files.exists(placesFile)) {
                List<Place> loaded = readPlacesFromFile(placesFile);
                places.addAll(loaded);
                System.out.println("[load] places.json → " + loaded.size() + " records");
                return;
            }
            if (Files.exists(placesSeed)) {
                List<Place> loaded = readPlacesFromFile(placesSeed);
                places.addAll(loaded);
                savePlaces();
                System.out.println("[load] places-seed.json → " + loaded.size() + " records (copied)");
                return;
            }
            if (Files.exists(indexHtml)) {
                System.out.println("[load] extracting from index.html …");
                List<Place> loaded = extractFromHtml(indexHtml);
                if (loaded != null && !loaded.isEmpty()) {
                    places.addAll(loaded);
                    savePlaces();
                    System.out.println("[load] index.html → " + loaded.size() + " records");
                    return;
                }
            }
            System.out.println("[load] no external data; using built-in seed");
            seedMinimal();
            savePlaces();
        } catch (Exception e) {
            System.err.println("[load] places failed: " + e.getMessage());
            if (places.isEmpty()) { seedMinimal(); }
        } finally {
            for (Place p : places) {
                if (p.id != null) placeById.put(p.id, p);
            }
        }
    }

    static List<Place> readPlacesFromFile(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Object parsed = Json.parse(content);
        List<Place> result = new ArrayList<>();
        if (parsed instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) result.add(Place.fromJson(castMap(m)));
            }
        }
        return result;
    }

    static void loadPassport() {
        if (!Files.exists(passportFile)) return;
        try {
            String content = Files.readString(passportFile, StandardCharsets.UTF_8);
            Object parsed = Json.parse(content);
            if (!(parsed instanceof Map<?, ?>)) return;
            Map<String, Object> m = castMap((Map<?, ?>) parsed);
            if (m.get("saved") instanceof Map<?, ?> savedMap) {
                for (Map.Entry<?, ?> e : savedMap.entrySet()) {
                    Set<String> ids = ConcurrentHashMap.newKeySet();
                    if (e.getValue() instanceof List<?> l) for (Object o : l) ids.add(o.toString());
                    savedByClient.put(e.getKey().toString(), ids);
                }
            }
            if (m.get("visited") instanceof Map<?, ?> visitedMap) {
                for (Map.Entry<?, ?> e : visitedMap.entrySet()) {
                    Set<String> ids = ConcurrentHashMap.newKeySet();
                    if (e.getValue() instanceof List<?> l) for (Object o : l) ids.add(o.toString());
                    visitedByClient.put(e.getKey().toString(), ids);
                }
            }
            System.out.println("[load] passport.json OK");
        } catch (Exception e) {
            System.err.println("[load] passport failed: " + e.getMessage());
        }
    }

    static void loadSubmissions() {
        if (!Files.exists(submissionsFile)) return;
        try {
            String content = Files.readString(submissionsFile, StandardCharsets.UTF_8);
            Object parsed = Json.parse(content);
            if (parsed instanceof List<?> list) {
                long maxId = 0;
                for (Object o : list) {
                    if (o instanceof Map<?, ?>) {
                        Map<String, Object> m = castMap((Map<?, ?>) o);
                        submissions.add(m);
                        Object id = m.get("id");
                        if (id instanceof Number n) maxId = Math.max(maxId, n.longValue());
                    }
                }
                submissionCounter.set(maxId);
                System.out.println("[load] submissions.json → " + submissions.size());
            }
        } catch (Exception e) {
            System.err.println("[load] submissions failed: " + e.getMessage());
        }
    }

    static void loadNewsletter() {
        if (!Files.exists(newsletterFile)) return;
        try {
            String content = Files.readString(newsletterFile, StandardCharsets.UTF_8);
            Object parsed = Json.parse(content);
            if (parsed instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Object e = castMap(m).get("email");
                        if (e != null) newsletterEmails.add(e.toString());
                    }
                }
                System.out.println("[load] newsletter.json → " + newsletterEmails.size());
            }
        } catch (Exception e) {
            System.err.println("[load] newsletter failed: " + e.getMessage());
        }
    }

    // =========================================================
    //  DATA SAVING
    // =========================================================
    static void savePlaces() {
        synchronized (saveLock) {
            try {
                List<Map<String, Object>> arr = new ArrayList<>(places.size());
                for (Place p : places) arr.add(p.toJson());
                writeAtomic(placesFile, Json.stringify(arr));
            } catch (Exception e) {
                System.err.println("[save] places failed: " + e.getMessage());
            }
        }
    }

    static void savePassport() {
        synchronized (saveLock) {
            try {
                Map<String, Object> root = new LinkedHashMap<>();
                Map<String, List<String>> savedM = new LinkedHashMap<>();
                for (Map.Entry<String, Set<String>> e : savedByClient.entrySet()) {
                    savedM.put(e.getKey(), new ArrayList<>(e.getValue()));
                }
                root.put("saved", savedM);
                Map<String, List<String>> visitedM = new LinkedHashMap<>();
                for (Map.Entry<String, Set<String>> e : visitedByClient.entrySet()) {
                    visitedM.put(e.getKey(), new ArrayList<>(e.getValue()));
                }
                root.put("visited", visitedM);
                writeAtomic(passportFile, Json.stringify(root));
            } catch (Exception e) {
                System.err.println("[save] passport failed: " + e.getMessage());
            }
        }
    }

    static void saveSubmissions() {
        synchronized (saveLock) {
            try {
                writeAtomic(submissionsFile, Json.stringify(submissions));
            } catch (Exception e) {
                System.err.println("[save] submissions failed: " + e.getMessage());
            }
        }
    }

    static void saveNewsletter() {
        synchronized (saveLock) {
            try {
                List<Map<String, Object>> arr = new ArrayList<>();
                for (String e : newsletterEmails) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("email", e);
                    m.put("subscribedAt", Instant.now().toString());
                    arr.add(m);
                }
                writeAtomic(newsletterFile, Json.stringify(arr));
            } catch (Exception e) {
                System.err.println("[save] newsletter failed: " + e.getMessage());
            }
        }
    }

    static void writeAtomic(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // =========================================================
    //  HTML EXTRACTION — RAW_ATTRACTIONS
    // =========================================================
    static List<Place> extractFromHtml(Path htmlFile) {
        try {
            String html = Files.readString(htmlFile, StandardCharsets.UTF_8);

            int idx = html.indexOf("RAW_ATTRACTIONS");
            if (idx < 0) return null;
            int eq = html.indexOf('=', idx);
            if (eq < 0) return null;
            int open = html.indexOf('{', eq);
            if (open < 0) return null;

            int depth = 0;
            int end = -1;
            int i = open;
            while (i < html.length()) {
                char c = html.charAt(i);
                if (c == '"') {
                    i++;
                    while (i < html.length()) {
                        if (html.charAt(i) == '\\') i += 2;
                        else if (html.charAt(i) == '"') { i++; break; }
                        else i++;
                    }
                } else if (c == '/' && i + 1 < html.length() && html.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < html.length()
                            && !(html.charAt(i) == '*' && html.charAt(i + 1) == '/')) i++;
                    i += 2;
                } else if (c == '/' && i + 1 < html.length() && html.charAt(i + 1) == '/') {
                    while (i < html.length() && html.charAt(i) != '\n') i++;
                } else if (c == '{') { depth++; i++; }
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { end = i; break; }
                    i++;
                } else { i++; }
            }
            if (end < 0) return null;

            String body = html.substring(open, end + 1);
            body = removeJsComments(body);
            body = quoteJsKeys(body);
            body = body.replaceAll(",\\s*}", "}");
            body = body.replaceAll(",\\s*]", "]");

            Object parsed = Json.parse(body);
            if (!(parsed instanceof Map<?, ?>)) return null;
            Map<String, Object> root = castMap((Map<?, ?>) parsed);

            List<Place> result = new ArrayList<>();
            int counter = 1;
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                String province = entry.getKey();
                if (!(entry.getValue() instanceof List<?> list)) continue;
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?>)) continue;
                    Map<String, Object> m = castMap((Map<?, ?>) item);
                    Place p = new Place();
                    p.id = String.format("place-%04d", counter++);
                    p.name = strOf(m.get("name"));
                    p.province = province;
                    p.locality = province;
                    p.category = strOf(m.get("cat"));
                    if (!CATEGORY_COLORS.containsKey(p.category)) p.category = "فرهنگی";
                    p.type = strOf(m.get("type"));
                    p.lat = dblOf(m.get("lat"));
                    p.lng = dblOf(m.get("lng"));
                    p.shortText = strOf(m.get("short"));
                    p.description = p.shortText;
                    p.image = placeholderImage(p.name);
                    p.difficulty = m.get("difficulty") instanceof String
                            ? (String) m.get("difficulty") : categoryDefault(p.category, "difficulty");
                    p.season = m.get("season") instanceof String
                            ? (String) m.get("season") : categoryDefault(p.category, "season");
                    p.duration = m.get("duration") instanceof String
                            ? (String) m.get("duration") : categoryDefault(p.category, "duration");
                    p.status = "نمونه نمایشی";
                    if (m.get("tags") instanceof List<?> tags) {
                        for (Object t : tags) p.tags.add(strOf(t));
                    }
                    p.visit = m.get("visit") instanceof String
                            ? (String) m.get("visit")
                            : "شرایط بازدید را پیش از سفر از منابع معتبر بررسی کنید.";
                    p.alert = m.get("alert") instanceof String ? (String) m.get("alert") : "";
                    p.verified = Boolean.TRUE.equals(m.get("verified"));
                    p.color = CATEGORY_COLORS.getOrDefault(p.category, "#9b6b9c");
                    result.add(p);
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("[extract] " + e.getMessage());
            return null;
        }
    }

    static String removeJsComments(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') {
                sb.append(c); i++;
                while (i < s.length()) {
                    char ch = s.charAt(i);
                    sb.append(ch);
                    if (ch == '\\' && i + 1 < s.length()) {
                        sb.append(s.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    i++;
                    if (ch == '"') break;
                }
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < s.length() && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i += 2;
            } else {
                sb.append(c); i++;
            }
        }
        return sb.toString();
    }

    static String quoteJsKeys(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 100);
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') {
                sb.append(c); i++;
                while (i < s.length()) {
                    char ch = s.charAt(i);
                    sb.append(ch);
                    if (ch == '\\' && i + 1 < s.length()) {
                        sb.append(s.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    i++;
                    if (ch == '"') break;
                }
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                String word = s.substring(start, i);
                int j = i;
                while (j < s.length() && Character.isWhitespace(s.charAt(j))) j++;
                if (j < s.length() && s.charAt(j) == ':') {
                    sb.append('"').append(word).append('"');
                } else {
                    sb.append(word);
                }
            } else {
                sb.append(c); i++;
            }
        }
        return sb.toString();
    }

    static String categoryDefault(String category, String field) {
        if ("طبیعی".equals(category)) {
            if ("difficulty".equals(field)) return "متوسط";
            if ("season".equals(field)) return "بهار";
            return "نیم‌روز";
        }
        if ("تاریخی".equals(category)) {
            if ("difficulty".equals(field)) return "آسان";
            if ("season".equals(field)) return "چهارفصل";
            return "نیم‌روز";
        }
        if ("زمین‌شناسی".equals(category)) {
            if ("difficulty".equals(field)) return "متوسط";
            if ("season".equals(field)) return "چهارفصل";
            return "کوتاه";
        }
        if ("خوراک".equals(category)) {
            if ("difficulty".equals(field)) return "آسان";
            if ("season".equals(field)) return "چهارفصل";
            return "کوتاه";
        }
        if ("مسیر".equals(category)) {
            if ("difficulty".equals(field)) return "متوسط";
            if ("season".equals(field)) return "چهارفصل";
            return "نیم‌روز";
        }
        if ("difficulty".equals(field)) return "آسان";
        if ("season".equals(field)) return "چهارفصل";
        return "نیم‌روز";
    }

    // =========================================================
    //  BUILT-IN SEED
    // =========================================================
    static void seedMinimal() {
        addSeed("تخت جمشید", "فارس", "تاریخی", "مجموعهٔ هخامنشی (میراث جهانی)",
                29.9352, 52.8916,
                "پایتخت تشریفاتی هخامنشیان و میراث جهانی یونسکو، با کاخ‌های آپادانا، تچر و هدیش.",
                new String[]{"هخامنشی", "یونسکو", "تاریخی"});
        addSeed("میدان نقش جهان", "اصفهان", "تاریخی", "میدان تاریخی (میراث جهانی)",
                32.6575, 51.6775,
                "میدان ۸۵ هزار مترمربعی صفوی، ثبت‌شده در یونسکو با مسجد امام، مسجد شیخ لطف‌الله، عالی‌قاپو و سردر قیصریه.",
                new String[]{"صفویه", "یونسکو", "میدان"});
        addSeed("برج میلاد", "تهران", "مسیر", "برج مخابراتی",
                35.7448, 51.3753,
                "بلندترین برج ایران (۴۳۵ متر) و ششمین برج مرتفع مخابراتی جهان، با سکوی دید ۲۸۰ متری.",
                new String[]{"برج", "مدرن", "تهران"});
        addSeed("باغ ارم", "فارس", "تاریخی", "باغ ایرانی (میراث جهانی)",
                29.6383, 52.5233,
                "باغی از پیش از سلجوقیان با عمارت سه‌طبقهٔ قاجاری، ۱۱۰ هزار مترمربع، از میراث جهانی باغ ایرانی.",
                new String[]{"باغ ایرانی", "یونسکو", "شیراز"});
        addSeed("آبشار مارگون", "فارس", "طبیعی", "آبشار چشمه‌ای",
                30.20, 52.20,
                "از مرتفع‌ترین و باشکوه‌ترین آبشارهای ایران با ارتفاع ۷۰ متر و عرض بیش از ۱۰۰ متر، با خروج آب از دل صخره‌ها.",
                new String[]{"آبشار", "سپیدان", "طبیعت"});
        addSeed("ارگ بم", "کرمان", "تاریخی", "ارگ خشتی (میراث جهانی)",
                29.1167, 58.3667,
                "بزرگ‌ترین سازهٔ خشتی جهان و میراث جهانی یونسکو، نماد تاریخ و معماری ایران در جنوب شرق.",
                new String[]{"ارگ", "خشتی", "یونسکو"});
        addSeed("روستای صخره‌ای کندوان", "آذربایجان شرقی", "فرهنگی", "روستای دستکند صخره‌ای",
                37.78, 46.30,
                "یکی از سه روستای صخره‌ای معروف جهان با خانه‌های کنده‌شده در دل صخره‌های سهند.",
                new String[]{"روستا", "صخره‌ای", "کندوان"});
        addSeed("بیابان لوت", "کرمان", "زمین‌شناسی", "بیابان جهانی (میراث جهانی)",
                30.45, 57.70,
                "گرم‌ترین نقطهٔ کرهٔ زمین و بلندترین هرم‌های ماسه‌ای، میراث جهانی یونسکو در شهداد.",
                new String[]{"کویر", "یونسکو", "ژئوتوریسم"});
        addSeed("جنگل ابر", "سمنان", "طبیعی", "جنگل هیرکانی (میراث جهانی)",
                36.75, 55.05,
                "بخشی از جنگل‌های هیرکانی ثبت‌شده در یونسکو، با پدیدهٔ «دریای ابر» در ارتفاع.",
                new String[]{"جنگل", "هیرکانی", "دریای ابر"});
        addSeed("مسجد نصیرالملک", "فارس", "فرهنگی", "مسجد قاجاری",
                29.6086, 52.5481,
                "مسجد قاجاری معروف به «مسجد صورتی» با شیشه‌های رنگی و کاشی‌های صورتی، از زیباترین مساجد جهان.",
                new String[]{"مسجد", "صورتی", "شیشه رنگی"});
        addSeed("جزیرهٔ هرمز", "هرمزگان", "طبیعی", "جزیرهٔ گنبد نمکی",
                27.06, 56.46,
                "جزیره‌ای با خاک سرخ و گنبد نمکی، از پدیده‌های طبیعی شاخص خلیج فارس.",
                new String[]{"جزیره", "گنبد نمکی", "خلیج فارس"});
        addSeed("ماسوله", "گیلان", "فرهنگی", "شهر تاریخی پلکانی",
                37.1567, 48.9844,
                "شهر تاریخی و پلکانی گیلان، ثبت‌شده به‌عنوان میراث فرهنگی و طبیعی، از کهن‌ترین شهرهای تاریخی ایران.",
                new String[]{"ماسوله", "پلکانی", "تاریخی"});
    }

    static void addSeed(String name, String province, String category, String type,
                        double lat, double lng, String shortText, String[] tags) {
        Place p = new Place();
        p.id = "seed-" + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
        p.name = name;
        p.province = province;
        p.locality = province;
        p.category = category;
        p.type = type;
        p.lat = lat;
        p.lng = lng;
        p.shortText = shortText;
        p.description = shortText;
        p.image = placeholderImage(name);
        p.difficulty = categoryDefault(category, "difficulty");
        p.season = categoryDefault(category, "season");
        p.duration = categoryDefault(category, "duration");
        p.status = "نمونه نمایشی";
        for (String t : tags) p.tags.add(t);
        p.visit = "شرایط بازدید را پیش از سفر از منابع معتبر بررسی کنید.";
        p.alert = "";
        p.verified = false;
        p.color = CATEGORY_COLORS.getOrDefault(category, "#9b6b9c");
        places.add(p);
    }

    static String placeholderImage(String name) {
        try {
            String trimmed = name.length() > 25 ? name.substring(0, 25) : name;
            String enc = URLEncoder.encode(trimmed, StandardCharsets.UTF_8).replace("+", "%20");
            return "https://placehold.co/600x400/102c27/c4dba5?text=" + enc;
        } catch (Exception e) {
            return "https://placehold.co/600x400/102c27/c4dba5?text=Tourism";
        }
    }

    // =========================================================
    //  HTTP HANDLERS
    // =========================================================
    static void handleHealth(HttpExchange ex) throws IOException {
        try {
            setCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { handleOptions(ex); return; }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "ok");
            body.put("places", places.size());
            body.put("time", Instant.now().toString());
            writeJson(ex, 200, body);
        } catch (Exception e) { handleException(ex, e); }
    }

    static void handleStatic(HttpExchange ex) throws IOException {
        try {
            setCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { handleOptions(ex); return; }
            String path = ex.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) {
                if (!Files.exists(indexHtml)) {
                    String msg = "<!DOCTYPE html><html lang='fa' dir='rtl'><meta charset='utf-8'>"
                            + "<h1>Iran Tourism Server</h1>"
                            + "<p>فایل <code>index.html</code> در کنار برنامه یافت نشد.</p>"
                            + "<p>پس از قرار دادن فایل، دوباره امتحان کنید.</p></html>";
                    writeText(ex, 404, msg, "text/html; charset=utf-8");
                    return;
                }
                byte[] bytes = Files.readAllBytes(indexHtml);
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                ex.getResponseHeaders().set("Cache-Control", "no-cache");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
            } else {
                writeError(ex, 404, "Not found");
            }
        } catch (Exception e) { handleException(ex, e); }
    }

    static void handlePlaces(HttpExchange ex) throws IOException {
        try {
            setCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { handleOptions(ex); return; }

            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method)) { writeError(ex, 405, "Method not allowed"); return; }

            if (path.equals("/api/places") || path.equals("/api/places/")) {
                listPlaces(ex);
            } else if (path.startsWith("/api/places/")) {
                String id = path.substring("/api/places/".length());
                getPlaceById(ex, id);
            } else {
                writeError(ex, 404, "Not found");
            }
        } catch (Exception e) { handleException(ex, e); }
    }

    static void listPlaces(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());

        String query = q.getOrDefault("query", "").trim();
        String category = q.getOrDefault("category", "").trim();
        String province = q.getOrDefault("province", "").trim();
        String difficulty = q.getOrDefault("difficulty", "").trim();
        String season = q.getOrDefault("season", "").trim();
        String duration = q.getOrDefault("duration", "").trim();
        String status = q.getOrDefault("status", "").trim();
        String sort = q.getOrDefault("sort", "default").trim();
        boolean savedOnly = "true".equalsIgnoreCase(q.getOrDefault("savedOnly", "false"));

        String clientId = ex.getRequestHeaders().getFirst("X-Client-Id");
        Set<String> savedIds = (clientId == null || clientId.isBlank())
                ? Set.of()
                : savedByClient.getOrDefault(clientId, Set.of());

        String qNorm = normalize(query);
        List<Place> results = new ArrayList<>();
        for (Place p : places) {
            if (!category.isEmpty() && !category.equals(p.category)) continue;
            if (!province.isEmpty() && !province.equals(p.province)) continue;
            if (!difficulty.isEmpty() && !difficulty.equals(p.difficulty)) continue;
            if (!season.isEmpty() && !season.equals(p.season)) continue;
            if (!duration.isEmpty() && !duration.equals(p.duration)) continue;
            if (!status.isEmpty() && !status.equals(p.status)) continue;
            if (savedOnly && !savedIds.contains(p.id)) continue;
            if (!qNorm.isEmpty() && !matchesQuery(p, qNorm)) continue;
            results.add(p);
        }

        switch (sort) {
            case "name":
                results.sort(Comparator.comparing(p -> p.name == null ? "" : p.name));
                break;
            case "province":
                results.sort(Comparator.comparing(p -> p.province == null ? "" : p.province));
                break;
            case "difficulty":
                results.sort(Comparator.comparingInt(p -> difficultyRank(p.difficulty)));
                break;
            default:
                break;
        }

        int page = parseIntOr(q.get("page"), 0);
        int size = Math.max(1, Math.min(parseIntOr(q.get("size"), DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE));
        int total = results.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);

        List<Map<String, Object>> items = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) items.add(results.get(i).toJson());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", total);
        body.put("page", page);
        body.put("size", size);

        ex.getResponseHeaders().set("X-Total-Count", String.valueOf(total));
        writeJson(ex, 200, body);
    }

    static void getPlaceById(HttpExchange ex, String id) throws IOException {
        String decoded = decode(id);
        Place p = placeById.get(decoded);
        if (p == null) { writeError(ex, 404, "جاذبه یافت نشد."); return; }
        writeJson(ex, 200, p.toJson());
    }

    static void handleProvinces(HttpExchange ex) throws IOException {
        try {
            setCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { handleOptions(ex); return; }
            List<String> provinces = new ArrayList<>();
            for (Place p : places) {
                if (p.province != null && !provinces.contains(p.province)) provinces.add(p.province);
            }
            provinces.sort(Comparator.nullsLast(String::compareTo));
            writeJson(ex, 200, provinces);
        } catch (Exception e) { handleException(ex, e); }
    }

    static void handlePassport(HttpExchange ex) throws IOException {
        try {
            setCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { handleOptions(ex); return; }

            String clientId = ex.getRequestHeaders().getFirst("X-Client-Id");
            if (clientId == null || clientId.isBlank()) {
                writeError(ex, 400, "هدر X-Client-Id الزامی است."); return;
            }

            String path = ex.getRequestURI().getPath();
            String rest = path.substring("/api/passport/".length());
            if (rest.isEmpty()) { writeError(ex, 404, "Not found"); return; }

            String[] parts = rest.split("/");
            String kind = parts[0];
            if (!"saved".equals(kind) && !"visited".equals(kind)) {
                writeError(ex, 404, "Not found"); return;
            }

            Map<String, Set<String>> map = "saved".equals(kind) ? savedByClient : visitedByClient;

            if (parts.length == 1) {
                if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                    writeError(ex, 405, "Method not allowed"); return;
                }
                Set<String> ids = map.getOrDefault(clientId, Set.of());
                List<Map<String, Object>> arr = new ArrayList<>();
                for (String id : ids) {
                    Place p = placeById.get(id);
                    if (p != null) arr.add(p.toJson());
                }
                writeJson(ex, 200, arr);
            } else {
                String placeId = decode(parts[1]);
                String method = ex.getRequestMethod();
                if ("POST".equalsIgnoreCase(method)) {
                    if (!placeById.containsKey(placeId)) {
                        writeError(ex, 404, "جاذبه یافت نشد."); return;
                    }
                    map.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet()).add(placeId);
                    savePassport();
                    ex.sendResponseHeaders(204, -1); ex.close();
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    Set<String> s = map.get(clientId);
                    if (s != null) { s.remove(placeId); savePassport(); }
                    ex.sendResponseHeaders(204, -1); ex.close();
                } else {
                    writeError(ex, 405, "Method not allowed");
                }
            }
        } catch (Exception e) { handleException(ex, e); }
    }

    static void handleSubmissions(HttpExchange ex) throws IOException {
        try {
            setCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { handleOptions(ex); return; }

            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (!"POST".equalsIgnoreCase(method)) {
                writeError(ex, 405, "Method not allowed"); return;
            }

            String body = readBody(ex);
            Object parsed;
            try { parsed = Json.parse(body); }
            catch (Exception e) { writeError(ex, 400, "Invalid JSON body"); return; }
            if (!(parsed instanceof Map<?, ?>)) { writeError(ex, 400, "Invalid JSON object"); return; }
            Map<String, Object> data = castMap((Map<?, ?>) parsed);

            if (path.equals("/api/submissions/report")) handleReport(ex, data);
            else if (path.equals("/api/submissions")) handleNewSubmission(ex, data);
            else writeError(ex, 404, "Not found");
        } catch (Exception e) { handleException(ex, e); }
    }

    static void handleNewSubmission(HttpExchange ex, Map<String, Object> data) throws IOException {
        String name = trim(data.get("name"));
        String type = trim(data.get("type"));
        String reason = trim(data.get("reason"));

        if (name.isEmpty() || type.isEmpty() || reason.isEmpty()) {
            writeError(ex, 400, "فیلدهای name و type و reason الزامی هستند."); return;
        }

        Double lat = dblOf(data.get("lat"));
        Double lng = dblOf(data.get("lng"));
        if ((lat == null) != (lng == null)) {
            writeError(ex, 400, "برای ثبت مختصات، هر دو مقدار عرض و طول لازم است."); return;
        }

        String category = mapTypeToCategory(type);
        String id = "local-" + UUID.randomUUID();

        Place p = new Place();
        p.id = id;
        p.name = name;
        p.province = nonBlank(strOf(data.get("province")), "استان نامشخص");
        p.locality = strOrEmpty(data.get("locality"));
        p.category = category;
        p.type = type;
        p.lat = lat;
        p.lng = lng;
        p.shortText = truncate(reason, 180);
        p.description = reason;
        p.image = "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=75";
        p.difficulty = "نامشخص";
        p.season = "چهارفصل";
        p.duration = "نامشخص";
        p.status = "نیازمند بررسی";
        p.tags.add("پیشنهاد کاربر");
        p.visit = nonBlank(strOf(data.get("access")), "شرایط دسترسی هنوز بررسی نشده است.");
        p.alert = "این پیشنهاد بررسی و تأیید نشده است. اطلاعات دسترسی و مختصات را پیش از بازدید از منابع معتبر بررسی کنید.";
        p.verified = false;
        p.color = CATEGORY_COLORS.getOrDefault(category, "#9b6b9c");

        places.add(0, p);
        placeById.put(p.id, p);
        savePlaces();

        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("id", submissionCounter.incrementAndGet());
        sub.put("kind", "new");
        sub.put("name", name);
        sub.put("province", strOf(data.get("province")));
        sub.put("locality", strOf(data.get("locality")));
        sub.put("type", type);
        sub.put("lat", lat);
        sub.put("lng", lng);
        sub.put("reason", reason);
        sub.put("source", strOf(data.get("source")));
        sub.put("access", strOf(data.get("access")));
        sub.put("placeId", id);
        sub.put("createdAt", Instant.now().toString());
        sub.put("status", "pending");
        submissions.add(sub);
        saveSubmissions();

        writeJson(ex, 201, p.toJson());
    }

    static void handleReport(HttpExchange ex, Map<String, Object> data) throws IOException {
        String reason = trim(data.get("reason"));
        if (reason.isEmpty()) { writeError(ex, 400, "فیلد reason الزامی است."); return; }

        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("id", submissionCounter.incrementAndGet());
        sub.put("kind", "report");
        sub.put("name", strOf(data.get("name")));
        sub.put("province", strOf(data.get("province")));
        sub.put("locality", strOf(data.get("locality")));
        sub.put("lat", dblOf(data.get("lat")));
        sub.put("lng", dblOf(data.get("lng")));
        sub.put("reason", reason);
        sub.put("source", strOf(data.get("source")));
        sub.put("access", strOf(data.get("access")));
        sub.put("placeId", strOf(data.get("placeId")));
        sub.put("createdAt", Instant.now().toString());
        sub.put("status", "pending");
        submissions.add(sub);
        saveSubmissions();

        ex.sendResponseHeaders(204, -1); ex.close();
    }

    static void handleNewsletter(HttpExchange ex) throws IOException {
        try {
            setCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { handleOptions(ex); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeError(ex, 405, "Method not allowed"); return;
            }
            String body = readBody(ex);
            Object parsed;
            try { parsed = Json.parse(body); }
            catch (Exception e) { writeError(ex, 400, "Invalid JSON body"); return; }
            if (!(parsed instanceof Map<?, ?>)) { writeError(ex, 400, "Invalid JSON object"); return; }
            Map<String, Object> data = castMap((Map<?, ?>) parsed);
            String email = trim(data.get("email")).toLowerCase(Locale.ROOT);
            if (email.isEmpty() || !email.contains("@")) {
                writeError(ex, 400, "ایمیل نامعتبر است."); return;
            }
            if (newsletterEmails.add(email)) saveNewsletter();
            ex.sendResponseHeaders(204, -1); ex.close();
        } catch (Exception e) { handleException(ex, e); }
    }

    // =========================================================
    //  SUPPORT
    // =========================================================
    static boolean matchesQuery(Place p, String qNorm) {
        StringBuilder sb = new StringBuilder();
        appendAll(sb, p.name);
        appendAll(sb, p.province);
        appendAll(sb, p.locality);
        appendAll(sb, p.category);
        appendAll(sb, p.type);
        appendAll(sb, p.shortText);
        appendAll(sb, p.description);
        if (p.tags != null) for (String t : p.tags) appendAll(sb, t);
        return normalize(sb.toString()).contains(qNorm);
    }

    static void appendAll(StringBuilder sb, String v) {
        if (v != null) sb.append(v).append(' ');
    }

    static String normalize(String value) {
        if (value == null) return "";
        String s = value.toLowerCase(Locale.ROOT);
        s = s.replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک');
        s = s.replaceAll("[\\u064B-\\u065F\\u0670]", "");
        s = s.replaceAll("[\\u200c\\u200e\\u200f\\sـ]+", " ");
        return s.trim();
    }

    static int difficultyRank(String d) {
        if (d == null) return 4;
        switch (d) {
            case "آسان": return 0;
            case "متوسط": return 1;
            case "دشوار": return 2;
            case "نامشخص": return 3;
            default: return 4;
        }
    }

    static String mapTypeToCategory(String type) {
        if (type == null) return "فرهنگی";
        switch (type) {
            case "جاذبه طبیعی": return "طبیعی";
            case "پدیده زمین‌شناسی": return "زمین‌شناسی";
            case "اثر تاریخی": case "بنای معماری": case "میراث صنعتی": return "تاریخی";
            case "خوراک یا محصول بومی": return "خوراک";
            case "مسیر گردشگری": return "مسیر";
            default: return "فرهنگی";
        }
    }

    // =========================================================
    //  HTTP UTILITIES
    // =========================================================
    static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return result;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) result.put(decode(pair), "");
            else result.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
        }
        return result;
    }

    static String decode(String s) {
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }

    static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void writeJson(HttpExchange ex, int status, Object body) throws IOException {
        String json = Json.stringify(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static void writeError(HttpExchange ex, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("message", message);
        writeJson(ex, status, body);
    }

    static void writeText(HttpExchange ex, int status, String text, String contentType) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static void setCorsHeaders(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, X-Client-Id");
        h.set("Access-Control-Max-Age", "3600");
    }

    static void handleOptions(HttpExchange ex) throws IOException {
        setCorsHeaders(ex);
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    static void handleException(HttpExchange ex, Exception e) {
        e.printStackTrace();
        try { writeError(ex, 500, "خطای داخلی سرور: " + e.getMessage()); }
        catch (IOException ignored) { try { ex.close(); } catch (Exception ignored2) {} }
    }

    static int parseIntOr(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }

    static String trim(Object o) { return o == null ? "" : o.toString().trim(); }
    static String strOf(Object o) { return o == null ? null : o.toString(); }
    static String strOrEmpty(Object o) { return o == null ? "" : o.toString(); }
    static String nonBlank(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
    static String truncate(String v, int n) {
        if (v == null) return "";
        return v.length() <= n ? v : v.substring(0, n);
    }

    static Double dblOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); }
        catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) m.put(e.getKey().toString(), e.getValue());
        return m;
    }

    // =========================================================
    //  PLACE MODEL
    // =========================================================
    static class Place {
        String id;
        String name;
        String province;
        String locality;
        String category;
        String type;
        Double lat;
        Double lng;
        String shortText;
        String description;
        String image;
        String difficulty;
        String season;
        String duration;
        String status;
        List<String> tags = new ArrayList<>();
        String visit;
        String alert;
        boolean verified;
        String color;

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("province", province);
            m.put("locality", locality);
            m.put("category", category);
            m.put("type", type);
            m.put("lat", lat);
            m.put("lng", lng);
            m.put("short", shortText);
            m.put("description", description);
            m.put("image", image);
            m.put("difficulty", difficulty);
            m.put("season", season);
            m.put("duration", duration);
            m.put("status", status);
            m.put("tags", tags);
            m.put("visit", visit);
            m.put("alert", alert);
            m.put("verified", verified);
            m.put("color", color);
            return m;
        }

        static Place fromJson(Map<String, Object> m) {
            Place p = new Place();
            p.id = strOf(m.get("id"));
            p.name = strOf(m.get("name"));
            p.province = strOf(m.get("province"));
            p.locality = strOf(m.get("locality"));
            p.category = strOf(m.get("category"));
            p.type = strOf(m.get("type"));
            p.lat = dblOf(m.get("lat"));
            p.lng = dblOf(m.get("lng"));
            Object shortVal = m.containsKey("short") ? m.get("short") : m.get("shortText");
            p.shortText = strOf(shortVal);
            p.description = strOf(m.get("description"));
            p.image = strOf(m.get("image"));
            p.difficulty = strOf(m.get("difficulty"));
            p.season = strOf(m.get("season"));
            p.duration = strOf(m.get("duration"));
            p.status = strOf(m.get("status"));
            if (m.get("tags") instanceof List<?> l) for (Object t : l) p.tags.add(strOf(t));
            p.visit = strOf(m.get("visit"));
            p.alert = strOf(m.get("alert"));
            p.verified = Boolean.TRUE.equals(m.get("verified"));
            p.color = strOf(m.get("color"));
            return p;
        }
    }

    // =========================================================
    //  MINIMAL JSON
    // =========================================================
    static class Json {
        static Object parse(String s) {
            Json j = new Json(s);
            Object v = j.parseValue();
            j.skipWs();
            return v;
        }

        static String stringify(Object o) {
            StringBuilder sb = new StringBuilder();
            writeValue(sb, o);
            return sb.toString();
        }

        private final String s;
        private int pos;

        private Json(String s) { this.s = s; }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        private Object parseValue() {
            skipWs();
            if (pos >= s.length()) return null;
            char c = s.charAt(pos);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return parseNumber();
            }
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++; skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') { pos++; return m; }
            while (pos < s.length()) {
                skipWs();
                String key = parseString();
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ':') pos++;
                m.put(key, parseValue());
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ',') { pos++; continue; }
                if (pos < s.length() && s.charAt(pos) == '}') { pos++; break; }
                break;
            }
            return m;
        }

        private List<Object> parseArray() {
            List<Object> arr = new ArrayList<>();
            pos++; skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') { pos++; return arr; }
            while (pos < s.length()) {
                arr.add(parseValue());
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ',') { pos++; continue; }
                if (pos < s.length() && s.charAt(pos) == ']') { pos++; break; }
                break;
            }
            return arr;
        }

        private String parseString() {
            skipWs();
            if (pos >= s.length() || s.charAt(pos) != '"') {
                throw new RuntimeException("Expected string at " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '"') { pos++; return sb.toString(); }
                if (c == '\\') {
                    pos++;
                    if (pos >= s.length()) break;
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > s.length()) throw new RuntimeException("Bad \\u escape");
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c); pos++;
                }
            }
            throw new RuntimeException("Unterminated string");
        }

        private Object parseNumber() {
            int start = pos;
            if (pos < s.length() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            if (pos < s.length() && s.charAt(pos) == '.') {
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            String num = s.substring(start, pos);
            if (num.isEmpty() || "-".equals(num) || "+".equals(num)) return null;
            if (num.contains(".") || num.contains("e") || num.contains("E")) {
                return Double.parseDouble(num);
            }
            try { return Long.parseLong(num); }
            catch (Exception e) { return Double.parseDouble(num); }
        }

        private void expect(String token) {
            if (!s.startsWith(token, pos)) throw new RuntimeException("Expected " + token + " at " + pos);
            pos += token.length();
        }

        private static void writeValue(StringBuilder sb, Object o) {
            if (o == null) { sb.append("null"); return; }
            if (o instanceof String) { writeString(sb, (String) o); return; }
            if (o instanceof Boolean || o instanceof Number) { sb.append(o); return; }
            if (o instanceof Map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    writeString(sb, e.getKey().toString());
                    sb.append(':');
                    writeValue(sb, e.getValue());
                }
                sb.append('}');
                return;
            }
            if (o instanceof List) {
                sb.append('[');
                boolean first = true;
                for (Object v : (List<?>) o) {
                    if (!first) sb.append(',');
                    first = false;
                    writeValue(sb, v);
                }
                sb.append(']');
                return;
            }
            writeString(sb, o.toString());
        }

        private static void writeString(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    default:
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                }
            }
            sb.append('"');
        }
    }
}