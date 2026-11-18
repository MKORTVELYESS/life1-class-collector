package hu.life1.classcollector.model;

import hu.life1.classcollector.TimeZones;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * One scheduled group class occurrence.
 *
 * <p>Identity key: {@code branch + start + className + instructor}.
 */
public record ClassSession(
        Branch branch,
        LocalDateTime start,
        LocalDateTime end,
        String className,
        String instructor,
        String sourceUrl
) {

    public ClassSession {
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        className = normalize(className);
        instructor = normalize(instructor);
        sourceUrl = sourceUrl == null || sourceUrl.isBlank() ? branch.timetableUrl() : sourceUrl.trim();
        if (className.isEmpty()) {
            throw new IllegalArgumentException("class name required");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end before start: " + start + " / " + end);
        }
    }

    public ClassIdentity identity() {
        return new ClassIdentity(branch.name(), start, className, instructor);
    }

    public ZonedDateTime startInBudapest() {
        return start.atZone(TimeZones.EUROPE_BUDAPEST);
    }

    public boolean startsInFuture(LocalDateTime nowBudapest) {
        return start.isAfter(nowBudapest);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').strip().replaceAll("\\s+", " ");
    }
}
