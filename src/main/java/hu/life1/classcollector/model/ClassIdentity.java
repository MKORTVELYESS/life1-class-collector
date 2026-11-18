package hu.life1.classcollector.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Stable identity for soft-delete CSV sync: branch + DateTime + class name + instructor. */
public record ClassIdentity(
        String branchName,
        LocalDateTime start,
        String className,
        String instructor
) {

    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public String key() {
        return String.join("|",
                branchName,
                start.format(KEY_TIME),
                className,
                instructor);
    }
}
