package com.gathin.auratvtools.mirror;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
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

import io.github.muntashirakon.adb.AdbStream;
import io.konnecttv.TvViewModel;
import io.konnecttv.adb.Adb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Modern STB Screen Mirroring matching Aura TV Tools Dark Aesthetic:
 * - Direct multiplexed ADB streaming via authenticated TvViewModel Adb session (zero hang / no RSA prompt)
 * - 16:9 Live Video / Snapshot Viewport docked strictly left
 * - Sleek Matte Dark Remote Sidebar with unified circular D-Pad and tactile feedback
 * - Instant Screenshot capture with dialog preview
 */
public class MirrorActivity extends Activity implements SurfaceHolder.Callback {

    private static final int COLOR_BG = 0xFF0C0C0F;
    private static final int COLOR_CARD = 0xFF16161B;
    private static final int COLOR_BORDER = 0xFF222229;
    private static final int COLOR_RAISED = 0xFF1F1F26;
    private static final int COLOR_TEXT = 0xFFECEEF4;
    private static final int COLOR_MUTED = 0xFF8A8D99;
    private static final int COLOR_ACCENT = 0xFF7CF0B0;
    private static final int COLOR_ACCENT_BG = 0xFF10201A;
    private static final int COLOR_DANGER = 0xFFFF7A7A;

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
    private volatile Bitmap latestFrameBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        buildUi();

