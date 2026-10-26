package com.sammy.catskinc.client;

import java.util.HashSet;
import java.util.Set;

/** Coalesces ordinary fetches and guarantees one follow-up for force-refresh events. */
final class FetchCoordinator<K> {
    private final Set<K> active = new HashSet<>();
    private final Set<K> pendingForce = new HashSet<>();

    synchronized boolean tryStart(K key) {
        return active.add(key);
    }

    synchronized boolean force(K key) {
        if (!active.add(key)) {
            pendingForce.add(key);
            return false;
        }
        return true;
    }

    /** Returns true when the caller must immediately start the queued follow-up. */
    synchronized boolean finish(K key) {
        if (pendingForce.remove(key)) {
            return true;
        }
        active.remove(key);
        return false;
    }

    synchronized void clear() {
        active.clear();
        pendingForce.clear();
    }
}
