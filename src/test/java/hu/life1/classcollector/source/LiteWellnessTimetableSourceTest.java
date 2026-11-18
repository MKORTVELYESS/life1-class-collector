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

class LiteWellnessTimetableSourceTest {

    @Test
    void parsesAerobikWeek47Fixture() throws IOException {
        String html = fixture("fixtures/lite-47-aerobik.html");
        Branch branch = new Branch("Lite Wellness", LiteWellnessTimetableSource.LANDING_URL);
        LiteWellnessTimetableSource source = new LiteWellnessTimetableSource(
                new HttpFetcher(HttpClient.newHttpClient()),
                List.of(branch),
                url -> html);

        List<ClassSession> sessions = source.parsePage(
                branch,
                html,
                47,
                LiteWellnessTimetableSource.pageUrl(47, "aerobik-terem"),
                LocalDateTime.of(2026, 11, 16, 6, 0));

        assertFalse(sessions.isEmpty());
        assertTrue(sessions.stream().anyMatch(s -> s.className().toLowerCase().contains("pump")
                || s.className().toLowerCase().contains("alakformáló")
                || s.className().toLowerCase().contains("kondi")));
    }

    @Test
    void discoversWeeksFromLandingMarkup() throws IOException {
        String html = fixture("fixtures/lite-47-aerobik.html");
        assertTrue(LiteWellnessTimetableSource.discoverWeeks(html).contains(47));
        assertTrue(LiteWellnessTimetableSource.discoverWeeks(html).contains(48));
    }

    private static String fixture(String path) throws IOException {
        try (var in = LiteWellnessTimetableSourceTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
