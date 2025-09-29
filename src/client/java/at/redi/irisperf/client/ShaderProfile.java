package at.redi.irisperf.client;

import java.util.Arrays;

public class ShaderProfile {
    private static final int MAX_FUNCTION_COUNT = 256;

    public int functionCount = 0;
    public final String[] functionNames = new String[MAX_FUNCTION_COUNT];
    public final long[] functionTime = new long[MAX_FUNCTION_COUNT];

    public void reset() {
        Arrays.fill(functionTime, 0);
    }
}
