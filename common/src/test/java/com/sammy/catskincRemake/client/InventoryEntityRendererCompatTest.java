package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class InventoryEntityRendererCompatTest {
    @Test
    void doesNotDependOnReflectedInventoryMethods() {
        boolean usesReflectedMethodFields = Arrays.stream(InventoryEntityRendererCompat.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(Method.class::equals);

        assertFalse(usesReflectedMethodFields, "preview rendering should call InventoryScreen directly");
    }
}
