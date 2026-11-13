package bigdata.matmul.benchmark;

public interface ResultSink {

    void write(MeasurementRow row);
}
