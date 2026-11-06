package com.gathin.auratvtools;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * KonnectTV Design System — Mono Matte Palette with Dark (default) & Light theme support.
 */
public final class Theme {
    private static boolean isLight = false;

    public static boolean isLight() {
        return isLight;
    }

    public static void setLight(boolean light) {
        isLight = light;
    }

    public static int bg() {
        return isLight ? Color.parseColor("#F4F5F7") : Color.parseColor("#0C0C0F");
    }

    public static int card() {
        return isLight ? Color.parseColor("#FFFFFF") : Color.parseColor("#16161B");
    }

    public static int border() {
        return isLight ? Color.parseColor("#E3E5EA") : Color.parseColor("#222229");
    }

    public static int raised() {
        return isLight ? Color.parseColor("#EEF0F3") : Color.parseColor("#1F1F26");
    }

    public static int raisedBorder() {
        return isLight ? Color.parseColor("#DADDE3") : Color.parseColor("#2A2A33");
    }

    public static int text() {
        return isLight ? Color.parseColor("#14151A") : Color.parseColor("#ECEEF4");
    }

    public static int muted() {
        return isLight ? Color.parseColor("#6B6F7B") : Color.parseColor("#8A8D99");
    }

    public static int accent() {
        return isLight ? Color.parseColor("#12B76A") : Color.parseColor("#7CF0B0");
    }

    public static int accentDim() {
        return isLight ? Color.parseColor("#2012B76A") : Color.parseColor("#257CF0B0");
    }

    public static int danger() {
        return isLight ? Color.parseColor("#E5484D") : Color.parseColor("#FF7A7A");
    }

    public static int dangerDim() {
        return isLight ? Color.parseColor("#20E5484D") : Color.parseColor("#25FF7A7A");
    }

    public static int orbOn() {
        return isLight ? Color.parseColor("#E3F8EE") : Color.parseColor("#10201A");
    }

    public static int glassTint() {
        return isLight ? Color.parseColor("#E6FFFFFF") : Color.parseColor("#E618181E");
    }

    public static int hairline() {
        return isLight ? Color.parseColor("#20000000") : Color.parseColor("#26FFFFFF");
    }

    // Helper to create rounded card drawable
    public static GradientDrawable cardDrawable(int cornerRadiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(card());
        gd.setCornerRadius(cornerRadiusPx);
        gd.setStroke(1, border());
        return gd;
    }

    public static GradientDrawable raisedDrawable(int cornerRadiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(raised());
        gd.setCornerRadius(cornerRadiusPx);
        gd.setStroke(1, raisedBorder());
        return gd;
    }

    public static GradientDrawable pillDrawable(int cornerRadiusPx, int fillColor, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(cornerRadiusPx);
        if (strokeColor != 0) {
            gd.setStroke(1, strokeColor);
        }
        return gd;
    }
}
