package hu.life1.classcollector.csv;

import hu.life1.classcollector.model.ClassIdentity;
import hu.life1.classcollector.model.ClassSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** One persisted class row (including soft-delete metadata). */
public record CsvRow(
        String branch,
        LocalDateTime start,
        LocalDateTime end,
        String className,
        String instructor,
        String sourceUrl,
        int deleted,
        LocalDateTime firstSeen,
        LocalDateTime lastSeen
) {

    private static final DateTimeFormatter ISO_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public ClassIdentity identity() {
        return new ClassIdentity(branch, start, className, instructor);
    }

    public static CsvRow fromNewSession(ClassSession session, LocalDateTime seenAt) {
        return new CsvRow(
                session.branch().name(),
                session.start(),
                session.end(),
                session.className(),
                session.instructor(),
                session.sourceUrl(),
                0,
                seenAt,
                seenAt);
    }

    public CsvRow withSeen(ClassSession session, LocalDateTime seenAt) {
        return new CsvRow(
                branch,
                start,
                session.end(),
                className,
                instructor,
                session.sourceUrl(),
                0,
                firstSeen,
                seenAt);
    }

    public CsvRow markedDeleted() {
        return deleted == 1 ? this : new CsvRow(
                branch, start, end, className, instructor, sourceUrl, 1, firstSeen, lastSeen);
    }

    public String startIso() {
        return start.format(ISO_MINUTE);
    }

    public String endIso() {
        return end.format(ISO_MINUTE);
    }

    public String firstSeenIso() {
        return firstSeen.format(ISO_MINUTE);
    }

    public String lastSeenIso() {
        return lastSeen.format(ISO_MINUTE);
    }
}
