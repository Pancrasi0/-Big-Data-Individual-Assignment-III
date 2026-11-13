package bigdata.matmul.benchmark;

import bigdata.matmul.validation.Tolerance;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class EnvironmentReport {

    private static final String AFFINITY_VARIABLE = "MATMUL_CPU_AFFINITY";
    private static final String NO_AFFINITY = "none";
    private static final String MANUAL_FIELD = "FILL IN";

    private final SystemInfo systemInfo;

    public EnvironmentReport(SystemInfo systemInfo) {
        this.systemInfo = systemInfo;
    }

    public void writeIfAbsent(Path file, long idleHeapBytes) throws IOException {
        if (!Files.exists(file)) {
            Files.write(file, lines(idleHeapBytes));
        }
    }

    private List<String> lines(long idleHeapBytes) {
        final Runtime runtime = Runtime.getRuntime();
        final List<String> lines = new ArrayList<>();
        lines.add(entry("timestamp_utc", Instant.now()));
        lines.add(entry("java.version", System.getProperty("java.version")));
        lines.add(entry("java.vm.name", System.getProperty("java.vm.name")));
        lines.add(entry("java.vendor", System.getProperty("java.vendor")));
        lines.add(entry("jvm_input_arguments", ManagementFactory.getRuntimeMXBean().getInputArguments()));
        lines.add(entry("os.name", System.getProperty("os.name")));
        lines.add(entry("os.version", System.getProperty("os.version")));
        lines.add(entry("os.arch", System.getProperty("os.arch")));
        lines.add(entry("cpu_model", systemInfo.cpuModel()));
        lines.add(entry("physical_cores_per_socket", systemInfo.physicalCoresPerSocket()));
        lines.add(entry("logical_processors", runtime.availableProcessors()));
        lines.add(entry("total_ram_bytes", systemInfo.totalRamBytes()));
        lines.add(entry("jvm_max_heap_bytes", runtime.maxMemory()));
        lines.add(entry("idle_live_heap_bytes_at_start", idleHeapBytes));
        lines.add(entry("cpu_scaling_governor", systemInfo.scalingGovernor()));
        lines.add(entry("cpu_affinity", System.getenv().getOrDefault(AFFINITY_VARIABLE, NO_AFFINITY)));
        lines.add(entry("numeric_type", "double (64-bit)"));
        lines.add(entry("algorithm", "ikj loop order, flat row-major double[], n^3 multiply-adds"));
        lines.add(entry("parallel_strategy", "static contiguous row partition, one task per thread, fixed thread pool"));
        lines.add(entry("internal_library_threads", "none (no numerical library is used)"));
        lines.add(entry("timer", "System.nanoTime (monotonic)"));
        lines.add(entry("timed_region", "multiplication call only; inputs generated outside; output array allocated "
                + "outside; zero-fill of the output inside the kernel; thread pool created and started outside; "
                + "task submission and join inside"));
        lines.add(entry("tolerance", "abs " + Tolerance.ABSOLUTE + ", rel " + Tolerance.RELATIVE));
        lines.add(entry("compiler_flags", "javac -d out (see scripts/build.sh)"));
        lines.add(entry("other_load_on_machine", MANUAL_FIELD));
        return lines;
    }

    private String entry(String key, Object value) {
        return key + " = " + value;
    }
}
