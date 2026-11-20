package hu.life1.classlist.source;

import hu.life1.classlist.http.HttpFetcher;
import hu.life1.classlist.model.Branch;
import hu.life1.classlist.model.ClassSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Life1BookingTimetableSourceTest {

    @Test
    void parsesAlleeFixtureFutureClasses() throws IOException {
        String html = fixture("fixtures/life1-allee.html");
        Branch branch = new Branch("Life1 Allee", "https://booking.life1.hu/allee/orarend/");
        Life1BookingTimetableSource source = new Life1BookingTimetableSource(
                new HttpFetcher(HttpClient.newHttpClient()),
                List.of(branch),
                url -> html);

        // Fixture was captured mid-week of 2026-11-16; treat "now" as Monday morning.
        List<ClassSession> sessions = source.parse(
                branch, html, LocalDateTime.of(2026, 11, 16, 6, 0));

        assertFalse(sessions.isEmpty());
        assertTrue(sessions.stream().anyMatch(s -> s.className().toLowerCase().contains("stretching")
                || s.className().toLowerCase().contains("pilates")));
        assertTrue(sessions.stream().allMatch(s -> s.start().isAfter(LocalDateTime.of(2026, 11, 16, 5, 59))));
        assertTrue(sessions.stream().map(s -> s.identity().key()).distinct().count() == sessions.size());
    }

    private static String fixture(String path) throws IOException {
        try (var in = Life1BookingTimetableSourceTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
