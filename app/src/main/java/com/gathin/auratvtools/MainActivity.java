package com.gathin.auratvtools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.MediaStore;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.auratvtools.adb.AdbClient;
import com.gathin.auratvtools.mirror.MirrorActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Aura TV Tools — Modern Light Minimalist STB Remote & Mirroring Controller.
 * Features:
 * - Proportional Modern Light Theme
 * - Tactile press bounce feedback on all buttons
 * - Animated loading dialogs (e.g. ADB authorization prompt helper)
 * - Clear popup dialogs for success & failure events
 * - Dual-Engine Live Screen Mirroring with side D-Pad
 * - STB Screenshot saving to phone gallery
 * - Auto-enable Media Listener for Aura TV Home
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "aura_tv_tools_prefs";
    private static final String KEY_LAST_IP = "last_stb_ip";
    private static final int REQ_PICK_APK = 1001;

    // Modern Light Color Palette
    private static final int COLOR_BG = Color.parseColor("#F8FAFC");        // Slate 50
    private static final int COLOR_CARD = Color.parseColor("#FFFFFF");      // Pure White
    private static final int COLOR_CARD_BORDER = Color.parseColor("#E2E8F0"); // Slate 200
    private static final int COLOR_TEXT_PRIMARY = Color.parseColor("#0F172A"); // Slate 900
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#64748B"); // Slate 500
    private static final int COLOR_PRIMARY = Color.parseColor("#0284C7");   // Sky 600
    private static final int COLOR_PRIMARY_LIGHT = Color.parseColor("#E0F2FE"); // Sky 100

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // Loading Dialog instance
    private AlertDialog loadingDialog;
    private TextView loadingMsgText;

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
    private LinearLayout appListContainer;
    private EditText appSearchInput;
    private final List<String> cachedPackages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Modern Light System Bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(COLOR_BG);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.setNavigationBarColor(COLOR_CARD);
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
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
        root.setBackgroundColor(COLOR_BG);

        // Top Light App Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(20), dp(16), dp(20), dp(16));
        topBar.setBackgroundColor(COLOR_CARD);

        GradientDrawable topBarBottomLine = new GradientDrawable();
        topBarBottomLine.setColor(COLOR_CARD);
        topBarBottomLine.setStroke(dp(1), COLOR_CARD_BORDER);
        topBar.setBackground(topBarBottomLine);

        TextView appTitle = new TextView(this);
        appTitle.setText("Aura TV Tools");
        appTitle.setTextColor(COLOR_TEXT_PRIMARY);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(appTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusPill = new TextView(this);
        statusPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        statusPill.setTypeface(Typeface.DEFAULT_BOLD);
        statusPill.setPadding(dp(12), dp(6), dp(12), dp(6));
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

        // Bottom Navigation Bar (Modern Light Style)
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(COLOR_CARD);
        bottomNav.setPadding(dp(12), dp(10), dp(12), dp(12));

        GradientDrawable navBorder = new GradientDrawable();
        navBorder.setColor(COLOR_CARD);
        navBorder.setStroke(dp(1), COLOR_CARD_BORDER);
        bottomNav.setBackground(navBorder);

        String[] labels = {"📺 Beranda", "🎮 Remote", "📦 Aplikasi"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView tabBtn = new TextView(this);
            tabBtn.setText(labels[i]);
            tabBtn.setGravity(Gravity.CENTER);
            tabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            tabBtn.setTypeface(Typeface.DEFAULT_BOLD);
            tabBtn.setPadding(dp(10), dp(11), dp(10), dp(11));
            applyPressFeedback(tabBtn);

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
                bg.setColor(COLOR_PRIMARY_LIGHT);
                navButtons[i].setTextColor(COLOR_PRIMARY);
            } else {
                bg.setColor(Color.TRANSPARENT);
                navButtons[i].setTextColor(COLOR_TEXT_SECONDARY);
            }
            navButtons[i].setBackground(bg);
        }

        if (index == 2 && AdbClient.get().isConnected() && cachedPackages.isEmpty()) {
            loadInstalledPackages();
        }
    }

    // =========================================================================
    // TAB 1: BERANDA & MIRROR (MODERN LIGHT)
    // =========================================================================
    private View buildHomeTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(24));

        // 1. Connection Card
        LinearLayout connCard = createCard();

        TextView connHeader = new TextView(this);
        connHeader.setText("Koneksi STB Android");
        connHeader.setTextColor(COLOR_TEXT_PRIMARY);
        connHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        connHeader.setTypeface(Typeface.DEFAULT_BOLD);
        connHeader.setPadding(0, 0, 0, dp(12));
        connCard.addView(connHeader);

        LinearLayout ipRow = new LinearLayout(this);
        ipRow.setOrientation(LinearLayout.HORIZONTAL);
        ipRow.setGravity(Gravity.CENTER_VERTICAL);

        ipInput = new EditText(this);
        ipInput.setHint("192.168.1.xxx");
        ipInput.setHintTextColor(Color.parseColor("#94A3B8"));
        ipInput.setTextColor(COLOR_TEXT_PRIMARY);
        ipInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        ipInput.setSingleLine(true);
        ipInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        ipInput.setBackground(makeInputBg());
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ipLp.setMarginEnd(dp(8));
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
        scanStatusText.setTextColor(COLOR_TEXT_SECONDARY);
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

        // 2. Big Live Screen Mirroring Card Button
        LinearLayout mirrorCard = createCard();
        applyPressFeedback(mirrorCard);
        mirrorCard.setOnClickListener(v -> {
            vibrate(10);
            if (!AdbClient.get().isConnected()) {
                showErrorPopup("Belum Terhubung", "Silakan masukkan IP dan hubungkan ke STB terlebih dahulu.");
                return;
            }
            startActivity(new Intent(this, MirrorActivity.class));
        });

        LinearLayout mirrorRow = new LinearLayout(this);
        mirrorRow.setOrientation(LinearLayout.HORIZONTAL);
        mirrorRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView mirrorIcon = new TextView(this);
        mirrorIcon.setText("📺");
        mirrorIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);
        mirrorIcon.setPadding(0, 0, dp(14), 0);
        mirrorRow.addView(mirrorIcon);

        LinearLayout mirrorTexts = new LinearLayout(this);
        mirrorTexts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mtLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView mirrorTitle = new TextView(this);
        mirrorTitle.setText("Live Mirror Layar STB");
        mirrorTitle.setTextColor(COLOR_TEXT_PRIMARY);
        mirrorTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        mirrorTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mirrorTexts.addView(mirrorTitle);

        TextView mirrorSubtitle = new TextView(this);
        mirrorSubtitle.setText("Tampilkan layar TV di kiri dan D-Pad kontrol di kanan");
        mirrorSubtitle.setTextColor(COLOR_TEXT_SECONDARY);
        mirrorSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        mirrorTexts.addView(mirrorSubtitle);
        mirrorRow.addView(mirrorTexts, mtLp);

        TextView mirrorArrow = new TextView(this);
        mirrorArrow.setText("➔");
        mirrorArrow.setTextColor(COLOR_PRIMARY);
        mirrorArrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        mirrorArrow.setTypeface(Typeface.DEFAULT_BOLD);
        mirrorRow.addView(mirrorArrow);

        mirrorCard.addView(mirrorRow);
        col.addView(mirrorCard, cardLp());

        // 3. Proportional STB Tools Section
        LinearLayout toolsSection = createCard();

        TextView toolsTitle = new TextView(this);
        toolsTitle.setText("Alat Cepat STB");
        toolsTitle.setTextColor(COLOR_TEXT_PRIMARY);
        toolsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        toolsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        toolsTitle.setPadding(0, 0, 0, dp(12));
        toolsSection.addView(toolsTitle);

        // Row 1: Bersihkan Cache + Tangkapan Layar
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(createToolButton("🧹 Bersihkan Cache", "Kosongkan cache sistem", this::clearAllStbCaches), gridLp());
        r1.addView(createToolButton("📸 Tangkapan Layar", "Simpan gambar TV ke HP", this::takeStbScreenshot), gridLp());
        toolsSection.addView(r1);

        // Row 2: Aktifkan Media Listener + Reboot STB
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(createToolButton("🎵 Media Listener", "Aktifkan Now Playing TV", this::enableMediaListener), gridLp());
        r2.addView(createToolButton("🔄 Reboot STB", "Mulai ulang sistem STB", () -> confirmPowerAction("Reboot STB", "reboot")), gridLp());
        toolsSection.addView(r2);

        col.addView(toolsSection, cardLp());

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
        col.setPadding(dp(16), dp(16), dp(16), dp(24));

        // Keyboard Row Card
        LinearLayout kbCard = createCard();
        LinearLayout kbRow = new LinearLayout(this);
        kbRow.setOrientation(LinearLayout.HORIZONTAL);
        kbRow.setGravity(Gravity.CENTER_VERTICAL);

        final EditText kbInput = new EditText(this);
        kbInput.setHint("Ketik teks ke TV...");
        kbInput.setHintTextColor(Color.parseColor("#94A3B8"));
        kbInput.setTextColor(COLOR_TEXT_PRIMARY);
        kbInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        kbInput.setSingleLine(true);
        kbInput.setPadding(dp(14), dp(11), dp(14), dp(11));
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
        kbCard.addView(kbRow);
        col.addView(kbCard, cardLp());

        // Nav Top Bar Card: Back, Home, Recents, Menu
        LinearLayout navCard = createCard();
        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.addView(createRemoteKeyButton("◁ Back", KeyEvent.KEYCODE_BACK), gridLp());
        navRow.addView(createRemoteKeyButton("⌂ Home", KeyEvent.KEYCODE_HOME), gridLp());
        navRow.addView(createRemoteKeyButton("❐ Recents", KeyEvent.KEYCODE_APP_SWITCH), gridLp());
        navRow.addView(createRemoteKeyButton("☰ Menu", KeyEvent.KEYCODE_MENU), gridLp());
        navCard.addView(navRow);
        col.addView(navCard, cardLp());

        // D-Pad Cross Card
        LinearLayout dpadCard = createCard();
        LinearLayout dpadBox = new LinearLayout(this);
        dpadBox.setOrientation(LinearLayout.VERTICAL);
        dpadBox.setGravity(Gravity.CENTER);
        dpadBox.setPadding(0, dp(12), 0, dp(14));

        GradientDrawable dpadBoxBg = new GradientDrawable();
        dpadBoxBg.setColor(Color.parseColor("#F1F5F9")); // Slate 100 soft container
        dpadBoxBg.setCornerRadius(dp(24));
        dpadBoxBg.setStroke(dp(1), COLOR_CARD_BORDER);
        dpadBox.setBackground(dpadBoxBg);

        TextView upBtn = createDpadButton("▲", KeyEvent.KEYCODE_DPAD_UP, false);
        dpadBox.addView(upBtn, new LinearLayout.LayoutParams(dp(96), dp(64)));

        LinearLayout midRow = new LinearLayout(this);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setGravity(Gravity.CENTER);
        midRow.setPadding(0, dp(8), 0, dp(8));

        TextView leftBtn = createDpadButton("◀", KeyEvent.KEYCODE_DPAD_LEFT, false);
        TextView okBtn = createDpadButton("OK", KeyEvent.KEYCODE_DPAD_CENTER, true);
        TextView rightBtn = createDpadButton("▶", KeyEvent.KEYCODE_DPAD_RIGHT, false);

        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(dp(84), dp(72));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(dp(92), dp(72));
        okLp.setMargins(dp(10), 0, dp(10), 0);

        midRow.addView(leftBtn, sideLp);
        midRow.addView(okBtn, okLp);
        midRow.addView(rightBtn, sideLp);
        dpadBox.addView(midRow);

        TextView downBtn = createDpadButton("▼", KeyEvent.KEYCODE_DPAD_DOWN, false);
        dpadBox.addView(downBtn, new LinearLayout.LayoutParams(dp(96), dp(64)));

        dpadCard.addView(dpadBox);
        col.addView(dpadCard, cardLp());

        // Volume & Power Row Card
        LinearLayout volCard = createCard();
        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.addView(createRemoteKeyButton("🔉 Vol -", KeyEvent.KEYCODE_VOLUME_DOWN), gridLp());
        volRow.addView(createRemoteKeyButton("🔊 Vol +", KeyEvent.KEYCODE_VOLUME_UP), gridLp());
        volRow.addView(createRemoteKeyButton("🔇 Mute", KeyEvent.KEYCODE_VOLUME_MUTE), gridLp());
        volRow.addView(createRemoteKeyButton("⏻ Power", KeyEvent.KEYCODE_POWER), gridLp());
        volCard.addView(volRow);
        col.addView(volCard, cardLp());

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
        col.setPadding(dp(16), dp(16), dp(16), dp(24));

        // Install APK Card
        LinearLayout installCard = createCard();
        TextView pickApkBtn = createButton("📂 Pilih & Install APK dari HP", "#0284C7", this::openApkFilePicker);
        installCard.addView(pickApkBtn);
        col.addView(installCard, cardLp());

        // App search & list Card
        LinearLayout listCard = createCard();
        TextView appHeader = new TextView(this);
        appHeader.setText("Daftar Aplikasi STB");
        appHeader.setTextColor(COLOR_TEXT_PRIMARY);
        appHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        appHeader.setTypeface(Typeface.DEFAULT_BOLD);
        appHeader.setPadding(0, 0, 0, dp(10));
        listCard.addView(appHeader);

        appSearchInput = new EditText(this);
        appSearchInput.setHint("Cari aplikasi di STB...");
        appSearchInput.setHintTextColor(Color.parseColor("#94A3B8"));
        appSearchInput.setTextColor(COLOR_TEXT_PRIMARY);
        appSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        appSearchInput.setSingleLine(true);
        appSearchInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        appSearchInput.setBackground(makeInputBg());
        listCard.addView(appSearchInput);

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
        appListContainer.setPadding(0, dp(12), 0, 0);
        listCard.addView(appListContainer);

        col.addView(listCard, cardLp());

        scroll.addView(col);
        return scroll;
    }

    // =========================================================================
    // ACTIONS & LOGIC (WITH ANIMATED LOADING & POPUPS)
    // =========================================================================
    private void onConnectClicked() {
        if (AdbClient.get().isConnected()) {
            AdbClient.get().disconnect();
            refreshConnectionUi();
            showSuccessPopup("Koneksi Diputuskan", "Koneksi ADB ke STB telah terputus.");
            return;
        }

        String rawIp = ipInput.getText().toString().trim();
        if (rawIp.isEmpty()) {
            showErrorPopup("IP Belum Diisi", "Silakan masukkan alamat IP STB Anda (contoh: 192.168.1.10).");
            return;
        }

        connectBtn.setEnabled(false);
        showLoading("Menghubungkan ke STB",
                "Menghubungkan ke " + rawIp + "...\n\n"
                        + "👉 Periksa layar TV Anda:\nJika muncul dialog 'Izinkan USB Debugging?', centang 'Selalu izinkan dari komputer ini' lalu pilih 'Izinkan'.");

        new Thread(() -> {
            try {
                AdbClient.get().connect(MainActivity.this, rawIp, 5555);
                prefs.edit().putString(KEY_LAST_IP, rawIp).apply();
                uiHandler.post(() -> {
                    connectBtn.setEnabled(true);
                    refreshConnectionUi();
                    hideLoading();
                    showSuccessPopup("Berhasil Terhubung!",
                            "Koneksi ADB ke STB (" + rawIp + ") aktif.\nAnda sekarang dapat menggunakan Remote, Live Mirror, dan Alat STB.");
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    connectBtn.setEnabled(true);
                    refreshConnectionUi();
                    hideLoading();
                    showErrorPopup("Gagal Terhubung ke STB",
                            "Tidak dapat menyambung ke " + rawIp + ":\n" + e.getMessage() + "\n\n"
                                    + "Periksa hal berikut:\n"
                                    + "1. HP dan TV terhubung ke jaringan Wi-Fi yang sama.\n"
                                    + "2. Opsi Pengembang & USB Debugging di TV sudah AKTIF.\n"
                                    + "3. Alamat IP TV sudah sesuai.");
                });
            }
        }).start();
    }

    private void startSubnetScan() {
        discoveredChipsRow.removeAllViews();
        scanStatusText.setText("Memindai Wi-Fi...");
        showLoading("Memindai Jaringan Wi-Fi", "Sedang mencari perangkat STB Android di jaringan lokal...");

        AdbClient.scanSubnet(this, new AdbClient.ScanCallback() {
            @Override
            public void onDeviceFound(final String ip) {
                uiHandler.post(() -> {
                    TextView chip = createSecondaryButton("📺 " + ip, () -> {
                        ipInput.setText(ip);
                        onConnectClicked();
                    });
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                    chip.setPadding(dp(12), dp(6), dp(12), dp(6));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.rightMargin = dp(8);
                    lp.topMargin = dp(6);
                    discoveredChipsRow.addView(chip, lp);
                });
            }

            @Override
            public void onScanFinished(final List<String> foundIps) {
                uiHandler.post(() -> {
                    hideLoading();
                    if (foundIps.isEmpty()) {
                        scanStatusText.setText("Tidak ditemukan STB di jaringan ini.");
                        showErrorPopup("Tidak Ditemukan STB",
                                "Tidak ada perangkat dengan port ADB (5555) terbuka di jaringan ini.\nPastikan USB Debugging di STB sudah aktif.");
                    } else {
                        scanStatusText.setText("Ditemukan " + foundIps.size() + " STB.");
                        if (ipInput.getText().toString().trim().isEmpty()) {
                            ipInput.setText(foundIps.get(0));
                        }
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < foundIps.size(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(foundIps.get(i));
                        }
                        showSuccessPopup("STB Ditemukan!", "Ditemukan " + foundIps.size() + " perangkat STB:\n" + sb.toString());
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
            statusPill.setTextColor(Color.parseColor("#15803D")); // Green 700
            pillBg.setColor(Color.parseColor("#DCFCE7")); // Green 100
            connectBtn.setText("Putus");
        } else {
            statusPill.setText("○ BELUM TERHUBUNG");
            statusPill.setTextColor(Color.parseColor("#DC2626")); // Red 600
            pillBg.setColor(Color.parseColor("#FEE2E2")); // Red 100
            connectBtn.setText("Hubungkan");
        }
        statusPill.setBackground(pillBg);
    }

    private void clearAllStbCaches() {
        if (!ensureConnected()) return;
        showLoading("Membersihkan Cache STB", "Sedang menghapus seluruh file cache aplikasi pada STB...");

        new Thread(() -> {
            try {
                AdbClient.get().execCommand("pm trim-caches 999999999999999999");
                uiHandler.post(() -> {
                    hideLoading();
                    showSuccessPopup("Cache STB Dibersihkan!", "Semua file cache sementara pada sistem STB telah berhasil dikosongkan.");
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    hideLoading();
                    showErrorPopup("Gagal Membersihkan Cache", e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Captures a screenshot from the STB and saves it directly to phone's Pictures/AuraTV album.
     */
    private void takeStbScreenshot() {
        if (!ensureConnected()) return;
        showLoading("Mengambil Tangkapan Layar", "Menangkap frame layar beresolusi penuh dari STB...");

        new Thread(() -> {
            try {
                final Bitmap bmp = AdbClient.get().captureScreenshot(1);
                if (bmp == null) {
                    throw new Exception("Gagal membaca gambar tangkapan layar dari STB.");
                }

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String fileName = "STB_Screenshot_" + timeStamp + ".png";
                Uri savedUri = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AuraTV");
                    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                            if (out != null) {
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                                savedUri = uri;
                            }
                        }
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES), "AuraTV");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, fileName);
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                        savedUri = Uri.fromFile(file);
                    }
                }

                final Uri finalUri = savedUri;
                uiHandler.post(() -> {
                    hideLoading();
                    showScreenshotPreviewDialog(bmp, fileName, finalUri);
                });

            } catch (Exception e) {
                uiHandler.post(() -> {
                    hideLoading();
                    showErrorPopup("Gagal Mengambil Screenshot", e.getMessage());
                });
            }
        }).start();
    }

    private void showScreenshotPreviewDialog(Bitmap bmp, String fileName, final Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setTitle("📸 Tangkapan Layar STB");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(10), dp(18), dp(10));

        ImageView preview = new ImageView(this);
        preview.setImageBitmap(bmp);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200));
        imgLp.bottomMargin = dp(10);
        layout.addView(preview, imgLp);

        TextView info = new TextView(this);
        info.setText("✓ Berhasil disimpan ke Galeri:\n" + fileName);
        info.setTextColor(COLOR_TEXT_PRIMARY);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        layout.addView(info);

        builder.setView(layout);
        if (uri != null) {
            builder.setPositiveButton("Buka", (d, w) -> {
                try {
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                    viewIntent.setDataAndType(uri, "image/*");
                    viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(viewIntent);
                } catch (Exception ignored) {
                    Toast.makeText(this, "Tersimpan di Pictures/AuraTV", Toast.LENGTH_SHORT).show();
                }
            });
        }
        builder.setNegativeButton("Tutup", null);
        builder.show();
    }

    /**
     * Enables Media Notification Listener on the STB for Aura TV Home automatically via ADB commands.
     */
    private void enableMediaListener() {
        if (!ensureConnected()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setTitle("🎵 Aktifkan Media Listener");
        builder.setMessage("Fitur ini akan memberikan izin Listener Notifikasi & Write Secure Settings ke aplikasi Aura TV Home (com.gathin.tvhome) secara otomatis melalui ADB.\n\nDengan ini, status Now Playing (Spotify, YouTube Music, dll) akan otomatis muncul di status bar Aura TV Home.");
        builder.setPositiveButton("Aktifkan Sekarang", (dialog, which) -> {
            showLoading("Mengaktifkan Media Listener", "Menerapkan konfigurasi izin ADB ke STB...");
            new Thread(() -> {
                try {
                    // Command 1: Allow Notification Listener
                    AdbClient.get().execCommand("cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaNotificationListener");
                    // Command 2: Grant Write Secure Settings
                    AdbClient.get().execCommand("pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS");

                    uiHandler.post(() -> {
                        hideLoading();
                        showSuccessPopup("Media Listener Berhasil Aktif!",
                                "Izin telah diberikan ke Aura TV Home!\n\nSaat Anda memutar lagu di Spotify atau YouTube Music di TV, widget Now Playing akan otomatis muncul di status bar launcher.");
                    });
                } catch (Exception e) {
                    uiHandler.post(() -> {
                        hideLoading();
                        showErrorPopup("Gagal Mengaktifkan Media Listener", e.getMessage());
                    });
                }
            }).start();
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void confirmPowerAction(String title, final String cmd) {
        if (!ensureConnected()) return;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
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
                            showSuccessPopup(title, "Perintah " + title + " telah dikirim ke STB.");
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
        showLoading("Mengirim & Menginstal APK", "Menyiapkan berkas APK...");

        new Thread(() -> {
            try {
                long fileSize = queryUriSize(uri);
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new Exception("File tidak dapat dibaca");
                    String result = AdbClient.get().installApk(in, fileSize, (percent, stage) ->
                            uiHandler.post(() -> {
                                updateLoadingMessage(percent + "% • " + stage);
                            }));
                    uiHandler.post(() -> {
                        hideLoading();
                        showSuccessPopup("Instalasi Berhasil!", "Aplikasi berhasil dipasang di STB:\n" + result);
                        loadInstalledPackages();
                    });
                }
            } catch (Exception e) {
                uiHandler.post(() -> {
                    hideLoading();
                    showErrorPopup("Gagal Menginstal APK", e.getMessage());
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
        loading.setTextColor(COLOR_TEXT_SECONDARY);
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
                    err.setTextColor(Color.parseColor("#DC2626"));
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
            item.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(COLOR_CARD);
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), COLOR_CARD_BORDER);
            item.setBackground(bg);

            TextView pkgName = new TextView(this);
            pkgName.setText(pkg);
            pkgName.setTextColor(COLOR_TEXT_PRIMARY);
            pkgName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            pkgName.setTypeface(Typeface.DEFAULT_BOLD);
            item.addView(pkgName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView actionBtn = createButton("Buka", "#0284C7", () -> {
                AdbClient.get().sendFastInput("monkey -p " + pkg + " 1");
                Toast.makeText(this, "Membuka " + pkg, Toast.LENGTH_SHORT).show();
            });
            actionBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            actionBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
            item.addView(actionBtn);

            TextView unBtn = createButton("Hapus", "#DC2626", () -> {
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                        .setTitle("Hapus " + pkg + "?")
                        .setPositiveButton("Ya", (d, w) -> {
                            showLoading("Menghapus Aplikasi", "Sedang mencopot " + pkg + " dari STB...");
                            new Thread(() -> {
                                try {
                                    AdbClient.get().execCommand("pm uninstall " + pkg);
                                    uiHandler.post(() -> {
                                        hideLoading();
                                        showSuccessPopup("Aplikasi Dihapus", pkg + " berhasil dihapus dari STB.");
                                        loadInstalledPackages();
                                    });
                                } catch (Exception err) {
                                    uiHandler.post(() -> {
                                        hideLoading();
                                        showErrorPopup("Gagal Menghapus", err.getMessage());
                                    });
                                }
                            }).start();
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            });
            unBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            unBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
            LinearLayout.LayoutParams unLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unLp.leftMargin = dp(6);
            item.addView(unBtn, unLp);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            appListContainer.addView(item, lp);
        }
    }

    private boolean ensureConnected() {
        if (!AdbClient.get().isConnected()) {
            showErrorPopup("Belum Terhubung", "Silakan hubungkan ke IP STB terlebih dahulu.");
            return false;
        }
        return true;
    }

    // =========================================================================
    // DIALOG HELPERS (ANIMATED LOADING & POPUPS)
    // =========================================================================
    private void showLoading(String title, String message) {
        hideLoading();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setCancelable(false);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        ProgressBar pb = new ProgressBar(this);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        pbLp.bottomMargin = dp(16);
        box.addView(pb, pbLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(COLOR_TEXT_PRIMARY);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, dp(8));
        box.addView(tvTitle);

        loadingMsgText = new TextView(this);
        loadingMsgText.setText(message);
        loadingMsgText.setTextColor(COLOR_TEXT_SECONDARY);
        loadingMsgText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        loadingMsgText.setGravity(Gravity.CENTER);
        box.addView(loadingMsgText);

        builder.setView(box);
        loadingDialog = builder.create();
        loadingDialog.show();
    }

    private void updateLoadingMessage(String message) {
        if (loadingMsgText != null) {
            loadingMsgText.setText(message);
        }
    }

    private void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }

    private void showSuccessPopup(String title, String message) {
        hideLoading();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("✓ " + title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showErrorPopup(String title, String message) {
        hideLoading();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("✕ " + title)
                .setMessage(message)
                .setPositiveButton("Mengerti", null)
                .show();
    }

    // =========================================================================
    // UI HELPERS (TACTILE FEEDBACK & MODERN LIGHT)
    // =========================================================================
    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), COLOR_CARD_BORDER);
        card.setBackground(bg);
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        return lp;
    }

    private GradientDrawable makeInputBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#CBD5E1"));
        return bg;
    }

    private TextView createButton(String text, String hexColor, final Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(16), dp(11), dp(16), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(hexColor));
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);

        applyPressFeedback(btn);

        btn.setOnClickListener(v -> {
            vibrate(10);
            onClick.run();
        });
        return btn;
    }

    private TextView createSecondaryButton(String text, final Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(COLOR_PRIMARY);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(14), dp(11), dp(14), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F0F9FF")); // Sky 50
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#BAE6FD")); // Sky 200
        btn.setBackground(bg);

        applyPressFeedback(btn);

        btn.setOnClickListener(v -> {
            vibrate(10);
            onClick.run();
        });
        return btn;
    }

    private View createToolButton(String title, String subtitle, final Runnable onClick) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), COLOR_CARD_BORDER);
        btn.setBackground(bg);

        applyPressFeedback(btn);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(COLOR_TEXT_PRIMARY);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        btn.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextColor(COLOR_TEXT_SECONDARY);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setPadding(0, dp(2), 0, 0);
        btn.addView(tvSub);

        btn.setOnClickListener(v -> {
            vibrate(10);
            onClick.run();
        });
        return btn;
    }

    private TextView createRemoteKeyButton(String label, final int keyCode) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(COLOR_TEXT_PRIMARY);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(10), dp(12), dp(10), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F8FAFC"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), COLOR_CARD_BORDER);
        btn.setBackground(bg);

        applyPressFeedback(btn);

        btn.setOnClickListener(v -> {
            vibrate(10);
            if (ensureConnected()) {
                AdbClient.get().sendKey(keyCode);
            }
        });
        return btn;
    }

    private TextView createDpadButton(String label, final int keyCode, boolean isCenter) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(isCenter ? Color.WHITE : COLOR_TEXT_PRIMARY);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCenter ? 16f : 18f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isCenter ? COLOR_PRIMARY : COLOR_CARD);
        bg.setCornerRadius(dp(isCenter ? 20 : 16));
        bg.setStroke(dp(1.5f), isCenter ? Color.parseColor("#38BDF8") : Color.parseColor("#CBD5E1"));
        btn.setBackground(bg);

        applyPressFeedback(btn);

        btn.setOnClickListener(v -> {
            vibrate(12);
            if (ensureConnected()) {
                AdbClient.get().sendKey(keyCode);
            }
        });
        return btn;
    }

    /**
     * Tactile press animation feedback: shrinks slightly on press, springs back on release.
     */
    private static void applyPressFeedback(final View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start();
                    break;
            }
            return false;
        });
    }

    private LinearLayout.LayoutParams gridLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
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
