package com.bitcoinrewards.nfcdisplay;

import java.util.Collections;
import java.util.List;

final class CameraFacingSelector {
    static final int NO_CAMERA = -1;

    static final class CameraDescriptor {
        final int id;
        final int facing;

        CameraDescriptor(int id, int facing) {
            this.id = id;
            this.facing = facing;
        }
    }

    static final class Selection {
        final int cameraId;
        final int facing;
        final int alternateFacing;

        Selection(int cameraId, int facing, int alternateFacing) {
            this.cameraId = cameraId;
            this.facing = facing;
            this.alternateFacing = alternateFacing;
        }

        boolean hasCamera() {
            return cameraId != NO_CAMERA;
        }

        boolean canFlip() {
            return alternateFacing != NO_CAMERA;
        }
    }

    private CameraFacingSelector() { }

    static Selection select(List<CameraDescriptor> available, int requestedFacing) {
        List<CameraDescriptor> cameras = available == null ? Collections.emptyList() : available;
        CameraDescriptor selected = null;
        for (CameraDescriptor camera : cameras) {
            if (camera.facing == requestedFacing) {
                selected = camera;
                break;
            }
        }
        if (selected == null && !cameras.isEmpty()) selected = cameras.get(0);
        if (selected == null) return new Selection(NO_CAMERA, NO_CAMERA, NO_CAMERA);

        int alternateFacing = NO_CAMERA;
        for (CameraDescriptor camera : cameras) {
            if (camera.facing != selected.facing) {
                alternateFacing = camera.facing;
                break;
            }
        }
        return new Selection(selected.id, selected.facing, alternateFacing);
    }
}
