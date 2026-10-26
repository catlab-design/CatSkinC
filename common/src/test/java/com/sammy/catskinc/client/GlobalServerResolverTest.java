package com.sammy.catskinc.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalServerResolverTest {
    @AfterEach
    void reset() {
        GlobalServerResolver.resetForTesting();
    }

    @Test
    void parsesGlobalIp() {
        assertEquals("storage-skin.catlabdesign.space",
                GlobalServerResolver.parseGlobalHost("{\"globalIp\": \" storage-skin.catlabdesign.space \"}"));
    }

    @Test
    void rejectsMissingOrInvalidGlobalIp() {
        assertNull(GlobalServerResolver.parseGlobalHost(null));
        assertNull(GlobalServerResolver.parseGlobalHost(""));
        assertNull(GlobalServerResolver.parseGlobalHost("404: Not Found"));
        assertNull(GlobalServerResolver.parseGlobalHost("{}"));
        assertNull(GlobalServerResolver.parseGlobalHost("{\"globalIp\": 42}"));
        assertNull(GlobalServerResolver.parseGlobalHost("{\"globalIp\": \"   \"}"));
        assertNull(GlobalServerResolver.parseGlobalHost("{\"globalIp\": \"bad host\"}"));
        assertNull(GlobalServerResolver.parseGlobalHost("[\"storage-skin.catlabdesign.space\"]"));
    }

    @Test
    void currentHostFallsBackBeforeAnyFetch() {
        assertEquals(GlobalServerResolver.FALLBACK_HOST, GlobalServerResolver.currentHost());
    }
}
