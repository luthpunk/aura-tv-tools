package com.gathin.auratvtools.mirror;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.auratvtools.adb.AdbClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Super lightweight STB Screen Mirroring with:
 * - Left docked 16:9 Live Video / Snapshot Viewport
 * - Right dedicated Remote Control sidebar with unified D-Pad, controls, and resolution switch
 * - Tactile press bounce feedback on all buttons
 * - Instant Screenshot capture with dialog preview
 */
public class MirrorActivity extends Activity implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private ImageView frameView;
    private TextView statusBadge;
    private TextView modeBtn;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private volatile boolean surfaceReady = false;
    private Thread streamThread;

    // 0 = Fast Snapshot (100% reliable fallback), 1 = Video 720p H.264, 2 = Video 480p H.264
    private volatile int currentMode = 0;
    private volatile long lastFrameTime = 0;
    private volatile Bitmap latestFrameBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (!AdbClient.get().isConnected()) {
            Toast.makeText(this, "Belum terhubung ke STB!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        buildUi();
    }

    private void buildUi() {
        // Horizontal Root Layout: Left = Video, Right = Remote Sidebar
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.parseColor("#090D16"));

        // -------------------------------------------------------------
        // LEFT: 16:9 Video Viewport (Full height, docked strictly left)
        // -------------------------------------------------------------
        FrameLayout videoContainer = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalH = MeasureSpec.getSize(heightMeasureSpec);
                int totalW = MeasureSpec.getSize(widthMeasureSpec);
                // Standard 16:9 width based on full screen height
                int targetW = (totalH * 16) / 9;
                // Reserve minimum 220dp for right sidebar on narrower screens
                int maxVideoW = totalW - dp(210);
                if (maxVideoW > 0 && targetW > maxVideoW) {
                    targetW = maxVideoW;
                }
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(targetW, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(totalH, MeasureSpec.EXACTLY));
            }
        };
        videoContainer.setBackgroundColor(Color.BLACK);

        // Layer 1: SurfaceView for H.264 MediaCodec
        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        videoContainer.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Layer 2: ImageView for Rapid Frame Snapshot Engine
        frameView = new ImageView(this);
        frameView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frameView.setVisibility(View.VISIBLE);
        videoContainer.addView(frameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(videoContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // -------------------------------------------------------------
        // RIGHT: Dedicated Modern Light Remote & Control Sidebar
        // -------------------------------------------------------------
        ScrollView sidebarScroll = new ScrollView(this);
        sidebarScroll.setFillViewport(true);
        sidebarScroll.setBackgroundColor(Color.parseColor("#F8FAFC")); // Slate 50 Modern Light

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        sidebarScroll.setLayoutParams(scrollLp);

        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setGravity(Gravity.CENTER_HORIZONTAL);
        sidebar.setPadding(dp(10), dp(10), dp(10), dp(12));

        // 1. Top Section: Mode, Screenshot, Close
        LinearLayout topControls = new LinearLayout(this);
        topControls.setOrientation(LinearLayout.HORIZONTAL);
        topControls.setGravity(Gravity.CENTER_VERTICAL);
        topControls.setPadding(0, 0, 0, dp(6));

        modeBtn = createSidebarTopBtn("⚡ Snapshot", this::cycleMode);
        topControls.addView(modeBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView shotBtn = createSidebarTopBtn("📸", this::captureAndSaveShot);
        LinearLayout.LayoutParams shotLp = new LinearLayout.LayoutParams(dp(40), dp(36));
        shotLp.setMargins(dp(4), 0, dp(4), 0);
        topControls.addView(shotBtn, shotLp);

        TextView closeBtn = createSidebarTopBtn("✕", this::finish);
        topControls.addView(closeBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        sidebar.addView(topControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Status Badge
        statusBadge = new TextView(this);
        statusBadge.setText("● LIVE SNAPSHOT");
        statusBadge.setTextColor(Color.parseColor("#15803D"));
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#DCFCE7"));
        badgeBg.setCornerRadius(dp(99));
        badgeBg.setStroke(dp(1), Color.parseColor("#BBF7D0"));
        statusBadge.setBackground(badgeBg);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.bottomMargin = dp(8);
        sidebar.addView(statusBadge, sbLp);

        // 2. Middle Section: UNIFIED INTEGRATED D-PAD (Gabung Jadi Satu)
        LinearLayout dpadContainer = new LinearLayout(this);
        dpadContainer.setOrientation(LinearLayout.VERTICAL);
        dpadContainer.setGravity(Gravity.CENTER);
        dpadContainer.setPadding(dp(8), dp(8), dp(8), dp(8));

        GradientDrawable dpadBg = new GradientDrawable();
        dpadBg.setColor(Color.parseColor("#FFFFFF"));
        dpadBg.setCornerRadius(dp(22));
        dpadBg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        dpadContainer.setBackground(dpadBg);

        // D-Pad UP
        TextView upBtn = createDpadBtn("▲", KeyEvent.KEYCODE_DPAD_UP, false);
        dpadContainer.addView(upBtn, new LinearLayout.LayoutParams(dp(54), dp(42)));

        // D-Pad MID ROW (LEFT - OK - RIGHT)
        LinearLayout midRow = new LinearLayout(this);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setGravity(Gravity.CENTER);
        midRow.setPadding(0, dp(4), 0, dp(4));

        TextView leftBtn = createDpadBtn("◀", KeyEvent.KEYCODE_DPAD_LEFT, false);
        TextView okBtn = createDpadBtn("OK", KeyEvent.KEYCODE_DPAD_CENTER, true);
        TextView rightBtn = createDpadBtn("▶", KeyEvent.KEYCODE_DPAD_RIGHT, false);

        midRow.addView(leftBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(dp(54), dp(44));
        okLp.setMargins(dp(6), 0, dp(6), 0);
        midRow.addView(okBtn, okLp);
        midRow.addView(rightBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        dpadContainer.addView(midRow);

        // D-Pad DOWN
        TextView downBtn = createDpadBtn("▼", KeyEvent.KEYCODE_DPAD_DOWN, false);
        dpadContainer.addView(downBtn, new LinearLayout.LayoutParams(dp(54), dp(42)));

        LinearLayout.LayoutParams dpadBoxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dpadBoxLp.bottomMargin = dp(8);
        sidebar.addView(dpadContainer, dpadBoxLp);

        // 3. Bottom Section: Quick Nav & Utility Buttons
        // Row A: Back, Home, Recents, Menu
        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.addView(createSmallRemoteBtn("◁", KeyEvent.KEYCODE_BACK), gridSmallLp());
        navRow.addView(createSmallRemoteBtn("⌂", KeyEvent.KEYCODE_HOME), gridSmallLp());
        navRow.addView(createSmallRemoteBtn("❐", KeyEvent.KEYCODE_APP_SWITCH), gridSmallLp());
        navRow.addView(createSmallRemoteBtn("☰", KeyEvent.KEYCODE_MENU), gridSmallLp());
        sidebar.addView(navRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Row B: Vol-, Vol+, Mute, Power
        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.addView(createSmallRemoteBtn("🔉", KeyEvent.KEYCODE_VOLUME_DOWN), gridSmallLp());
        volRow.addView(createSmallRemoteBtn("🔊", KeyEvent.KEYCODE_VOLUME_UP), gridSmallLp());
        volRow.addView(createSmallRemoteBtn("🔇", KeyEvent.KEYCODE_VOLUME_MUTE), gridSmallLp());
        volRow.addView(createSmallRemoteBtn("⏻", KeyEvent.KEYCODE_POWER), gridSmallLp());
        LinearLayout.LayoutParams vrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vrLp.topMargin = dp(4);
        sidebar.addView(volRow, vrLp);

        sidebarScroll.addView(sidebar);
        root.addView(sidebarScroll);

        setContentView(root);
    }

    private TextView createDpadBtn(String label, final int keyCode, boolean isOk) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(isOk ? Color.WHITE : Color.parseColor("#0F172A"));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, isOk ? 13f : 15f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isOk ? Color.parseColor("#0284C7") : Color.parseColor("#F1F5F9"));
        bg.setCornerRadius(dp(isOk ? 16 : 12));
        bg.setStroke(dp(1), isOk ? Color.parseColor("#38BDF8") : Color.parseColor("#CBD5E1"));
        btn.setBackground(bg);

        applyPressFeedback(btn);

        btn.setOnClickListener(v -> {
            vibrate(12);
            AdbClient.get().sendKey(keyCode);
        });
        return btn;
    }

    private TextView createSidebarTopBtn(String label, final Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(Color.parseColor("#0F172A"));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(6), dp(6), dp(6), dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#FFFFFF"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#CBD5E1"));
        btn.setBackground(bg);

        applyPressFeedback(btn);

        btn.setOnClickListener(v -> {
            vibrate(10);
            action.run();
        });
        return btn;
    }

    private TextView createSmallRemoteBtn(String label, final int keyCode) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(Color.parseColor("#0F172A"));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dp(8), 0, dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#FFFFFF"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        btn.setBackground(bg);

        applyPressFeedback(btn);

        btn.setOnClickListener(v -> {
            vibrate(10);
            AdbClient.get().sendKey(keyCode);
        });
        return btn;
    }

    private LinearLayout.LayoutParams gridSmallLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        return lp;
    }

    /**
     * Tactile press animation feedback: shrinks slightly on press, springs back on release.
     */
    private static void applyPressFeedback(final View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(70).start();
                    break;
            }
            return false;
        });
    }

    private void cycleMode() {
        currentMode = (currentMode + 1) % 3;
        if (currentMode == 0) {
            modeBtn.setText("⚡ Snapshot");
            statusBadge.setText("● LIVE SNAPSHOT");
            frameView.setVisibility(View.VISIBLE);
        } else if (currentMode == 1) {
            modeBtn.setText("📹 720p H.264");
            statusBadge.setText("● LIVE 720p");
        } else {
            modeBtn.setText("📹 480p H.264");
            statusBadge.setText("● LIVE 480p");
        }
        restartStreaming();
    }

    private void captureAndSaveShot() {
        Toast.makeText(this, "Mengambil tangkapan layar...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Bitmap bmp = latestFrameBitmap;
                if (bmp == null) {
                    bmp = AdbClient.get().captureScreenshot(1);
                }
                if (bmp == null) {
                    uiHandler.post(() -> Toast.makeText(this, "Gagal membaca layar STB", Toast.LENGTH_SHORT).show());
                    return;
                }
                final Bitmap toSave = bmp;
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String fileName = "STB_MirrorShot_" + timeStamp + ".png";

                OutputStream fos = null;
                Uri savedUri = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AuraTV");
                    savedUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (savedUri != null) {
                        fos = getContentResolver().openOutputStream(savedUri);
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AuraTV");
                    if (!dir.exists()) dir.mkdirs();
                    File savedFile = new File(dir, fileName);
                    fos = new FileOutputStream(savedFile);
                    savedUri = Uri.fromFile(savedFile);
                }

                if (fos != null) {
                    toSave.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    fos.close();
                    final Uri finalUri = savedUri;
                    uiHandler.post(() -> showScreenshotSuccessDialog(toSave, fileName, finalUri));
                }
            } catch (Exception e) {
                uiHandler.post(() -> Toast.makeText(this, "Gagal simpan: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showScreenshotSuccessDialog(Bitmap bmp, String fileName, final Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setTitle("📸 Tangkapan Layar STB");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));

        ImageView preview = new ImageView(this);
        preview.setImageBitmap(bmp);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        imgLp.bottomMargin = dp(8);
        layout.addView(preview, imgLp);

        TextView info = new TextView(this);
        info.setText("Tersimpan ke Pictures/AuraTV:\n" + fileName);
        info.setTextColor(Color.parseColor("#0F172A"));
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        layout.addView(info);

        builder.setView(layout);
        if (uri != null) {
            builder.setPositiveButton("Buka", (d, w) -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "image/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Exception ignored) {}
            });
        }
        builder.setNegativeButton("Tutup", null);
        builder.show();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        startStreaming();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        stopStreaming();
    }

    @Override
    protected void onDestroy() {
        stopStreaming();
        super.onDestroy();
    }

    private synchronized void restartStreaming() {
        stopStreaming();
        if (surfaceReady || currentMode == 0) {
            startStreaming();
        }
    }

    private synchronized void startStreaming() {
        if (running) return;
        running = true;
        streamThread = new Thread(this::runStreamLoop, "stb-stream");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    private synchronized void stopStreaming() {
        running = false;
        if (streamThread != null) {
            streamThread.interrupt();
            streamThread = null;
        }
    }

    private void runStreamLoop() {
        while (running && AdbClient.get().isConnected()) {
            if (currentMode == 0) {
                // Engine 1: High-Speed Live Frame Snapshot (100% Reliable fallback on any STB)
                runSnapshotLoop();
            } else {
                // Engine 2: Hardware H.264 Stream
                boolean ok = runH264Loop();
                if (!ok && running) {
                    uiHandler.post(() -> {
                        Toast.makeText(this, "H.264 tidak didukung oleh STB, beralih ke Mode Snapshot", Toast.LENGTH_SHORT).show();
                        currentMode = 0;
                        modeBtn.setText("⚡ Snapshot");
                        statusBadge.setText("● LIVE SNAPSHOT");
                        frameView.setVisibility(View.VISIBLE);
                    });
                }
            }
        }
    }

    private void runSnapshotLoop() {
        uiHandler.post(() -> frameView.setVisibility(View.VISIBLE));
        while (running && currentMode == 0 && AdbClient.get().isConnected()) {
            try {
                // inSampleSize = 2 for 4x faster JPEG/PNG decoding and lower memory
                final Bitmap bmp = AdbClient.get().captureScreenshot(2);
                if (bmp != null) {
                    latestFrameBitmap = bmp;
                    lastFrameTime = System.currentTimeMillis();
                    uiHandler.post(() -> {
                        if (running && currentMode == 0) {
                            frameView.setImageBitmap(bmp);
                        }
                    });
                }
                // Short throttle ~40ms (~20-25 FPS theoretical max for adb screencap)
                Thread.sleep(40);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                try { Thread.sleep(250); } catch (InterruptedException ignored) { break; }
            }
        }
    }

    private boolean runH264Loop() {
        uiHandler.post(() -> frameView.setVisibility(View.GONE));
        int targetW = currentMode == 1 ? 1280 : 864;
        int targetH = currentMode == 1 ? 720 : 480;
        int bitRate = currentMode == 1 ? 3000000 : 1500000;

        String cmd = "screenrecord --output-format=h264 --size " + targetW + "x" + targetH
                + " --bit-rate " + bitRate + " --time-limit 180 -";

        MediaCodec codec = null;
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetW, targetH);
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, surfaceView.getHolder().getSurface(), null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] chunk = new byte[32768];
            byte[] streamBuf = new byte[262144];
            int streamLen = 0;

            try (AdbClient.StreamSession session = AdbClient.get().openExecStream("exec:" + cmd)) {
                while (running && currentMode != 0 && AdbClient.get().isConnected()) {
                    int r = session.in.read(chunk);
                    if (r < 0) break;
                    if (r == 0) continue;

                    if (streamLen + r > streamBuf.length) {
                        streamBuf = Arrays.copyOf(streamBuf, Math.max(streamBuf.length * 2, streamLen + r));
                    }
                    System.arraycopy(chunk, 0, streamBuf, streamLen, r);
                    streamLen += r;

                    int offset = 0;
                    while (true) {
                        int nalStart = findNalStart(streamBuf, offset, streamLen);
                        if (nalStart < 0) {
                            if (offset > 0) {
                                int rem = streamLen - offset;
                                System.arraycopy(streamBuf, offset, streamBuf, 0, rem);
                                streamLen = rem;
                            }
                            break;
                        }

                        int nextNal = findNalStart(streamBuf, nalStart + 3, streamLen);
                        if (nextNal < 0) {
                            int rem = streamLen - nalStart;
                            System.arraycopy(streamBuf, nalStart, streamBuf, 0, rem);
                            streamLen = rem;
                            break;
                        }

                        int frameSize = nextNal - nalStart;
                        feedCodec(codec, streamBuf, nalStart, frameSize);
                        drainCodec(codec, info);
                        offset = nextNal;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                    codec.release();
                } catch (Exception ignored) {}
            }
        }
    }

    private void feedCodec(MediaCodec codec, byte[] buf, int offset, int size) {
        try {
            int inIndex = codec.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                ByteBuffer inputBuffer;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    inputBuffer = codec.getInputBuffer(inIndex);
                } else {
                    inputBuffer = codec.getInputBuffers()[inIndex];
                }
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(buf, offset, size);
                    codec.queueInputBuffer(inIndex, 0, size, System.nanoTime() / 1000, 0);
                }
            }
        } catch (Exception ignored) {}
    }

    private void drainCodec(MediaCodec codec, MediaCodec.BufferInfo info) {
        try {
            int outIndex = codec.dequeueOutputBuffer(info, 10000);
            while (outIndex >= 0) {
                codec.releaseOutputBuffer(outIndex, true);
                outIndex = codec.dequeueOutputBuffer(info, 0);
            }
        } catch (Exception ignored) {}
    }

    private int findNalStart(byte[] data, int start, int length) {
        for (int i = start; i < length - 3; i++) {
            if (data[i] == 0 && data[i + 1] == 0) {
                if (data[i + 2] == 1) return i;
                if (data[i + 2] == 0 && i + 3 < length && data[i + 3] == 1) return i;
            }
        }
        return -1;
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
