package dev.autobuilder.control;

import java.util.concurrent.ThreadLocalRandom;

public final class HumanDelay {
    /** Gaussian-distributed delay in ms, clamped at >= 0. */
    public static long sample(int meanMs, int varianceMs) {
        return Math.max(0, Math.round(meanMs + ThreadLocalRandom.current().nextGaussian() * varianceMs));
    }
}
