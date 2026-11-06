package com.gathin.auratvtools;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.auratvtools.adb.AdbClient;
import com.gathin.auratvtools.mirror.MirrorActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Aura TV Tools — Reimagined with KonnectTV Design System:
 * - Mono Matte Palette with Dark (default) & Light theme switch
 * - 4 Navigation Tabs: Remote, Apps, Detox, Boost
 * - Circular D-Pad Zone + OK button
 * - Live Screen Mirroring Card strictly placed at the bottom of the Remote Tab
 * - Aura TV Home glowing orb replacing Glowlauncher with tailored options
 * - Detox safe debloat with debloat.json & undo history
 * - RAM & Storage stat meters, instant animation & screensaver switches, sleep timer
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "aura_tv_tools_prefs";
    private static final String KEY_THEME_LIGHT = "theme_is_light";
    private static final String KEY_SAVED_TVS = "saved_tvs_set";
    private static final String KEY_LAST_IP = "last_stb_ip";
    private static final int REQ_PICK_APK = 1001;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private DebloatManager debloatManager;

    private LinearLayout rootLayout;
    private FrameLayout contentFrame;
    private LinearLayout bottomTabBar;
    private View tvHeaderView;
    private TextView tvNameText;
    private TextView themeToggleBtn;

    // Screens
    private View connectScreen;
    private View remoteScreen;
    private View appsScreen;
    private View detoxScreen;
    private View boostScreen;

    private final TextView[] tabButtons = new TextView[4];
    private int currentTab = 0; // 0=Remote, 1=Apps, 2=Detox, 3=Boost

    // Connect Screen Views
    private LinearLayout savedTvsContainer;
    private LinearLayout discoveredTvsContainer;
    private TextView lookingText;

    // Apps Screen Views
    private EditText appSearchInput;
    private LinearLayout appListContainer;
    private TextView appCountBadge;
    private int appFilterMode = 0; // 0=Installed, 1=System, 2=Disabled
    private final List<AppInfo> allApps = new ArrayList<>();
    private final TextView[] filterChips = new TextView[3];

    // Detox Screen Views
    private LinearLayout detoxListContainer;
    private TextView detoxSafeCountText;
    private TextView btnDisableSelected;

    // Boost Screen Views
    private TextView ramFreeText;
    private TextView ramTotalText;
    private TextView storageFreeText;
    private TextView storageTotalText;
    private AuraOrbView auraOrbView;
    private TextView orbStatusPill;
    private Switch switchAnimations;
    private Switch switchScreensaver;
    private TextView sleepChoosedText;

    private AlertDialog loadingDialog;
    private TextView loadingMsgText;

    private static class AppInfo {
        String pkg;
        String label;
        boolean system;
        boolean disabled;

        AppInfo(String pkg, String label, boolean system, boolean disabled) {
            this.pkg = pkg;
            this.label = label;
            this.system = system;
            this.disabled = disabled;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isLight = prefs.getBoolean(KEY_THEME_LIGHT, false);
        Theme.setLight(isLight);

        debloatManager = new DebloatManager(this);

        buildMainUi();
        applyTheme();

        String lastIp = prefs.getString(KEY_LAST_IP, "");
        if (!lastIp.isEmpty()) {
            autoConnect(lastIp);
        } else {
            showConnectScreen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AdbClient.get().isConnected()) {
            showMainContent();
            refreshCurrentTab();
        } else {
            showConnectScreen();
        }
    }

    // =========================================================================
    // UI BUILDER
    // =========================================================================
    private void buildMainUi() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Theme.bg());

        // 1. Top TV Header (TV Name + Status Dot + Chevron | Theme Toggle | Menu)
        tvHeaderView = buildTvHeader();
        rootLayout.addView(tvHeaderView);

        // 2. Content Container (Switches between ConnectScreen and Tabs)
        contentFrame = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rootLayout.addView(contentFrame, contentLp);

        // Build all screens
        connectScreen = buildConnectScreen();
        remoteScreen = buildRemoteScreen();
        appsScreen = buildAppsScreen();
        detoxScreen = buildDetoxScreen();
        boostScreen = buildBoostScreen();

        contentFrame.addView(connectScreen);
        contentFrame.addView(remoteScreen);
        contentFrame.addView(appsScreen);
        contentFrame.addView(detoxScreen);
        contentFrame.addView(boostScreen);

        // 3. Floating Bottom Navigation Tab Bar (Remote, Apps, Detox, Boost)
        bottomTabBar = buildBottomTabBar();
        rootLayout.addView(bottomTabBar);

        setContentView(rootLayout);
    }

    private View buildTvHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(18), dp(10));

        // TV Switcher Pill: [🟢 TV Name ▾]
        LinearLayout tvBox = new LinearLayout(this);
        tvBox.setOrientation(LinearLayout.HORIZONTAL);
        tvBox.setGravity(Gravity.CENTER_VERTICAL);
        tvBox.setPadding(dp(6), dp(6), dp(10), dp(6));
        tvBox.setBackground(Theme.pillDrawable(dp(12), Color.TRANSPARENT, 0));
        applyPress(tvBox);
        tvBox.setOnClickListener(v -> showTvSwitchSheet());

        View dot = new View(this);
        dot.setBackground(Theme.pillDrawable(dp(4), Theme.accent(), 0));
        tvBox.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        tvNameText = new TextView(this);
        tvNameText.setText("Android TV");
        tvNameText.setTextColor(Theme.text());
        tvNameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        tvNameText.setTypeface(Typeface.DEFAULT_BOLD);
        tvNameText.setPadding(dp(8), 0, dp(4), 0);
        tvBox.addView(tvNameText);

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextColor(Theme.muted());
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        tvBox.addView(chevron);

        header.addView(tvBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Theme Toggle (Moon / Sun)
        themeToggleBtn = new TextView(this);
        themeToggleBtn.setText(Theme.isLight() ? "🌙" : "☀️");
        themeToggleBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        themeToggleBtn.setGravity(Gravity.CENTER);
        themeToggleBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
        applyPress(themeToggleBtn);
        themeToggleBtn.setOnClickListener(v -> toggleTheme());
        header.addView(themeToggleBtn);

        // Menu (•••)
        TextView menuBtn = new TextView(this);
        menuBtn.setText("•••");
        menuBtn.setTextColor(Theme.muted());
        menuBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        menuBtn.setTypeface(Typeface.DEFAULT_BOLD);
        menuBtn.setGravity(Gravity.CENTER);
        menuBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
        applyPress(menuBtn);
        menuBtn.setOnClickListener(v -> showOverflowMenu());
        header.addView(menuBtn);

        return header;
    }

    private LinearLayout buildBottomTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        barLp.setMargins(dp(16), 0, dp(16), dp(14));
        bar.setLayoutParams(barLp);

        bar.setBackground(Theme.pillDrawable(dp(32), Theme.glassTint(), Theme.hairline()));

        String[] icons = {"🎮", "📦", "🧹", "⚡"};
        String[] titles = {"Remote", "Apps", "Detox", "Boost"};

        for (int i = 0; i < 4; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, dp(6), 0, dp(6));
            applyPress(tab);

            TextView btn = new TextView(this);
            btn.setText(icons[i] + "\n" + titles[i]);
            btn.setGravity(Gravity.CENTER);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            btn.setTypeface(Typeface.DEFAULT_BOLD);
            tab.addView(btn);

            tabButtons[i] = btn;
            tab.setOnClickListener(v -> {
                vibrate(10);
                selectTab(index);
            });

            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            bar.addView(tab, tlp);
        }

        return bar;
    }

    private void selectTab(int index) {
        currentTab = index;
        remoteScreen.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        appsScreen.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        detoxScreen.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        boostScreen.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < 4; i++) {
            if (i == index) {
                tabButtons[i].setTextColor(Theme.accent());
            } else {
                tabButtons[i].setTextColor(Theme.muted());
            }
        }

        refreshCurrentTab();
    }

    private void showConnectScreen() {
        tvHeaderView.setVisibility(View.GONE);
        bottomTabBar.setVisibility(View.GONE);
        connectScreen.setVisibility(View.VISIBLE);
        remoteScreen.setVisibility(View.GONE);
        appsScreen.setVisibility(View.GONE);
        detoxScreen.setVisibility(View.GONE);
        boostScreen.setVisibility(View.GONE);
        refreshSavedTvsUi();
        startAutoDiscovery();
    }

    private void showMainContent() {
        tvHeaderView.setVisibility(View.VISIBLE);
        bottomTabBar.setVisibility(View.VISIBLE);
        connectScreen.setVisibility(View.GONE);

        String host = AdbClient.get().getConnectedHost();
        tvNameText.setText(host.isEmpty() ? "Android TV" : "TV (" + host + ")");
        selectTab(currentTab);
    }

    private void refreshCurrentTab() {
        if (!AdbClient.get().isConnected()) return;
        if (currentTab == 1 && allApps.isEmpty()) {
            loadAppsFromTv();
        } else if (currentTab == 2) {
            refreshDetoxUi();
        } else if (currentTab == 3) {
            refreshBoostUi();
        }
    }

    // =========================================================================
    // SCREEN 1: CONNECT SCREEN
    // =========================================================================
    private View buildConnectScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(24), dp(16), dp(24));

        TextView title = new TextView(this);
        title.setText("Hubungkan ke TV");
        title.setTextColor(Theme.text());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(14));
        col.addView(title);

        // Saved TVs Section
        savedTvsContainer = new LinearLayout(this);
        savedTvsContainer.setOrientation(LinearLayout.VERTICAL);
        col.addView(savedTvsContainer);

        // Found on Wi-Fi Section
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("DITEMUKAN DI WI-FI");
        sectionTitle.setTextColor(Theme.muted());
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sectionTitle.setPadding(0, dp(14), 0, dp(8));
        col.addView(sectionTitle);

        discoveredTvsContainer = new LinearLayout(this);
        discoveredTvsContainer.setOrientation(LinearLayout.VERTICAL);
        col.addView(discoveredTvsContainer);

        lookingText = new TextView(this);
        lookingText.setText("Mencari Android TV di jaringan Wi-Fi...");
        lookingText.setTextColor(Theme.muted());
        lookingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        lookingText.setPadding(0, dp(8), 0, dp(14));
        col.addView(lookingText);

        // Manual IP Button
        TextView manualBtn = createButton("＋ Masukkan Alamat IP Manual", Theme.raised(), Theme.text(), () -> showManualIpDialog());
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(14);
        col.addView(manualBtn, mlp);

        scroll.addView(col);
        return scroll;
    }

    private void refreshSavedTvsUi() {
        savedTvsContainer.removeAllViews();
        Set<String> saved = prefs.getStringSet(KEY_SAVED_TVS, new HashSet<>());
        if (saved.isEmpty()) return;

        TextView header = new TextView(this);
        header.setText("TV TERSIMPAN");
        header.setTextColor(Theme.muted());
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, dp(4), 0, dp(8));
        savedTvsContainer.addView(header);

        for (String ip : saved) {
            LinearLayout card = createCard();
            applyPress(card);
            card.setOnClickListener(v -> autoConnect(ip));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView icon = new TextView(this);
            icon.setText("📺");
            icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            icon.setPadding(0, 0, dp(12), 0);
            row.addView(icon);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this);
            name.setText("Android TV (" + ip + ")");
            name.setTextColor(Theme.text());
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            info.addView(name);

            TextView sub = new TextView(this);
            sub.setText(ip + " · Terakhir terhubung");
            sub.setTextColor(Theme.muted());
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            info.addView(sub);
            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView forgetBtn = new TextView(this);
            forgetBtn.setText("Lupakan");
            forgetBtn.setTextColor(Theme.danger());
            forgetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            forgetBtn.setPadding(dp(8), dp(4), dp(4), dp(4));
            forgetBtn.setOnClickListener(v -> {
                saved.remove(ip);
                prefs.edit().putStringSet(KEY_SAVED_TVS, saved).apply();
                refreshSavedTvsUi();
            });
            row.addView(forgetBtn);

            card.addView(row);
            savedTvsContainer.addView(card, cardLp());
        }
    }

    private void startAutoDiscovery() {
        lookingText.setVisibility(View.VISIBLE);
        discoveredTvsContainer.removeAllViews();
        AdbClient.scanSubnet(this, new AdbClient.ScanCallback() {
            @Override
            public void onDeviceFound(String ip) {
                uiHandler.post(() -> addDiscoveredTv(ip));
            }

            @Override
            public void onScanFinished(List<String> foundIps) {
                uiHandler.post(() -> {
                    lookingText.setVisibility(foundIps.isEmpty() ? View.VISIBLE : View.GONE);
                    if (foundIps.isEmpty()) {
                        lookingText.setText("Tidak ada TV yang terdeteksi otomatis. Masukkan IP secara manual.");
                    }
                });
            }
        });
    }

    private void addDiscoveredTv(String ip) {
        lookingText.setVisibility(View.GONE);
        LinearLayout card = createCard();
        applyPress(card);
        card.setOnClickListener(v -> autoConnect(ip));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = new TextView(this);
        icon.setText("📺");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        icon.setPadding(0, 0, dp(12), 0);
        row.addView(icon);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText("Android TV");
        name.setTextColor(Theme.text());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(name);

        TextView sub = new TextView(this);
        sub.setText(ip + " · Port 5555 siap");
        sub.setTextColor(Theme.accent());
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        info.addView(sub);
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView connectText = new TextView(this);
        connectText.setText("Hubungkan ›");
        connectText.setTextColor(Theme.accent());
        connectText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        connectText.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(connectText);

        card.addView(row);
        discoveredTvsContainer.addView(card, cardLp());
    }

    private void showManualIpDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Masukkan IP TV");

        final EditText input = new EditText(this);
        input.setHint("192.168.1.xxx");
        input.setText(prefs.getString(KEY_LAST_IP, ""));
        input.setSingleLine(true);
        input.setPadding(dp(20), dp(16), dp(20), dp(16));
        b.setView(input);

        b.setPositiveButton("Hubungkan", (dialog, which) -> {
            String ip = input.getText().toString().trim();
            if (!ip.isEmpty()) autoConnect(ip);
        });
        b.setNegativeButton("Batal", null);
        b.show();
    }

    private void autoConnect(String ip) {
        showLoading("Menghubungkan ke " + ip,
                "Menyambung ke port 5555...\nJika muncul prompt di layar TV, centang 'Selalu izinkan' dan pilih 'Izinkan'.");
        new Thread(() -> {
            try {
                AdbClient.get().connect(this, ip, 5555);
                prefs.edit().putString(KEY_LAST_IP, ip).apply();

                Set<String> saved = new HashSet<>(prefs.getStringSet(KEY_SAVED_TVS, new HashSet<>()));
                saved.add(ip);
                prefs.edit().putStringSet(KEY_SAVED_TVS, saved).apply();

                uiHandler.post(() -> {
                    hideLoading();
                    showMainContent();
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    hideLoading();
                    Toast.makeText(this, "Gagal terhubung: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // =========================================================================
    // SCREEN 2: REMOTE SCREEN (CIRCULAR DPAD, BUTTON ROWS, MIRRORING AT BOTTOM)
    // =========================================================================
    private View buildRemoteScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(10), dp(16), dp(80)); // 80dp bottom clearance for tab bar

        // 1. Circular D-Pad Zone (KonnectTV Signature D-Pad)
        FrameLayout dpadZone = new FrameLayout(this);
        LinearLayout.LayoutParams dpadZoneLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(264));
        dpadZoneLp.gravity = Gravity.CENTER;
        dpadZone.setLayoutParams(dpadZoneLp);

        // Outer Ring Circle
        FrameLayout dpadCircle = new FrameLayout(this);
        FrameLayout.LayoutParams circleLp = new FrameLayout.LayoutParams(dp(260), dp(260));
        circleLp.gravity = Gravity.CENTER;
        dpadCircle.setLayoutParams(circleLp);
        dpadCircle.setBackground(Theme.pillDrawable(dp(130), Theme.card(), Theme.border()));

        // Arrow Buttons: Up, Down, Left, Right
        TextView btnUp = createDpadArrow("▲", KeyEvent.KEYCODE_DPAD_UP);
        FrameLayout.LayoutParams upLp = new FrameLayout.LayoutParams(dp(86), dp(80));
        upLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        dpadCircle.addView(btnUp, upLp);

        TextView btnDown = createDpadArrow("▼", KeyEvent.KEYCODE_DPAD_DOWN);
        FrameLayout.LayoutParams downLp = new FrameLayout.LayoutParams(dp(86), dp(80));
        downLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dpadCircle.addView(btnDown, downLp);

        TextView btnLeft = createDpadArrow("◀", KeyEvent.KEYCODE_DPAD_LEFT);
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(dp(80), dp(86));
        leftLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        dpadCircle.addView(btnLeft, leftLp);

        TextView btnRight = createDpadArrow("▶", KeyEvent.KEYCODE_DPAD_RIGHT);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(dp(80), dp(86));
        rightLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        dpadCircle.addView(btnRight, rightLp);

        // Center OK Circle
        TextView btnOk = new TextView(this);
        btnOk.setText("OK");
        btnOk.setTextColor(Theme.accent());
        btnOk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        btnOk.setTypeface(Typeface.DEFAULT_BOLD);
        btnOk.setGravity(Gravity.CENTER);
        btnOk.setBackground(Theme.pillDrawable(dp(48), Theme.raised(), Theme.raisedBorder()));
        applyPress(btnOk);
        btnOk.setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_DPAD_CENTER));

        FrameLayout.LayoutParams okLp = new FrameLayout.LayoutParams(dp(96), dp(96));
        okLp.gravity = Gravity.CENTER;
        dpadCircle.addView(btnOk, okLp);

        dpadZone.addView(dpadCircle);
        col.addView(dpadZone);

        // Space
        col.addView(createSpacer(dp(16)));

        // 2. Key Row 1: Back, Home, Type
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(createRemoteKey("Back", "◁", () -> sendKey(KeyEvent.KEYCODE_BACK)), rowLp());
        r1.addView(createRemoteKey("Home", "⌂", () -> sendKey(KeyEvent.KEYCODE_HOME)), rowLp());
        r1.addView(createRemoteKey("Type", "⌨", this::showTypeDialog), rowLp());
        col.addView(r1);

        col.addView(createSpacer(dp(10)));

        // 3. Key Row 2: Vol −, Mute, Vol +, Power
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(createRemoteKey("Vol −", "🔉", () -> sendKey(KeyEvent.KEYCODE_VOLUME_DOWN)), rowLp());
        r2.addView(createRemoteKey("Mute", "🔇", () -> sendKey(KeyEvent.KEYCODE_VOLUME_MUTE)), rowLp());
        r2.addView(createRemoteKey("Vol +", "🔊", () -> sendKey(KeyEvent.KEYCODE_VOLUME_UP)), rowLp());
        r2.addView(createRemoteKey("Power", "⏻", () -> sendKey(KeyEvent.KEYCODE_POWER), Theme.danger()), rowLp());
        col.addView(r2);

        col.addView(createSpacer(dp(10)));

        // 4. Key Row 3: Play / Pause, Menu, Settings
        LinearLayout r3 = new LinearLayout(this);
        r3.setOrientation(LinearLayout.HORIZONTAL);
        r3.addView(createRemoteKey("Play / Pause", "⏯", () -> sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)), new LinearLayout.LayoutParams(0, dp(66), 1.5f));
        r3.addView(createSpacer(dp(10)), new LinearLayout.LayoutParams(dp(10), dp(1)));
        r3.addView(createRemoteKey("Menu", "☰", () -> sendKey(KeyEvent.KEYCODE_MENU)), rowLp());
        r3.addView(createSpacer(dp(10)), new LinearLayout.LayoutParams(dp(10), dp(1)));
        r3.addView(createRemoteKey("Settings", "⚙", () -> sendKey(KeyEvent.KEYCODE_SETTINGS)), rowLp());
        col.addView(r3);

        col.addView(createSpacer(dp(14)));

        // =====================================================================
        // 5. USER REQUIREMENT: TOMBOL MIRRORING DI PALING BAWAH TAB REMOTE
        // =====================================================================
        LinearLayout mirrorCard = createCard();
        applyPress(mirrorCard);
        mirrorCard.setOnClickListener(v -> {
            vibrate(10);
            if (!AdbClient.get().isConnected()) {
                Toast.makeText(this, "Belum terhubung ke TV!", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, MirrorActivity.class));
        });

        LinearLayout mirrorRow = new LinearLayout(this);
        mirrorRow.setOrientation(LinearLayout.HORIZONTAL);
        mirrorRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView mirrorIcon = new TextView(this);
        mirrorIcon.setText("📺");
        mirrorIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        mirrorIcon.setPadding(0, 0, dp(14), 0);
        mirrorRow.addView(mirrorIcon);

        LinearLayout mirrorTexts = new LinearLayout(this);
        mirrorTexts.setOrientation(LinearLayout.VERTICAL);

        TextView mirrorTitle = new TextView(this);
        mirrorTitle.setText("Live Screen Mirroring");
        mirrorTitle.setTextColor(Theme.text());
        mirrorTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        mirrorTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mirrorTexts.addView(mirrorTitle);

        TextView mirrorSubtitle = new TextView(this);
        mirrorSubtitle.setText("Tampilkan layar TV dan kontrol langsung dari HP");
        mirrorSubtitle.setTextColor(Theme.muted());
        mirrorSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        mirrorTexts.addView(mirrorSubtitle);

        mirrorRow.addView(mirrorTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView mirrorArrow = new TextView(this);
        mirrorArrow.setText("➔");
        mirrorArrow.setTextColor(Theme.accent());
        mirrorArrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        mirrorArrow.setTypeface(Typeface.DEFAULT_BOLD);
        mirrorRow.addView(mirrorArrow);

        mirrorCard.addView(mirrorRow);
        col.addView(mirrorCard, cardLp());

        scroll.addView(col);
        return scroll;
    }

    private TextView createDpadArrow(String symbol, int keyCode) {
        TextView tv = new TextView(this);
        tv.setText(symbol);
        tv.setTextColor(Theme.muted());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        tv.setGravity(Gravity.CENTER);
        applyPress(tv);
        tv.setOnClickListener(v -> sendKey(keyCode));
        return tv;
    }

    private View createRemoteKey(String label, String icon, Runnable action) {
        return createRemoteKey(label, icon, action, Theme.text());
    }

    private View createRemoteKey(String label, String icon, Runnable action, int iconColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(Theme.cardDrawable(dp(18)));
        applyPress(box);
        box.setOnClickListener(v -> {
            vibrate(10);
            if (action != null) action.run();
        });

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextColor(iconColor);
        iconTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
        box.addView(iconTv);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(Theme.muted());
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        labelTv.setPadding(0, dp(4), 0, 0);
        box.addView(labelTv);

        box.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        return box;
    }

    private void showTypeDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Ketik Teks ke TV");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        TextView hint = new TextView(this);
        hint.setText("Pilih kolom pencarian di TV terlebih dahulu.");
        hint.setTextColor(Theme.muted());
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        hint.setPadding(0, 0, 0, dp(10));
        layout.addView(hint);

        final EditText input = new EditText(this);
        input.setHint("Teks yang ingin dikirim...");
        input.setSingleLine(true);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(Theme.raisedDrawable(dp(12)));
        layout.addView(input);

        b.setView(layout);
        b.setPositiveButton("Kirim", (dialog, which) -> {
            String text = input.getText().toString();
            if (!text.isEmpty()) {
                AdbClient.get().sendText(text);
            }
        });
        b.setNeutralButton("⌫ Hapus", (dialog, which) -> {
            sendKey(KeyEvent.KEYCODE_DEL);
        });
        b.setNegativeButton("Tutup", null);
        b.show();
    }

    // =========================================================================
    // SCREEN 3: APPS SCREEN
    // =========================================================================
    private View buildAppsScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(80));

        // Header: Apps + Count Badge
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Apps");
        title.setTextColor(Theme.text());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title);

        appCountBadge = new TextView(this);
        appCountBadge.setText("0");
        appCountBadge.setTextColor(Theme.muted());
        appCountBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        appCountBadge.setPadding(dp(8), 0, 0, 0);
        header.addView(appCountBadge);

        col.addView(header);

        // Search Field
        appSearchInput = new EditText(this);
        appSearchInput.setHint("Cari aplikasi di TV...");
        appSearchInput.setHintTextColor(Theme.muted());
        appSearchInput.setTextColor(Theme.text());
        appSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        appSearchInput.setSingleLine(true);
        appSearchInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        appSearchInput.setBackground(Theme.raisedDrawable(dp(14)));
        appSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderAppsList();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = dp(12);
        col.addView(appSearchInput, searchLp);

        // Filter Chips (Installed, System, Disabled)
        LinearLayout chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(0, dp(10), 0, dp(10));

        String[] chipTitles = {"Installed", "System", "Disabled"};
        for (int i = 0; i < 3; i++) {
            final int filterIdx = i;
            TextView chip = new TextView(this);
            chip.setText(chipTitles[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            applyPress(chip);
            chip.setOnClickListener(v -> {
                appFilterMode = filterIdx;
                updateFilterChipsUi();
                renderAppsList();
            });
            filterChips[i] = chip;

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMarginEnd(dp(8));
            chipsRow.addView(chip, clp);
        }
        col.addView(chipsRow);
        updateFilterChipsUi();

        // App List Container
        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        col.addView(appListContainer);

        // Install APK from Phone Button
        TextView installBtn = createButton("＋ Install APK dari HP", Theme.raised(), Theme.text(), this::openApkFilePicker);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(14);
        col.addView(installBtn, ilp);

        scroll.addView(col);
        return scroll;
    }

    private void updateFilterChipsUi() {
        for (int i = 0; i < 3; i++) {
            if (i == appFilterMode) {
                filterChips[i].setTextColor(Theme.accent());
                filterChips[i].setBackground(Theme.pillDrawable(dp(14), Theme.accentDim(), Theme.accent()));
            } else {
                filterChips[i].setTextColor(Theme.muted());
                filterChips[i].setBackground(Theme.pillDrawable(dp(14), Theme.card(), Theme.border()));
            }
        }
    }

    private void loadAppsFromTv() {
        allApps.clear();
        new Thread(() -> {
            try {
                String disabledOut = AdbClient.get().execCommand("pm list packages -d");
                Set<String> disabledPkgs = new HashSet<>();
                for (String line : disabledOut.split("\n")) {
                    if (line.startsWith("package:")) {
                        disabledPkgs.add(line.replace("package:", "").trim());
                    }
                }

                String thirdPartyOut = AdbClient.get().execCommand("pm list packages -3");
                Set<String> thirdPartyPkgs = new HashSet<>();
                for (String line : thirdPartyOut.split("\n")) {
                    if (line.startsWith("package:")) {
                        thirdPartyPkgs.add(line.replace("package:", "").trim());
                    }
                }

                String allOut = AdbClient.get().execCommand("pm list packages");
                for (String line : allOut.split("\n")) {
                    if (line.startsWith("package:")) {
                        String pkg = line.replace("package:", "").trim();
                        if (pkg.isEmpty()) continue;
                        boolean isSys = !thirdPartyPkgs.contains(pkg);
                        boolean isDis = disabledPkgs.contains(pkg);
                        String label = formatAppLabel(pkg);
                        allApps.add(new AppInfo(pkg, label, isSys, isDis));
                    }
                }

                Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));

                uiHandler.post(() -> {
                    appCountBadge.setText(String.valueOf(allApps.size()));
                    renderAppsList();
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    private String formatAppLabel(String pkg) {
        if ("com.gathin.tvhome".equals(pkg)) return "Aura TV Home";
        if ("com.netflix.ninja".equals(pkg)) return "Netflix";
        if ("com.google.android.youtube.tv".equals(pkg)) return "YouTube";
        if ("com.spotify.tv.android".equals(pkg)) return "Spotify";
        if ("com.amazon.amazonvideo.livingroom".equals(pkg)) return "Prime Video";
        int lastDot = pkg.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < pkg.length() - 1) {
            String sub = pkg.substring(lastDot + 1);
            return Character.toUpperCase(sub.charAt(0)) + sub.substring(1);
        }
        return pkg;
    }

    private void renderAppsList() {
        appListContainer.removeAllViews();
        String query = appSearchInput.getText().toString().trim().toLowerCase();

        for (AppInfo app : allApps) {
            if (appFilterMode == 0 && app.system) continue;
            if (appFilterMode == 1 && !app.system) continue;
            if (appFilterMode == 2 && !app.disabled) continue;

            if (!query.isEmpty() && !app.label.toLowerCase().contains(query) && !app.pkg.toLowerCase().contains(query)) {
                continue;
            }

            LinearLayout card = createCard();
            applyPress(card);
            card.setOnClickListener(v -> showAppDetailSheet(app));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            // Monogram Icon
            TextView mono = new TextView(this);
            mono.setText(app.label.substring(0, 1).toUpperCase());
            mono.setTextColor(Theme.accent());
            mono.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            mono.setTypeface(Typeface.DEFAULT_BOLD);
            mono.setGravity(Gravity.CENTER);
            mono.setBackground(Theme.pillDrawable(dp(18), Theme.raised(), Theme.raisedBorder()));
            row.addView(mono, new LinearLayout.LayoutParams(dp(36), dp(36)));

            // App Label & Package
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, 0, 0);

            TextView lText = new TextView(this);
            lText.setText(app.label);
            lText.setTextColor(app.disabled ? Theme.muted() : Theme.text());
            lText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            lText.setTypeface(Typeface.DEFAULT_BOLD);
            info.addView(lText);

            TextView pText = new TextView(this);
            pText.setText(app.pkg + (app.disabled ? " · dinonaktifkan" : ""));
            pText.setTextColor(Theme.muted());
            pText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            info.addView(pText);

            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView more = new TextView(this);
            more.setText("⋯");
            more.setTextColor(Theme.muted());
            more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            row.addView(more);

            card.addView(row);
            appListContainer.addView(card, cardLp());
        }
    }

    private void showAppDetailSheet(AppInfo app) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(app.label);
        b.setMessage(app.pkg);

        String[] options;
        if (!app.system) {
            options = new String[]{"Buka Aplikasi", "Paksa Berhenti (Force Stop)", "Hapus Data",
                    app.disabled ? "Aktifkan Kembali" : "Nonaktifkan", "Uninstall"};
        } else {
            options = new String[]{"Buka Aplikasi", "Paksa Berhenti (Force Stop)", "Hapus Data",
                    app.disabled ? "Aktifkan Kembali" : "Nonaktifkan"};
        }

        b.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Buka
                    sendFastInput("monkey -p " + app.pkg + " -c android.intent.category.LAUNCHER 1");
                    Toast.makeText(this, "Membuka " + app.label, Toast.LENGTH_SHORT).show();
                    break;
                case 1: // Force stop
                    sendFastInput("am force-stop " + app.pkg);
                    Toast.makeText(this, "Paksa berhenti: " + app.label, Toast.LENGTH_SHORT).show();
                    break;
                case 2: // Clear data
                    sendFastInput("pm clear " + app.pkg);
                    Toast.makeText(this, "Data dihapus: " + app.label, Toast.LENGTH_SHORT).show();
                    break;
                case 3: // Toggle disabled
                    if (app.disabled) {
                        sendFastInput("pm enable " + app.pkg);
                        app.disabled = false;
                        debloatManager.removeDisabledHistory(app.pkg);
                        Toast.makeText(this, app.label + " diaktifkan", Toast.LENGTH_SHORT).show();
                    } else {
                        if (debloatManager.isProtected(app.pkg)) {
                            Toast.makeText(this, "Aplikasi ini dibutuhkan sistem TV dan tidak boleh dimatikan.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        sendFastInput("pm disable-user " + app.pkg);
                        app.disabled = true;
                        debloatManager.addDisabledHistory(app.pkg);
                        Toast.makeText(this, app.label + " dinonaktifkan", Toast.LENGTH_SHORT).show();
                    }
                    renderAppsList();
                    break;
                case 4: // Uninstall
                    new Thread(() -> {
                        try {
                            AdbClient.get().execCommand("pm uninstall " + app.pkg);
                            uiHandler.post(() -> {
                                Toast.makeText(this, app.label + " berhasil di-uninstall", Toast.LENGTH_SHORT).show();
                                allApps.remove(app);
                                renderAppsList();
                            });
                        } catch (Exception ignored) {}
                    }).start();
                    break;
            }
        });
        b.setNegativeButton("Tutup", null);
        b.show();
    }

    // =========================================================================
    // SCREEN 4: DETOX SCREEN (SAFE DEBLOAT & HISTORY)
    // =========================================================================
    private View buildDetoxScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(80));

        // Header: Detox + History Button
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Detox");
        title.setTextColor(Theme.text());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView historyBtn = new TextView(this);
        historyBtn.setText("⟲ Riwayat");
        historyBtn.setTextColor(Theme.accent());
        historyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        historyBtn.setTypeface(Typeface.DEFAULT_BOLD);
        historyBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        applyPress(historyBtn);
        historyBtn.setOnClickListener(v -> showDetoxHistoryDialog());
        header.addView(historyBtn);

        col.addView(header);

        // Safe debloat summary card
        LinearLayout summaryCard = createCard();
        detoxSafeCountText = new TextView(this);
        detoxSafeCountText.setText("Memeriksa bloatware TV...");
        detoxSafeCountText.setTextColor(Theme.accent());
        detoxSafeCountText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        detoxSafeCountText.setTypeface(Typeface.DEFAULT_BOLD);
        summaryCard.addView(detoxSafeCountText);

        TextView summarySub = new TextView(this);
        summarySub.setText("Dinonaktifkan via pm disable-user, tidak pernah dihapus. Bisa dibatalkan kapan saja.");
        summarySub.setTextColor(Theme.muted());
        summarySub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        summarySub.setPadding(0, dp(4), 0, 0);
        summaryCard.addView(summarySub);

        col.addView(summaryCard, cardLp());

        // Detox Safe List
        detoxListContainer = new LinearLayout(this);
        detoxListContainer.setOrientation(LinearLayout.VERTICAL);
        col.addView(detoxListContainer);

        // Disable Action Button
        btnDisableSelected = createButton("Nonaktifkan Aplikasi Terpilih", Theme.raised(), Theme.text(), this::executeDetoxDisable);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(14);
        col.addView(btnDisableSelected, dlp);

        scroll.addView(col);
        return scroll;
    }

    private void refreshDetoxUi() {
        detoxListContainer.removeAllViews();
        List<DebloatManager.DebloatItem> safeItems = debloatManager.getSafeItems();
        detoxSafeCountText.setText(safeItems.size() + " aplikasi aman dinonaktifkan");

        for (DebloatManager.DebloatItem item : safeItems) {
            LinearLayout card = createCard();

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);

            TextView label = new TextView(this);
            label.setText(item.label);
            label.setTextColor(Theme.text());
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            info.addView(label);

            TextView reason = new TextView(this);
            reason.setText(item.reason);
            reason.setTextColor(Theme.muted());
            reason.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            info.addView(reason);

            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            CheckBox cb = new CheckBox(this);
            cb.setChecked(item.selected);
            cb.setOnCheckedChangeListener((btn, isChecked) -> item.selected = isChecked);
            row.addView(cb);

            card.addView(row);
            detoxListContainer.addView(card, cardLp());
        }
    }

    private void executeDetoxDisable() {
        List<DebloatManager.DebloatItem> toDisable = new ArrayList<>();
        for (DebloatManager.DebloatItem item : debloatManager.getSafeItems()) {
            if (item.selected) toDisable.add(item);
        }

        if (toDisable.isEmpty()) {
            Toast.makeText(this, "Tidak ada aplikasi yang dipilih.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading("Menonaktifkan Bloatware", "Memproses " + toDisable.size() + " aplikasi...");
        new Thread(() -> {
            for (DebloatManager.DebloatItem item : toDisable) {
                try {
                    AdbClient.get().execCommand("pm disable-user " + item.pkg);
                    debloatManager.addDisabledHistory(item.pkg);
                } catch (Exception ignored) {}
            }
            uiHandler.post(() -> {
                hideLoading();
                Toast.makeText(this, toDisable.size() + " aplikasi berhasil dinonaktifkan!", Toast.LENGTH_LONG).show();
                refreshDetoxUi();
            });
        }).start();
    }

    private void showDetoxHistoryDialog() {
        Set<String> history = debloatManager.getDisabledHistory();
        if (history.isEmpty()) {
            Toast.makeText(this, "Belum ada riwayat aplikasi yang dinonaktifkan.", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<String> list = new ArrayList<>(history);
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            labels[i] = list.get(i);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Riwayat Detox (" + list.size() + ")");
        b.setItems(labels, (dialog, which) -> {
            String pkg = list.get(which);
            sendFastInput("pm enable " + pkg);
            debloatManager.removeDisabledHistory(pkg);
            Toast.makeText(this, pkg + " diaktifkan kembali!", Toast.LENGTH_SHORT).show();
        });
        b.setPositiveButton("Pulihkan Semua", (dialog, which) -> {
            for (String pkg : list) {
                sendFastInput("pm enable " + pkg);
                debloatManager.removeDisabledHistory(pkg);
            }
            Toast.makeText(this, "Semua aplikasi dipulihkan!", Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("Tutup", null);
        b.show();
    }

    // =========================================================================
    // SCREEN 5: BOOST SCREEN (RAM/STORAGE STATS, AURA TV HOME ORB, TWEAKS)
    // =========================================================================
    private View buildBoostScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(80));

        TextView title = new TextView(this);
        title.setText("Boost");
        title.setTextColor(Theme.text());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);

        // 1. RAM & Storage Stat Cards Row
        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setPadding(0, dp(12), 0, dp(10));

        // RAM Card
        LinearLayout ramCard = createCard();
        TextView ramTitle = new TextView(this);
        ramTitle.setText("RAM");
        ramTitle.setTextColor(Theme.muted());
        ramTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        ramTitle.setTypeface(Typeface.DEFAULT_BOLD);
        ramCard.addView(ramTitle);

        ramFreeText = new TextView(this);
        ramFreeText.setText("Free ...");
        ramFreeText.setTextColor(Theme.accent());
        ramFreeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        ramFreeText.setTypeface(Typeface.DEFAULT_BOLD);
        ramCard.addView(ramFreeText);

        ramTotalText = new TextView(this);
        ramTotalText.setText("Total ...");
        ramTotalText.setTextColor(Theme.muted());
        ramTotalText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        ramCard.addView(ramTotalText);

        statsRow.addView(ramCard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statsRow.addView(createSpacer(dp(10)), new LinearLayout.LayoutParams(dp(10), dp(1)));

        // Storage Card
        LinearLayout storageCard = createCard();
        TextView storageTitle = new TextView(this);
        storageTitle.setText("Storage");
        storageTitle.setTextColor(Theme.muted());
        storageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        storageTitle.setTypeface(Typeface.DEFAULT_BOLD);
        storageCard.addView(storageTitle);

        storageFreeText = new TextView(this);
        storageFreeText.setText("Free ...");
        storageFreeText.setTextColor(Theme.accent());
        storageFreeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        storageFreeText.setTypeface(Typeface.DEFAULT_BOLD);
        storageCard.addView(storageFreeText);

        storageTotalText = new TextView(this);
        storageTotalText.setText("Total ...");
        storageTotalText.setTextColor(Theme.muted());
        storageTotalText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        storageCard.addView(storageTotalText);

        statsRow.addView(storageCard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(statsRow);

        // =====================================================================
        // 2. AURA TV HOME GLOWING ORB (REPLACING GLOWLAUNCHER)
        // =====================================================================
        LinearLayout orbCard = createCard();
        orbCard.setGravity(Gravity.CENTER_HORIZONTAL);
        applyPress(orbCard);
        orbCard.setOnClickListener(v -> showAuraTvHomeSheet());

        auraOrbView = new AuraOrbView(this);
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(dp(180), dp(180));
        orbLp.gravity = Gravity.CENTER_HORIZONTAL;
        orbLp.topMargin = dp(6);
        orbCard.addView(auraOrbView, orbLp);

        TextView orbTitle = new TextView(this);
        orbTitle.setText("Aura TV Home");
        orbTitle.setTextColor(Theme.text());
        orbTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        orbTitle.setTypeface(Typeface.DEFAULT_BOLD);
        orbTitle.setGravity(Gravity.CENTER);
        orbTitle.setPadding(0, dp(10), 0, 0);
        orbCard.addView(orbTitle);

        orbStatusPill = new TextView(this);
        orbStatusPill.setText("KETUK UNTUK KELOLA");
        orbStatusPill.setTextColor(Theme.accent());
        orbStatusPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        orbStatusPill.setTypeface(Typeface.DEFAULT_BOLD);
        orbStatusPill.setGravity(Gravity.CENTER);
        orbStatusPill.setPadding(dp(12), dp(5), dp(12), dp(5));
        orbStatusPill.setBackground(Theme.pillDrawable(dp(12), Theme.accentDim(), 0));

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp.topMargin = dp(8);
        pillLp.gravity = Gravity.CENTER_HORIZONTAL;
        orbCard.addView(orbStatusPill, pillLp);

        TextView orbSub = new TextView(this);
        orbSub.setText("Launcher TV modern bergaya Glass, Neumorphic, & Dark.");
        orbSub.setTextColor(Theme.muted());
        orbSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        orbSub.setGravity(Gravity.CENTER);
        orbSub.setPadding(0, dp(8), 0, dp(6));
        orbCard.addView(orbSub);

        col.addView(orbCard, cardLp());

        // 3. Tweak Rows
        // Animation Off Switch
        LinearLayout animCard = createCard();
        LinearLayout animRow = new LinearLayout(this);
        animRow.setOrientation(LinearLayout.HORIZONTAL);
        animRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout animTexts = new LinearLayout(this);
        animTexts.setOrientation(LinearLayout.VERTICAL);
        TextView animTitle = new TextView(this);
        animTitle.setText("Animations off");
        animTitle.setTextColor(Theme.text());
        animTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        animTitle.setTypeface(Typeface.DEFAULT_BOLD);
        animTexts.addView(animTitle);

        TextView animSub = new TextView(this);
        animSub.setText("Menu terbuka seketika tanpa jeda animasi");
        animSub.setTextColor(Theme.muted());
        animSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        animTexts.addView(animSub);
        animRow.addView(animTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        switchAnimations = new Switch(this);
        switchAnimations.setOnCheckedChangeListener((btn, isChecked) -> {
            String scale = isChecked ? "0" : "1";
            sendFastInput("settings put global window_animation_scale " + scale
                    + "; settings put global transition_animation_scale " + scale
                    + "; settings put global animator_duration_scale " + scale);
        });
        animRow.addView(switchAnimations);
        animCard.addView(animRow);
        col.addView(animCard, cardLp());

        // Screensaver Off Switch
        LinearLayout ssCard = createCard();
        LinearLayout ssRow = new LinearLayout(this);
        ssRow.setOrientation(LinearLayout.HORIZONTAL);
        ssRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout ssTexts = new LinearLayout(this);
        ssTexts.setOrientation(LinearLayout.VERTICAL);
        TextView ssTitle = new TextView(this);
        ssTitle.setText("Screensaver off");
        ssTitle.setTextColor(Theme.text());
        ssTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        ssTitle.setTypeface(Typeface.DEFAULT_BOLD);
        ssTexts.addView(ssTitle);

        TextView ssSub = new TextView(this);
        ssSub.setText("Mencegah beban latar belakang saat TV menganggur");
        ssSub.setTextColor(Theme.muted());
        ssSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        ssTexts.addView(ssSub);
        ssRow.addView(ssTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        switchScreensaver = new Switch(this);
        switchScreensaver.setOnCheckedChangeListener((btn, isChecked) -> {
            String enabled = isChecked ? "0" : "1";
            sendFastInput("settings put secure screensaver_enabled " + enabled);
        });
        ssRow.addView(switchScreensaver);
        ssCard.addView(ssRow);
        col.addView(ssCard, cardLp());

        // Sleep After Card
        LinearLayout sleepCard = createCard();
        applyPress(sleepCard);
        sleepCard.setOnClickListener(v -> showSleepChooserDialog());

        LinearLayout sleepRow = new LinearLayout(this);
        sleepRow.setOrientation(LinearLayout.HORIZONTAL);
        sleepRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout sleepTexts = new LinearLayout(this);
        sleepTexts.setOrientation(LinearLayout.VERTICAL);
        TextView sleepTitle = new TextView(this);
        sleepTitle.setText("Sleep after");
        sleepTitle.setTextColor(Theme.text());
        sleepTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        sleepTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sleepTexts.addView(sleepTitle);

        TextView sleepSub = new TextView(this);
        sleepSub.setText("TV mati otomatis saat tidak digunakan");
        sleepSub.setTextColor(Theme.muted());
        sleepSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        sleepTexts.addView(sleepSub);
        sleepRow.addView(sleepTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sleepChoosedText = new TextView(this);
        sleepChoosedText.setText("15 min ›");
        sleepChoosedText.setTextColor(Theme.accent());
        sleepChoosedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        sleepChoosedText.setTypeface(Typeface.DEFAULT_BOLD);
        sleepRow.addView(sleepChoosedText);

        sleepCard.addView(sleepRow);
        col.addView(sleepCard, cardLp());

        // Kill Background Apps Button
        TextView killBtn = createButton("Hentikan Aplikasi Latar & Bersihkan Cache", Theme.raised(), Theme.text(), this::killBackgroundApps);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.topMargin = dp(14);
        col.addView(killBtn, klp);

        scroll.addView(col);
        return scroll;
    }

    private void refreshBoostUi() {
        new Thread(() -> {
            try {
                // Read MemInfo
                String memOut = AdbClient.get().execCommand("cat /proc/meminfo");
                long totalKb = 0, freeKb = 0, availKb = 0;
                for (String line : memOut.split("\n")) {
                    if (line.startsWith("MemTotal:")) totalKb = parseKb(line);
                    if (line.startsWith("MemFree:")) freeKb = parseKb(line);
                    if (line.startsWith("MemAvailable:")) availKb = parseKb(line);
                }
                long finalFree = availKb > 0 ? availKb : freeKb;
                final String fStr = formatKb(finalFree);
                final String tStr = formatKb(totalKb);

                // Read Storage
                String dfOut = AdbClient.get().execCommand("df /data");
                long sFreeKb = 0, sTotalKb = 0;
                String[] lines = dfOut.split("\n");
                if (lines.length > 1) {
                    String[] tokens = lines[1].trim().split("\\s+");
                    if (tokens.length >= 4) {
                        try {
                            sTotalKb = Long.parseLong(tokens[1]);
                            sFreeKb = Long.parseLong(tokens[3]);
                        } catch (Exception ignored) {}
                    }
                }
                final String sfStr = formatKb(sFreeKb);
                final String stStr = formatKb(sTotalKb);

                // Check Aura TV Home installation & default status
                String auraCheck = AdbClient.get().execCommand("pm path com.gathin.tvhome");
                boolean auraInstalled = auraCheck.contains("package:");

                uiHandler.post(() -> {
                    ramFreeText.setText("Free " + fStr);
                    ramTotalText.setText("Total " + tStr);
                    storageFreeText.setText("Free " + sfStr);
                    storageTotalText.setText("Total " + stStr);

                    auraOrbView.setActive(auraInstalled);
                    if (auraInstalled) {
                        String host = AdbClient.get().getConnectedHost();
                        orbStatusPill.setText("AKTIF DI " + (host.isEmpty() ? "TV" : host.toUpperCase()));
                        orbStatusPill.setTextColor(Theme.accent());
                        orbStatusPill.setBackground(Theme.pillDrawable(dp(12), Theme.accentDim(), 0));
                    } else {
                        orbStatusPill.setText("BELUM TERPASANG DI TV");
                        orbStatusPill.setTextColor(Theme.muted());
                        orbStatusPill.setBackground(Theme.pillDrawable(dp(12), Theme.raised(), 0));
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private long parseKb(String line) {
        String digits = line.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0 : Long.parseLong(digits);
    }

    private String formatKb(long kb) {
        if (kb >= 1024 * 1024) {
            return String.format(Locale.US, "%.1f GB", kb / (1024f * 1024f));
        }
        return (kb / 1024) + " MB";
    }

    // =========================================================================
    // AURA TV HOME CUSTOMIZED SHEET (REPLACING GLOWLAUNCHER OPTIONS)
    // =========================================================================
    private void showAuraTvHomeSheet() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Aura TV Home");
        b.setMessage("Pusat kendali launcher TV Aura TV Home.");

        String[] options = {
                "Jadikan Default Launcher",
                "Buka Aura TV Home di TV",
                "Install / Update Aura TV Home APK",
                "Kembalikan Launcher Bawaan TV"
        };

        b.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Set as default launcher
                    sendFastInput("cmd package set-home-activity com.gathin.tvhome/.MainActivity");
                    Toast.makeText(this, "Aura TV Home diatur sebagai default launcher!", Toast.LENGTH_SHORT).show();
                    break;
                case 1: // Open Aura TV Home
                    sendFastInput("monkey -p com.gathin.tvhome -c android.intent.category.LAUNCHER 1");
                    Toast.makeText(this, "Membuka Aura TV Home di TV...", Toast.LENGTH_SHORT).show();
                    break;
                case 2: // Install / Update from phone
                    openApkFilePicker();
                    break;
                case 3: // Restore stock launcher
                    sendFastInput("cmd package set-home-activity default; pm enable com.google.android.tvlauncher; pm enable com.google.android.apps.tv.launcherx");
                    Toast.makeText(this, "Launcher bawaan TV dipulihkan.", Toast.LENGTH_LONG).show();
                    break;
            }
        });
        b.setNegativeButton("Tutup", null);
        b.show();
    }

    private void showSleepChooserDialog() {
        String[] options = {"5 menit", "15 menit", "30 menit", "60 menit", "Jangan Pernah"};
        int[] minutes = {5, 15, 30, 60, Integer.MAX_VALUE / 60000};

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Sleep after");
        b.setItems(options, (dialog, which) -> {
            int m = minutes[which];
            long ms = (long) m * 60L * 1000L;
            sendFastInput("settings put system screen_off_timeout " + ms);
            sleepChoosedText.setText(options[which] + " ›");
            Toast.makeText(this, "Waktu tidur TV: " + options[which], Toast.LENGTH_SHORT).show();
        });
        b.show();
    }

    private void killBackgroundApps() {
        showLoading("Membersihkan Sistem", "Menghentikan proses latar belakang & membersihkan cache...");
        new Thread(() -> {
            try {
                AdbClient.get().execCommand("am kill-all; sync; echo 3 > /proc/sys/vm/drop_caches");
            } catch (Exception ignored) {}
            uiHandler.post(() -> {
                hideLoading();
                Toast.makeText(this, "Aplikasi latar belakang dihentikan dan cache dibersihkan!", Toast.LENGTH_SHORT).show();
                refreshBoostUi();
            });
        }).start();
    }

    // =========================================================================
    // APK SIDELOAD PICKER
    // =========================================================================
    private void openApkFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Pilih File APK"), REQ_PICK_APK);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak ada file picker yang tersedia: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_APK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                installApkFromUri(uri);
            }
        }
    }

    private void installApkFromUri(Uri uri) {
        showLoading("Menginstal APK", "Membaca file APK...");
        new Thread(() -> {
            try {
                long size = -1;
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                        if (sizeIndex >= 0) size = cursor.getLong(sizeIndex);
                    }
                }

                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    String result = AdbClient.get().installApk(is, size, (percent, stage) -> {
                        uiHandler.post(() -> {
                            if (loadingMsgText != null) {
                                loadingMsgText.setText(stage + " (" + percent + "%)");
                            }
                        });
                    });

                    uiHandler.post(() -> {
                        hideLoading();
                        Toast.makeText(this, "Hasil instalasi: " + result, Toast.LENGTH_LONG).show();
                        loadAppsFromTv();
                    });
                }
            } catch (Exception e) {
                uiHandler.post(() -> {
                    hideLoading();
                    Toast.makeText(this, "Gagal menginstal APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // =========================================================================
    // THEME & CONTROLS
    // =========================================================================
    private void toggleTheme() {
        boolean nextLight = !Theme.isLight();
        Theme.setLight(nextLight);
        prefs.edit().putBoolean(KEY_THEME_LIGHT, nextLight).apply();
        themeToggleBtn.setText(nextLight ? "🌙" : "☀️");
        applyTheme();
    }

    private void applyTheme() {
        int bg = Theme.bg();
        rootLayout.setBackgroundColor(bg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(bg);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.setNavigationBarColor(bg);
                window.getDecorView().setSystemUiVisibility(
                        Theme.isLight() ? (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) : 0);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.getDecorView().setSystemUiVisibility(Theme.isLight() ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
            }
        }

        bottomTabBar.setBackground(Theme.pillDrawable(dp(32), Theme.glassTint(), Theme.hairline()));
        selectTab(currentTab);
    }

    private void showTvSwitchSheet() {
        showConnectScreen();
    }

    private void showOverflowMenu() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Aura TV Tools");
        String[] options = {
                "Segarkan Data (Refresh)",
                "Ganti TV",
                "Lupakan TV Ini",
                "Tentang Aura TV Tools"
        };
        b.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    refreshCurrentTab();
                    Toast.makeText(this, "Data disegarkan.", Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    AdbClient.get().disconnect();
                    showConnectScreen();
                    break;
                case 2:
                    String host = AdbClient.get().getConnectedHost();
                    Set<String> saved = new HashSet<>(prefs.getStringSet(KEY_SAVED_TVS, new HashSet<>()));
                    saved.remove(host);
                    prefs.edit().putStringSet(KEY_SAVED_TVS, saved).apply();
                    AdbClient.get().disconnect();
                    showConnectScreen();
                    break;
                case 3:
                    showAboutDialog();
                    break;
            }
        });
        b.show();
    }

    private void showAboutDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Aura TV Tools");
        b.setMessage("Aura TV Tools — Pengendali nirkabel STB / Android TV modern dengan tampilan terinspirasi dari KonnectTV.\n\n"
                + "• Live Screen Mirroring interaktif\n"
                + "• Remote Control responsif dengan D-Pad bulat\n"
                + "• Detox bloatware aman tanpa root\n"
                + "• Pusat integrasi Aura TV Home\n\n"
                + "Open Source · github.com/luthpunk/aura-tv-tools");
        b.setPositiveButton("OK", null);
        b.show();
    }

    private void sendKey(int keyCode) {
        vibrate(10);
        AdbClient.get().sendKey(keyCode);
    }

    private void sendFastInput(String cmd) {
        AdbClient.get().sendFastInput(cmd);
    }

    private void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    private void applyPress(View v) {
        v.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }
            return false;
        });
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.cardDrawable(dp(18)));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        return card;
    }

    private TextView createButton(String text, int bgColor, int textColor, Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(16), dp(13), dp(16), dp(13));
        btn.setBackground(Theme.pillDrawable(dp(14), bgColor, 0));
        applyPress(btn);
        btn.setOnClickListener(v -> {
            vibrate(10);
            if (onClick != null) onClick.run();
        });
        return btn;
    }

    private View createSpacer(int heightPx) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        return v;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        return lp;
    }

    private LinearLayout.LayoutParams rowLp() {
        return new LinearLayout.LayoutParams(0, dp(66), 1f);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showLoading(String title, String message) {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setCancelable(false);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(20), dp(24), dp(20));
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        ProgressBar pb = new ProgressBar(this);
        box.addView(pb);

        TextView tView = new TextView(this);
        tView.setText(title);
        tView.setTextColor(Theme.text());
        tView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        tView.setTypeface(Typeface.DEFAULT_BOLD);
        tView.setGravity(Gravity.CENTER);
        tView.setPadding(0, dp(14), 0, 0);
        box.addView(tView);

        loadingMsgText = new TextView(this);
        loadingMsgText.setText(message);
        loadingMsgText.setTextColor(Theme.muted());
        loadingMsgText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        loadingMsgText.setGravity(Gravity.CENTER);
        loadingMsgText.setPadding(0, dp(8), 0, 0);
        box.addView(loadingMsgText);

        b.setView(box);
        loadingDialog = b.create();
        loadingDialog.show();
    }

    private void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }
}
