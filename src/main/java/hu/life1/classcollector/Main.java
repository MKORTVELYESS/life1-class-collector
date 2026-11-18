package hu.life1.classcollector;

import hu.life1.classcollector.csv.CsvStore;
import hu.life1.classcollector.http.HttpFetcher;
import hu.life1.classcollector.source.Life1BookingTimetableSource;
import hu.life1.classcollector.source.LiteWellnessTimetableSource;
import hu.life1.classcollector.source.PrestigeWellnessGateTimetableSource;
import hu.life1.classcollector.source.TimetableSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entry point for life1-class-collector.
 *
 * <p>Usage:
 * {@code java -jar life1-class-collector-1.0.0.jar [--out=classes.csv]}
 * or {@code ./gradlew run --args="--out=classes.csv"}
 */
public class Main {

    private static final Path DEFAULT_OUT = Path.of("classes.csv");

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        IO.println("life1-class-collector → %s (timezone=%s)".formatted(
                config.outputCsv, TimeZones.EUROPE_BUDAPEST));

        HttpFetcher http = HttpFetcher.createDefault();
        List<TimetableSource> sources = new ArrayList<>();
        sources.add(new Life1BookingTimetableSource(http));
        sources.add(new PrestigeWellnessGateTimetableSource(http));
        sources.add(new LiteWellnessTimetableSource(http));

        CsvStore store = new CsvStore(config.outputCsv);
        if (!config.outputCsv.toFile().exists()) {
            store.writeHeaderOnly();
        }

        new Collector(sources, store).run();
    }

    private record Config(Path outputCsv) {

        static Config parse(String[] args) {
            Path out = DEFAULT_OUT;
            for (String arg : args) {
                if (arg.startsWith("--out=")) {
                    out = Path.of(arg.substring("--out=".length()));
                } else if ("--help".equals(arg) || "-h".equals(arg)) {
                    printHelpAndExit();
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            return new Config(out);
        }

        private static void printHelpAndExit() {
            IO.println("""
                    Usage: java -jar life1-class-collector.jar [--out=classes.csv]
                    Collects future Life1 / Prestige / Lite classes into a soft-delete CSV.
                    """);
            System.exit(0);
        }
    }
}
