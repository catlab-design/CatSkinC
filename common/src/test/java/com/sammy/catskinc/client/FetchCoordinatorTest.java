package com.sammy.catskinc.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FetchCoordinatorTest {
    @Test
    void forceDuringFetchSchedulesExactlyOneFollowUp() {
        FetchCoordinator<UUID> coordinator = new FetchCoordinator<>();
        UUID player = UUID.randomUUID();

        assertTrue(coordinator.tryStart(player));
        assertFalse(coordinator.force(player));
        assertFalse(coordinator.force(player));
        assertTrue(coordinator.finish(player), "pending force must retain ownership for its retry");
        assertFalse(coordinator.tryStart(player), "the retry owns the key already");
        assertFalse(coordinator.finish(player), "no further retry should remain");
        assertTrue(coordinator.tryStart(player), "key must be released after the retry completes");
    }

    @Test
    void differentPlayersDoNotBlockEachOther() {
        FetchCoordinator<UUID> coordinator = new FetchCoordinator<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(coordinator.tryStart(first));
        assertTrue(coordinator.force(second));
        assertFalse(coordinator.finish(first));
        assertFalse(coordinator.finish(second));
    }
}
