package bigdata.matmul.kernel;

import java.util.ArrayList;
import java.util.List;

public final class RowPartitioner {

    private static final int MIN_ROW_COUNT = 0;
    private static final int MIN_PART_COUNT = 1;
    private static final int EXTRA_ROW = 1;
    private static final int NO_EXTRA_ROW = 0;

    private RowPartitioner() {
    }

    public static List<RowRange> partition(int rowCount, int partCount) {
        if (rowCount < MIN_ROW_COUNT || partCount < MIN_PART_COUNT) {
            throw new IllegalArgumentException(
                    "rowCount must be >= " + MIN_ROW_COUNT + " and partCount must be >= " + MIN_PART_COUNT);
        }
        final int baseLength = rowCount / partCount;
        final int remainder = rowCount % partCount;
        final List<RowRange> ranges = new ArrayList<>(partCount);
        int start = 0;
        for (int part = 0; part < partCount; part++) {
            final int length = baseLength + (part < remainder ? EXTRA_ROW : NO_EXTRA_ROW);
            ranges.add(new RowRange(start, start + length));
            start += length;
        }
        return ranges;
    }
}
