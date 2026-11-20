package hu.life1.classlist;

import hu.life1.classlist.csv.CsvStore;
import hu.life1.classlist.model.Branch;
import hu.life1.classlist.model.ClassSession;
import hu.life1.classlist.source.TimetableSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Orchestrates all timetable sources and soft-delete CSV sync. */
public final class Collector {

    private final List<TimetableSource> sources;
    private final CsvStore store;

    public Collector(List<TimetableSource> sources, CsvStore store) {
        this.sources = List.copyOf(sources);
        this.store = store;
    }

    public CsvStore.SyncStats run() throws Exception {
        LocalDateTime seenAt = LocalDateTime.now(TimeZones.EUROPE_BUDAPEST);
        List<ClassSession> scraped = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (TimetableSource source : sources) {
            IO.println("[%s] %d branch(es)".formatted(source.familyName(), source.branches().size()));
            for (Branch branch : source.branches()) {
                try {
                    List<ClassSession> sessions = source.collectFutureClasses(branch);
                    scraped.addAll(sessions);
                    IO.println("  OK  %s -> %d future class(es)".formatted(branch.name(), sessions.size()));
                } catch (Exception error) {
                    failures.add(branch.name() + ": " + rootMessage(error));
                    IO.println("  FAIL %s: %s".formatted(branch.name(), rootMessage(error)));
                }
            }
        }

        CsvStore.SyncStats stats = store.sync(scraped, seenAt);
        IO.println("CSV %s — added=%d refreshed=%d revived=%d deleted=%d total=%d".formatted(
                store.path(),
                stats.added(),
                stats.refreshed(),
                stats.revived(),
                stats.deleted(),
                stats.totalRows()));
        if (!failures.isEmpty()) {
            IO.println("Branch failures (%d): %s".formatted(failures.size(), String.join("; ", failures)));
        }
        return stats;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.toString();
    }
}
