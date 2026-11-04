package com.gathin.auratvtools.adb;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure-Java Wireless ADB Client with multiplexed streams, persistent low-latency touch shell,
 * ADB SYNC file push/install, screenshot capture, and fast subnet scanner.
 */
public final class AdbClient {
    private static final int A_SYNC = 0x434e5953;
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;
    private static final int A_AUTH = 0x48545541;

    private static final int ADB_VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 256 * 1024;

    private static final int AUTH_TYPE_TOKEN = 1;
    private static final int AUTH_TYPE_SIGNATURE = 2;
    private static final int AUTH_TYPE_RSA_PUBLIC = 3;

    private static final AdbClient INSTANCE = new AdbClient();

    public static AdbClient get() {
        return INSTANCE;
    }

    public interface ProgressListener {
        void onProgress(int percent, String stage);
    }

    public interface ScanCallback {
        void onDeviceFound(String ip);
        void onScanFinished(List<String> foundIps);
    }

    public static final class StreamChannel {
        final int localId;
        volatile int remoteId;
        volatile boolean open = true;
        final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        final BlockingQueue<Boolean> ackQueue = new LinkedBlockingQueue<>();

        StreamChannel(int localId) {
            this.localId = localId;
        }

        public byte[] readChunk(long timeoutMs) throws InterruptedException {
            return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        public boolean isOpen() {
            return open;
        }
    }

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private final Object writeLock = new Object();
    private volatile boolean connected = false;
    private String connectedHost = "";
    private int connectedPort = 5555;
    private String deviceBanner = "";

    private final AtomicInteger nextLocalId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, StreamChannel> streams = new ConcurrentHashMap<>();
    private Thread readerThread;

    // Persistent interactive shell stream for ultra-fast (<15ms) touch/swipe/key injection
    private StreamChannel inputShell;
    private final ExecutorService ioPool = Executors.newCachedThreadPool();

    private AdbClient() {}

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public String getConnectedHost() {
        return connectedHost;
    }

    public int getConnectedPort() {
        return connectedPort;
    }

    public String getDeviceBanner() {
        return deviceBanner;
    }

    public synchronized void disconnect() {
        connected = false;
        if (inputShell != null) {
            inputShell.open = false;
            inputShell = null;
        }
        for (StreamChannel sc : streams.values()) {
            sc.open = false;
            sc.queue.offer(new byte[0]);
            sc.ackQueue.offer(false);
        }
        streams.clear();
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
    }

    public synchronized void connect(Context context, String host, int port) throws Exception {
        disconnect();
        AdbKeyPair keyPair = AdbKeyPair.loadOrCreate(context);

        Socket s = new Socket();
        s.setTcpNoDelay(true);
        s.connect(new InetSocketAddress(host, port), 5000);
        s.setSoTimeout(0); // blocking reader thread

        this.socket = s;
        this.in = new DataInputStream(s.getInputStream());
        this.out = s.getOutputStream();

        byte[] banner = "host::features=shell_v2,cmd,stat_v2\0".getBytes(StandardCharsets.UTF_8);
        sendPacket(A_CNXN, ADB_VERSION, MAX_PAYLOAD, banner);

        boolean sentSignature = false;
        while (true) {
            Packet pkt = readPacket();
            if (pkt.command == A_CNXN) {
                deviceBanner = new String(pkt.payload, StandardCharsets.UTF_8).trim();
                connected = true;
                connectedHost = host;
                connectedPort = port;
                break;
            } else if (pkt.command == A_AUTH) {
                if (pkt.arg0 == AUTH_TYPE_TOKEN) {
                    if (!sentSignature) {
                        sentSignature = true;
                        byte[] sig = keyPair.signToken(pkt.payload);
                        sendPacket(A_AUTH, AUTH_TYPE_SIGNATURE, 0, sig);
                    } else {
                        // STB doesn't know our public key yet: send it and wait for user to click "Allow" on TV
                        sendPacket(A_AUTH, AUTH_TYPE_RSA_PUBLIC, 0, keyPair.getAdbPublicKeyPayload());
                    }
                }
            } else {
                throw new IOException("Respons ADB tidak dikenal: 0x" + Integer.toHexString(pkt.command));
            }
        }

        startReaderLoop();
    }

