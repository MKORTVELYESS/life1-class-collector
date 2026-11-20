package hu.life1.classlist.source;

import hu.life1.classlist.TimeZones;
import hu.life1.classlist.http.HttpFetcher;
import hu.life1.classlist.model.Branch;
import hu.life1.classlist.model.ClassSession;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Life1 Mindbody-backed booking sites ({@code booking.life1.hu/.../orarend/}).
 *
 * <p>The full current + next ISO week are server-rendered in three daypart tables
 * ({@code Reggel}, {@code Napközben}, {@code Délután - este}). Week navigation is
 * client-side show/hide of {@code tbody.actualWeek} / {@code tbody.nextWeek}; both
 * are present in the initial HTML, so one GET per branch is enough.
 */
public final class Life1BookingTimetableSource implements TimetableSource {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern HOUR_RANGE = Pattern.compile("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})");
    private static final Pattern WEEK_RANGE = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})\\s*-\\s*(20\\d{2}-\\d{2}-\\d{2})");

    private final HttpFetcher http;
    private final List<Branch> branches;
    private final HtmlLoader htmlLoader;

    public Life1BookingTimetableSource(HttpFetcher http) {
        this(http, defaultBranches(), http::getText);
    }

    Life1BookingTimetableSource(HttpFetcher http, List<Branch> branches, HtmlLoader htmlLoader) {
        this.http = http;
        this.branches = List.copyOf(branches);
        this.htmlLoader = htmlLoader;
    }

    public static List<Branch> defaultBranches() {
        return List.of(
                new Branch("Life1 Allee", "https://booking.life1.hu/allee/orarend/"),
                new Branch("Life1 Corvin", "https://booking.life1.hu/corvin/orarend/"),
                new Branch("Life1 Etele", "https://booking.life1.hu/etele/orarend/"),
                new Branch("Life1 Nyugati", "https://booking.life1.hu/nyugati/orarend/"),
                new Branch("Life1 Váci35", "https://booking.life1.hu/vaci35/orarend/"));
    }

    @Override
    public String familyName() {
        return "life1-booking";
    }

    @Override
    public List<Branch> branches() {
        return branches;
    }

    @Override
    public List<ClassSession> collectFutureClasses(Branch branch) throws Exception {
        String html = htmlLoader.load(branch.timetableUrl());
        return parse(branch, html, LocalDateTime.now(TimeZones.EUROPE_BUDAPEST));
    }

    List<ClassSession> parse(Branch branch, String html, LocalDateTime nowBudapest) {
        Document doc = Jsoup.parse(html, branch.timetableUrl());
        Map<String, LocalDate> weekStarts = resolveWeekStarts(doc);
        if (weekStarts.isEmpty()) {
            throw new IllegalStateException("No week ranges found on " + branch.timetableUrl());
        }

        Map<String, ClassSession> unique = new LinkedHashMap<>();
        for (Element table : doc.select("table.table-timetable.hidden-xs")) {
            for (Map.Entry<String, LocalDate> week : weekStarts.entrySet()) {
                Elements bodies = table.select("tbody." + week.getKey());
                for (Element body : bodies) {
                    parseBody(branch, body, week.getValue(), nowBudapest, unique);
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private static void parseBody(
            Branch branch,
            Element body,
            LocalDate monday,
            LocalDateTime nowBudapest,
            Map<String, ClassSession> unique) {
        for (Element row : body.select("> tr")) {
            Elements cells = row.select("> td");
            if (cells.size() != 7) {
                continue;
            }
            for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
                LocalDate day = monday.plusDays(dayIndex);
                Element cell = cells.get(dayIndex);
                Elements hours = cell.select(".timetableHour");
                Elements sports = cell.select(".timetableSport");
                Elements coaches = cell.select(".timetableCoach");
                int n = Math.min(hours.size(), Math.min(sports.size(), coaches.size()));
                for (int i = 0; i < n; i++) {
                    Matcher matcher = HOUR_RANGE.matcher(hours.get(i).text().trim());
                    if (!matcher.find()) {
                        continue;
                    }
                    LocalTime startTime = LocalTime.parse(normalizeTime(matcher.group(1)));
                    LocalTime endTime = LocalTime.parse(normalizeTime(matcher.group(2)));
                    String className = textOf(sports.get(i));
                    String instructor = textOf(coaches.get(i));
                    if (className.isEmpty()) {
                        continue;
                    }
                    LocalDateTime start = LocalDateTime.of(day, startTime);
                    LocalDateTime end = LocalDateTime.of(day, endTime);
                    if (!end.isAfter(start)) {
                        // overnight guard (rare); treat as same-day end = start
                        end = start;
                    }
                    ClassSession session = new ClassSession(branch, start, end, className, instructor, branch.timetableUrl());
                    if (session.startsInFuture(nowBudapest)) {
                        unique.putIfAbsent(session.identity().key(), session);
                    }
                }
            }
        }
    }

    private static Map<String, LocalDate> resolveWeekStarts(Document doc) {
        Map<String, LocalDate> starts = new LinkedHashMap<>();
        for (Element group : doc.select("div.input-group.actual, div.input-group.next")) {
            Element input = group.selectFirst("input[value]");
            if (input == null) {
                continue;
            }
            Matcher matcher = WEEK_RANGE.matcher(input.attr("value"));
            if (!matcher.find()) {
                continue;
            }
            LocalDate monday = LocalDate.parse(matcher.group(1), DAY);
            String key = group.hasClass("actual") ? "actualWeek" : "nextWeek";
            starts.putIfAbsent(key, monday);
        }
        return starts;
    }

    private static String textOf(Element element) {
        Element link = element.selectFirst("a");
        String text = link != null ? link.text() : element.text();
        return text.replace('\u00a0', ' ').strip().replaceAll("\\s+", " ");
    }

    private static String normalizeTime(String raw) {
        String[] parts = raw.trim().split(":");
        return "%02d:%02d".formatted(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    @FunctionalInterface
    interface HtmlLoader {
        String load(String url) throws Exception;
    }
}
