package hu.life1.classcollector.source;

import hu.life1.classcollector.http.HttpFetcher;
import hu.life1.classcollector.model.Branch;
import hu.life1.classcollector.model.ClassSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrestigeWellnessGateTimetableSourceTest {

    @Test
    void parsesWellnessGateJsonFixture() throws IOException {
        String json = fixture("fixtures/prestige-timetable.json");
        Branch branch = new Branch("Prestige Fitness", PrestigeWellnessGateTimetableSource.PAGE_URL);
        PrestigeWellnessGateTimetableSource source = new PrestigeWellnessGateTimetableSource(
                new HttpFetcher(HttpClient.newHttpClient()),
                List.of(branch),
                url -> json);

        List<ClassSession> sessions = source.parse(
                branch, json, LocalDateTime.of(2026, 11, 18, 6, 0));

        assertFalse(sessions.isEmpty());
        assertTrue(sessions.stream().anyMatch(s -> s.className().toLowerCase().contains("pilates")
                || s.className().toLowerCase().contains("iron")));
        assertTrue(sessions.stream().allMatch(s -> s.branch().name().equals("Prestige Fitness")));
    }

    @Test
    void decodesJsonUnicodeEscapesInNames() throws IOException {
        String json = fixture("fixtures/prestige-timetable.json");
        Branch branch = new Branch("Prestige Fitness", PrestigeWellnessGateTimetableSource.PAGE_URL);
        PrestigeWellnessGateTimetableSource source = new PrestigeWellnessGateTimetableSource(
                new HttpFetcher(HttpClient.newHttpClient()),
                List.of(branch),
                url -> json);

        List<ClassSession> sessions = source.parse(
                branch, json, LocalDateTime.of(2026, 11, 18, 6, 0));

        assertTrue(sessions.stream().anyMatch(s -> s.instructor().contains("Erdélyi")),
                "expected decoded Erdélyi in instructors");
        assertTrue(sessions.stream().noneMatch(s -> s.instructor().contains("\\u") || s.className().contains("\\u")),
                "no literal \\u escapes should remain");
        assertTrue(sessions.stream().anyMatch(s ->
                s.className().contains("á") || s.className().contains("ö") || s.className().contains("é")
                        || s.instructor().contains("é") || s.instructor().contains("á")));
    }

    @Test
    void unescapeJsonHandlesUnicodeAndSlash() {
        // Build "\u00e9" as chars without a Java source unicode escape eating the sequence.
        String rawErdelyi = "Erd" + "\\u" + "00e9lyi Kamilla";
        String rawFunkcionalis = "Funkcion" + "\\u" + "00e1lis k" + "\\u" + "00f6redz" + "\\u" + "00e9s";
        assertEquals("Erdélyi Kamilla",
                PrestigeWellnessGateTimetableSource.unescapeJson(rawErdelyi));
        assertEquals("Funkcionális köredzés",
                PrestigeWellnessGateTimetableSource.unescapeJson(rawFunkcionalis));
        assertEquals("<span class=\"trainer\">x</span>",
                PrestigeWellnessGateTimetableSource.unescapeJson("<span class=\\\"trainer\\\">x<\\/span>"));
    }

    private static String fixture(String path) throws IOException {
        try (var in = PrestigeWellnessGateTimetableSourceTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