    private void startReaderLoop() {
        readerThread = new Thread(() -> {
            try {
                while (connected && socket != null && !socket.isClosed()) {
                    Packet pkt = readPacket();
                    int localId = pkt.arg1;
                    StreamChannel ch = streams.get(localId);
                    if (ch == null) continue;

                    if (pkt.command == A_OKAY) {
                        ch.remoteId = pkt.arg0;
                        ch.ackQueue.offer(true);
                    } else if (pkt.command == A_WRTE) {
                        ch.remoteId = pkt.arg0;
                        sendPacket(A_OKAY, ch.localId, ch.remoteId, null);
                        if (pkt.payload != null && pkt.payload.length > 0) {
                            ch.queue.offer(pkt.payload);
                        }
                    } else if (pkt.command == A_CLSE) {
                        ch.open = false;
                        ch.queue.offer(new byte[0]);
                        ch.ackQueue.offer(false);
                        streams.remove(localId);
                        try {
                            sendPacket(A_CLSE, ch.localId, ch.remoteId, null);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                disconnect();
            }
        }, "adb-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public StreamChannel openStream(String destination) throws Exception {
        if (!isConnected()) throw new IOException("Belum terhubung ke STB");
        int localId = nextLocalId.getAndIncrement();
        StreamChannel ch = new StreamChannel(localId);
        streams.put(localId, ch);

        byte[] destBytes = (destination + "\0").getBytes(StandardCharsets.UTF_8);
        sendPacket(A_OPEN, localId, 0, destBytes);

        Boolean ok = ch.ackQueue.poll(8000, TimeUnit.MILLISECONDS);
        if (ok == null || !ok || !ch.open) {
            streams.remove(localId);
            throw new IOException("Gagal membuka stream ADB: " + destination);
        }
        return ch;
    }

    public void writeStream(StreamChannel ch, byte[] data, int offset, int length) throws Exception {
        if (!ch.open) throw new IOException("Stream sudah ditutup");
        byte[] slice = new byte[length];
        System.arraycopy(data, offset, slice, 0, length);
        ch.ackQueue.clear();
        sendPacket(A_WRTE, ch.localId, ch.remoteId, slice);
        Boolean ok = ch.ackQueue.poll(6000, TimeUnit.MILLISECONDS);
        if (ok == null || !ok) {
            throw new IOException("Stream write timeout");
        }
    }

    public void closeStream(StreamChannel ch) {
        if (ch == null) return;
        ch.open = false;
        streams.remove(ch.localId);
        try {
            sendPacket(A_CLSE, ch.localId, ch.remoteId, null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Executes a shell command on the STB and returns its UTF-8 output.
     */
    public String execCommand(String command) throws Exception {
        byte[] raw = execRaw("shell:" + command);
        return new String(raw, StandardCharsets.UTF_8);
    }

    public byte[] execRaw(String service) throws Exception {
        StreamChannel ch = openStream(service);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            while (ch.open) {
                byte[] chunk = ch.readChunk(15000);
                if (chunk == null) break;
                if (chunk.length == 0 && !ch.open) break;
                bos.write(chunk);
            }
        } finally {
            closeStream(ch);
        }
        return bos.toByteArray();
    }

    /**
     * Sends ultra-low-latency input commands over a persistent shell session.
     */
    public void sendFastInput(final String shellLine) {
        ioPool.execute(() -> {
            try {
                synchronized (this) {
                    if (inputShell == null || !inputShell.open) {
                        inputShell = openStream("shell:");
                    }
                }
                byte[] cmd = (shellLine + "\n").getBytes(StandardCharsets.UTF_8);
                writeStream(inputShell, cmd, 0, cmd.length);
                inputShell.queue.clear(); // discard echo
            } catch (Exception e) {
                inputShell = null;
                try {
                    execCommand(shellLine);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void sendTap(int x, int y) {
        sendFastInput("input tap " + x + " " + y);
    }

    public void sendSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        sendFastInput("input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + Math.max(50, durationMs));
    }

    /**
     * Executes non-blocking fast input on ADB shell.
     */
    public void sendKey(final int keyCode) {
        ioPool.execute(() -> {
            try {
                // Using exec: directly avoids shell wrapper overhead on STB
                StreamChannel ch = openStream("exec:input keyevent " + keyCode);
                closeStream(ch);
            } catch (Exception e) {
                try {
                    StreamChannel ch = openStream("shell:input keyevent " + keyCode);
                    closeStream(ch);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void sendText(String text) {
        if (text == null || text.isEmpty()) return;
        String escaped = text.replace(" ", "%s").replace("'", "\\'").replace("\"", "\\\"");
        sendFastInput("input text \"" + escaped + "\"");
    }

    /**
     * Captures a live PNG screenshot from the STB and decodes it into a Bitmap.
     */
    public Bitmap captureScreenshot() throws Exception {
        byte[] pngBytes = execRaw("exec:screencap -p");
        Bitmap bmp = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length);
        if (bmp != null) return bmp;
        // Fallback for older STB firmwares that convert LF to CRLF on shell:
        execCommand("screencap -p /data/local/tmp/aura_shot.png");
        byte[] fallback = execRaw("exec:cat /data/local/tmp/aura_shot.png");
        return BitmapFactory.decodeByteArray(fallback, 0, fallback.length);
    }

    /**
     * Pushes a file over ADB SYNC protocol ("sync:") and optionally installs it via Package Manager.
     */
    public void pushFile(InputStream input, long totalBytes, String remotePath, ProgressListener listener) throws Exception {
        StreamChannel sync = openStream("sync:");
        try {
            String spec = remotePath + ",33206"; // 0100666 mode
            byte[] specBytes = spec.getBytes(StandardCharsets.UTF_8);
            ByteBuffer sendHdr = ByteBuffer.allocate(8 + specBytes.length).order(ByteOrder.LITTLE_ENDIAN);
            sendHdr.put("SEND".getBytes(StandardCharsets.UTF_8));
            sendHdr.putInt(specBytes.length);
            sendHdr.put(specBytes);
            byte[] hdrArr = sendHdr.array();
            writeStream(sync, hdrArr, 0, hdrArr.length);

            byte[] buf = new byte[32 * 1024];
            long sent = 0;
            int r;
            while ((r = input.read(buf)) > 0) {
                ByteBuffer dataPkt = ByteBuffer.allocate(8 + r).order(ByteOrder.LITTLE_ENDIAN);
                dataPkt.put("DATA".getBytes(StandardCharsets.UTF_8));
                dataPkt.putInt(r);
                dataPkt.put(buf, 0, r);
                byte[] pktArr = dataPkt.array();
                writeStream(sync, pktArr, 0, pktArr.length);
                sent += r;
                if (listener != null && totalBytes > 0) {
                    int pct = (int) Math.min(95, (sent * 95L) / totalBytes);
                    listener.onProgress(pct, "Mengirim ke STB (" + (sent / 1024) + " KB)...");
                }
            }

            ByteBuffer donePkt = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            donePkt.put("DONE".getBytes(StandardCharsets.UTF_8));
            donePkt.putInt((int) (System.currentTimeMillis() / 1000L));
            byte[] doneArr = donePkt.array();
            writeStream(sync, doneArr, 0, doneArr.length);

            byte[] resp = sync.readChunk(10000);
            if (resp != null && resp.length >= 4) {
                String status = new String(resp, 0, 4, StandardCharsets.UTF_8);
                if ("FAIL".equals(status)) {
                    throw new IOException("ADB SYNC gagal mengirim file ke " + remotePath);
                }
            }
        } finally {
            closeStream(sync);
        }
    }

    public String installApk(InputStream apkStream, long fileSize, ProgressListener listener) throws Exception {
        String tempPath = "/data/local/tmp/aura_install.apk";
        pushFile(apkStream, fileSize, tempPath, listener);
        if (listener != null) {
            listener.onProgress(97, "Menginstal APK di STB...");
        }
        String res = execCommand("pm install -r " + tempPath + " 2>&1; rm -f " + tempPath);
        if (listener != null) {
            listener.onProgress(100, "Selesai!");
        }
        return res.trim();
    }

    /**
     * Scans the local /24 Wi-Fi subnet on port 5555 in parallel to auto-discover STBs/Android TVs.
     */
    public static void scanSubnet(Context context, final ScanCallback callback) {
        new Thread(() -> {
            List<String> found = Collections.synchronizedList(new ArrayList<>());
            try {
                String prefix = getWifiSubnetPrefix(context);
                if (prefix != null) {
                    ExecutorService pool = Executors.newFixedThreadPool(48);
                    for (int i = 1; i <= 254; i++) {
                        final String candidate = prefix + i;
                        pool.execute(() -> {
                            try (Socket s = new Socket()) {
                                s.connect(new InetSocketAddress(candidate, 5555), 320);
                                found.add(candidate);
                                if (callback != null) callback.onDeviceFound(candidate);
                            } catch (Exception ignored) {
                            }
                        });
                    }
                    pool.shutdown();
                    pool.awaitTermination(3500, TimeUnit.MILLISECONDS);
                }
            } catch (Exception ignored) {
            }
            if (callback != null) callback.onScanFinished(found);
        }, "lan-scanner").start();
    }

    private static String getWifiSubnetPrefix(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo info = wm.getConnectionInfo();
                int ip = info != null ? info.getIpAddress() : 0;
                if (ip != 0) {
                    return String.format("%d.%d.%d.", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff));
                }
            }
        } catch (Exception ignored) {
        }
        return "192.168.1.";
    }

    private static final class Packet {
        final int command;
        final int arg0;
        final int arg1;
        final byte[] payload;

        Packet(int command, int arg0, int arg1, byte[] payload) {
            this.command = command;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.payload = payload;
        }
    }

    private void sendPacket(int command, int arg0, int arg1, byte[] payload) throws IOException {
        int len = payload == null ? 0 : payload.length;
        int sum = 0;
        if (payload != null) {
            for (byte b : payload) {
                sum += (b & 0xFF);
            }
        }
        ByteBuffer hdr = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        hdr.putInt(command);
        hdr.putInt(arg0);
        hdr.putInt(arg1);
        hdr.putInt(len);
        hdr.putInt(sum);
        hdr.putInt(~command);

        synchronized (writeLock) {
            out.write(hdr.array());
            if (payload != null && len > 0) {
                out.write(payload);
            }
            out.flush();
        }
    }

    private Packet readPacket() throws IOException {
        byte[] hdrBytes = new byte[24];
        in.readFully(hdrBytes);
        ByteBuffer hdr = ByteBuffer.wrap(hdrBytes).order(ByteOrder.LITTLE_ENDIAN);
        int command = hdr.getInt();
        int arg0 = hdr.getInt();
        int arg1 = hdr.getInt();
        int len = hdr.getInt();
        hdr.getInt(); // checksum
        hdr.getInt(); // magic

        byte[] payload = new byte[Math.max(0, len)];
        if (len > 0) {
            in.readFully(payload);
        }
        return new Packet(command, arg0, arg1, payload);
    }
}
