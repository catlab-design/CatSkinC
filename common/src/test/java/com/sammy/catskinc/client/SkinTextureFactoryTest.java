package com.sammy.catskinc.client;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SkinTextureFactoryTest {
    @Test
    void withTextureAndModelReturnsBaseWhenInputsAreNull() {
        // Test with null base - should return null
        Object result1 = SkinTextureFactory.withTextureAndModel(null, Identifiers.mod("test"), true);
        assertNull(result1);
        
        // Test with null texture - should return base unchanged
        Object mockBase = new Object();
        Object result2 = SkinTextureFactory.withTextureAndModel(mockBase, null, true);
        assertEquals(mockBase, result2);
        
        // Test with both null
        Object result3 = SkinTextureFactory.withTextureAndModel(null, null, true);
        assertNull(result3);
    }
    
    @Test
    void withTextureAndModelPreservesBaseWhenTextureIsNull() {
        Object mockBase = new Object();
        Object result = SkinTextureFactory.withTextureAndModel(mockBase, null, true);
        assertEquals(mockBase, result);
    }
}