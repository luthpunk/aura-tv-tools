package com.gathin.auratvtools;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages Safe Debloat (Detox) operations based on debloat.json rules.
 */
public class DebloatManager {

    public static class DebloatItem {
        public final String pkg;
        public final String label;
        public final String reason;
        public final boolean optional;
        public boolean selected = true;

        public DebloatItem(String pkg, String label, String reason, boolean optional) {
            this.pkg = pkg;
            this.label = label;
            this.reason = reason;
            this.optional = optional;
            this.selected = !optional; // Non-optional bloat is checked by default
        }
    }

    private static final String PREF_NAME = "aura_debloat_history";
    private static final String KEY_DISABLED_SET = "disabled_packages";

    private final List<DebloatItem> safeItems = new ArrayList<>();
    private final Set<String> protectedPackages = new HashSet<>();
    private final List<String> protectedParts = new ArrayList<>();
    private final SharedPreferences prefs;

    public DebloatManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadRules(context);
    }

    private void loadRules(Context context) {
        try (InputStream is = context.getAssets().open("debloat.json")) {
            byte[] bytes = new byte[is.available()];
            int read = is.read(bytes);
            if (read <= 0) return;

            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            JSONArray safeArr = root.optJSONArray("safe");
            if (safeArr != null) {
                for (int i = 0; i < safeArr.length(); i++) {
                    JSONObject obj = safeArr.getJSONObject(i);
                    safeItems.add(new DebloatItem(
                            obj.getString("pkg"),
                            obj.getString("label"),
                            obj.getString("reason"),
                            obj.optBoolean("optional", false)
                    ));
                }
            }

            JSONArray protArr = root.optJSONArray("protected");
            if (protArr != null) {
                for (int i = 0; i < protArr.length(); i++) {
                    protectedPackages.add(protArr.getString(i));
                }
            }

            JSONArray partsArr = root.optJSONArray("protectedParts");
            if (partsArr != null) {
                for (int i = 0; i < partsArr.length(); i++) {
                    protectedParts.add(partsArr.getString(i).toLowerCase());
                }
            }
        } catch (Exception ignored) {
        }
    }

    public List<DebloatItem> getSafeItems() {
        return safeItems;
    }

    public boolean isProtected(String pkg) {
        if (pkg == null) return true;
        if (protectedPackages.contains(pkg)) return true;
        String lower = pkg.toLowerCase();
        for (String part : protectedParts) {
            if (lower.contains(part)) return true;
        }
        return false;
    }

    public Set<String> getDisabledHistory() {
        return new HashSet<>(prefs.getStringSet(KEY_DISABLED_SET, new HashSet<>()));
    }

    public void addDisabledHistory(String pkg) {
        Set<String> set = getDisabledHistory();
        set.add(pkg);
        prefs.edit().putStringSet(KEY_DISABLED_SET, set).apply();
    }

    public void removeDisabledHistory(String pkg) {
        Set<String> set = getDisabledHistory();
        set.remove(pkg);
        prefs.edit().putStringSet(KEY_DISABLED_SET, set).apply();
    }
}
