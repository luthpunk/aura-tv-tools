package com.gathin.auratvtools;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Animated Glowing Orb for Aura TV Home (KonnectTV Boost style).
 */
public class AuraOrbView extends View {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringSweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();
    private final Path sparkPath = new Path();

    private boolean active = false;
    private boolean busy = false;
    private float animProgress = 0f;
    private float spinAngle = 0f;

    private ValueAnimator spinAnimator;

    public AuraOrbView(Context context) {
        super(context);
        init();
    }

    private void init() {
        ringBgPaint.setStyle(Paint.Style.STROKE);
        ringSweepPaint.setStyle(Paint.Style.STROKE);
        ringSweepPaint.setStrokeCap(Paint.Cap.ROUND);
        coreBorderPaint.setStyle(Paint.Style.STROKE);
        sparkPaint.setStyle(Paint.Style.FILL);

        spinAnimator = ValueAnimator.ofFloat(0f, 360f);
        spinAnimator.setDuration(3000);
        spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
        spinAnimator.setInterpolator(new LinearInterpolator());
        spinAnimator.addUpdateListener(animation -> {
            spinAngle = (float) animation.getAnimatedValue();
            if (active || busy) {
                invalidate();
            }
        });
        spinAnimator.start();
    }

    public void setActive(boolean active) {
        this.active = active;
        this.animProgress = active ? 1f : 0f;
        invalidate();
    }

    public boolean isActive() {
        return active;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = dp(180);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - dp(8);
        if (radius <= 0) return;

        // 1. Soft radial glow behind core when active
        if (active) {
            int mintGlow = Color.argb((int) (60 * (0.8f + 0.2f * (float) Math.sin(Math.toRadians(spinAngle)))), 0x7C, 0xF0, 0xB0);
            glowPaint.setShader(new RadialGradient(cx, cy, radius, mintGlow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, glowPaint);
        }

        // 2. Background Ring Arc
        float strokeW = dp(7);
        ringBgPaint.setStrokeWidth(strokeW);
        ringBgPaint.setColor(Theme.border());

        float inset = strokeW / 2f + dp(2);
        arcRect.set(cx - radius + inset, cy - radius + inset, cx + radius - inset, cy + radius - inset);
        canvas.drawArc(arcRect, 0f, 360f, false, ringBgPaint);

        // 3. Glowing Gradient Ring Arc (Mint to Cyan)
        ringSweepPaint.setStrokeWidth(strokeW);
        int mint = Color.parseColor("#3FD6A0");
        int cyan = Color.parseColor("#9FF7FF");
        ringSweepPaint.setShader(new LinearGradient(arcRect.left, arcRect.top, arcRect.right, arcRect.bottom,
                mint, cyan, Shader.TileMode.CLAMP));

        if (busy) {
            canvas.drawArc(arcRect, spinAngle, 270f, false, ringSweepPaint);
        } else if (active) {
            canvas.drawArc(arcRect, -90f, 360f, false, ringSweepPaint);
        }

        // 4. Inner Core Circle
        float coreRadius = radius - dp(24);
        corePaint.setStyle(Paint.Style.FILL);
        corePaint.setColor(active ? Theme.orbOn() : Theme.raised());
        canvas.drawCircle(cx, cy, coreRadius, corePaint);

        coreBorderPaint.setStrokeWidth(dp(1.2f));
        coreBorderPaint.setColor(active ? Theme.accent() : Theme.raisedBorder());
        canvas.drawCircle(cx, cy, coreRadius, coreBorderPaint);

        // 5. Center 4-Pointed Sparkle / Star Glyph
        float sparkSize = dp(24);
        sparkPaint.setColor(active ? Theme.accent() : Theme.muted());

        canvas.save();
        canvas.translate(cx, cy);
        if (active) {
            canvas.rotate(spinAngle / 4f);
        }

        buildSparklePath(sparkPath, sparkSize);
        canvas.drawPath(sparkPath, sparkPaint);
        canvas.restore();
    }

    private void buildSparklePath(Path p, float s) {
        p.reset();
        float mid = s * 0.28f;
        p.moveTo(0, -s);
        p.quadTo(0, -mid, mid, 0);
        p.quadTo(0, mid, 0, s);
        p.quadTo(0, mid, -mid, 0);
        p.quadTo(0, -mid, 0, -s);
        p.close();

        // Cross accents
        p.moveTo(-mid * 0.7f, -mid * 0.7f);
        p.lineTo(mid * 0.7f, mid * 0.7f);
        p.moveTo(-mid * 0.7f, mid * 0.7f);
        p.lineTo(mid * 0.7f, -mid * 0.7f);
    }

    private float dp(float val) {
        return val * getResources().getDisplayMetrics().density;
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (spinAnimator != null) {
            spinAnimator.cancel();
        }
    }
}
