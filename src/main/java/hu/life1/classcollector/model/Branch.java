package hu.life1.classcollector.model;

/** A gym branch / partner site whose timetable we scrape. */
public record Branch(String name, String timetableUrl) {

    public Branch {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("branch name required");
        }
        if (timetableUrl == null || timetableUrl.isBlank()) {
            throw new IllegalArgumentException("timetable URL required");
        }
    }
}
