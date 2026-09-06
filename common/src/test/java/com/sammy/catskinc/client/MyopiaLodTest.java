package com.sammy.catskinc.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MyopiaLodTest {
    private static final int BASE_DISTANCE = 20;

    @Test
    void normalModeUsesPowerOfTwoDistanceBands() {
        assertEquals(MyopiaLod.Level.L0, MyopiaLod.levelFor(19.9, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
        assertEquals(MyopiaLod.Level.L1, MyopiaLod.levelFor(20, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
        assertEquals(MyopiaLod.Level.L2, MyopiaLod.levelFor(40, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
        assertEquals(MyopiaLod.Level.L3, MyopiaLod.levelFor(80, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
    }

    @Test
    void panickedModeStartsOneLevelLower() {
        assertEquals(MyopiaLod.Level.L1, MyopiaLod.levelFor(0, BASE_DISTANCE, ModConfig.MyopiaMode.PANICKED));
        assertEquals(MyopiaLod.Level.L2, MyopiaLod.levelFor(20, BASE_DISTANCE, ModConfig.MyopiaMode.PANICKED));
        assertEquals(MyopiaLod.Level.L3, MyopiaLod.levelFor(40, BASE_DISTANCE, ModConfig.MyopiaMode.PANICKED));
    }

    @Test
    void hysteresisPreventsThresholdOscillationUntilTheBandIsCrossed() {
        assertEquals(MyopiaLod.Level.L0, MyopiaLod.levelWithHysteresis(
                MyopiaLod.Level.L0, 21.9, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
        assertEquals(MyopiaLod.Level.L1, MyopiaLod.levelWithHysteresis(
                MyopiaLod.Level.L0, 22.0, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
        assertEquals(MyopiaLod.Level.L1, MyopiaLod.levelWithHysteresis(
                MyopiaLod.Level.L1, 18.1, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
        assertEquals(MyopiaLod.Level.L0, MyopiaLod.levelWithHysteresis(
                MyopiaLod.Level.L1, 18.0, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
    }

    @Test
    void trackerEnforcesDwellTimeAndReevaluatesImmediatelyAfterASettingsChange() {
        AtomicLong clock = new AtomicLong(0);
        MyopiaLodTracker tracker = new MyopiaLodTracker(clock::get, 500);
        UUID player = UUID.randomUUID();

        assertEquals(MyopiaLod.Level.L0, tracker.level(player, 10, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
        clock.set(1_000);
        assertEquals(MyopiaLod.Level.L1, tracker.level(player, 22, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
        clock.set(1_200);
        assertEquals(MyopiaLod.Level.L1, tracker.level(player, 18, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));
        clock.set(1_500);
        assertEquals(MyopiaLod.Level.L0, tracker.level(player, 18, BASE_DISTANCE, ModConfig.MyopiaMode.NORMAL));

        assertEquals(MyopiaLod.Level.L1, tracker.level(player, 18, 10, ModConfig.MyopiaMode.NORMAL));
    }

    @Test
    void configDistanceIsClampedToTheSupportedRange() {
        assertEquals(ModConfig.MYOPIA_DISTANCE_MIN, ModConfig.clampMyopiaDistance(-1));
        assertEquals(20, ModConfig.clampMyopiaDistance(20));
        assertEquals(ModConfig.MYOPIA_DISTANCE_MAX, ModConfig.clampMyopiaDistance(500));
    }
}
