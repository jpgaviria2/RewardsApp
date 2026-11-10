package com.bitcoinrewards.nfcdisplay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Decorative, finite, dependency-free effect. No customer data or input handling. */
public final class CheckInConfettiView extends View {
    private static final int[] COLORS = {0xFFF4EDDF, 0xFFF2C14E, 0xFF7BCEAB, 0xFFF18F73};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float progress;

    public CheckInConfettiView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void start() {
        stop();
        if (!isAttachedToWindow()) return;
        setVisibility(VISIBLE);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1_400L);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            progress = (Float) value.getAnimatedValue();
            if (progress >= 1f) stop();
            else invalidate();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.removeAllUpdateListeners();
            animator.cancel();
            animator = null;
        }
        progress = 0f;
        setVisibility(GONE);
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (animator == null) return;
        float density = getResources().getDisplayMetrics().density;
        paint.setAlpha(Math.round(255f * Math.min(1f, (1f - progress) * 3f)));
        for (int i = 0; i < 64; i++) {
            float x = getWidth() * ((i * 37 % 101) / 100f)
                + (float) Math.sin(i + progress * 6f) * 18f * density;
            float y = getHeight() * (progress * 1.3f - (i % 9) / 30f);
            paint.setColor(COLORS[i % COLORS.length]);
            paint.setAlpha(Math.round(255f * Math.min(1f, (1f - progress) * 3f)));
            canvas.save();
            canvas.rotate(i * 23f + progress * 240f, x, y);
            canvas.drawRect(x, y, x + 5f * density, y + 10f * density, paint);
            canvas.restore();
        }
    }
}
