package hu.life1.classcollector;

import java.time.ZoneId;

/** Shared timezone for every timetable instant stored by this collector. */
public final class TimeZones {

    public static final ZoneId EUROPE_BUDAPEST = ZoneId.of("Europe/Budapest");

    private TimeZones() {}
}
