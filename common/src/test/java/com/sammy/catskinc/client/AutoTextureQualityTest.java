package com.sammy.catskinc.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutoTextureQualityTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    @AfterEach
    void reset() {
        ModConfig config = ModConfig.get();
        config.setTextureQuality(ModConfig.TextureQuality.LOSSLESS);
        config.setMyopiaEnabled(false);
        config.setMyopiaMode(ModConfig.MyopiaMode.NORMAL);
        config.setMyopiaDistance(ModConfig.MYOPIA_DISTANCE_DEFAULT);
        CompressedSkinCache.invalidate(A);
        MyopiaLodTracker.get().reset();
    }

    @Test
    void autoUsesStrongerCompressionForTheFarthestLevel() {
        ModConfig.TextureQuality auto = ModConfig.TextureQuality.AUTO;
        assertEquals(DxtEncoder.Format.BC3, auto.formatFor(MyopiaLod.Level.L0));
        assertEquals(DxtEncoder.Format.BC3, auto.formatFor(MyopiaLod.Level.L1));
        assertEquals(DxtEncoder.Format.BC3, auto.formatFor(MyopiaLod.Level.L2));
        assertEquals(DxtEncoder.Format.BC1, auto.formatFor(MyopiaLod.Level.L3));
        assertTrue(auto.losslessWhenNear());
        assertFalse(ModConfig.TextureQuality.BALANCED.losslessWhenNear());
        assertEquals(DxtEncoder.Format.BC1, ModConfig.TextureQuality.FASTEST.formatFor(MyopiaLod.Level.L0));
    }

    @Test
    void autoEncodesEachLevelInItsOwnFormat() {
        int[] pixels = new int[512 * 512];
        CompressedSkinCache.Mip[] mips = CompressedSkinCache.encodeLevels(ModConfig.TextureQuality.AUTO, pixels, 512, 512);
        assertEquals(DxtEncoder.Format.BC3, mips[0].format());
        assertEquals(DxtEncoder.Format.BC1, mips[3].format());
        assertEquals(DxtEncoder.encodedSize(DxtEncoder.Format.BC1, 64, 64), mips[3].data().capacity());
    }

    @Test
    void autoFollowsDistanceEvenWithMyopiaOff() {
        installCompressedSkin(A);
        ModConfig config = ModConfig.get();
        config.setTextureQuality(ModConfig.TextureQuality.AUTO);
        config.setMyopiaEnabled(false);
        double base = config.getMyopiaDistance();
        assertEquals(MyopiaLod.Level.L0, MyopiaRenderHook.levelFor(A, sq(base * 0.5)));
        MyopiaLodTracker.get().reset();
        assertEquals(MyopiaLod.Level.L1, MyopiaRenderHook.levelFor(A, sq(base * 1.5)));
        MyopiaLodTracker.get().reset();
        assertEquals(MyopiaLod.Level.L3, MyopiaRenderHook.levelFor(A, sq(base * 5)));
    }

    @Test
    void autoWithMyopiaOnUsesTheSameSingleLevel() {
        installCompressedSkin(A);
        ModConfig config = ModConfig.get();
        config.setTextureQuality(ModConfig.TextureQuality.AUTO);
        config.setMyopiaEnabled(true);
        double base = config.getMyopiaDistance();
        assertEquals(MyopiaLod.Level.L2, MyopiaRenderHook.levelFor(A, sq(base * 3)));
    }

    @Test
    void panickedModeNeverReachesTheLosslessLevelForOthers() {
        installCompressedSkin(A);
        ModConfig config = ModConfig.get();
        config.setTextureQuality(ModConfig.TextureQuality.AUTO);
        config.setMyopiaMode(ModConfig.MyopiaMode.PANICKED);
        assertEquals(MyopiaLod.Level.L1, MyopiaRenderHook.levelFor(A, sq(1)));
    }

    @Test
    void localPlayerAndGuiStayFullResolution() {
        installCompressedSkin(A);
        ModConfig.get().setTextureQuality(ModConfig.TextureQuality.AUTO);
        assertEquals(MyopiaLod.Level.L0, MyopiaRenderHook.levelFor(A, 0.0));
        assertEquals(MyopiaLod.Level.L0, MyopiaRenderHook.levelFor(A, Double.NaN));
    }

    @Test
    void losslessSlotsAreCappedAndGoToTheNearest() {
        List<UUID> released = new ArrayList<>();
        LosslessSlots slots = new LosslessSlots(released::add);
        assertTrue(slots.acquire(A, sq(10), 0, 2));
        assertTrue(slots.acquire(B, sq(8), 0, 2));
        // Farther than both holders: denied.
        assertFalse(slots.acquire(C, sq(12), 0, 2));
        // Clearly closer than the farthest holder (A at 10): takes A's slot.
        assertTrue(slots.acquire(C, sq(5), 0, 2));
        assertEquals(List.of(A), released);
        assertFalse(slots.holds(A));
        assertEquals(2, slots.size());
    }

    @Test
    void losslessSlotsDoNotSwapWithinTheMargin() {
        LosslessSlots slots = new LosslessSlots(uuid -> { });
        assertTrue(slots.acquire(A, sq(10), 0, 1));
        assertFalse(slots.acquire(B, sq(9.5), 0, 1));
        assertTrue(slots.holds(A));
    }

    @Test
    void losslessSlotsReleasePlayersNoLongerDrawn() {
        List<UUID> released = new ArrayList<>();
        LosslessSlots slots = new LosslessSlots(released::add);
        assertTrue(slots.acquire(A, sq(3), 0, 1));
        slots.releaseIdle(LosslessSlots.RELEASE_MS + 1);
        assertEquals(List.of(A), released);
        assertTrue(slots.acquire(B, sq(30), LosslessSlots.RELEASE_MS + 1, 1));
    }

    @Test
    void lowerCapShrinksHolders() {
        List<UUID> released = new ArrayList<>();
        LosslessSlots slots = new LosslessSlots(released::add);
        slots.acquire(A, sq(3), 0, 3);
        slots.acquire(B, sq(6), 0, 3);
        slots.acquire(C, sq(9), 0, 3);
        // Cap lowered to 1 while C is still nearby: the farthest holders are released first.
        assertFalse(slots.acquire(new UUID(0, 4), sq(20), 0, 1));
        assertEquals(List.of(C, B), released);
        assertTrue(slots.holds(A));
    }

    @Test
    void maxLosslessPlayersIsClamped() {
        ModConfig config = ModConfig.get();
        config.setMaxLosslessPlayers(0);
        assertEquals(ModConfig.MAX_LOSSLESS_PLAYERS_MIN, config.getMaxLosslessPlayers());
        config.setMaxLosslessPlayers(99);
        assertEquals(ModConfig.MAX_LOSSLESS_PLAYERS_MAX, config.getMaxLosslessPlayers());
        config.setMaxLosslessPlayers(ModConfig.MAX_LOSSLESS_PLAYERS_DEFAULT);
    }

    private static void installCompressedSkin(UUID uuid) {
        int[] pixels = new int[256 * 256];
        CompressedSkinCache.Mip[] mips = CompressedSkinCache.encodeLevels(ModConfig.TextureQuality.AUTO, pixels, 256, 256);
        CompressedSkinCache.put(uuid, new CompressedSkinCache.Prepared(
                Map.of(MyopiaTextureCache.VARIANT_SKIN, mips), null));
    }

    private static double sq(double distance) {
        return distance * distance;
    }
}
