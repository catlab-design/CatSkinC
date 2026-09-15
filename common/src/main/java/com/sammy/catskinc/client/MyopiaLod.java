package com.sammy.catskinc.client;

/**
 * Pure (Minecraft-free) LOD math for the Myopia skin-resolution system.
 *
 * <p>Levels step the uploaded skin down by powers of two. For a 64x64 skin the
 * levels are 64 / 32 / 16 / 8; for a 1024x1024 skin they are 1024 / 512 / 256 / 128.
 * The resolution never drops below {@link #MIN_TEXTURE_SIZE} pixels.
 */
public final class MyopiaLod {
    public static final int MIN_TEXTURE_SIZE = 8;

    /** Hysteresis band around each threshold, as a fraction of the threshold distance. */
    public static final double HYSTERESIS = 0.10;

    public enum Level {
        L0(0),
        L1(1),
        L2(2),
        L3(3);

        private final int shift;

        Level(int shift) {
            this.shift = shift;
        }

        /** Power-of-two divisor applied to the source resolution (1, 2, 4, 8). */
        public int divisor() {
            return 1 << shift;
        }

        /** Target texture size for a source of the given size, floored at {@link #MIN_TEXTURE_SIZE}. */
        public int targetSize(int sourceSize) {
            if (sourceSize <= 0) {
                return MIN_TEXTURE_SIZE;
            }
            return Math.max(MIN_TEXTURE_SIZE, Math.min(sourceSize, sourceSize >> shift));
        }

        public boolean isFull() {
            return this == L0;
        }

        static Level of(int index) {
            Level[] values = values();
            return values[Math.max(0, Math.min(values.length - 1, index))];
        }
    }

    private MyopiaLod() {
    }

    /**
     * Picks the LOD level for a player at the given distance without hysteresis.
     *
     * @param distance  distance in blocks from the local player
     * @param baseBlocks configured base transition distance in blocks
     * @param mode      NORMAL or PANICKED
     */
    public static Level levelFor(double distance, int baseBlocks, ModConfig.MyopiaMode mode) {
        double base = Math.max(1, baseBlocks);
        int steps = stepsBeyond(distance, base);
        return Level.of(steps + panicOffset(mode));
    }

    /**
     * Picks the LOD level for a player using a squared distance (avoids sqrt in render code).
     */
    public static Level levelForSquared(double distanceSq, int baseBlocks, ModConfig.MyopiaMode mode) {
        return levelFor(Math.sqrt(Math.max(0, distanceSq)), baseBlocks, mode);
    }

    /**
     * Applies hysteresis: returns {@code current} unless the distance has moved clearly past
     * the threshold separating {@code current} from {@code candidate}.
     */
    public static Level levelWithHysteresis(Level current, double distance, int baseBlocks, ModConfig.MyopiaMode mode) {
        if (current == null) {
            return levelFor(distance, baseBlocks, mode);
        }
        Level candidate = levelFor(distance, baseBlocks, mode);
        if (candidate == current) {
            return current;
        }
        double base = Math.max(1, baseBlocks);
        int offset = panicOffset(mode);
        if (candidate.ordinal() > current.ordinal()) {
            // Moving away: must exceed the threshold that leaves `current` by the hysteresis band.
            double threshold = thresholdAfter(current.ordinal() - offset, base);
            if (Double.isInfinite(threshold) || distance >= threshold * (1.0 + HYSTERESIS)) {
                return candidate;
            }
            return current;
        }
        // Moving closer: must drop below the threshold that enters `current` by the hysteresis band.
        double threshold = thresholdAfter(current.ordinal() - offset - 1, base);
        if (threshold <= 0 || distance <= threshold * (1.0 - HYSTERESIS)) {
            return candidate;
        }
        return current;
    }

    /** Number of base-distance doublings the player is beyond: 0 for < base, 1 for < 2*base, 2 for < 4*base, 3 otherwise. */
    private static int stepsBeyond(double distance, double base) {
        if (distance < base) {
            return 0;
        }
        if (distance < base * 2) {
            return 1;
        }
        if (distance < base * 4) {
            return 2;
        }
        return 3;
    }

    /** Distance at which step index {@code step} ends (0 -> base, 1 -> 2*base, 2 -> 4*base, 3+ -> infinity). */
    private static double thresholdAfter(int step, double base) {
        switch (step) {
            case 0:
                return base;
            case 1:
                return base * 2;
            case 2:
                return base * 4;
            default:
                return step < 0 ? 0 : Double.POSITIVE_INFINITY;
        }
    }

    private static int panicOffset(ModConfig.MyopiaMode mode) {
        return mode == ModConfig.MyopiaMode.PANICKED ? 1 : 0;
    }
}