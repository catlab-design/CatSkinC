package com.sammy.catskinc.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Myopia LOD math. Pure logic, no Minecraft dependencies.
 */
class MyopiaLodTest {

    @ParameterizedTest
    @MethodSource("distanceCases")
    void levelFor(double distance, int base, ModConfig.MyopiaMode mode, MyopiaLod.Level expected) {
        MyopiaLod.Level result = MyopiaLod.levelFor(distance, base, mode);
        assertEquals(expected, result,
                "distance=" + distance + " base=" + base + " mode=" + mode);
    }

    static Stream<Arguments> distanceCases() {
        return Stream.of(
                // NORMAL mode
                Arguments.of(0.0, 20, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L0),
                Arguments.of(19.9, 20, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L0),
                Arguments.of(20.0, 20, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L1),
                Arguments.of(39.9, 20, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L1),
                Arguments.of(40.0, 20, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L2),
                Arguments.of(79.9, 20, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L2),
                Arguments.of(80.0, 20, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L3),
                Arguments.of(160.0, 20, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L3),
                // PANICKED mode (shifted by one)
                Arguments.of(0.0, 20, ModConfig.MyopiaMode.PANICKED, MyopiaLod.Level.L1),
                Arguments.of(19.9, 20, ModConfig.MyopiaMode.PANICKED, MyopiaLod.Level.L1),
                Arguments.of(20.0, 20, ModConfig.MyopiaMode.PANICKED, MyopiaLod.Level.L2),
                Arguments.of(39.9, 20, ModConfig.MyopiaMode.PANICKED, MyopiaLod.Level.L2),
                Arguments.of(40.0, 20, ModConfig.MyopiaMode.PANICKED, MyopiaLod.Level.L3),
                Arguments.of(80.0, 20, ModConfig.MyopiaMode.PANICKED, MyopiaLod.Level.L3),
                // Different base
                Arguments.of(0.0, 10, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L0),
                Arguments.of(9.9, 10, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L0),
                Arguments.of(10.0, 10, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L1),
                Arguments.of(19.9, 10, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L1),
                Arguments.of(20.0, 10, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L2),
                // Very small base (min 1)
                Arguments.of(0.0, 1, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L0),
                Arguments.of(0.5, 1, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L0),
                Arguments.of(1.0, 1, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L1),
                Arguments.of(1.9, 1, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L1),
                Arguments.of(2.0, 1, ModConfig.MyopiaMode.NORMAL, MyopiaLod.Level.L2)
        );
    }

    @Test
    void hysteresisPreventsFlicker() {
        UUID uuid = UUID.randomUUID();
        // Use a tracker with instant clock (0ms dwell) to test pure hysteresis
        MyopiaLodTracker tracker = new MyopiaLodTracker(() -> 1000L, 0L);
        // Start at L0
        MyopiaLod.Level level = tracker.level(uuid, 19.0, 20, ModConfig.MyopiaMode.NORMAL);
        assertEquals(MyopiaLod.Level.L0, level);

        // Move to threshold - should stay at L0 due to hysteresis
        level = tracker.level(uuid, 20.0, 20, ModConfig.MyopiaMode.NORMAL);
        assertEquals(MyopiaLod.Level.L0, level);

        // Move well past threshold - should advance
        level = tracker.level(uuid, 25.0, 20, ModConfig.MyopiaMode.NORMAL);
        assertEquals(MyopiaLod.Level.L1, level);

        // Move back - should stay at L1 due to hysteresis
        level = tracker.level(uuid, 19.0, 20, ModConfig.MyopiaMode.NORMAL);
        assertEquals(MyopiaLod.Level.L1, level);

        // Move well below - should return to L0
        level = tracker.level(uuid, 10.0, 20, ModConfig.MyopiaMode.NORMAL);
        assertEquals(MyopiaLod.Level.L0, level);
    }

@Test
    void dwellTimePreventsRapidChanges() {
        UUID uuid = UUID.randomUUID();
        // Use a tracker with manually controlled time
        final long[] clock = {1000L};
        MyopiaLodTracker tracker = new MyopiaLodTracker(() -> clock[0], 500L);
        // Start at L0 (distance 10 < base 20)
        MyopiaLod.Level level = tracker.level(uuid, 10.0, 20, ModConfig.MyopiaMode.NORMAL);
        assertEquals(MyopiaLod.Level.L0, level);

        // Move past threshold to L1
        MyopiaLod.Level level1 = tracker.level(uuid, 25.0, 20, ModConfig.MyopiaMode.NORMAL);
        assertEquals(MyopiaLod.Level.L1, level1);

        // Move back immediately - should stay at L1 due to dwell time
        clock[0] = 1100L; // 100ms later, less than dwell
        MyopiaLod.Level level2 = tracker.level(uuid, 10.0, 20, ModConfig.MyopiaMode.NORMAL);
        assertEquals(MyopiaLod.Level.L1, level2);

        // Wait for dwell time to pass
        clock[0] = 2000L; // 1000ms later, past dwell
        MyopiaLod.Level level3 = tracker.level(uuid, 10.0, 20, ModConfig.MyopiaMode.NORMAL);
        assertEquals(MyopiaLod.Level.L0, level3);
    }

    @Test
    void levelTargetSize() {
        assertEquals(64, MyopiaLod.Level.L0.targetSize(64));
        assertEquals(32, MyopiaLod.Level.L1.targetSize(64));
        assertEquals(16, MyopiaLod.Level.L2.targetSize(64));
        assertEquals(8, MyopiaLod.Level.L3.targetSize(64));

        assertEquals(1024, MyopiaLod.Level.L0.targetSize(1024));
        assertEquals(512, MyopiaLod.Level.L1.targetSize(1024));
        assertEquals(256, MyopiaLod.Level.L2.targetSize(1024));
        assertEquals(128, MyopiaLod.Level.L3.targetSize(1024));

        // Never below MIN_TEXTURE_SIZE
        assertEquals(8, MyopiaLod.Level.L3.targetSize(16));
        assertEquals(8, MyopiaLod.Level.L3.targetSize(8));
        assertEquals(8, MyopiaLod.Level.L3.targetSize(4));
    }

    @Test
    void isFull() {
        assertTrue(MyopiaLod.Level.L0.isFull());
        assertFalse(MyopiaLod.Level.L1.isFull());
        assertFalse(MyopiaLod.Level.L2.isFull());
        assertFalse(MyopiaLod.Level.L3.isFull());
    }

    @Test
    void divisor() {
        assertEquals(1, MyopiaLod.Level.L0.divisor());
        assertEquals(2, MyopiaLod.Level.L1.divisor());
        assertEquals(4, MyopiaLod.Level.L2.divisor());
        assertEquals(8, MyopiaLod.Level.L3.divisor());
    }

    @Test
    void squaredDistanceEquivalent() {
        ModConfig config = ModConfig.get();
        double distance = 30.0;
        int base = 20;
        ModConfig.MyopiaMode mode = ModConfig.MyopiaMode.NORMAL;

        MyopiaLod.Level linear = MyopiaLod.levelFor(distance, base, mode);
        MyopiaLod.Level squared = MyopiaLod.levelForSquared(distance * distance, base, mode);

        assertEquals(linear, squared);
    }
}