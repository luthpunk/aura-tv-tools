package com.gathin.auratvtools.mirror;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.text.InputType;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.auratvtools.adb.AdbClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real-time STB -> Phone Screen Mirroring Activity with Hardware H.264 Decoding
 * and Direct Coordinate Touch/Swipe/Long-Press Injection.
 */
public class MirrorActivity extends Activity implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private ImageView fallbackImageView;
    private TouchFeedbackView touchFeedbackView;
    private TextView statusBadge;
    private TextView qualityBtn;
    private LinearLayout controlBar;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private volatile boolean surfaceReady = false;
    private Thread streamThread;

    // Quality modes: 0 = 720p Smooth (4Mbps), 1 = 1080p HD (8Mbps), 2 = Compatibility Frame Mode
    private volatile int qualityMode = 0;

    // Detected STB physical resolution for exact touch mapping
    private volatile int stbWidth = 1920;
    private volatile int stbHeight = 1080;

    // Touch tracking state
    private float downX, downY;
    private long downTime;
    private boolean longPressTriggered = false;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!longPressTriggered && surfaceView != null) {
                longPressTriggered = true;
                vibrate(35);
                int[] coords = mapToStb(downX, downY);
                touchFeedbackView.showRipple(downX, downY, true);
                AdbClient.get().sendSwipe(coords[0], coords[1], coords[0], coords[1], 650);
                showMiniStatus("Long Press (" + coords[0] + ", " + coords[1] + ")");
            }
        }
    };

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
        detectStbResolution();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#05070E"));

        // 16:9 Container centered in screen
        FrameLayout videoContainer = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int w = MeasureSpec.getSize(widthMeasureSpec);
                int h = MeasureSpec.getSize(heightMeasureSpec);
                int targetW = w;
                int targetH = (w * 9) / 16;
                if (targetH > h) {
                    targetH = h;
                    targetW = (h * 16) / 9;
                }
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(targetW, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(targetH, MeasureSpec.EXACTLY));
            }
        };
        FrameLayout.LayoutParams vcLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        videoContainer.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        fallbackImageView = new ImageView(this);
        fallbackImageView.setScaleType(ImageView.ScaleType.FIT_XY);
        fallbackImageView.setVisibility(View.GONE);
        videoContainer.addView(fallbackImageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        touchFeedbackView = new TouchFeedbackView(this);
        videoContainer.addView(touchFeedbackView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setupTouchOverlay(touchFeedbackView);
        root.addView(videoContainer, vcLp);

        // Top-left Live Status Badge
        statusBadge = new TextView(this);
        statusBadge.setText("● LIVE MIRROR • Menghubungkan stream...");
        statusBadge.setTextColor(Color.parseColor("#E2E8F0"));
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#B30F172A"));
        badgeBg.setCornerRadius(dp(99));
        badgeBg.setStroke(dp(1), Color.parseColor("#3338BDF8"));
        statusBadge.setBackground(badgeBg);

        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        badgeLp.setMargins(dp(14), dp(12), dp(14), dp(12));
        root.addView(statusBadge, badgeLp);

        // Bottom Floating Control Dock
        HorizontalScrollView dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockLp.setMargins(dp(12), dp(8), dp(12), dp(12));

        controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER_VERTICAL);
        controlBar.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(Color.parseColor("#CC0F172A"));
        dockBg.setCornerRadius(dp(24));
        dockBg.setStroke(dp(1), Color.parseColor("#4038BDF8"));
        controlBar.setBackground(dockBg);

        addDockButton("◁ Back", () -> AdbClient.get().sendKey(KeyEvent.KEYCODE_BACK));
        addDockButton("⌂ Home", () -> AdbClient.get().sendKey(KeyEvent.KEYCODE_HOME));
        addDockButton("❐ Recents", () -> AdbClient.get().sendKey(KeyEvent.KEYCODE_APP_SWITCH));
        addDockButton("☰ Menu", () -> AdbClient.get().sendKey(KeyEvent.KEYCODE_MENU));
        addDockButton("🔉 Vol-", () -> AdbClient.get().sendKey(KeyEvent.KEYCODE_VOLUME_DOWN));
        addDockButton("🔊 Vol+", () -> AdbClient.get().sendKey(KeyEvent.KEYCODE_VOLUME_UP));
        addDockButton("⌨ Ketik", this::showKeyboardInputDialog);
        addDockButton("📸 Foto", this::takeAndSaveScreenshot);

        qualityBtn = addDockButton("⚡ 720p Smooth", this::cycleQualityMode);
        TextView closeBtn = addDockButton("✕ Tutup", this::finish);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor("#33EF4444"));
        closeBg.setCornerRadius(dp(16));
        closeBg.setStroke(dp(1), Color.parseColor("#80EF4444"));
        closeBtn.setBackground(closeBg);
        closeBtn.setTextColor(Color.parseColor("#FCA5A5"));

        dockScroll.addView(controlBar);
        root.addView(dockScroll, dockLp);

        setContentView(root);
    }

    private TextView addDockButton(String label, final Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.parseColor("#3394A3B8"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), 0, dp(4), 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            vibrate(15);
            action.run();
        });
        controlBar.addView(btn);
        return btn;
    }

    private void setupTouchOverlay(View overlay) {
        overlay.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    downTime = System.currentTimeMillis();
                    longPressTriggered = false;
                    uiHandler.postDelayed(longPressRunnable, 520);
                    touchFeedbackView.showRipple(downX, downY, false);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (dx > dp(14) || dy > dp(14)) {
                        uiHandler.removeCallbacks(longPressRunnable);
                        touchFeedbackView.updateDrag(downX, downY, event.getX(), event.getY());
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    uiHandler.removeCallbacks(longPressRunnable);
                    touchFeedbackView.clearDrag();
                    if (longPressTriggered) {
                        return true;
                    }
                    float upX = event.getX();
                    float upY = event.getY();
                    long elapsed = System.currentTimeMillis() - downTime;
                    float dist = (float) Math.hypot(upX - downX, upY - downY);

                    int[] startStb = mapToStb(downX, downY);
                    int[] endStb = mapToStb(upX, upY);

                    if (dist < dp(14)) {
                        vibrate(12);
                        AdbClient.get().sendTap(startStb[0], startStb[1]);
                        showMiniStatus("Tap (" + startStb[0] + ", " + startStb[1] + ")");
                    } else {
                        vibrate(18);
                        int duration = (int) Math.min(600, Math.max(90, elapsed));
                        AdbClient.get().sendSwipe(startStb[0], startStb[1], endStb[0], endStb[1], duration);
                        showMiniStatus("Swipe → (" + endStb[0] + ", " + endStb[1] + ")");
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    uiHandler.removeCallbacks(longPressRunnable);
                    touchFeedbackView.clearDrag();
                    return true;
            }
            return false;
        });
    }

    private int[] mapToStb(float viewX, float viewY) {
        int vw = Math.max(1, touchFeedbackView.getWidth());
        int vh = Math.max(1, touchFeedbackView.getHeight());
        int x = Math.round((viewX / vw) * stbWidth);
        int y = Math.round((viewY / vh) * stbHeight);
        x = Math.max(0, Math.min(stbWidth - 1, x));
        y = Math.max(0, Math.min(stbHeight - 1, y));
        return new int[]{x, y};
    }

    private void detectStbResolution() {
        new Thread(() -> {
            try {
                String out = AdbClient.get().execCommand("wm size");
                Pattern p = Pattern.compile("(\\d+)x(\\d+)");
                Matcher m = p.matcher(out);
                while (m.find()) {
                    int w = Integer.parseInt(m.group(1));
                    int h = Integer.parseInt(m.group(2));
                    if (w > 0 && h > 0) {
                        if (w < h) {
                            int tmp = w;
                            w = h;
                            h = tmp;
                        }
                        stbWidth = w;
                        stbHeight = h;
                    }
                }
            } catch (Exception ignored) {
            }
        }, "wm-size-detector").start();
    }

    private void cycleQualityMode() {
        qualityMode = (qualityMode + 1) % 3;
        if (qualityMode == 0) {
            qualityBtn.setText("⚡ 720p Smooth");
            fallbackImageView.setVisibility(View.GONE);
            surfaceView.setVisibility(View.VISIBLE);
        } else if (qualityMode == 1) {
            qualityBtn.setText("✨ 1080p HD");
            fallbackImageView.setVisibility(View.GONE);
            surfaceView.setVisibility(View.VISIBLE);
        } else {
            qualityBtn.setText("🖼 Mode Kompatibel");
            fallbackImageView.setVisibility(View.VISIBLE);
        }
        restartStreaming();
    }

    private void showKeyboardInputDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("Ketik Teks ke STB");

        final EditText input = new EditText(this);
        input.setHint("Ketik kata / password / pencarian...");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextColor(Color.WHITE);
        b.setView(input);

        b.setPositiveButton("Kirim + Enter", (d, w) -> {
            String txt = input.getText().toString();
            if (!txt.isEmpty()) {
                AdbClient.get().sendText(txt);
                uiHandler.postDelayed(() -> AdbClient.get().sendKey(KeyEvent.KEYCODE_ENTER), 180);
            }
        });
        b.setNeutralButton("Kirim Saja", (d, w) -> {
            String txt = input.getText().toString();
            if (!txt.isEmpty()) {
                AdbClient.get().sendText(txt);
            }
        });
        b.setNegativeButton("Batal", null);
        b.show();
    }

    private void takeAndSaveScreenshot() {
        showMiniStatus("Mengambil screenshot STB...");
        new Thread(() -> {
            try {
                Bitmap bmp = AdbClient.get().captureScreenshot();
                if (bmp == null) throw new Exception("Gagal decode gambar");
                String savedPath = saveBitmapToGallery(bmp);
                uiHandler.post(() -> Toast.makeText(MirrorActivity.this,
                        "Screenshot disimpan: " + savedPath, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                uiHandler.post(() -> Toast.makeText(MirrorActivity.this,
                        "Gagal screenshot: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }, "mirror-shot").start();
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

    private void showMiniStatus(final String extra) {
        uiHandler.post(() -> {
            String modeLabel = qualityMode == 0 ? "720p H.264" : (qualityMode == 1 ? "1080p H.264" : "Screencap");
            statusBadge.setText("● LIVE " + modeLabel + " (" + stbWidth + "x" + stbHeight + ") • " + extra);
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        startStreaming();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

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
        if (surfaceReady) {
            startStreaming();
        }
    }

    private synchronized void startStreaming() {
        if (running) return;
        running = true;
        streamThread = new Thread(this::runStreamLoop, "h264-mirror-loop");
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
            if (qualityMode == 2) {
                runScreencapFallbackLoop();
                return;
            }

            int streamW = qualityMode == 1 ? 1920 : 1280;
            int streamH = qualityMode == 1 ? 1080 : 720;
            int bitRate = qualityMode == 1 ? 8000000 : 4000000;

            AdbClient.StreamChannel channel = null;
            MediaCodec codec = null;
            try {
                showMiniStatus("Streaming aktif");
                String cmd = "exec:screenrecord --output-format=h264 --size "
                        + streamW + "x" + streamH + " --bit-rate " + bitRate + " -";
                channel = AdbClient.get().openStream(cmd);

                Surface surface = surfaceView.getHolder().getSurface();
                if (surface == null || !surface.isValid()) {
                    Thread.sleep(200);
                    continue;
                }

                codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, streamW, streamH);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
                }
                codec.configure(format, surface, null, 0);
                codec.start();

                NalStreamParser parser = new NalStreamParser(codec);
                int emptyReads = 0;

                while (running && channel.isOpen() && surfaceReady) {
                    byte[] chunk = channel.readChunk(2500);
                    if (chunk == null) {
                        emptyReads++;
                        // Keep encoder producing frames even when STB screen is static
                        if (emptyReads % 2 == 0) {
                            parser.drainOutput();
                        }
                        continue;
                    }
                    if (chunk.length == 0 && !channel.isOpen()) {
                        break;
                    }
                    emptyReads = 0;

                    // Detect if STB rejected screenrecord with text error on first bytes
                    if (parser.totalBytesFed == 0 && chunk.length > 0 && chunk[0] != 0x00) {
                        String errText = new String(chunk, 0, Math.min(chunk.length, 120));
                        if (errText.contains("ERROR") || errText.contains("not found") || errText.contains("Invalid")) {
                            qualityMode = 2;
                            uiHandler.post(() -> {
                                qualityBtn.setText("🖼 Mode Kompatibel");
                                fallbackImageView.setVisibility(View.VISIBLE);
                            });
                            break;
                        }
                    }

                    parser.feedChunk(chunk, chunk.length);
                }
            } catch (Exception e) {
                if (running) {
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            } finally {
                if (channel != null) {
                    AdbClient.get().closeStream(channel);
                }
                if (codec != null) {
                    try {
                        codec.stop();
                    } catch (Exception ignored) {
                    }
                    try {
                        codec.release();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void runScreencapFallbackLoop() {
        showMiniStatus("Mode Kompatibel Aktif");
        while (running && qualityMode == 2 && AdbClient.get().isConnected()) {
            try {
                final Bitmap bmp = AdbClient.get().captureScreenshot();
                if (bmp != null) {
                    stbWidth = bmp.getWidth();
                    stbHeight = bmp.getHeight();
                    uiHandler.post(() -> {
                        fallbackImageView.setVisibility(View.VISIBLE);
                        fallbackImageView.setImageBitmap(bmp);
                    });
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Parses raw Annex-B H.264 stream (`00 00 00 01` start codes) into discrete NAL units
     * and feeds them with zero-latency into Android's hardware MediaCodec.
     */
    private final class NalStreamParser {
        private final MediaCodec codec;
        private byte[] buffer = new byte[512 * 1024];
        private int length = 0;
        long totalBytesFed = 0;
        private long ptsUs = 0;
        private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        NalStreamParser(MediaCodec codec) {
            this.codec = codec;
        }

        void feedChunk(byte[] data, int count) {
            totalBytesFed += count;
            if (length + count > buffer.length) {
                int newCap = Math.max(buffer.length * 2, length + count + 65536);
                if (newCap > 4 * 1024 * 1024) {
                    length = 0; // safety reset
                } else {
                    buffer = Arrays.copyOf(buffer, newCap);
                }
            }
            System.arraycopy(data, 0, buffer, length, count);
            length += count;

            int nalStart = findStartCode(buffer, 0, length);
            if (nalStart < 0) return;

            while (running) {
                int nextStart = findStartCode(buffer, nalStart + 3, length);
                if (nextStart < 0) break;

                queueNalUnit(buffer, nalStart, nextStart - nalStart);
                drainOutput();
                nalStart = nextStart;
            }

            if (nalStart > 0) {
                int remaining = length - nalStart;
                System.arraycopy(buffer, nalStart, buffer, 0, remaining);
                length = remaining;
            }
        }

        private int findStartCode(byte[] buf, int offset, int limit) {
            for (int i = offset; i <= limit - 4; i++) {
                if (buf[i] == 0 && buf[i + 1] == 0) {
                    if (buf[i + 2] == 1) return i;
                    if (buf[i + 2] == 0 && buf[i + 3] == 1) return i;
                }
            }
            return -1;
        }

        private void queueNalUnit(byte[] buf, int offset, int size) {
            if (size <= 4) return;
            int headerIdx = (buf[offset + 2] == 1) ? (offset + 3) : (offset + 4);
            if (headerIdx >= offset + size) return;

            int nalType = buf[headerIdx] & 0x1F;
            int flags = 0;
            if (nalType == 7 || nalType == 8) {
                flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
            } else if (nalType == 5) {
                flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }

            try {
                int inIdx = codec.dequeueInputBuffer(8000);
                if (inIdx >= 0) {
                    ByteBuffer inBuf = codec.getInputBuffer(inIdx);
                    if (inBuf != null) {
                        inBuf.clear();
                        int toPut = Math.min(size, inBuf.remaining());
                        inBuf.put(buf, offset, toPut);
                        codec.queueInputBuffer(inIdx, 0, toPut, ptsUs, flags);
                        ptsUs += 16666;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        void drainOutput() {
            try {
                while (running) {
                    int outIdx = codec.dequeueOutputBuffer(bufferInfo, 0);
                    if (outIdx >= 0) {
                        codec.releaseOutputBuffer(outIdx, true);
                    } else {
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Visual touch feedback overlay showing tap ripples and swipe vectors on the phone screen.
     */
    private final class TouchFeedbackView extends View {
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float rippleX = -1, rippleY = -1;
        private float dragX = -1, dragY = -1;
        private boolean isLongPress = false;
        private long rippleTimestamp = 0;

        TouchFeedbackView(Activity context) {
            super(context);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(2.5f));
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(dp(2.5f));
            linePaint.setColor(Color.parseColor("#CC38BDF8"));
        }

        void showRipple(float x, float y, boolean longPress) {
            this.rippleX = x;
            this.rippleY = y;
            this.isLongPress = longPress;
            this.rippleTimestamp = System.currentTimeMillis();
            invalidate();
        }

        void updateDrag(float x1, float y1, float x2, float y2) {
            this.rippleX = x1;
            this.rippleY = y1;
            this.dragX = x2;
            this.dragY = y2;
            this.rippleTimestamp = System.currentTimeMillis();
            invalidate();
        }

        void clearDrag() {
            this.dragX = -1;
            this.dragY = -1;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            long age = System.currentTimeMillis() - rippleTimestamp;
            if (rippleX >= 0 && age < 420) {
                float progress = age / 420f;
                int alpha = (int) ((1f - progress) * 230);
                circlePaint.setColor(isLongPress ? Color.argb(alpha, 244, 63, 94) : Color.argb(alpha, 56, 189, 248));
                float radius = dp(12) + progress * dp(22);
                canvas.drawCircle(rippleX, rippleY, radius, circlePaint);
                if (dragX >= 0 && dragY >= 0) {
                    canvas.drawLine(rippleX, rippleY, dragX, dragY, linePaint);
                    canvas.drawCircle(dragX, dragY, dp(10), circlePaint);
                }
                postInvalidateDelayed(16);
            }
        }
    }

    private void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Exception ignored) {
        }
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
