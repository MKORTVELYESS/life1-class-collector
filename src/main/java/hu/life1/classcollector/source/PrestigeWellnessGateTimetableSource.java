package hu.life1.classcollector.source;

import hu.life1.classcollector.TimeZones;
import hu.life1.classcollector.http.HttpFetcher;
import hu.life1.classcollector.model.Branch;
import hu.life1.classcollector.model.ClassSession;
import org.jsoup.Jsoup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prestige Fitness timetable via WellnessGate JSON API embedded on
 * {@code https://prestigefitness.hu/orarend/}.
 *
 * <p>The public page loads
 * {@code //foglalas.wellnessgate.hu/api.php?...&action=get_timetable&timetable_days=14}.
 * The API currently returns about two weeks of future classes even when a larger
 * {@code timetable_days} is requested.
 */
public final class PrestigeWellnessGateTimetableSource implements TimetableSource {

    static final String PAGE_URL = "https://prestigefitness.hu/orarend/";
    static final String API_KEY = "459a629d1bf49bdbe1231c94e2487f19f7e30d39";
    static final String API_URL =
            "https://foglalas.wellnessgate.hu/api.php?api_key=" + API_KEY
                    + "&logged=0&action=get_timetable&timetable_days=14";

    private static final DateTimeFormatter DATE_VALUE = DateTimeFormatter.ofPattern("yyyy.MM.dd.");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private final HttpFetcher http;
    private final List<Branch> branches;
    private final JsonLoader jsonLoader;

    public PrestigeWellnessGateTimetableSource(HttpFetcher http) {
        this(http, List.of(new Branch("Prestige Fitness", PAGE_URL)), url -> http.getText(url));
    }

    PrestigeWellnessGateTimetableSource(HttpFetcher http, List<Branch> branches, JsonLoader jsonLoader) {
        this.http = http;
        this.branches = List.copyOf(branches);
        this.jsonLoader = jsonLoader;
    }

    @Override
    public String familyName() {
        return "prestige-wellnessgate";
    }

    @Override
    public List<Branch> branches() {
        return branches;
    }

    @Override
    public List<ClassSession> collectFutureClasses(Branch branch) throws Exception {
        String json = jsonLoader.load(API_URL);
        return parse(branch, json, LocalDateTime.now(TimeZones.EUROPE_BUDAPEST));
    }

    List<ClassSession> parse(Branch branch, String json, LocalDateTime nowBudapest) {
        // Minimal JSON walk without an extra dependency: extract offline.datas objects.
        List<Map<String, String>> rows = extractOfflineDatas(json);
        Map<String, ClassSession> unique = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String className = stripHtml(row.getOrDefault("classes", ""));
            String instructor = stripHtml(row.getOrDefault("trainer", ""));
            if (className.isEmpty() || isCancelled(className, instructor)) {
                continue;
            }
            LocalDate day = parseDateValue(row.get("dateValue"), row.get("date"));
            LocalTime startTime = LocalTime.parse(normalizeTime(row.get("start")));
            LocalTime endTime = LocalTime.parse(normalizeTime(row.get("end")));
            LocalDateTime start = LocalDateTime.of(day, startTime);
            LocalDateTime end = LocalDateTime.of(day, endTime);
            ClassSession session = new ClassSession(branch, start, end, className, instructor, PAGE_URL);
            if (session.startsInFuture(nowBudapest)) {
                unique.putIfAbsent(session.identity().key(), session);
            }
        }
        return List.copyOf(unique.values());
    }

    private static boolean isCancelled(String className, String instructor) {
        String c = className.toLowerCase();
        String i = instructor.toLowerCase();
        return c.contains("elmarad") || i.contains("elmarad");
    }

    private static LocalDate parseDateValue(String dateValue, String dateFallback) {
        if (dateValue != null && !dateValue.isBlank()) {
            String normalized = dateValue.trim();
            if (!normalized.endsWith(".")) {
                normalized = normalized + ".";
            }
            return LocalDate.parse(normalized, DATE_VALUE);
        }
        // e.g. "2026.11.18. (Szerda)"
        Matcher matcher = Pattern.compile("(20\\d{2}\\.\\d{2}\\.\\d{2})").matcher(dateFallback == null ? "" : dateFallback);
        if (matcher.find()) {
            return LocalDate.parse(matcher.group(1) + ".", DATE_VALUE);
        }
        throw new IllegalArgumentException("Cannot parse Prestige date: dateValue=" + dateValue + " date=" + dateFallback);
    }

    private static String normalizeTime(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing time");
        }
        String cleaned = stripHtml(raw);
        String[] parts = cleaned.split(":");
        return "%02d:%02d".formatted(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    private static String stripHtml(String value) {
        if (value == null) {
            return "";
        }
        String noTags = HTML_TAG.matcher(value).replaceAll("");
        return Jsoup.parse(noTags).text().replace('\u00a0', ' ').strip().replaceAll("\\s+", " ");
    }

    /**
     * Extract objects from {@code "offline":{"datas":[ ... ]}} without requiring Jackson.
     * Fields we care about are flat strings.
     */
    static List<Map<String, String>> extractOfflineDatas(String json) {
        int offline = json.indexOf("\"offline\"");
        if (offline < 0) {
            // fall back to reservations.datas
            offline = json.indexOf("\"reservations\"");
        }
        if (offline < 0) {
            throw new IllegalStateException("Prestige JSON missing offline/reservations");
        }
        int datas = json.indexOf("\"datas\"", offline);
        if (datas < 0) {
            throw new IllegalStateException("Prestige JSON missing datas array");
        }
        int arrayStart = json.indexOf('[', datas);
        if (arrayStart < 0) {
            throw new IllegalStateException("Prestige JSON datas is not an array");
        }
        int arrayEnd = findMatchingBracket(json, arrayStart);
        String arrayBody = json.substring(arrayStart + 1, arrayEnd);
        List<Map<String, String>> rows = new ArrayList<>();
        int pos = 0;
        while (pos < arrayBody.length()) {
            int objStart = arrayBody.indexOf('{', pos);
            if (objStart < 0) {
                break;
            }
            int objEnd = findMatchingBrace(arrayBody, objStart);
            String obj = arrayBody.substring(objStart, objEnd + 1);
            rows.add(extractStringFields(obj));
            pos = objEnd + 1;
        }
        return rows;
    }

    private static Map<String, String> extractStringFields(String objectJson) {
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("\"(dateValue|date|start|end|classes|trainer|location)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .matcher(objectJson);
        while (matcher.find()) {
            fields.put(matcher.group(1), unescapeJson(matcher.group(2)));
        }
        return fields;
    }

    /** Decode JSON string escapes, including Unicode backslash-u hex sequences. */
    static String unescapeJson(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (i + 4 >= value.length()) {
                        out.append('\\').append('u');
                        break;
                    }
                    String hex = value.substring(i + 1, i + 5);
                    try {
                        out.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    } catch (NumberFormatException ex) {
                        out.append('\\').append('u').append(hex);
                        i += 4;
                    }
                }
                default -> out.append('\\').append(next);
            }
        }
        return out.toString();
    }

    private static int findMatchingBracket(String text, int openIndex) {
        int depth = 0;
        boolean inString = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Unbalanced [ in Prestige JSON");
    }

    private static int findMatchingBrace(String text, int openIndex) {
        int depth = 0;
        boolean inString = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Unbalanced { in Prestige JSON");
    }

    @FunctionalInterface
    interface JsonLoader {
        String load(String url) throws Exception;
    }
}