        Adb adb = TvViewModel.Companion.getActiveAdb();
        if (adb == null || !adb.isConnected()) {
            Toast.makeText(this, "STB belum terhubung di aplikasi! Silakan hubungkan TV terlebih dahulu.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (statusBadge != null) {
            statusBadge.setText("● LIVE STREAMING");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(COLOR_BG);

        // -------------------------------------------------------------
        // LEFT: 16:9 Video Viewport (Full height, docked strictly left)
        // -------------------------------------------------------------
        FrameLayout videoContainer = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalH = MeasureSpec.getSize(heightMeasureSpec);
                int totalW = MeasureSpec.getSize(widthMeasureSpec);
                int targetW = (totalH * 16) / 9;
                int maxVideoW = totalW - dp(220);
                if (maxVideoW > 0 && targetW > maxVideoW) {
                    targetW = maxVideoW;
                }
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(targetW, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(totalH, MeasureSpec.EXACTLY));
            }
        };
        videoContainer.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        videoContainer.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        frameView = new ImageView(this);
        frameView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frameView.setVisibility(View.VISIBLE);
        videoContainer.addView(frameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(videoContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // -------------------------------------------------------------
        // RIGHT: Dedicated Modern Dark Remote Sidebar
        // -------------------------------------------------------------
        ScrollView sidebarScroll = new ScrollView(this);
        sidebarScroll.setFillViewport(true);
        sidebarScroll.setBackgroundColor(COLOR_BG);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        sidebarScroll.setLayoutParams(scrollLp);

        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setGravity(Gravity.CENTER_HORIZONTAL);
        sidebar.setPadding(dp(12), dp(10), dp(12), dp(12));

        // 1. Top Section: Mode, Screenshot, Close
        LinearLayout topControls = new LinearLayout(this);
        topControls.setOrientation(LinearLayout.HORIZONTAL);
        topControls.setGravity(Gravity.CENTER_VERTICAL);
        topControls.setPadding(0, 0, 0, dp(8));

        modeBtn = createSidebarTopBtn("⚡ Snapshot", this::cycleMode);
        topControls.addView(modeBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView shotBtn = createSidebarTopBtn("📸", this::captureAndSaveShot);
        LinearLayout.LayoutParams shotLp = new LinearLayout.LayoutParams(dp(42), dp(36));
        shotLp.setMargins(dp(6), 0, dp(6), 0);
        topControls.addView(shotBtn, shotLp);

        TextView closeBtn = createSidebarTopBtn("✕", this::finish);
        topControls.addView(closeBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        sidebar.addView(topControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Status Badge
        statusBadge = new TextView(this);
        statusBadge.setText("● LIVE SNAPSHOT");
        statusBadge.setTextColor(COLOR_ACCENT);
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(COLOR_ACCENT_BG);
        badgeBg.setCornerRadius(dp(99));
        badgeBg.setStroke(dp(1), 0xFF1A382B);
        statusBadge.setBackground(badgeBg);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.bottomMargin = dp(10);
        sidebar.addView(statusBadge, sbLp);

        // 2. Middle Section: Sleek D-PAD matching Aura TV Tools
        FrameLayout dpadContainer = new FrameLayout(this);
        int dpadSize = dp(176);
        LinearLayout.LayoutParams dpadLp = new LinearLayout.LayoutParams(dpadSize, dpadSize);
        dpadLp.gravity = Gravity.CENTER_HORIZONTAL;
        dpadLp.bottomMargin = dp(12);
        dpadContainer.setLayoutParams(dpadLp);

        GradientDrawable dpadBg = new GradientDrawable();
        dpadBg.setShape(GradientDrawable.OVAL);
        dpadBg.setColor(COLOR_CARD);
        dpadBg.setStroke(dp(1), COLOR_BORDER);
        dpadContainer.setBackground(dpadBg);

        // Directional buttons around center
        TextView upBtn = createDpadArrowBtn("▲", KeyEvent.KEYCODE_DPAD_UP);
        FrameLayout.LayoutParams upLp = new FrameLayout.LayoutParams(dp(54), dp(48), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dpadContainer.addView(upBtn, upLp);

        TextView downBtn = createDpadArrowBtn("▼", KeyEvent.KEYCODE_DPAD_DOWN);
        FrameLayout.LayoutParams downLp = new FrameLayout.LayoutParams(dp(54), dp(48), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dpadContainer.addView(downBtn, downLp);

        TextView leftBtn = createDpadArrowBtn("◀", KeyEvent.KEYCODE_DPAD_LEFT);
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(dp(48), dp(54), Gravity.START | Gravity.CENTER_VERTICAL);
        dpadContainer.addView(leftBtn, leftLp);

        TextView rightBtn = createDpadArrowBtn("▶", KeyEvent.KEYCODE_DPAD_RIGHT);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(dp(48), dp(54), Gravity.END | Gravity.CENTER_VERTICAL);
        dpadContainer.addView(rightBtn, rightLp);

        // Center OK Button
        TextView okBtn = new TextView(this);
        okBtn.setText("OK");
        okBtn.setTextColor(COLOR_ACCENT);
        okBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        okBtn.setTypeface(Typeface.DEFAULT_BOLD);
        okBtn.setGravity(Gravity.CENTER);
        GradientDrawable okBg = new GradientDrawable();
        okBg.setShape(GradientDrawable.OVAL);
        okBg.setColor(COLOR_RAISED);
        okBg.setStroke(dp(1), COLOR_BORDER);
        okBtn.setBackground(okBg);
        applyPressFeedback(okBtn);
        okBtn.setOnClickListener(v -> {
            vibrate(14);
            sendAdbKey(KeyEvent.KEYCODE_DPAD_CENTER);
        });
        FrameLayout.LayoutParams okLp = new FrameLayout.LayoutParams(dp(68), dp(68), Gravity.CENTER);
        dpadContainer.addView(okBtn, okLp);

        sidebar.addView(dpadContainer);

        // 3. Lower Section: Functional Remote Grid
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(createSmallRemoteBtn("⮌ Back", KeyEvent.KEYCODE_BACK), gridSmallLp());
        row1.addView(createSmallRemoteBtn("⌂ Home", KeyEvent.KEYCODE_HOME), gridSmallLp());
        row1.addView(createSmallRemoteBtn("≡ Menu", KeyEvent.KEYCODE_MENU), gridSmallLp());
        sidebar.addView(row1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(createSmallRemoteBtn("Vol −", KeyEvent.KEYCODE_VOLUME_DOWN), gridSmallLp());
        row2.addView(createSmallRemoteBtn("🔇 Mute", KeyEvent.KEYCODE_VOLUME_MUTE), gridSmallLp());
        row2.addView(createSmallRemoteBtn("Vol +", KeyEvent.KEYCODE_VOLUME_UP), gridSmallLp());
        sidebar.addView(row2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        sidebarScroll.addView(sidebar);
        root.addView(sidebarScroll);

        setContentView(root);
    }

    private void sendAdbKey(int keyCode) {
        Adb adb = TvViewModel.Companion.getActiveAdb();
        if (adb != null) {
            new Thread(() -> {
                try {
                    adb.keyevent(keyCode);
                } catch (Exception ignored) {}
            }).start();
        }
    }

    private TextView createDpadArrowBtn(String arrow, final int keyCode) {
        TextView btn = new TextView(this);
        btn.setText(arrow);
        btn.setTextColor(COLOR_TEXT);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        applyPressFeedback(btn);
        btn.setOnClickListener(v -> {
            vibrate(12);
            sendAdbKey(keyCode);
        });
        return btn;
    }

    private TextView createSidebarTopBtn(String label, final Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(COLOR_TEXT);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(6), dp(6), dp(6), dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), COLOR_BORDER);
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
        btn.setTextColor(COLOR_TEXT);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dp(10), 0, dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), COLOR_BORDER);
        btn.setBackground(bg);

        applyPressFeedback(btn);
        btn.setOnClickListener(v -> {
            vibrate(10);
            sendAdbKey(keyCode);
        });
        return btn;
    }

    private LinearLayout.LayoutParams gridSmallLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        return lp;
    }

    private static void applyPressFeedback(final View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(60).start();
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
                    Adb adb = TvViewModel.Companion.getActiveAdb();
                    if (adb != null) bmp = adb.captureScreenshot(1);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
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
        info.setTextColor(COLOR_TEXT);
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
        while (running) {
            Adb adb = TvViewModel.Companion.getActiveAdb();
            if (adb == null || !adb.isConnected()) break;

            if (currentMode == 0) {
                runSnapshotLoop(adb);
            } else {
                boolean ok = runH264Loop(adb);
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

    private void runSnapshotLoop(Adb adb) {
        uiHandler.post(() -> frameView.setVisibility(View.VISIBLE));
        while (running && currentMode == 0 && adb.isConnected()) {
            try {
                final Bitmap bmp = adb.captureScreenshot(2);
                if (bmp != null) {
                    latestFrameBitmap = bmp;
                    uiHandler.post(() -> {
                        if (running && currentMode == 0) {
                            frameView.setImageBitmap(bmp);
                        }
                    });
                }
                Thread.sleep(40);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) { break; }
            }
        }
    }

    private boolean runH264Loop(Adb adb) {
        uiHandler.post(() -> frameView.setVisibility(View.GONE));
        int targetW = currentMode == 1 ? 1280 : 864;
        int targetH = currentMode == 1 ? 720 : 480;
        int bitRate = currentMode == 1 ? 3000000 : 1500000;

        String cmd = "screenrecord --output-format=h264 --size " + targetW + "x" + targetH
                + " --bit-rate " + bitRate + " --time-limit 180 -";

        MediaCodec codec = null;
        AdbStream stream = null;
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetW, targetH);
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, surfaceView.getHolder().getSurface(), null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] streamBuf = new byte[262144];
            int streamLen = 0;

            stream = adb.openStream("exec:" + cmd);
            InputStream in = stream.openInputStream();
            byte[] chunk = new byte[16384];

            while (running && currentMode != 0 && adb.isConnected()) {
                int r = in.read(chunk);
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
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (Exception ignored) {}
            }
            if (codec != null) {
                try {
                    codec.stop();
                    codec.release();
                } catch (Exception ignored) {}
            }
        }
    }

    private static int findNalStart(byte[] data, int offset, int length) {
        for (int i = offset; i < length - 3; i++) {
            if (data[i] == 0 && data[i + 1] == 0) {
                if (data[i + 2] == 1) return i;
                if (data[i + 2] == 0 && i < length - 4 && data[i + 3] == 1) return i;
            }
        }
        return -1;
    }

    private static void feedCodec(MediaCodec codec, byte[] data, int offset, int size) {
        int inIndex = codec.dequeueInputBuffer(10000);
        if (inIndex >= 0) {
            java.nio.ByteBuffer buf = codec.getInputBuffer(inIndex);
            if (buf != null) {
                buf.clear();
                buf.put(data, offset, size);
                codec.queueInputBuffer(inIndex, 0, size, System.nanoTime() / 1000L, 0);
            }
        }
    }

    private static void drainCodec(MediaCodec codec, MediaCodec.BufferInfo info) {
        int outIndex;
        while ((outIndex = codec.dequeueOutputBuffer(info, 0)) >= 0) {
            codec.releaseOutputBuffer(outIndex, true);
        }
    }

    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Exception ignored) {}
    }

    private int dp(float dpVal) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpVal, getResources().getDisplayMetrics());
    }
}
