package bigdata.matmul.validation;

public final class Tolerance {

    public static final double ABSOLUTE = 1e-12;
    public static final double RELATIVE = 1e-9;

    private Tolerance() {
    }

    public static boolean areClose(double x, double y) {
        return Math.abs(x - y) <= ABSOLUTE + RELATIVE * Math.max(Math.abs(x), Math.abs(y));
    }

    public static boolean areClose(double[] x, double[] y) {
        if (x.length != y.length) {
            return false;
        }
        for (int i = 0; i < x.length; i++) {
            if (!areClose(x[i], y[i])) {
                return false;
            }
        }
        return true;
    }
}
