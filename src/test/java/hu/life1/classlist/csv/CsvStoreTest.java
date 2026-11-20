package hu.life1.classlist.csv;

import hu.life1.classlist.model.Branch;
import hu.life1.classlist.model.ClassSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvStoreTest {

    @TempDir
    Path temp;

    @Test
    void softDeletesMissingAndAddsNew() throws Exception {
        Path csv = temp.resolve("classes.csv");
        CsvStore store = new CsvStore(csv);
        Branch branch = new Branch("Life1 Allee", "https://booking.life1.hu/allee/orarend/");
        LocalDateTime seen1 = LocalDateTime.of(2026, 11, 18, 10, 0);

        ClassSession keep = new ClassSession(
                branch,
                LocalDateTime.of(2026, 11, 20, 7, 0),
                LocalDateTime.of(2026, 11, 20, 8, 0),
                "Pilates",
                "Oli Renáta",
                branch.timetableUrl());
        ClassSession drop = new ClassSession(
                branch,
                LocalDateTime.of(2026, 11, 20, 9, 0),
                LocalDateTime.of(2026, 11, 20, 10, 0),
                "Stretching",
                "Dán Vanda",
                branch.timetableUrl());

        store.sync(List.of(keep, drop), seen1);
        assertEquals(2, store.readAll().size());

        ClassSession neu = new ClassSession(
                branch,
                LocalDateTime.of(2026, 11, 21, 18, 0),
                LocalDateTime.of(2026, 11, 21, 19, 0),
                "Zumba",
                "Oli Renáta",
                branch.timetableUrl());
        CsvStore.SyncStats stats = store.sync(List.of(keep, neu), seen1.plusHours(1));
        assertEquals(1, stats.added());
        assertEquals(1, stats.deleted());

        List<CsvRow> rows = store.readAll();
        assertEquals(3, rows.size());
        assertEquals(0, rows.stream().filter(r -> r.className().equals("Pilates")).findFirst().orElseThrow().deleted());
        assertEquals(1, rows.stream().filter(r -> r.className().equals("Stretching")).findFirst().orElseThrow().deleted());
        assertEquals(0, rows.stream().filter(r -> r.className().equals("Zumba")).findFirst().orElseThrow().deleted());
    }
}
