package bigdata.matmul.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public final class SystemInfo {

    public static final String UNKNOWN = "UNKNOWN";

    private static final Path CPU_INFO = Path.of("/proc/cpuinfo");
    private static final Path SCALING_GOVERNOR = Path.of("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
    private static final String CPU_MODEL_KEY = "model name";
    private static final String CPU_CORES_KEY = "cpu cores";
    private static final char KEY_VALUE_SEPARATOR = ':';

    public String cpuModel() {
        return firstValueForKey(CPU_INFO, CPU_MODEL_KEY).orElse(UNKNOWN);
    }

    public String physicalCoresPerSocket() {
        return firstValueForKey(CPU_INFO, CPU_CORES_KEY).orElse(UNKNOWN);
    }

    public String scalingGovernor() {
        return firstLine(SCALING_GOVERNOR).orElse(UNKNOWN);
    }

    public String totalRamBytes() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return Long.toString(os.getTotalMemorySize());
        }
        return UNKNOWN;
    }

    private Optional<String> firstValueForKey(Path file, String key) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.filter(line -> line.startsWith(key)).map(this::valueAfterSeparator).findFirst();
        } catch (IOException | UncheckedIOException e) {
            return Optional.empty();
        }
    }

    private Optional<String> firstLine(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.map(String::trim).findFirst();
        } catch (IOException | UncheckedIOException e) {
            return Optional.empty();
        }
    }

    private String valueAfterSeparator(String line) {
        return line.substring(line.indexOf(KEY_VALUE_SEPARATOR) + 1).trim();
    }
}
