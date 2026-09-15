package com.sammy.catskinc.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-player LOD state with hysteresis and a minimum dwell time so skins don't flicker
 * between resolutions when a player hovers around a threshold.
 */
public final class MyopiaLodTracker {
    /** Minimum time a level must be held before it may change again. */
    public static final long DEFAULT_DWELL_MS = 500L;

    private static final MyopiaLodTracker INSTANCE = new MyopiaLodTracker(System::currentTimeMillis, DEFAULT_DWELL_MS);

    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final long dwellMs;

    MyopiaLodTracker(LongSupplier clock, long dwellMs) {
        this.clock = clock;
        this.dwellMs = Math.max(0, dwellMs);
    }

    public static MyopiaLodTracker get() {
        return INSTANCE;
    }

    /**
     * Returns the LOD level to render {@code uuid} at, given its squared distance to the local player.
     */
    public MyopiaLod.Level levelForSquared(UUID uuid, double distanceSq, int baseBlocks, ModConfig.MyopiaMode mode) {
        return level(uuid, Math.sqrt(Math.max(0, distanceSq)), baseBlocks, mode);
    }

    public MyopiaLod.Level level(UUID uuid, double distance, int baseBlocks, ModConfig.MyopiaMode mode) {
        if (uuid == null) {
            return MyopiaLod.levelFor(distance, baseBlocks, mode);
        }
        long now = clock.getAsLong();
        State state = states.get(uuid);
        if (state == null) {
            MyopiaLod.Level initial = MyopiaLod.levelFor(distance, baseBlocks, mode);
            states.put(uuid, new State(initial, now, baseBlocks, mode));
            return initial;
        }
        if (state.baseBlocks != baseBlocks || state.mode != mode) {
            // Settings changed: re-evaluate immediately without hysteresis.
            MyopiaLod.Level fresh = MyopiaLod.levelFor(distance, baseBlocks, mode);
            states.put(uuid, new State(fresh, now, baseBlocks, mode));
            return fresh;
        }
        MyopiaLod.Level next = MyopiaLod.levelWithHysteresis(state.level, distance, baseBlocks, mode);
        if (next == state.level) {
            return state.level;
        }
        // Only apply dwell time for downgrades (candidate < current) to prevent rapid toggling.
        // Allow upgrades (candidate > current) immediately when hysteresis permits.
        if (next.ordinal() < state.level.ordinal() && now - state.since < dwellMs) {
            return state.level;
        }
        states.put(uuid, new State(next, now, baseBlocks, mode));
        return next;
    }

    public MyopiaLod.Level current(UUID uuid) {
        State state = uuid == null ? null : states.get(uuid);
        return state == null ? null : state.level;
    }

    public void forget(UUID uuid) {
        if (uuid != null) {
            states.remove(uuid);
        }
    }

    public void reset() {
        states.clear();
    }

    private static final class State {
        final MyopiaLod.Level level;
        final long since;
        final int baseBlocks;
        final ModConfig.MyopiaMode mode;

        State(MyopiaLod.Level level, long since, int baseBlocks, ModConfig.MyopiaMode mode) {
            this.level = level;
            this.since = since;
            this.baseBlocks = baseBlocks;
            this.mode = mode;
        }
    }
}