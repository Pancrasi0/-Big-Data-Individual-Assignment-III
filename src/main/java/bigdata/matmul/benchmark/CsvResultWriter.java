package bigdata.matmul.benchmark;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class CsvResultWriter implements ResultSink, Closeable {

    private static final long EMPTY_FILE_SIZE = 0L;

    private final BufferedWriter writer;

    public CsvResultWriter(Path file) throws IOException {
        final boolean needsHeader = !Files.exists(file) || Files.size(file) == EMPTY_FILE_SIZE;
        this.writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (needsHeader) {
            writeLine(MeasurementRow.HEADER);
        }
    }

    @Override
    public void write(MeasurementRow row) {
        try {
            writeLine(row.toCsvLine());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeLine(String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
