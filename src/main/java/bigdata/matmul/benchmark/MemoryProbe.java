package bigdata.matmul.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MemoryProbe {

    public static final long UNAVAILABLE = MeasurementRow.ABSENT;

    private static final int GC_REQUESTS = 3;
    private static final long GC_SETTLE_MILLIS = 30L;
    private static final Path PROCESS_STATUS = Path.of("/proc/self/status");
    private static final String PEAK_RSS_KEY = "VmHWM:";
    private static final Pattern NON_DIGITS = Pattern.compile("[^0-9]");
    private static final String EMPTY = "";

    public long liveHeapBytesAfterGc() {
        final Runtime runtime = Runtime.getRuntime();
        for (int request = 0; request < GC_REQUESTS; request++) {
            System.gc();
            settle();
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public long peakResidentKilobytes() {
        try (Stream<String> lines = Files.lines(PROCESS_STATUS)) {
            return lines.filter(line -> line.startsWith(PEAK_RSS_KEY))
                    .map(line -> NON_DIGITS.matcher(line).replaceAll(EMPTY))
                    .mapToLong(Long::parseLong)
                    .findFirst()
                    .orElse(UNAVAILABLE);
        } catch (IOException | UncheckedIOException | NumberFormatException e) {
            return UNAVAILABLE;
        }
    }

    private void settle() {
        try {
            Thread.sleep(GC_SETTLE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
