package at.redi.irisperf.client;

import java.util.Arrays;

public class ShaderProfile {
    private static final int MAX_FUNCTION_COUNT = 256;

    public String patchedShader = "";

    public int functionCount = 0;
    public final String[] functionNames = new String[MAX_FUNCTION_COUNT];
    public final long[] functionTime = new long[MAX_FUNCTION_COUNT];
    public final int[] functionSamples = new int[MAX_FUNCTION_COUNT];

    public long[] getAverageTiming() {
        long[] functions = new long[functionCount];
        for (int i = 0; i < functions.length; i++) {
            long samples = functionSamples[i];
            functions[i] = samples > 0 ? functionTime[i] / samples : 0;
        }

        return functions;
    }

    public void reset() {
        Arrays.fill(functionTime, 0);
        Arrays.fill(functionSamples, 0);
    }
}
