package com.sammy.catskinc.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Decides which players may render their lossless original under the AUTO texture quality.
 *
 * <p>At most {@code cap} players hold a slot. A player that stops being drawn for
 * {@link #RELEASE_MS} loses it; a newcomer that is clearly closer than the farthest holder
 * (by {@link #SWAP_MARGIN_BLOCKS}) takes that holder's slot. Released players are reported
 * to {@code onRelease} so their lossless GPU texture can be freed right away, which keeps
 * VRAM bounded even in crowds. Not thread-safe: use from the render thread only.
 */
final class LosslessSlots {
    static final long RELEASE_MS = 1_000L;
    static final double SWAP_MARGIN_BLOCKS = 1.0;

    private final Map<UUID, Holder> holders = new HashMap<>();
    private final Consumer<UUID> onRelease;

    LosslessSlots(Consumer<UUID> onRelease) {
        this.onRelease = onRelease;
    }

    /** Returns true when {@code uuid}, at the given distance, may render lossless this frame. */
    boolean acquire(UUID uuid, double distanceSq, long now, int cap) {
        releaseIdle(now);
        double distance = Math.sqrt(Math.max(0, distanceSq));
        Holder holder = holders.get(uuid);
        if (holder != null) {
            holder.distance = distance;
            holder.lastUsed = now;
            return true;
        }
        while (holders.size() > Math.max(0, cap)) {
            release(farthest());
        }
        if (holders.size() < cap) {
            holders.put(uuid, new Holder(distance, now));
            return true;
        }
        UUID farthest = farthest();
        if (farthest != null && distance + SWAP_MARGIN_BLOCKS < holders.get(farthest).distance) {
            release(farthest);
            holders.put(uuid, new Holder(distance, now));
            return true;
        }
        return false;
    }

    boolean holds(UUID uuid) {
        return holders.containsKey(uuid);
    }

    int size() {
        return holders.size();
    }

    /** Frees slots of players that have not been drawn recently. */
    void releaseIdle(long now) {
        List<UUID> idle = new ArrayList<>();
        for (Map.Entry<UUID, Holder> e : holders.entrySet()) {
            if (now - e.getValue().lastUsed > RELEASE_MS) {
                idle.add(e.getKey());
            }
        }
        for (UUID uuid : idle) {
            release(uuid);
        }
    }

    void forget(UUID uuid) {
        holders.remove(uuid);
    }

    void clear() {
        holders.clear();
    }

    private UUID farthest() {
        UUID farthest = null;
        double max = -1;
        for (Map.Entry<UUID, Holder> e : holders.entrySet()) {
            if (e.getValue().distance > max) {
                max = e.getValue().distance;
                farthest = e.getKey();
            }
        }
        return farthest;
    }

    private void release(UUID uuid) {
        if (uuid != null && holders.remove(uuid) != null) {
            onRelease.accept(uuid);
        }
    }

    private static final class Holder {
        double distance;
        long lastUsed;

        Holder(double distance, long lastUsed) {
            this.distance = distance;
            this.lastUsed = lastUsed;
        }
    }
}
