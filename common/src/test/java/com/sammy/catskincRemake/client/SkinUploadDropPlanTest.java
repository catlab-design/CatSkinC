package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinUploadDropPlanTest {
    @Test
    void usesLibraryImportWhenPointerIsInsideLibraryPanel() {
        SkinUploadDropPlan.Mode mode = SkinUploadDropPlan.resolve(25, 25, 10, 10, 100, 100, 2);

        assertEquals(SkinUploadDropPlan.Mode.LIBRARY_IMPORT, mode);
    }

    @Test
    void usesLibraryImportWhenMoreThanThreePngsAreDropped() {
        SkinUploadDropPlan.Mode mode = SkinUploadDropPlan.resolve(150, 25, 10, 10, 100, 100, 4);

        assertEquals(SkinUploadDropPlan.Mode.LIBRARY_IMPORT, mode);
    }

    @Test
    void keepsPreviewSelectionForSmallDropsOutsideLibraryPanel() {
        SkinUploadDropPlan.Mode mode = SkinUploadDropPlan.resolve(150, 25, 10, 10, 100, 100, 3);

        assertEquals(SkinUploadDropPlan.Mode.PREVIEW_SELECTION, mode);
    }
}
