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
import android.text.InputType;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.auratvtools.adb.AdbClient;
import com.gathin.auratvtools.mirror.MirrorActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aura TV Tools — All-in-One Android TV / STB Remote Manager, APK Sideloader,
 * App Manager, and Live Touch Screen Mirroring Controller.
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "aura_tv_tools_prefs";
    private static final String KEY_LAST_IP = "last_stb_ip";
    private static final int REQ_PICK_APK = 1001;
    private static final int APP_VERSION_CODE = 1;
    private static final String APP_VERSION_NAME = "1.0.0";
    private static final String VERSION_JSON_URL =
            "https://raw.githubusercontent.com/luthpunk/aura-tv-tools/main/release/version.json";

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // Header Status
    private TextView statusPill;
    private TextView statusSubText;

    // Tab Views
    private View tabHome;
    private View tabRemote;
    private View tabApps;
    private View tabTerminal;
    private final TextView[] navButtons = new TextView[4];

    // Tab 1: Home & Connection widgets
    private EditText ipInput;
    private TextView connectBtn;
    private TextView scanStatusText;
    private LinearLayout discoveredChipsRow;
    private TextView sysModelVal, sysAndroidVal, sysRamVal, sysStorageVal, sysTempVal;

    // Tab 3: Sideloader & App Manager widgets
    private TextView apkStatusText;
    private ProgressBar apkProgressBar;
    private LinearLayout appListContainer;
    private EditText appSearchInput;
    private boolean showSystemApps = false;
    private final List<String> cachedPackages = new ArrayList<>();

    // Tab 4: Terminal
    private EditText shellInput;
    private TextView shellOutputText;

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

        // Top App Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(18), dp(14), dp(18), dp(12));
        topBar.setBackgroundColor(Color.parseColor("#0B1120"));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView appTitle = new TextView(this);
        appTitle.setText("Aura TV Tools");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(appTitle);

        statusSubText = new TextView(this);
        statusSubText.setText("Wireless ADB & Live Screen Mirroring");
        statusSubText.setTextColor(Color.parseColor("#94A3B8"));
        statusSubText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        titleCol.addView(statusSubText);

        topBar.addView(titleCol, titleLp);

        statusPill = new TextView(this);
        statusPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        statusPill.setTypeface(Typeface.DEFAULT_BOLD);
        statusPill.setPadding(dp(12), dp(6), dp(12), dp(6));
        topBar.addView(statusPill);

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Thin divider
        View topDivider = new View(this);
        topDivider.setBackgroundColor(Color.parseColor("#1E293B"));
        root.addView(topDivider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // Content Frame
        FrameLayout contentFrame = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);

        tabHome = buildHomeTab();
        tabRemote = buildRemoteTab();
        tabApps = buildAppsTab();
        tabTerminal = buildTerminalTab();

        contentFrame.addView(tabHome);
        contentFrame.addView(tabRemote);
        contentFrame.addView(tabApps);
        contentFrame.addView(tabTerminal);

        root.addView(contentFrame, contentLp);

        // Bottom Navigation Bar
        View navDivider = new View(this);
        navDivider.setBackgroundColor(Color.parseColor("#1E293B"));
        root.addView(navDivider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(Color.parseColor("#0B1120"));
        bottomNav.setPadding(dp(8), dp(8), dp(8), dp(8));

        String[] labels = {"🏠 Beranda", "🎮 Remote", "📦 Aplikasi", "💻 Terminal"};
        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextView tabBtn = new TextView(this);
            tabBtn.setText(labels[i]);
            tabBtn.setGravity(Gravity.CENTER);
            tabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            tabBtn.setTypeface(Typeface.DEFAULT_BOLD);
            tabBtn.setPadding(dp(8), dp(10), dp(8), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            tabBtn.setOnClickListener(v -> {
                vibrate(12);
                selectTab(index);
            });
            navButtons[i] = tabBtn;
            bottomNav.addView(tabBtn, lp);
        }

        root.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        selectTab(0);
    }

    private void selectTab(int index) {
        tabHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        tabRemote.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabApps.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        tabTerminal.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < 4; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            if (i == index) {
                bg.setColor(Color.parseColor("#2638BDF8"));
                bg.setStroke(dp(1), Color.parseColor("#6638BDF8"));
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
    // TAB 1: BERANDA, KONEKSI, LIVE MIRROR & MONITOR STB
    // =========================================================================
    private View buildHomeTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(24));

        // 1. Connection Card
        LinearLayout connCard = createCard();
        addSectionTitle(connCard, "🔌 Koneksi Wireless ADB (Port 5555)");

        LinearLayout ipRow = new LinearLayout(this);
        ipRow.setOrientation(LinearLayout.HORIZONTAL);
        ipRow.setGravity(Gravity.CENTER_VERTICAL);

        ipInput = new EditText(this);
        ipInput.setHint("IP STB (cth: 192.168.1.10)");
        ipInput.setHintTextColor(Color.parseColor("#64748B"));
        ipInput.setTextColor(Color.WHITE);
        ipInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        ipInput.setSingleLine(true);
        ipInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        ipInput.setBackground(makeInputBg());
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ipLp.setMarginEnd(dp(10));
        ipRow.addView(ipInput, ipLp);

        connectBtn = createPrimaryButton("Hubungkan", "#0284C7", this::onConnectClicked);
        ipRow.addView(connectBtn);
        connCard.addView(ipRow);

        // Auto Scan Button
        TextView scanBtn = createSecondaryButton("🔍 Scan Otomatis STB di Wi-Fi", this::startSubnetScan);
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scanLp.topMargin = dp(10);
        connCard.addView(scanBtn, scanLp);

        scanStatusText = new TextView(this);
        scanStatusText.setText("Tips: Aktifkan Opsi Pengembang & Debugging Nirkabel/USB di STB.");
        scanStatusText.setTextColor(Color.parseColor("#94A3B8"));
        scanStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        scanStatusText.setPadding(0, dp(8), 0, dp(4));
        connCard.addView(scanStatusText);

        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        discoveredChipsRow = new LinearLayout(this);
        discoveredChipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsScroll.addView(discoveredChipsRow);
        connCard.addView(chipsScroll);

        col.addView(connCard, cardLp());

        // 2. Hero Live Screen Mirroring Card
        LinearLayout mirrorCard = createCard();
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0F2942"), Color.parseColor("#111C35")});
        heroBg.setCornerRadius(dp(18));
        heroBg.setStroke(dp(1.5f), Color.parseColor("#6638BDF8"));
        mirrorCard.setBackground(heroBg);

        TextView heroBadge = new TextView(this);
        heroBadge.setText("✨ FITUR UTAMA • SENTUH LANGSUNG");
        heroBadge.setTextColor(Color.parseColor("#38BDF8"));
        heroBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        heroBadge.setTypeface(Typeface.DEFAULT_BOLD);
        mirrorCard.addView(heroBadge);

        TextView heroTitle = new TextView(this);
        heroTitle.setText("📺 Live Screen Mirroring STB ke HP");
        heroTitle.setTextColor(Color.WHITE);
        heroTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        heroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        heroTitle.setPadding(0, dp(4), 0, dp(4));
        mirrorCard.addView(heroTitle);

        TextView heroDesc = new TextView(this);
        heroDesc.setText("Tampilkan layar STB secara real-time (H.264 60fps) di HP Anda. Sentuh, geser (swipe), dan tahan langsung di layar HP untuk mengendalikan kursor/layar TV tanpa mouse!");
        heroDesc.setTextColor(Color.parseColor("#CBD5E1"));
        heroDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        mirrorCard.addView(heroDesc);

        TextView launchMirrorBtn = createPrimaryButton("▶ Buka Live Mirror & Layar Sentuh", "#0EA5E9", () -> {
            if (!AdbClient.get().isConnected()) {
                Toast.makeText(this, "Hubungkan ke IP STB terlebih dahulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, MirrorActivity.class));
        });
        LinearLayout.LayoutParams lmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lmLp.topMargin = dp(12);
        mirrorCard.addView(launchMirrorBtn, lmLp);

        col.addView(mirrorCard, cardLp());

        // 3. STB System Monitor Card
        LinearLayout sysCard = createCard();
        LinearLayout sysHeader = new LinearLayout(this);
        sysHeader.setOrientation(LinearLayout.HORIZONTAL);
        sysHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView sysTitle = new TextView(this);
        sysTitle.setText("📊 Informasi & Status STB");
        sysTitle.setTextColor(Color.WHITE);
        sysTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        sysTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sysHeader.addView(sysTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView refreshSysBtn = createSmallChip("🔄 Segarkan", this::fetchSystemStats);
        sysHeader.addView(refreshSysBtn);
        sysCard.addView(sysHeader);

        sysModelVal = addStatRow(sysCard, "Model Perangkat", "-");
        sysAndroidVal = addStatRow(sysCard, "Versi Android / Resolusi", "-");
        sysRamVal = addStatRow(sysCard, "Penggunaan RAM", "-");
        sysStorageVal = addStatRow(sysCard, "Penyimpanan Internal", "-");
        sysTempVal = addStatRow(sysCard, "Suhu CPU STB", "-");

        col.addView(sysCard, cardLp());

        // 4. Quick Power & Maintenance Tools
        LinearLayout toolsCard = createCard();
        addSectionTitle(toolsCard, "🛠 Alat Cepat & Kontrol Daya STB");

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(createGridButton("📸 Screenshot STB", this::captureAndPreviewScreenshot), gridLp());
        row1.addView(createGridButton("🧹 Bersihkan Cache", this::clearAllStbCaches), gridLp());
        toolsCard.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(createGridButton("🔄 Reboot Normal", () -> confirmPowerAction("Reboot Normal", "reboot")), gridLp());
        row2.addView(createGridButton("⚙ Reboot Recovery", () -> confirmPowerAction("Reboot ke Recovery", "reboot recovery")), gridLp());
        toolsCard.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(createGridButton("⚡ Bootloader", () -> confirmPowerAction("Reboot ke Bootloader", "reboot bootloader")), gridLp());
        row3.addView(createGridButton("🌙 Layar On/Off", () -> AdbClient.get().sendKey(KeyEvent.KEYCODE_POWER)), gridLp());
        toolsCard.addView(row3);

        col.addView(toolsCard, cardLp());

        scroll.addView(col);
        return scroll;
    }

    // =========================================================================
    // TAB 2: REMOTE D-PAD, MEDIA & KEYBOARD
    // =========================================================================
    private View buildRemoteTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(24));

        // Direct Keyboard Card
        LinearLayout kbCard = createCard();
        addSectionTitle(kbCard, "⌨ Ketik Teks Langsung ke Layar TV");

        LinearLayout kbRow = new LinearLayout(this);
        kbRow.setOrientation(LinearLayout.HORIZONTAL);
        kbRow.setGravity(Gravity.CENTER_VERTICAL);

        final EditText kbInput = new EditText(this);
        kbInput.setHint("Ketik pencarian YouTube, sandi, URL...");
        kbInput.setHintTextColor(Color.parseColor("#64748B"));
        kbInput.setTextColor(Color.WHITE);
        kbInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        kbInput.setSingleLine(true);
        kbInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        kbInput.setBackground(makeInputBg());
        LinearLayout.LayoutParams kbLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        kbLp.setMarginEnd(dp(8));
        kbRow.addView(kbInput, kbLp);

        TextView sendTxtBtn = createPrimaryButton("Kirim", "#0284C7", () -> {
            String text = kbInput.getText().toString();
            if (!text.isEmpty()) {
                AdbClient.get().sendText(text);
                kbInput.setText("");
            }
        });
        kbRow.addView(sendTxtBtn);
        kbCard.addView(kbRow);

        LinearLayout kbQuickRow = new LinearLayout(this);
        kbQuickRow.setOrientation(LinearLayout.HORIZONTAL);
        kbQuickRow.setPadding(0, dp(8), 0, 0);
        kbQuickRow.addView(createRemoteKeyButton("⌫ Hapus (Backspace)", KeyEvent.KEYCODE_DEL), gridLp());
        kbQuickRow.addView(createRemoteKeyButton("↵ Enter / Cari", KeyEvent.KEYCODE_ENTER), gridLp());
        kbCard.addView(kbQuickRow);

        col.addView(kbCard, cardLp());

        // D-Pad Card
        LinearLayout dpadCard = createCard();
        addSectionTitle(dpadCard, "🎮 Navigasi Remote STB");

        // Top nav row: Back, Home, Recents, Menu
        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.addView(createRemoteKeyButton("◁ Back", KeyEvent.KEYCODE_BACK), gridLp());
        navRow.addView(createRemoteKeyButton("⌂ Home", KeyEvent.KEYCODE_HOME), gridLp());
        navRow.addView(createRemoteKeyButton("❐ Recents", KeyEvent.KEYCODE_APP_SWITCH), gridLp());
        navRow.addView(createRemoteKeyButton("☰ Menu", KeyEvent.KEYCODE_MENU), gridLp());
        dpadCard.addView(navRow);

        // D-Pad Cross
        LinearLayout dpadBox = new LinearLayout(this);
        dpadBox.setOrientation(LinearLayout.VERTICAL);
        dpadBox.setGravity(Gravity.CENTER);
        dpadBox.setPadding(0, dp(14), 0, dp(14));

        TextView upBtn = createDpadButton("▲", KeyEvent.KEYCODE_DPAD_UP, false);
        dpadBox.addView(upBtn, new LinearLayout.LayoutParams(dp(92), dp(64)));

        LinearLayout midRow = new LinearLayout(this);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setGravity(Gravity.CENTER);
        midRow.setPadding(0, dp(8), 0, dp(8));

        TextView leftBtn = createDpadButton("◀", KeyEvent.KEYCODE_DPAD_LEFT, false);
        TextView okBtn = createDpadButton("OK", KeyEvent.KEYCODE_DPAD_CENTER, true);
        TextView rightBtn = createDpadButton("▶", KeyEvent.KEYCODE_DPAD_RIGHT, false);

        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(dp(84), dp(74));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(dp(92), dp(74));
        okLp.setMargins(dp(10), 0, dp(10), 0);

        midRow.addView(leftBtn, sideLp);
        midRow.addView(okBtn, okLp);
        midRow.addView(rightBtn, sideLp);
        dpadBox.addView(midRow);

        TextView downBtn = createDpadButton("▼", KeyEvent.KEYCODE_DPAD_DOWN, false);
        dpadBox.addView(downBtn, new LinearLayout.LayoutParams(dp(92), dp(64)));

        dpadCard.addView(dpadBox);

        // Volume & Media Row
        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.addView(createRemoteKeyButton("🔇 Mute", KeyEvent.KEYCODE_VOLUME_MUTE), gridLp());
        volRow.addView(createRemoteKeyButton("🔉 Vol -", KeyEvent.KEYCODE_VOLUME_DOWN), gridLp());
        volRow.addView(createRemoteKeyButton("🔊 Vol +", KeyEvent.KEYCODE_VOLUME_UP), gridLp());
        volRow.addView(createRemoteKeyButton("⏻ Power", KeyEvent.KEYCODE_POWER), gridLp());
        dpadCard.addView(volRow);

        LinearLayout mediaRow = new LinearLayout(this);
        mediaRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaRow.addView(createRemoteKeyButton("⏮ Prev", KeyEvent.KEYCODE_MEDIA_PREVIOUS), gridLp());
        mediaRow.addView(createRemoteKeyButton("⏯ Play/Pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE), gridLp());
        mediaRow.addView(createRemoteKeyButton("⏭ Next", KeyEvent.KEYCODE_MEDIA_NEXT), gridLp());
        dpadCard.addView(mediaRow);

        col.addView(dpadCard, cardLp());

        scroll.addView(col);
        return scroll;
    }

    // =========================================================================
    // TAB 3: SIDELOAD INSTALL APK & KELOLA APLIKASI STB
    // =========================================================================
    private View buildAppsTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(24));

        // 1. Sideload APK Card
        LinearLayout sideloadCard = createCard();
        addSectionTitle(sideloadCard, "📦 Install APK dari HP ke STB (1-Klik)");

        TextView sideloadDesc = new TextView(this);
        sideloadDesc.setText("Pilih file .apk dari penyimpanan HP Anda untuk langsung dikirim dan diinstal ke STB tanpa flashdisk.");
        sideloadDesc.setTextColor(Color.parseColor("#94A3B8"));
        sideloadDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        sideloadCard.addView(sideloadDesc);

        TextView pickApkBtn = createPrimaryButton("📂 Pilih File APK & Install ke STB", "#0284C7", this::openApkFilePicker);
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pickLp.topMargin = dp(12);
        sideloadCard.addView(pickApkBtn, pickLp);

        apkProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        apkProgressBar.setMax(100);
        apkProgressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbLp.topMargin = dp(10);
        sideloadCard.addView(apkProgressBar, pbLp);

        apkStatusText = new TextView(this);
        apkStatusText.setTextColor(Color.parseColor("#38BDF8"));
        apkStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        apkStatusText.setVisibility(View.GONE);
        apkStatusText.setPadding(0, dp(6), 0, 0);
        sideloadCard.addView(apkStatusText);

        col.addView(sideloadCard, cardLp());

        // 2. Installed Apps Manager Card
        LinearLayout mgrCard = createCard();
        LinearLayout hdrRow = new LinearLayout(this);
        hdrRow.setOrientation(LinearLayout.HORIZONTAL);
        hdrRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView mgrTitle = new TextView(this);
        mgrTitle.setText("📱 Aplikasi Terinstal di STB");
        mgrTitle.setTextColor(Color.WHITE);
        mgrTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        mgrTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hdrRow.addView(mgrTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView filterChip = createSmallChip("User Apps", null);
        filterChip.setOnClickListener(v -> {
            showSystemApps = !showSystemApps;
            filterChip.setText(showSystemApps ? "Semua Apps" : "User Apps");
            loadInstalledPackages();
        });
        hdrRow.addView(filterChip);

        TextView reloadBtn = createSmallChip("🔄 Muat", this::loadInstalledPackages);
        LinearLayout.LayoutParams rlLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlLp.leftMargin = dp(6);
        hdrRow.addView(reloadBtn, rlLp);

        mgrCard.addView(hdrRow);

        appSearchInput = new EditText(this);
        appSearchInput.setHint("Cari nama paket aplikasi (cth: youtube, glass)...");
        appSearchInput.setHintTextColor(Color.parseColor("#64748B"));
        appSearchInput.setTextColor(Color.WHITE);
        appSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        appSearchInput.setSingleLine(true);
        appSearchInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        appSearchInput.setBackground(makeInputBg());
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = dp(10);
        searchLp.bottomMargin = dp(8);
        mgrCard.addView(appSearchInput, searchLp);

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
        mgrCard.addView(appListContainer);

        col.addView(mgrCard, cardLp());

        scroll.addView(col);
        return scroll;
    }

    // =========================================================================
    // TAB 4: TERMINAL ADB & UPDATE OTA
    // =========================================================================
    private View buildTerminalTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(24));

        // 1. Terminal Card
        LinearLayout termCard = createCard();
        addSectionTitle(termCard, "💻 Terminal Shell ADB");

        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] presets = {"wm size", "df -h", "free -m", "getprop ro.product.model", "ip addr", "pm list packages -3"};
        for (final String cmd : presets) {
            TextView chip = createSmallChip(cmd, () -> {
                shellInput.setText(cmd);
                runCustomShellCommand();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = dp(6);
            presetRow.addView(chip, cp);
        }
        presetScroll.addView(presetRow);
        termCard.addView(presetScroll);

        LinearLayout cmdRow = new LinearLayout(this);
        cmdRow.setOrientation(LinearLayout.HORIZONTAL);
        cmdRow.setGravity(Gravity.CENTER_VERTICAL);
        cmdRow.setPadding(0, dp(10), 0, dp(10));

        shellInput = new EditText(this);
        shellInput.setHint("Masukkan perintah shell ADB...");
        shellInput.setHintTextColor(Color.parseColor("#64748B"));
        shellInput.setTextColor(Color.WHITE);
        shellInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        shellInput.setTypeface(Typeface.MONOSPACE);
        shellInput.setSingleLine(true);
        shellInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        shellInput.setBackground(makeInputBg());
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        shLp.setMarginEnd(dp(8));
        cmdRow.addView(shellInput, shLp);

        TextView runBtn = createPrimaryButton("Jalankan", "#0284C7", this::runCustomShellCommand);
        cmdRow.addView(runBtn);
        termCard.addView(cmdRow);

        shellOutputText = new TextView(this);
        shellOutputText.setText("$ Siap menjalankan perintah shell di STB...\n");
        shellOutputText.setTextColor(Color.parseColor("#4ADE80"));
        shellOutputText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        shellOutputText.setTypeface(Typeface.MONOSPACE);
        shellOutputText.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable termBg = new GradientDrawable();
        termBg.setColor(Color.parseColor("#050811"));
        termBg.setCornerRadius(dp(12));
        termBg.setStroke(dp(1), Color.parseColor("#1E293B"));
        shellOutputText.setBackground(termBg);
        shellOutputText.setMinLines(8);
        termCard.addView(shellOutputText);

        col.addView(termCard, cardLp());

        // 2. About & OTA Update Card
        LinearLayout aboutCard = createCard();
        addSectionTitle(aboutCard, "🚀 Tentang Aura TV Tools & Pembaruan OTA");

        TextView verInfo = new TextView(this);
        verInfo.setText("Versi Aplikasi: v" + APP_VERSION_NAME + " (Build " + APP_VERSION_CODE + ")\n"
                + "Mendukung semua STB Android TV / Amlogic / Rockchip / Allwinner via Wireless ADB.");
        verInfo.setTextColor(Color.parseColor("#94A3B8"));
        verInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        aboutCard.addView(verInfo);

        TextView otaBtn = createSecondaryButton("🔄 Cek Update Aplikasi dari GitHub", this::checkForOtaUpdate);
        LinearLayout.LayoutParams otaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        otaLp.topMargin = dp(10);
        aboutCard.addView(otaBtn, otaLp);

        col.addView(aboutCard, cardLp());

        scroll.addView(col);
        return scroll;
    }

    // =========================================================================
    // ACTIONS & ADB LOGIC
    // =========================================================================
    private void onConnectClicked() {
        if (AdbClient.get().isConnected()) {
            AdbClient.get().disconnect();
            refreshConnectionUi();
            Toast.makeText(this, "Koneksi ke STB diputuskan", Toast.LENGTH_SHORT).show();
            return;
        }

        String rawIp = ipInput.getText().toString().trim();
        if (rawIp.isEmpty()) {
            Toast.makeText(this, "Masukkan alamat IP STB terlebih dahulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        String host = rawIp;
        int port = 5555;
        if (rawIp.contains(":")) {
            String[] parts = rawIp.split(":");
            host = parts[0].trim();
            try {
                port = Integer.parseInt(parts[1].trim());
            } catch (Exception ignored) {
            }
        }

        final String finalHost = host;
        final int finalPort = port;

        connectBtn.setEnabled(false);
        connectBtn.setText("Menghubungkan...");
        scanStatusText.setText("Menghubungi " + finalHost + ":" + finalPort + " — Jika muncul popup di TV, pilih 'Allow / Izinkan'...");

        new Thread(() -> {
            try {
                AdbClient.get().connect(MainActivity.this, finalHost, finalPort);
                prefs.edit().putString(KEY_LAST_IP, finalHost).apply();
                uiHandler.post(() -> {
                    connectBtn.setEnabled(true);
                    refreshConnectionUi();
                    scanStatusText.setText("Terhubung ke STB " + finalHost + ":" + finalPort);
                    Toast.makeText(MainActivity.this, "Berhasil terhubung ke STB!", Toast.LENGTH_SHORT).show();
                    fetchSystemStats();
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    connectBtn.setEnabled(true);
                    refreshConnectionUi();
                    scanStatusText.setText("Gagal terhubung: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "adb-connect").start();
    }

    private void startSubnetScan() {
        discoveredChipsRow.removeAllViews();
        scanStatusText.setText("Memindai jaringan Wi-Fi lokal pada port 5555...");
        AdbClient.scanSubnet(this, new AdbClient.ScanCallback() {
            @Override
            public void onDeviceFound(final String ip) {
                uiHandler.post(() -> {
                    TextView chip = createSmallChip("📺 " + ip, () -> {
                        ipInput.setText(ip);
                        if (!AdbClient.get().isConnected()) {
                            onConnectClicked();
                        }
                    });
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
                        scanStatusText.setText("Tidak ditemukan STB dengan port 5555 terbuka di subnet ini. Pastikan Wireless ADB aktif di STB.");
                    } else {
                        scanStatusText.setText("Ditemukan " + foundIps.size() + " perangkat STB! Ketuk IP di bawah untuk menghubungkan:");
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
            pillBg.setStroke(dp(1), Color.parseColor("#4D4ADE80"));
            statusSubText.setText("STB Aktif: " + AdbClient.get().getConnectedHost() + ":" + AdbClient.get().getConnectedPort());
            connectBtn.setText("Putuskan");
        } else {
            statusPill.setText("○ BELUM TERHUBUNG");
            statusPill.setTextColor(Color.parseColor("#F87171"));
            pillBg.setColor(Color.parseColor("#1AF87171"));
            pillBg.setStroke(dp(1), Color.parseColor("#4DF87171"));
            statusSubText.setText("Wireless ADB & Live Screen Mirroring");
            connectBtn.setText("Hubungkan");
        }
        statusPill.setBackground(pillBg);
    }

    private void fetchSystemStats() {
        if (!AdbClient.get().isConnected()) return;
        new Thread(() -> {
            try {
                String script = "echo MODEL=$(getprop ro.product.manufacturer) $(getprop ro.product.model);"
                        + "echo ANDROID=Android $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk));"
                        + "echo WMSIZE=$(wm size | tail -n 1);"
                        + "echo MEM=$(awk '/MemTotal/{t=$2} /MemAvailable/{a=$2} END{printf \"%d MB / %d MB\", (t-a)/1024, t/1024}' /proc/meminfo);"
                        + "echo DISK=$(df -h /data | tail -n 1 | awk '{print $3 \" / \" $2 \" (\" $5 \")\"}');"
                        + "echo TEMP=$(cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null || echo 0)";
                String out = AdbClient.get().execCommand(script);

                String model = "-", androidVer = "-", wmSize = "", mem = "-", disk = "-", temp = "-";
                for (String line : out.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("MODEL=")) model = line.substring(6).trim();
                    else if (line.startsWith("ANDROID=")) androidVer = line.substring(8).trim();
                    else if (line.startsWith("WMSIZE=")) wmSize = line.substring(7).replace("Physical size:", "").replace("Override size:", "").trim();
                    else if (line.startsWith("MEM=")) mem = line.substring(4).trim();
                    else if (line.startsWith("DISK=")) disk = line.substring(5).trim();
                    else if (line.startsWith("TEMP=")) {
                        String rawT = line.substring(5).trim();
                        try {
                            float v = Float.parseFloat(rawT);
                            if (v > 1000) v = v / 1000f;
                            if (v > 0) temp = String.format("%.1f °C", v);
                        } catch (Exception ignored) {
                        }
                    }
                }

                final String fModel = model;
                final String fAndroid = wmSize.isEmpty() ? androidVer : (androidVer + " • " + wmSize);
                final String fMem = mem;
                final String fDisk = disk;
                final String fTemp = temp;

                uiHandler.post(() -> {
                    sysModelVal.setText(fModel);
                    sysAndroidVal.setText(fAndroid);
                    sysRamVal.setText(fMem);
                    sysStorageVal.setText(fDisk);
                    sysTempVal.setText(fTemp);
                });
            } catch (Exception ignored) {
            }
        }, "sys-stats").start();
    }

    private void captureAndPreviewScreenshot() {
        if (!ensureConnected()) return;
        Toast.makeText(this, "Mengambil screenshot dari STB...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final Bitmap bmp = AdbClient.get().captureScreenshot();
                if (bmp == null) throw new Exception("Data gambar kosong");
                uiHandler.post(() -> showScreenshotDialog(bmp));
            } catch (Exception e) {
                uiHandler.post(() -> Toast.makeText(this, "Gagal screenshot: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }, "shot-preview").start();
    }

    private void showScreenshotDialog(final Bitmap bmp) {
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("Screenshot STB (" + bmp.getWidth() + "x" + bmp.getHeight() + ")");

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.setView(iv);

        b.setPositiveButton("💾 Simpan ke Galeri HP", (d, w) -> {
            new Thread(() -> {
                try {
                    String path = saveBitmapToGallery(bmp);
                    uiHandler.post(() -> Toast.makeText(this, "Tersimpan di: " + path, Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    uiHandler.post(() -> Toast.makeText(this, "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }).start();
        });
        b.setNegativeButton("Tutup", null);
        b.show();
    }

    private String saveBitmapToGallery(Bitmap bmp) throws Exception {
        String filename = "STB_" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AuraTVTools");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                return "Pictures/AuraTVTools/" + filename;
            }
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AuraTVTools");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        return file.getAbsolutePath();
    }

    private void clearAllStbCaches() {
        if (!ensureConnected()) return;
        Toast.makeText(this, "Membersihkan cache aplikasi di STB...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                AdbClient.get().execCommand("pm trim-caches 999999999999999999");
                uiHandler.post(() -> {
                    Toast.makeText(this, "Cache STB berhasil dibersihkan!", Toast.LENGTH_SHORT).show();
                    fetchSystemStats();
                });
            } catch (Exception e) {
                uiHandler.post(() -> Toast.makeText(this, "Gagal: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void confirmPowerAction(String title, final String cmd) {
        if (!ensureConnected()) return;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setMessage("Apakah Anda yakin ingin menjalankan '" + title + "' pada STB?")
                .setPositiveButton("Ya, Jalankan", (d, w) -> {
                    new Thread(() -> {
                        try {
                            AdbClient.get().execCommand(cmd);
                        } catch (Exception ignored) {
                        }
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
        apkStatusText.setText("Menyiapkan pengiriman APK...");

        new Thread(() -> {
            try {
                long fileSize = queryUriSize(uri);
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new Exception("Tidak dapat membaca file APK");
                    String result = AdbClient.get().installApk(in, fileSize, (percent, stage) ->
                            uiHandler.post(() -> {
                                apkProgressBar.setProgress(percent);
                                apkStatusText.setText(percent + "% • " + stage);
                            }));
                    uiHandler.post(() -> {
                        apkStatusText.setText("Hasil Install: " + result);
                        Toast.makeText(this, "Install Selesai: " + result, Toast.LENGTH_LONG).show();
                        loadInstalledPackages();
                    });
                }
            } catch (Exception e) {
                uiHandler.post(() -> {
                    apkStatusText.setText("Gagal menginstal APK: " + e.getMessage());
                    Toast.makeText(this, "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "apk-sideloader").start();
    }

    private long queryUriSize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) return c.getLong(idx);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private void loadInstalledPackages() {
        if (!ensureConnected()) return;
        appListContainer.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Memuat daftar aplikasi dari STB...");
        loading.setTextColor(Color.parseColor("#94A3B8"));
        loading.setPadding(0, dp(8), 0, dp(8));
        appListContainer.addView(loading);

        new Thread(() -> {
            try {
                String flag = showSystemApps ? "" : "-3";
                String out = AdbClient.get().execCommand("pm list packages " + flag);
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
                    err.setText("Gagal memuat aplikasi: " + e.getMessage());
                    err.setTextColor(Color.parseColor("#F87171"));
                    appListContainer.addView(err);
                });
            }
        }, "pkg-loader").start();
    }

    private void renderFilteredPackages(String query) {
        appListContainer.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase();
        List<String> snapshot;
        synchronized (cachedPackages) {
            snapshot = new ArrayList<>(cachedPackages);
        }

        int count = 0;
        for (final String pkg : snapshot) {
            if (!q.isEmpty() && !pkg.toLowerCase().contains(q)) continue;
            count++;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#131C31"));
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), Color.parseColor("#1E293B"));
            item.setBackground(bg);

            TextView pkgName = new TextView(this);
            pkgName.setText(pkg);
            pkgName.setTextColor(Color.WHITE);
            pkgName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            item.addView(pkgName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView actionBtn = createSmallChip("Kelola ▸", () -> showPackageActionsDialog(pkg));
            item.addView(actionBtn);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            item.setOnClickListener(v -> showPackageActionsDialog(pkg));
            appListContainer.addView(item, lp);
        }

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("Tidak ada paket aplikasi yang cocok.");
            empty.setTextColor(Color.parseColor("#64748B"));
            empty.setPadding(0, dp(8), 0, dp(8));
            appListContainer.addView(empty);
        }
    }

    private void showPackageActionsDialog(final String pkg) {
        String[] actions = {
                "🚀 Buka Aplikasi di Layar TV",
                "🛑 Paksa Berhenti (Force Stop)",
                "🧹 Hapus Data & Cache Aplikasi",
                "🗑 Copot Pemasangan (Uninstall APK)"
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(pkg)
                .setItems(actions, (d, which) -> {
                    new Thread(() -> {
                        try {
                            String res;
                            if (which == 0) {
                                res = AdbClient.get().execCommand("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
                            } else if (which == 1) {
                                res = AdbClient.get().execCommand("am force-stop " + pkg + " && echo 'Berhasil dihentikan'");
                            } else if (which == 2) {
                                res = AdbClient.get().execCommand("pm clear " + pkg);
                            } else {
                                res = AdbClient.get().execCommand("pm uninstall " + pkg);
                            }
                            final String msg = res.trim();
                            uiHandler.post(() -> {
                                Toast.makeText(this, pkg + ": " + msg, Toast.LENGTH_SHORT).show();
                                if (which == 3) loadInstalledPackages();
                            });
                        } catch (Exception e) {
                            uiHandler.post(() -> Toast.makeText(this, "Gagal: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void runCustomShellCommand() {
        if (!ensureConnected()) return;
        final String cmd = shellInput.getText().toString().trim();
        if (cmd.isEmpty()) return;

        shellOutputText.append("\n$ " + cmd + "\n");
        new Thread(() -> {
            try {
                final String out = AdbClient.get().execCommand(cmd + " 2>&1");
                uiHandler.post(() -> shellOutputText.append(out.isEmpty() ? "(Selesai tanpa output)\n" : out + "\n"));
            } catch (Exception e) {
                uiHandler.post(() -> shellOutputText.append("Error: " + e.getMessage() + "\n"));
            }
        }, "custom-shell").start();
    }

    private void checkForOtaUpdate() {
        Toast.makeText(this, "Memeriksa pembaruan di GitHub...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(VERSION_JSON_URL).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    JSONObject json = new JSONObject(sb.toString());
                    final int remoteCode = json.optInt("versionCode", 1);
                    final String remoteName = json.optString("versionName", "1.0.0");
                    final String apkUrl = json.optString("apkUrl", "");
                    final String notes = json.optString("changelog", "");

                    uiHandler.post(() -> {
                        if (remoteCode > APP_VERSION_CODE && !apkUrl.isEmpty()) {
                            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                    .setTitle("Update Tersedia: v" + remoteName)
                                    .setMessage(notes)
                                    .setPositiveButton("Unduh APK", (d, w) ->
                                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))))
                                    .setNegativeButton("Nanti", null)
                                    .show();
                        } else {
                            Toast.makeText(this, "Aplikasi sudah versi terbaru (v" + APP_VERSION_NAME + ")", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                uiHandler.post(() -> Toast.makeText(this, "Gagal cek update: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }, "ota-check").start();
    }

    private boolean ensureConnected() {
        if (!AdbClient.get().isConnected()) {
            Toast.makeText(this, "Hubungkan ke IP STB terlebih dahulu di Tab Beranda!", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // =========================================================================
    // UI COMPONENT HELPERS
    // =========================================================================
    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0F172A"));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.parseColor("#1E293B"));
        card.setBackground(bg);
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        return lp;
    }

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 0, 0, dp(10));
        parent.addView(tv);
    }

    private TextView addStatRow(LinearLayout parent, String label, String initialVal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.parseColor("#94A3B8"));
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        row.addView(lbl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = new TextView(this);
        val.setText(initialVal);
        val.setTextColor(Color.WHITE);
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(val);

        parent.addView(row);
        return val;
    }

    private GradientDrawable makeInputBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#070B14"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#334155"));
        return bg;
    }

    private TextView createPrimaryButton(String text, String hexColor, final Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(hexColor));
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);
        btn.setOnClickListener(v -> {
            vibrate(15);
            onClick.run();
        });
        return btn;
    }

    private TextView createSecondaryButton(String text, final Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.parseColor("#38BDF8"));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(14), dp(11), dp(14), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#132238"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#3338BDF8"));
        btn.setBackground(bg);
        btn.setOnClickListener(v -> {
            vibrate(14);
            onClick.run();
        });
        return btn;
    }

    private TextView createSmallChip(String text, final Runnable onClick) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(Color.parseColor("#E2E8F0"));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(99));
        bg.setStroke(dp(1), Color.parseColor("#3394A3B8"));
        chip.setBackground(bg);
        if (onClick != null) {
            chip.setOnClickListener(v -> {
                vibrate(12);
                onClick.run();
            });
        }
        return chip;
    }

    private TextView createGridButton(String text, final Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(10), dp(12), dp(10), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#162033"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#2638BDF8"));
        btn.setBackground(bg);
        btn.setOnClickListener(v -> {
            vibrate(15);
            onClick.run();
        });
        return btn;
    }

    private TextView createRemoteKeyButton(String label, final int keyCode) {
        return createGridButton(label, () -> {
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
        bg.setCornerRadius(dp(isCenter ? 22 : 18));
        bg.setStroke(dp(1.5f), Color.parseColor(isCenter ? "#7DD3FC" : "#334155"));
        btn.setBackground(bg);
        btn.setOnClickListener(v -> {
            vibrate(18);
            if (ensureConnected()) {
                AdbClient.get().sendKey(keyCode);
            }
        });
        return btn;
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
        } catch (Exception ignored) {
        }
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
