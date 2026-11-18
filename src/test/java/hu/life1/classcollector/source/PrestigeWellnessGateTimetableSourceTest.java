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

    private static String fixture(String path) throws IOException {
        try (var in = PrestigeWellnessGateTimetableSourceTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
