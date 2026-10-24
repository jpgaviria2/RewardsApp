package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CameraFacingSelectorTest {
    private static final int BACK = 0;
    private static final int FRONT = 1;

    private static CameraFacingSelector.CameraDescriptor camera(int id, int facing) {
        return new CameraFacingSelector.CameraDescriptor(id, facing);
    }

    @Test public void selectsRequestedFacingWithoutAssumingCameraId() {
        CameraFacingSelector.Selection selection = CameraFacingSelector.select(
            Arrays.asList(camera(7, BACK), camera(3, FRONT)), FRONT);

        assertEquals(3, selection.cameraId);
        assertEquals(FRONT, selection.facing);
        assertEquals(BACK, selection.alternateFacing);
        assertTrue(selection.canFlip());
    }

    @Test public void singleFrontCameraAtIdZeroIsSelectedAndFlipIsDisabled() {
        CameraFacingSelector.Selection selection = CameraFacingSelector.select(
            Collections.singletonList(camera(0, FRONT)), FRONT);

        assertEquals(0, selection.cameraId);
        assertEquals(FRONT, selection.facing);
        assertFalse(selection.canFlip());
    }

    @Test public void missingRequestedFacingFallsBackToAnyAvailableCamera() {
        CameraFacingSelector.Selection selection = CameraFacingSelector.select(
            Collections.singletonList(camera(5, BACK)), FRONT);

        assertEquals(5, selection.cameraId);
        assertEquals(BACK, selection.facing);
        assertFalse(selection.canFlip());
    }

    @Test public void selectionExposesAlternateFacingForFlip() {
        CameraFacingSelector.Selection front = CameraFacingSelector.select(
            Arrays.asList(camera(9, FRONT), camera(2, BACK)), FRONT);
        CameraFacingSelector.Selection back = CameraFacingSelector.select(
            Arrays.asList(camera(9, FRONT), camera(2, BACK)), front.alternateFacing);

        assertEquals(2, back.cameraId);
        assertEquals(BACK, back.facing);
        assertEquals(FRONT, back.alternateFacing);
    }

    @Test public void multipleCamerasWithOnlyOneFacingDoNotEnableFlip() {
        CameraFacingSelector.Selection selection = CameraFacingSelector.select(
            Arrays.asList(camera(4, BACK), camera(8, BACK)), FRONT);

        assertEquals(4, selection.cameraId);
        assertFalse(selection.canFlip());
    }

    @Test public void emptyOrNullCameraListsReportNoCamera() {
        CameraFacingSelector.Selection empty = CameraFacingSelector.select(Collections.emptyList(), FRONT);
        CameraFacingSelector.Selection absent = CameraFacingSelector.select(null, FRONT);

        assertFalse(empty.hasCamera());
        assertFalse(empty.canFlip());
        assertFalse(absent.hasCamera());
        assertEquals(CameraFacingSelector.NO_CAMERA, absent.cameraId);
    }
}
