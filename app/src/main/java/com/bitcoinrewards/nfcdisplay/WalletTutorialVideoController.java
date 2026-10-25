package com.bitcoinrewards.nfcdisplay;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;

/** Lifecycle-safe, muted playback for the bundled customer tutorial. */
final class WalletTutorialVideoController implements TextureView.SurfaceTextureListener {
    private final Context context;
    private final TextureView view;
    private MediaPlayer player;
    private Surface surface;
    private boolean active;

    WalletTutorialVideoController(Context context, TextureView view) {
        this.context = context.getApplicationContext();
        this.view = view;
        view.setSurfaceTextureListener(this);
        if (view.isAvailable()) prepare(view.getSurfaceTexture());
    }

    void setActive(boolean value) {
        active = value;
        if (player == null && value && view.isAvailable()) prepare(view.getSurfaceTexture());
        if (player == null) return;
        if (value) {
            try { player.start(); } catch (IllegalStateException ignored) { }
        } else {
            try { player.pause(); } catch (IllegalStateException ignored) { }
        }
    }

    void release() {
        active = false;
        if (player != null) {
            player.setOnPreparedListener(null);
            player.release();
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    private void prepare(SurfaceTexture texture) {
        if (texture == null || player != null) return;
        surface = new Surface(texture);
        MediaPlayer next = new MediaPlayer();
        try (AssetFileDescriptor asset = context.getResources().openRawResourceFd(R.raw.wallet_rewards_tutorial)) {
            if (asset == null) throw new IOException("Missing tutorial video");
            next.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
            next.setSurface(surface);
            next.setLooping(true);
            next.setVolume(0f, 0f);
            player = next;
            next.setOnPreparedListener(value -> {
                if (active) value.start();
            });
            next.setOnErrorListener((value, what, extra) -> {
                release();
                return true;
            });
            next.prepareAsync();
        } catch (Exception e) {
            if (player == next) player = null;
            next.release();
            if (surface != null) {
                surface.release();
                surface = null;
            }
        }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        prepare(surfaceTexture);
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) { }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        release();
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) { }
}
