package hu.life1.classcollector.csv;

import hu.life1.classcollector.model.ClassIdentity;
import hu.life1.classcollector.model.ClassSession;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CSV persistence with soft-delete sync semantics:
 * <ul>
 *   <li>new identity → insert with {@code deleted=0}</li>
 *   <li>seen again → keep/revive {@code deleted=0}, refresh {@code last_seen}</li>
 *   <li>previously non-deleted identity missing from scrape → {@code deleted=1} (row kept)</li>
 * </ul>
 */
public final class CsvStore {

    public static final String[] HEADER = {
            "branch",
            "start",
            "end",
            "class_name",
            "instructor",
            "source_url",
            "deleted",
            "first_seen",
            "last_seen"
    };

    private static final DateTimeFormatter ISO_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final Path path;

    public CsvStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public Path path() {
        return path;
    }

    public List<CsvRow> readAll() throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                return List.of();
            }
            List<CsvRow> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                rows.add(parseRow(line));
            }
            return rows;
        }
    }

    /**
     * Merge scraped sessions into the store. Soft-delete is scoped to the set of branch names
     * that appear in {@code scraped} (so a failed empty scrape for a branch does not wipe it —
     * callers should only pass sessions for successfully scraped branches).
     */
    public SyncStats sync(Collection<ClassSession> scraped, LocalDateTime seenAt) throws IOException {
        Map<String, CsvRow> byKey = new LinkedHashMap<>();
        for (CsvRow row : readAll()) {
            byKey.put(row.identity().key(), row);
        }

        Map<String, ClassSession> scrapedByKey = new LinkedHashMap<>();
        for (ClassSession session : scraped) {
            scrapedByKey.putIfAbsent(session.identity().key(), session);
        }

        int added = 0;
        int revived = 0;
        int refreshed = 0;
        for (Map.Entry<String, ClassSession> entry : scrapedByKey.entrySet()) {
            String key = entry.getKey();
            ClassSession session = entry.getValue();
            CsvRow existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, CsvRow.fromNewSession(session, seenAt));
                added++;
            } else if (existing.deleted() == 1) {
                byKey.put(key, existing.withSeen(session, seenAt));
                revived++;
            } else {
                byKey.put(key, existing.withSeen(session, seenAt));
                refreshed++;
            }
        }

        int deleted = 0;
        // Soft-delete only rows for branches we successfully scraped in this run.
        var scrapedBranches = scrapedByKey.values().stream()
                .map(s -> s.branch().name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        List<CsvRow> result = new ArrayList<>(byKey.size());
        for (CsvRow row : byKey.values()) {
            if (scrapedBranches.contains(row.branch())
                    && row.deleted() == 0
                    && !scrapedByKey.containsKey(row.identity().key())) {
                result.add(row.markedDeleted());
                deleted++;
            } else {
                result.add(row);
            }
        }

        writeAll(result);
        return new SyncStats(added, refreshed, revived, deleted, result.size());
    }

    public void writeAll(List<CsvRow> rows) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(String.join(",", HEADER));
            writer.newLine();
            for (CsvRow row : rows) {
                writer.write(formatRow(row));
                writer.newLine();
            }
        }
    }

    public void writeHeaderOnly() throws IOException {
        writeAll(List.of());
    }

    private static CsvRow parseRow(String line) {
        List<String> cols = parseCsvLine(line);
        if (cols.size() < 9) {
            throw new IllegalArgumentException("CSV row has < 9 columns: " + line);
        }
        return new CsvRow(
                cols.get(0),
                LocalDateTime.parse(cols.get(1), ISO_MINUTE),
                LocalDateTime.parse(cols.get(2), ISO_MINUTE),
                cols.get(3),
                cols.get(4),
                cols.get(5),
                Integer.parseInt(cols.get(6)),
                LocalDateTime.parse(cols.get(7), ISO_MINUTE),
                LocalDateTime.parse(cols.get(8), ISO_MINUTE));
    }

    private static String formatRow(CsvRow row) {
        return String.join(",",
                escape(row.branch()),
                escape(row.startIso()),
                escape(row.endIso()),
                escape(row.className()),
                escape(row.instructor()),
                escape(row.sourceUrl()),
                Integer.toString(row.deleted()),
                escape(row.firstSeenIso()),
                escape(row.lastSeenIso()));
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }

    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    public record SyncStats(int added, int refreshed, int revived, int deleted, int totalRows) {}
}
