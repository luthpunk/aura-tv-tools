package com.gathin.auratvtools.mirror;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.auratvtools.adb.AdbClient;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Super lightweight STB Screen Mirroring with clean 360p / 480p H.264 stream
 * and focused simple D-Pad buttons (Up, Down, Left, Right, OK, Back, Home).
 */
public class MirrorActivity extends Activity implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private TextView statusBadge;
    private TextView qualityBtn;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private volatile boolean surfaceReady = false;
    private Thread streamThread;

    // 0 = 360p (Fastest, ~1.2Mbps), 1 = 480p (~2.0Mbps)
    private volatile int qualityMode = 0;

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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#05070E"));

        // 16:9 Video Container
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

        root.addView(videoContainer, vcLp);

        // Top Status Header (Quality & Close)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(14), dp(10), dp(14), dp(10));

        statusBadge = new TextView(this);
        statusBadge.setText("● LIVE 360p");
        statusBadge.setTextColor(Color.parseColor("#4ADE80"));
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#B30F172A"));
        badgeBg.setCornerRadius(dp(16));
        badgeBg.setStroke(dp(1), Color.parseColor("#334ADE80"));
        statusBadge.setBackground(badgeBg);
        topBar.addView(statusBadge, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        qualityBtn = createSmallBtn("360p Cepat", this::toggleQuality);
        topBar.addView(qualityBtn);

        TextView closeBtn = createSmallBtn("✕ Keluar", this::finish);
        topBar.addView(closeBtn);

        root.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // Floating Clean Remote Pad on Bottom-Right (or Bottom Center)
        LinearLayout remoteOverlay = new LinearLayout(this);
        remoteOverlay.setOrientation(LinearLayout.HORIZONTAL);
        remoteOverlay.setGravity(Gravity.CENTER_VERTICAL);
        remoteOverlay.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable rBg = new GradientDrawable();
        rBg.setColor(Color.parseColor("#D90B1120"));
        rBg.setCornerRadius(dp(28));
        rBg.setStroke(dp(1), Color.parseColor("#3338BDF8"));
        remoteOverlay.setBackground(rBg);

        // Quick back & home buttons
        remoteOverlay.addView(createDpadBtn("◁", KeyEvent.KEYCODE_BACK, false));
        remoteOverlay.addView(createDpadBtn("▲", KeyEvent.KEYCODE_DPAD_UP, false));
        remoteOverlay.addView(createDpadBtn("OK", KeyEvent.KEYCODE_DPAD_CENTER, true));
        remoteOverlay.addView(createDpadBtn("▼", KeyEvent.KEYCODE_DPAD_DOWN, false));
        remoteOverlay.addView(createDpadBtn("◀", KeyEvent.KEYCODE_DPAD_LEFT, false));
        remoteOverlay.addView(createDpadBtn("▶", KeyEvent.KEYCODE_DPAD_RIGHT, false));
        remoteOverlay.addView(createDpadBtn("⌂", KeyEvent.KEYCODE_HOME, false));

        FrameLayout.LayoutParams rLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        rLp.setMargins(0, 0, 0, dp(14));
        root.addView(remoteOverlay, rLp);

        setContentView(root);
    }

    private TextView createDpadBtn(String label, final int keyCode, boolean isOk) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, isOk ? 14f : 16f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(isOk ? "#0284C7" : "#1E293B"));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.parseColor(isOk ? "#7DD3FC" : "#334155"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.setMargins(dp(4), 0, dp(4), 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            vibrate(12);
            AdbClient.get().sendKey(keyCode);
        });
        return btn;
    }

    private TextView createSmallBtn(String label, final Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#3394A3B8"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            vibrate(12);
            action.run();
        });
        return btn;
    }

    private void toggleQuality() {
        qualityMode = (qualityMode == 0) ? 1 : 0;
        if (qualityMode == 0) {
            qualityBtn.setText("360p Cepat");
            statusBadge.setText("● LIVE 360p");
        } else {
            qualityBtn.setText("480p Standar");
            statusBadge.setText("● LIVE 480p");
        }
        restartStreaming();
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
        if (surfaceReady) {
            startStreaming();
        }
    }

    private synchronized void startStreaming() {
        if (running) return;
        running = true;
        streamThread = new Thread(this::runStreamLoop, "h264-stream");
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
            int streamW = (qualityMode == 0) ? 640 : 854;
            int streamH = (qualityMode == 0) ? 360 : 480;
            int bitRate = (qualityMode == 0) ? 1200000 : 2000000;

            AdbClient.StreamChannel channel = null;
            MediaCodec codec = null;
            try {
                // Execute screenrecord with time limit to avoid indefinite hangs
                String cmd = "exec:screenrecord --output-format=h264 --size "
                        + streamW + "x" + streamH + " --bit-rate " + bitRate + " --time-limit 180 -";
                channel = AdbClient.get().openStream(cmd);

                Surface surface = surfaceView.getHolder().getSurface();
                if (surface == null || !surface.isValid()) {
                    Thread.sleep(150);
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

                while (running && channel.isOpen() && surfaceReady) {
                    byte[] chunk = channel.readChunk(1000);
                    if (chunk == null) {
                        parser.drainOutput();
                        continue;
                    }
                    if (chunk.length == 0 && !channel.isOpen()) {
                        break;
                    }

                    // Feed to parser
                    parser.feedChunk(chunk, chunk.length);
                }
            } catch (Exception e) {
                if (running) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            } finally {
                if (channel != null) AdbClient.get().closeStream(channel);
                if (codec != null) {
                    try { codec.stop(); } catch (Exception ignored) {}
                    try { codec.release(); } catch (Exception ignored) {}
                }
            }
        }
    }

    private final class NalStreamParser {
        private final MediaCodec codec;
        private byte[] buffer = new byte[256 * 1024];
        private int length = 0;
        private long ptsUs = 0;
        private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        NalStreamParser(MediaCodec codec) {
            this.codec = codec;
        }

        void feedChunk(byte[] data, int count) {
            if (length + count > buffer.length) {
                int newCap = Math.max(buffer.length * 2, length + count + 32768);
                if (newCap > 2 * 1024 * 1024) {
                    length = 0; // drop buffer to prevent lag buildup
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
                int inIdx = codec.dequeueInputBuffer(4000);
                if (inIdx >= 0) {
                    ByteBuffer inBuf = codec.getInputBuffer(inIdx);
                    if (inBuf != null) {
                        inBuf.clear();
                        int toPut = Math.min(size, inBuf.remaining());
                        inBuf.put(buf, offset, toPut);
                        codec.queueInputBuffer(inIdx, 0, toPut, ptsUs, flags);
                        ptsUs += 20000;
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
