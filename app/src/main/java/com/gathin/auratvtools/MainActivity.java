package com.gathin.auratvtools;

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
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.auratvtools.adb.AdbClient;
import com.gathin.auratvtools.mirror.MirrorActivity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aura TV Tools — Simple, Fast, Minimalist STB Remote & Mirroring Controller.
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "aura_tv_tools_prefs";
    private static final String KEY_LAST_IP = "last_stb_ip";
    private static final int REQ_PICK_APK = 1001;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // Header Status
    private TextView statusPill;

    // Tab Views
    private View tabHome;
    private View tabRemote;
    private View tabApps;
    private final TextView[] navButtons = new TextView[3];

    // Tab 1: Home
    private EditText ipInput;
    private TextView connectBtn;
    private TextView scanStatusText;
    private LinearLayout discoveredChipsRow;

    // Tab 3: Apps
    private TextView apkStatusText;
    private ProgressBar apkProgressBar;
    private LinearLayout appListContainer;
    private EditText appSearchInput;
    private final List<String> cachedPackages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#070B14"));
            getWindow().setNavigationBarColor(Color.parseColor("#0B1120"));
        }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        buildMainLayout();

        String savedIp = prefs.getString(KEY_LAST_IP, "");
        if (!savedIp.isEmpty()) {
            ipInput.setText(savedIp);
        }
        refreshConnectionUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshConnectionUi();
    }

    private void buildMainLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#070B14"));

        // Top Simple App Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(18), dp(14), dp(18), dp(14));
        topBar.setBackgroundColor(Color.parseColor("#0B1120"));

        TextView appTitle = new TextView(this);
        appTitle.setText("Aura TV Tools");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(appTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusPill = new TextView(this);
        statusPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        statusPill.setTypeface(Typeface.DEFAULT_BOLD);
        statusPill.setPadding(dp(12), dp(5), dp(12), dp(5));
        topBar.addView(statusPill);

        root.addView(topBar);

        // Content Frame
        FrameLayout contentFrame = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);

        tabHome = buildHomeTab();
        tabRemote = buildRemoteTab();
        tabApps = buildAppsTab();

        contentFrame.addView(tabHome);
        contentFrame.addView(tabRemote);
        contentFrame.addView(tabApps);

        root.addView(contentFrame, contentLp);

        // Bottom Navigation Bar (Only 3 clean tabs)
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(Color.parseColor("#0B1120"));
        bottomNav.setPadding(dp(10), dp(8), dp(10), dp(10));

        String[] labels = {"📺 Beranda", "🎮 Remote", "📦 Aplikasi"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView tabBtn = new TextView(this);
            tabBtn.setText(labels[i]);
            tabBtn.setGravity(Gravity.CENTER);
            tabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            tabBtn.setTypeface(Typeface.DEFAULT_BOLD);
            tabBtn.setPadding(dp(8), dp(10), dp(8), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            tabBtn.setOnClickListener(v -> {
                vibrate(10);
                selectTab(index);
            });
            navButtons[i] = tabBtn;
            bottomNav.addView(tabBtn, lp);
        }

        root.addView(bottomNav);
        setContentView(root);
        selectTab(0);
    }

    private void selectTab(int index) {
        tabHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        tabRemote.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabApps.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < 3; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            if (i == index) {
                bg.setColor(Color.parseColor("#2638BDF8"));
                navButtons[i].setTextColor(Color.parseColor("#38BDF8"));
            } else {
                bg.setColor(Color.TRANSPARENT);
                navButtons[i].setTextColor(Color.parseColor("#94A3B8"));
            }
            navButtons[i].setBackground(bg);
        }

        if (index == 2 && AdbClient.get().isConnected() && cachedPackages.isEmpty()) {
            loadInstalledPackages();
        }
    }

    // =========================================================================
    // TAB 1: BERANDA & MIRROR (SUPER SIMPLE)
    // =========================================================================
    private View buildHomeTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(20));

        // 1. Connection Card
        LinearLayout connCard = createCard();

        LinearLayout ipRow = new LinearLayout(this);
        ipRow.setOrientation(LinearLayout.HORIZONTAL);
        ipRow.setGravity(Gravity.CENTER_VERTICAL);

        ipInput = new EditText(this);
        ipInput.setHint("IP STB (192.168.1.xxx)");
        ipInput.setHintTextColor(Color.parseColor("#64748B"));
        ipInput.setTextColor(Color.WHITE);
        ipInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        ipInput.setSingleLine(true);
        ipInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        ipInput.setBackground(makeInputBg());
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ipLp.setMarginEnd(dp(10));
        ipRow.addView(ipInput, ipLp);

        connectBtn = createButton("Hubungkan", "#0284C7", this::onConnectClicked);
        ipRow.addView(connectBtn);
        connCard.addView(ipRow);

        TextView scanBtn = createSecondaryButton("🔍 Scan Otomatis Wi-Fi", this::startSubnetScan);
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scanLp.topMargin = dp(10);
        connCard.addView(scanBtn, scanLp);

        scanStatusText = new TextView(this);
        scanStatusText.setTextColor(Color.parseColor("#94A3B8"));
        scanStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        scanStatusText.setPadding(0, dp(6), 0, 0);
        connCard.addView(scanStatusText);

        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        discoveredChipsRow = new LinearLayout(this);
        discoveredChipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsScroll.addView(discoveredChipsRow);
        connCard.addView(chipsScroll);

        col.addView(connCard, cardLp());

        // 2. Big Live Screen Mirroring Button
        TextView launchMirrorBtn = createButton("▶ Buka Layar STB (Live Mirror)", "#0EA5E9", () -> {
            if (!AdbClient.get().isConnected()) {
                Toast.makeText(this, "Hubungkan ke IP STB terlebih dahulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, MirrorActivity.class));
        });
        launchMirrorBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        launchMirrorBtn.setPadding(dp(16), dp(16), dp(16), dp(16));
        col.addView(launchMirrorBtn, cardLp());

        // 3. Quick STB Tools
        LinearLayout toolsCard = createCard();
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(createButton("🧹 Bersihkan Cache", "#162033", this::clearAllStbCaches), gridLp());
        r1.addView(createButton("🔄 Reboot STB", "#162033", () -> confirmPowerAction("Reboot STB", "reboot")), gridLp());
        toolsCard.addView(r1);

        col.addView(toolsCard, cardLp());

        scroll.addView(col);
        return scroll;
    }

    // =========================================================================
    // TAB 2: REMOTE (FAST D-PAD & KEYBOARD)
    // =========================================================================
    private View buildRemoteTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(20));

        // Keyboard Row
        LinearLayout kbRow = new LinearLayout(this);
        kbRow.setOrientation(LinearLayout.HORIZONTAL);
        kbRow.setGravity(Gravity.CENTER_VERTICAL);

        final EditText kbInput = new EditText(this);
        kbInput.setHint("Ketik teks ke TV...");
        kbInput.setHintTextColor(Color.parseColor("#64748B"));
        kbInput.setTextColor(Color.WHITE);
        kbInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        kbInput.setSingleLine(true);
        kbInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        kbInput.setBackground(makeInputBg());
        LinearLayout.LayoutParams kbLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        kbLp.setMarginEnd(dp(8));
        kbRow.addView(kbInput, kbLp);

        TextView sendTxtBtn = createButton("Kirim", "#0284C7", () -> {
            String text = kbInput.getText().toString();
            if (!text.isEmpty()) {
                AdbClient.get().sendText(text);
                kbInput.setText("");
            }
        });
        kbRow.addView(sendTxtBtn);
        col.addView(kbRow, cardLp());

        // Nav Top Bar: Back, Home, Recents, Menu
        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.addView(createRemoteKeyButton("◁ Back", KeyEvent.KEYCODE_BACK), gridLp());
        navRow.addView(createRemoteKeyButton("⌂ Home", KeyEvent.KEYCODE_HOME), gridLp());
        navRow.addView(createRemoteKeyButton("❐ Recents", KeyEvent.KEYCODE_APP_SWITCH), gridLp());
        navRow.addView(createRemoteKeyButton("☰ Menu", KeyEvent.KEYCODE_MENU), gridLp());
        col.addView(navRow, cardLp());

        // D-Pad Cross
        LinearLayout dpadBox = new LinearLayout(this);
        dpadBox.setOrientation(LinearLayout.VERTICAL);
        dpadBox.setGravity(Gravity.CENTER);
        dpadBox.setPadding(0, dp(10), 0, dp(14));

        TextView upBtn = createDpadButton("▲", KeyEvent.KEYCODE_DPAD_UP, false);
        dpadBox.addView(upBtn, new LinearLayout.LayoutParams(dp(96), dp(68)));

        LinearLayout midRow = new LinearLayout(this);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setGravity(Gravity.CENTER);
        midRow.setPadding(0, dp(8), 0, dp(8));

        TextView leftBtn = createDpadButton("◀", KeyEvent.KEYCODE_DPAD_LEFT, false);
        TextView okBtn = createDpadButton("OK", KeyEvent.KEYCODE_DPAD_CENTER, true);
        TextView rightBtn = createDpadButton("▶", KeyEvent.KEYCODE_DPAD_RIGHT, false);

        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(dp(88), dp(76));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(dp(96), dp(76));
        okLp.setMargins(dp(10), 0, dp(10), 0);

        midRow.addView(leftBtn, sideLp);
        midRow.addView(okBtn, okLp);
        midRow.addView(rightBtn, sideLp);
        dpadBox.addView(midRow);

        TextView downBtn = createDpadButton("▼", KeyEvent.KEYCODE_DPAD_DOWN, false);
        dpadBox.addView(downBtn, new LinearLayout.LayoutParams(dp(96), dp(68)));

        col.addView(dpadBox, cardLp());

        // Volume & Power Row
        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.addView(createRemoteKeyButton("🔉 Vol -", KeyEvent.KEYCODE_VOLUME_DOWN), gridLp());
        volRow.addView(createRemoteKeyButton("🔊 Vol +", KeyEvent.KEYCODE_VOLUME_UP), gridLp());
        volRow.addView(createRemoteKeyButton("🔇 Mute", KeyEvent.KEYCODE_VOLUME_MUTE), gridLp());
        volRow.addView(createRemoteKeyButton("⏻ Power", KeyEvent.KEYCODE_POWER), gridLp());
        col.addView(volRow, cardLp());

        scroll.addView(col);
        return scroll;
    }

    // =========================================================================
    // TAB 3: APK SIDELOAD & APP LIST
    // =========================================================================
    private View buildAppsTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(20));

        // Install APK button
        TextView pickApkBtn = createButton("📂 Pilih & Install APK dari HP", "#0284C7", this::openApkFilePicker);
        col.addView(pickApkBtn, cardLp());

        apkProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        apkProgressBar.setMax(100);
        apkProgressBar.setVisibility(View.GONE);
        col.addView(apkProgressBar, cardLp());

        apkStatusText = new TextView(this);
        apkStatusText.setTextColor(Color.parseColor("#38BDF8"));
        apkStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        apkStatusText.setVisibility(View.GONE);
        col.addView(apkStatusText, cardLp());

        // App search & list
        appSearchInput = new EditText(this);
        appSearchInput.setHint("Cari aplikasi terinstal di STB...");
        appSearchInput.setHintTextColor(Color.parseColor("#64748B"));
        appSearchInput.setTextColor(Color.WHITE);
        appSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        appSearchInput.setSingleLine(true);
        appSearchInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        appSearchInput.setBackground(makeInputBg());
        col.addView(appSearchInput, cardLp());

        appSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderFilteredPackages(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        col.addView(appListContainer);

        scroll.addView(col);
        return scroll;
    }

    // =========================================================================
    // ACTIONS & LOGIC
    // =========================================================================
    private void onConnectClicked() {
        if (AdbClient.get().isConnected()) {
            AdbClient.get().disconnect();
            refreshConnectionUi();
            Toast.makeText(this, "Koneksi diputuskan", Toast.LENGTH_SHORT).show();
            return;
        }

        String rawIp = ipInput.getText().toString().trim();
        if (rawIp.isEmpty()) {
            Toast.makeText(this, "Masukkan IP STB!", Toast.LENGTH_SHORT).show();
            return;
        }

        connectBtn.setEnabled(false);
        connectBtn.setText("Menghubungkan...");
        scanStatusText.setText("Menghubungkan ke " + rawIp + "...");

        new Thread(() -> {
            try {
                AdbClient.get().connect(MainActivity.this, rawIp, 5555);
                prefs.edit().putString(KEY_LAST_IP, rawIp).apply();
                uiHandler.post(() -> {
                    connectBtn.setEnabled(true);
                    refreshConnectionUi();
                    scanStatusText.setText("Terhubung!");
                    Toast.makeText(MainActivity.this, "Berhasil terhubung ke STB!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    connectBtn.setEnabled(true);
                    refreshConnectionUi();
                    scanStatusText.setText("Gagal: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void startSubnetScan() {
        discoveredChipsRow.removeAllViews();
        scanStatusText.setText("Memindai Wi-Fi...");
        AdbClient.scanSubnet(this, new AdbClient.ScanCallback() {
            @Override
            public void onDeviceFound(final String ip) {
                uiHandler.post(() -> {
                    TextView chip = createButton("📺 " + ip, "#1E293B", () -> {
                        ipInput.setText(ip);
                        onConnectClicked();
                    });
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                    chip.setPadding(dp(10), dp(6), dp(10), dp(6));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.rightMargin = dp(8);
                    lp.topMargin = dp(4);
                    discoveredChipsRow.addView(chip, lp);
                });
            }

            @Override
            public void onScanFinished(final List<String> foundIps) {
                uiHandler.post(() -> {
                    if (foundIps.isEmpty()) {
                        scanStatusText.setText("Tidak ditemukan STB di jaringan ini.");
                    } else {
                        scanStatusText.setText("Ditemukan " + foundIps.size() + " STB:");
                        if (ipInput.getText().toString().trim().isEmpty()) {
                            ipInput.setText(foundIps.get(0));
                        }
                    }
                });
            }
        });
    }

    private void refreshConnectionUi() {
        boolean conn = AdbClient.get().isConnected();
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(99));
        if (conn) {
            statusPill.setText("● TERHUBUNG");
            statusPill.setTextColor(Color.parseColor("#4ADE80"));
            pillBg.setColor(Color.parseColor("#1A4ADE80"));
            connectBtn.setText("Putus");
        } else {
            statusPill.setText("○ BELUM TERHUBUNG");
            statusPill.setTextColor(Color.parseColor("#F87171"));
            pillBg.setColor(Color.parseColor("#1AF87171"));
            connectBtn.setText("Hubungkan");
        }
        statusPill.setBackground(pillBg);
    }

    private void clearAllStbCaches() {
        if (!ensureConnected()) return;
        Toast.makeText(this, "Membersihkan cache STB...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                AdbClient.get().execCommand("pm trim-caches 999999999999999999");
                uiHandler.post(() -> Toast.makeText(this, "Cache STB berhasil dibersihkan!", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                uiHandler.post(() -> Toast.makeText(this, "Gagal: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void confirmPowerAction(String title, final String cmd) {
        if (!ensureConnected()) return;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setMessage("Jalankan " + title + "?")
                .setPositiveButton("Ya", (d, w) -> {
                    new Thread(() -> {
                        try {
                            AdbClient.get().execCommand(cmd);
                        } catch (Exception ignored) {}
                        uiHandler.post(() -> {
                            AdbClient.get().disconnect();
                            refreshConnectionUi();
                        });
                    }).start();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void openApkFilePicker() {
        if (!ensureConnected()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        try {
            startActivityForResult(intent, REQ_PICK_APK);
        } catch (Exception e) {
            intent.setType("*/*");
            startActivityForResult(intent, REQ_PICK_APK);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_APK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            installSelectedApkUri(data.getData());
        }
    }

    private void installSelectedApkUri(final Uri uri) {
        apkProgressBar.setVisibility(View.VISIBLE);
        apkProgressBar.setProgress(0);
        apkStatusText.setVisibility(View.VISIBLE);
        apkStatusText.setText("Mengirim APK...");

        new Thread(() -> {
            try {
                long fileSize = queryUriSize(uri);
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new Exception("File tidak dapat dibaca");
                    String result = AdbClient.get().installApk(in, fileSize, (percent, stage) ->
                            uiHandler.post(() -> {
                                apkProgressBar.setProgress(percent);
                                apkStatusText.setText(percent + "% • " + stage);
                            }));
                    uiHandler.post(() -> {
                        apkStatusText.setText("Hasil: " + result);
                        Toast.makeText(this, "Install Selesai: " + result, Toast.LENGTH_LONG).show();
                        loadInstalledPackages();
                    });
                }
            } catch (Exception e) {
                uiHandler.post(() -> {
                    apkStatusText.setText("Gagal: " + e.getMessage());
                    Toast.makeText(this, "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private long queryUriSize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) return c.getLong(idx);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void loadInstalledPackages() {
        if (!ensureConnected()) return;
        appListContainer.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Memuat aplikasi...");
        loading.setTextColor(Color.parseColor("#94A3B8"));
        appListContainer.addView(loading);

        new Thread(() -> {
            try {
                String out = AdbClient.get().execCommand("pm list packages -3");
                List<String> list = new ArrayList<>();
                for (String line : out.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("package:")) {
                        list.add(line.substring(8).trim());
                    }
                }
                Collections.sort(list);
                synchronized (cachedPackages) {
                    cachedPackages.clear();
                    cachedPackages.addAll(list);
                }
                uiHandler.post(() -> renderFilteredPackages(appSearchInput.getText().toString()));
            } catch (Exception e) {
                uiHandler.post(() -> {
                    appListContainer.removeAllViews();
                    TextView err = new TextView(this);
                    err.setText("Gagal: " + e.getMessage());
                    err.setTextColor(Color.parseColor("#F87171"));
                    appListContainer.addView(err);
                });
            }
        }).start();
    }

    private void renderFilteredPackages(String query) {
        appListContainer.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase();
        List<String> snapshot;
        synchronized (cachedPackages) {
            snapshot = new ArrayList<>(cachedPackages);
        }

        for (final String pkg : snapshot) {
            if (!q.isEmpty() && !pkg.toLowerCase().contains(q)) continue;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#131C31"));
            bg.setCornerRadius(dp(10));
            item.setBackground(bg);

            TextView pkgName = new TextView(this);
            pkgName.setText(pkg);
            pkgName.setTextColor(Color.WHITE);
            pkgName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            item.addView(pkgName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView actionBtn = createButton("Buka", "#0284C7", () -> {
                AdbClient.get().sendFastInput("monkey -p " + pkg + " 1");
                Toast.makeText(this, "Membuka " + pkg, Toast.LENGTH_SHORT).show();
            });
            actionBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            actionBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
            item.addView(actionBtn);

            TextView unBtn = createButton("Hapus", "#7F1D1D", () -> {
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Hapus " + pkg + "?")
                        .setPositiveButton("Ya", (d, w) -> {
                            new Thread(() -> {
                                try {
                                    AdbClient.get().execCommand("pm uninstall " + pkg);
                                    uiHandler.post(this::loadInstalledPackages);
                                } catch (Exception ignored) {}
                            }).start();
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            });
            unBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            unBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
            LinearLayout.LayoutParams unLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unLp.leftMargin = dp(6);
            item.addView(unBtn, unLp);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            appListContainer.addView(item, lp);
        }
    }

    private boolean ensureConnected() {
        if (!AdbClient.get().isConnected()) {
            Toast.makeText(this, "Belum terhubung ke STB!", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // =========================================================================
    // UI HELPERS
    // =========================================================================
    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0F172A"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#1E293B"));
        card.setBackground(bg);
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        return lp;
    }

    private GradientDrawable makeInputBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#070B14"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#334155"));
        return bg;
    }

    private TextView createButton(String text, String hexColor, final Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(hexColor));
        bg.setCornerRadius(dp(10));
        btn.setBackground(bg);
        btn.setOnClickListener(v -> {
            vibrate(10);
            onClick.run();
        });
        return btn;
    }

    private TextView createSecondaryButton(String text, final Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.parseColor("#38BDF8"));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#132238"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#3338BDF8"));
        btn.setBackground(bg);
        btn.setOnClickListener(v -> {
            vibrate(10);
            onClick.run();
        });
        return btn;
    }

    private TextView createRemoteKeyButton(String label, final int keyCode) {
        return createButton(label, "#162033", () -> {
            if (ensureConnected()) {
                AdbClient.get().sendKey(keyCode);
            }
        });
    }

    private TextView createDpadButton(String label, final int keyCode, boolean isCenter) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCenter ? 16f : 18f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(isCenter ? "#0284C7" : "#1E293B"));
        bg.setCornerRadius(dp(isCenter ? 20 : 16));
        bg.setStroke(dp(1.5f), Color.parseColor(isCenter ? "#7DD3FC" : "#334155"));
        btn.setBackground(bg);
        btn.setOnClickListener(v -> {
            vibrate(12);
            if (ensureConnected()) {
                AdbClient.get().sendKey(keyCode);
            }
        });
        return btn;
    }

    private LinearLayout.LayoutParams gridLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Exception ignored) {}
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
