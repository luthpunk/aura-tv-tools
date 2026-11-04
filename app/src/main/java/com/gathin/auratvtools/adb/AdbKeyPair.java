package com.gathin.auratvtools.adb;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * Generates, stores, and uses a 2048-bit RSA keypair for Android Wireless ADB authentication.
 * Once the user approves the RSA fingerprint on the STB/TV ("Always allow"), it is remembered permanently.
 */
public final class AdbKeyPair {
    private static final int KEY_BITS = 2048;
    private static final int WORDS = KEY_BITS / 32; // 64 words (256 bytes)

    // Standard ASN.1 SHA-1 DigestInfo prefix for ADB AUTH_SIGNATURE (PKCS#1 v1.5)
    private static final byte[] SHA1_DIGEST_INFO_PREFIX = new byte[]{
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final byte[] adbPublicKeyPayload;

    private AdbKeyPair(PrivateKey privateKey, RSAPublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.adbPublicKeyPayload = buildAdbPublicKeyPayload(publicKey);
    }

    public static synchronized AdbKeyPair loadOrCreate(Context context) throws Exception {
        File dir = context.getFilesDir();
        File privFile = new File(dir, "adbkey");
        File pubFile = new File(dir, "adbkey.pub");

        if (privFile.exists() && pubFile.exists()) {
            try {
                byte[] privBytes = readAll(privFile);
                byte[] pubBytes = readAll(pubFile);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
                RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pubBytes));
                return new AdbKeyPair(priv, pub);
            } catch (Exception ignored) {
            }
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(KEY_BITS);
        KeyPair kp = kpg.generateKeyPair();
        writeAll(privFile, kp.getPrivate().getEncoded());
        writeAll(pubFile, kp.getPublic().getEncoded());
        return new AdbKeyPair(kp.getPrivate(), (RSAPublicKey) kp.getPublic());
    }

    /**
     * Signs the 20-byte ADB AUTH_TOKEN using raw RSA private key encryption with PKCS#1 v1.5 padding
     * and the ASN.1 SHA-1 DigestInfo header expected by Android's adbd daemon.
     */
    public byte[] signToken(byte[] token) throws Exception {
        byte[] message = new byte[SHA1_DIGEST_INFO_PREFIX.length + token.length];
        System.arraycopy(SHA1_DIGEST_INFO_PREFIX, 0, message, 0, SHA1_DIGEST_INFO_PREFIX.length);
        System.arraycopy(token, 0, message, SHA1_DIGEST_INFO_PREFIX.length, token.length);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        return cipher.doFinal(message);
    }

    public byte[] getAdbPublicKeyPayload() {
        return adbPublicKeyPayload;
    }

    /**
     * Encodes the RSA public key into Android's 524-byte RSAPublicKey C-struct,
     * Base64-encodes it, and appends " aura@tvtools\0".
     */
    private static byte[] buildAdbPublicKeyPayload(RSAPublicKey pub) {
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n = pub.getModulus();
        BigInteger r = BigInteger.ONE.shiftLeft(KEY_BITS);
        BigInteger rr = r.multiply(r).mod(n);
        BigInteger rem = n.mod(r32);
        BigInteger n0inv = rem.modInverse(r32).negate().mod(r32);

        int[] myN = new int[WORDS];
        int[] myRr = new int[WORDS];
        BigInteger[] temp;
        BigInteger curN = n;
        BigInteger curRr = rr;
        for (int i = 0; i < WORDS; i++) {
            temp = curRr.divideAndRemainder(r32);
            curRr = temp[0];
            myRr[i] = temp[1].intValue();

            temp = curN.divideAndRemainder(r32);
            curN = temp[0];
            myN[i] = temp[1].intValue();
        }

        ByteBuffer buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(WORDS);
        buf.putInt(n0inv.intValue());
        for (int v : myN) buf.putInt(v);
        for (int v : myRr) buf.putInt(v);
        buf.putInt(pub.getPublicExponent().intValue());

        String b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP);
        String full = b64 + " aura@tvtools\0";
        return full.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readAll(File f) throws Exception {
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                int r = fis.read(data, off, data.length - off);
                if (r < 0) break;
                off += r;
            }
        }
        return data;
    }

    private static void writeAll(File f, byte[] data) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data);
        }
    }
}
