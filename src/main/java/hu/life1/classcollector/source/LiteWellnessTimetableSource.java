package hu.life1.classcollector.source;

import hu.life1.classcollector.TimeZones;
import hu.life1.classcollector.http.HttpFetcher;
import hu.life1.classcollector.model.Branch;
import hu.life1.classcollector.model.ClassSession;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lite Wellness (Mammut / Life1 partner) Typo3 órarend.
 *
 * <p>Server-rendered weekly HTML tables for each room
 * ({@code aerobik-terem}, {@code alternativ-terem}, {@code spinningr-terem})
 * and the current + next ISO week ({@code N-het} / {@code (N+1)-het}).
 */
public final class LiteWellnessTimetableSource implements TimetableSource {

    static final String LANDING_URL = "https://www.litewellness.hu/csoportos-orak-es-orarend/orarend/";
    private static final String BASE = "https://www.litewellness.hu/";
    private static final Pattern WEEK_ROOM = Pattern.compile("orarend/(\\d+)-het/([a-z0-9-]+)/?");
    private static final Pattern HOUR_RANGE = Pattern.compile("(\\d{1,2}:\\d{2})\\s*[-–]\\s*(\\d{1,2}:\\d{2})");

    private static final List<String> ROOMS = List.of(
            "aerobik-terem",
            "alternativ-terem",
            "spinningr-terem");

    private final HttpFetcher http;
    private final List<Branch> branches;
    private final HtmlLoader htmlLoader;

    public LiteWellnessTimetableSource(HttpFetcher http) {
        this(http, List.of(new Branch("Lite Wellness", LANDING_URL)), http::getText);
    }

    LiteWellnessTimetableSource(HttpFetcher http, List<Branch> branches, HtmlLoader htmlLoader) {
        this.http = http;
        this.branches = List.copyOf(branches);
        this.htmlLoader = htmlLoader;
    }

    @Override
    public String familyName() {
        return "lite-wellness";
    }

    @Override
    public List<Branch> branches() {
        return branches;
    }

    @Override
    public List<ClassSession> collectFutureClasses(Branch branch) throws Exception {
        String landing = htmlLoader.load(branch.timetableUrl());
        Set<Integer> weeks = discoverWeeks(landing);
        if (weeks.isEmpty()) {
            // Fallback: current ISO week in Europe/Budapest + next week.
            int current = LocalDate.now(TimeZones.EUROPE_BUDAPEST)
                    .get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            weeks.add(current);
            weeks.add(current >= 52 ? 1 : current + 1);
        }

        LocalDateTime now = LocalDateTime.now(TimeZones.EUROPE_BUDAPEST);
        Map<String, ClassSession> unique = new LinkedHashMap<>();
        for (int week : weeks) {
            for (String room : ROOMS) {
                String url = pageUrl(week, room);
                String html = htmlLoader.load(url);
                for (ClassSession session : parsePage(branch, html, week, url, now)) {
                    unique.putIfAbsent(session.identity().key(), session);
                }
            }
        }
        return List.copyOf(unique.values());
    }

    List<ClassSession> parsePage(
            Branch branch,
            String html,
            int isoWeek,
            String pageUrl,
            LocalDateTime nowBudapest) {
        Document doc = Jsoup.parse(html, pageUrl);
        // Prefer week number from the H1 ("Órarend - 47. hét") when present.
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            Matcher weekMatcher = Pattern.compile("(\\d+)\\.\\s*hét").matcher(h1.text());
            if (weekMatcher.find()) {
                isoWeek = Integer.parseInt(weekMatcher.group(1));
            }
        }
        int weekBasedYear = LocalDate.now(TimeZones.EUROPE_BUDAPEST)
                .get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
        LocalDate monday = LocalDate.parse(
                "%d-W%02d-1".formatted(weekBasedYear, isoWeek),
                java.time.format.DateTimeFormatter.ISO_WEEK_DATE);

        Map<String, ClassSession> unique = new LinkedHashMap<>();
        for (Element table : doc.select("table.info")) {
            for (Element row : table.select("tbody > tr")) {
                Elements cells = row.select("> td");
                if (cells.size() < 8) {
                    continue;
                }
                // col0 = hour, col1..7 = Mon..Sun (wd1..wd7)
                Matcher hourMatcher = HOUR_RANGE.matcher(cells.get(0).text());
                if (!hourMatcher.find()) {
                    continue;
                }
                LocalTime defaultStart = LocalTime.parse(pad(hourMatcher.group(1)));
                LocalTime defaultEnd = LocalTime.parse(pad(hourMatcher.group(2)));
                for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
                    Element cell = cells.get(dayIndex + 1);
                    String raw = cell.text().replace('\u00a0', ' ').strip();
                    if (raw.isEmpty()) {
                        continue;
                    }
                    for (ClassParts parts : splitCellClasses(raw, defaultStart, defaultEnd)) {
                        LocalDateTime start = LocalDateTime.of(monday.plusDays(dayIndex), parts.start());
                        LocalDateTime end = LocalDateTime.of(monday.plusDays(dayIndex), parts.end());
                        ClassSession session = new ClassSession(
                                branch, start, end, parts.className(), parts.instructor(), LANDING_URL);
                        if (session.startsInFuture(nowBudapest)) {
                            unique.putIfAbsent(session.identity().key(), session);
                        }
                    }
                }
            }
        }
        return List.copyOf(unique.values());
    }

    static Set<Integer> discoverWeeks(String landingHtml) {
        Set<Integer> weeks = new LinkedHashSet<>();
        Matcher matcher = WEEK_ROOM.matcher(landingHtml);
        while (matcher.find()) {
            weeks.add(Integer.parseInt(matcher.group(1)));
        }
        return weeks;
    }

    static String pageUrl(int week, String room) {
        return BASE + "csoportos-orak-es-orarend/orarend/" + week + "-het/" + room + "/";
    }

    private static List<ClassParts> splitCellClasses(String raw, LocalTime defaultStart, LocalTime defaultEnd) {
        // Cells are usually "ClassName\nInstructor". Workshops may embed an alternate time.
        String[] lines = raw.split("\\R+");
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            String t = line.strip();
            if (!t.isEmpty()) {
                cleaned.add(t);
            }
        }
        if (cleaned.isEmpty()) {
            return List.of();
        }

        LocalTime start = defaultStart;
        LocalTime end = defaultEnd;
        int index = 0;
        Matcher altTime = HOUR_RANGE.matcher(cleaned.getFirst());
        if (altTime.find() && cleaned.getFirst().matches(".*\\d{1,2}:\\d{2}.*")) {
            // e.g. "15:30-16:30" on its own line before WORKSHOP
            if (cleaned.getFirst().replaceAll("\\s", "").matches("\\d{1,2}:\\d{2}[-–]\\d{1,2}:\\d{2}")) {
                start = LocalTime.parse(pad(altTime.group(1)));
                end = LocalTime.parse(pad(altTime.group(2)));
                index = 1;
            }
        }
        if (index >= cleaned.size()) {
            return List.of();
        }
        String className = cleaned.get(index);
        String instructor = index + 1 < cleaned.size()
                ? String.join(" ", cleaned.subList(index + 1, cleaned.size()))
                : "";
        return List.of(new ClassParts(className, instructor, start, end));
    }

    private static String pad(String hhmm) {
        String[] parts = hhmm.split(":");
        return "%02d:%02d".formatted(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private record ClassParts(String className, String instructor, LocalTime start, LocalTime end) {}

    @FunctionalInterface
    interface HtmlLoader {
        String load(String url) throws Exception;
    }
}
