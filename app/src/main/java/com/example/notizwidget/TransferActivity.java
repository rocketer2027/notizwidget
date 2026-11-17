package com.example.notizwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Notiz per WLAN auf ein anderes Geraet uebertragen.
 *
 * Senden: Das Geraet oeffnet kurz einen kleinen Server und zeigt einen QR-Code mit Adresse,
 * Einmal-Kennung und Schluessel. Empfangen: Das andere Geraet scannt den Code, holt die Notiz
 * direkt aus dem lokalen Netz ab (AES-verschluesselt) und ersetzt nach Rueckfrage die eigene Notiz.
 * Es laeuft nichts ueber das Internet.
 */
public class TransferActivity extends Activity {

    private static final String PREFIX = "CATATAN1";
    private static final long SEND_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final int MAX_PAYLOAD = 20 * 1024 * 1024;
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private LinearLayout box;
    private int fg;
    private int bg;
    private volatile boolean stopped;
    private volatile ServerSocket server;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Settings.themeRes(this));
        super.onCreate(savedInstanceState);
        bg = Settings.bgColor(this);
        fg = Palette.textColorFor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Palette.dp(this, 24);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        showChooser();
    }

    @Override
    protected void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    // ---------- Oberflaeche ----------

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(fg);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, Palette.dp(this, 8), 0, Palette.dp(this, 8));
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Palette.dp(this, 12);
        box.addView(b, lp);
        return b;
    }

    private void showChooser() {
        stopServer();
        stopped = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        box.removeAllViews();
        box.addView(text(getString(R.string.transfer_title), 22, true));
        box.addView(text(getString(R.string.transfer_intro), 15, false));
        button(getString(R.string.transfer_send), v -> startSending());
        button(getString(R.string.transfer_receive), v -> startReceiving());
    }

    private void showMessage(String msg) {
        box.removeAllViews();
        box.addView(text(msg, 16, false));
        button(getString(R.string.transfer_back), v -> showChooser());
    }

    // ---------- Senden ----------

    private void startSending() {
        final String raw = NoteStore.loadRaw(this);
        if (NoteStore.deserialize(raw).toString().trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.note_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        final String ip = localIp();
        if (ip == null) {
            showMessage(getString(R.string.transfer_no_wifi));
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        box.removeAllViews();
        box.addView(text(getString(R.string.transfer_waiting), 16, false));

        final SecureRandom rnd = new SecureRandom();
        final byte[] token = new byte[8];
        final byte[] key = new byte[16];
        rnd.nextBytes(token);
        rnd.nextBytes(key);
        final String tokenHex = hex(token);

        stopped = false;
        new Thread(() -> {
            ServerSocket ss = null;
            try {
                ss = new ServerSocket(0);
                server = ss;
                String code = PREFIX + ";" + ip + ";" + ss.getLocalPort() + ";" + tokenHex + ";" + hex(key);
                runOnUiThread(() -> showQr(code));
                ss.setSoTimeout(1000);
                long deadline = System.currentTimeMillis() + SEND_TIMEOUT_MS;
                while (!stopped && System.currentTimeMillis() < deadline) {
                    Socket s;
                    try {
                        s = ss.accept();
                    } catch (SocketTimeoutException e) {
                        continue;
                    }
                    boolean ok = false;
                    try {
                        ok = serve(s, tokenHex, key, raw);
                    } catch (Exception ignored) {
                        // falscher oder abgebrochener Aufruf: weiter warten
                    } finally {
                        try {
                            s.close();
                        } catch (IOException ignored) {
                        }
                    }
                    if (ok) {
                        runOnUiThread(() -> {
                            if (isFinishing()) return;
                            stopServer();
                            showMessage(getString(R.string.transfer_sent));
                        });
                        return;
                    }
                }
                if (!stopped) {
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        stopServer();
                        showMessage(getString(R.string.transfer_timeout));
                    });
                }
            } catch (Exception e) {
                if (!stopped) {
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        showMessage(getString(R.string.transfer_failed));
                    });
                }
            } finally {
                // nur den eigenen Server schliessen (ein neuer Versuch darf nicht mitgerissen werden)
                if (ss != null) {
                    try {
                        ss.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }, "catatan-send").start();
    }

    /** Beantwortet einen Abruf: Kennung pruefen, verschluesselte Notiz senden, Quittung abwarten. */
    private boolean serve(Socket s, String tokenHex, byte[] key, String raw) throws Exception {
        s.setSoTimeout(10000);
        InputStream in = s.getInputStream();
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            if (line.length() > 64) return false;
            line.append((char) c);
        }
        byte[] got = line.toString().trim().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(got, tokenHex.getBytes(StandardCharsets.UTF_8))) return false;

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));

        DataOutputStream out = new DataOutputStream(s.getOutputStream());
        out.writeInt(iv.length + ct.length);
        out.write(iv);
        out.write(ct);
        out.flush();
        return in.read() == 1; // Quittung vom Empfaenger: entschluesselt und gueltig
    }

    private void showQr(String code) {
        if (isFinishing()) return;
        box.removeAllViews();
        box.addView(text(getString(R.string.transfer_title), 22, true));
        Bitmap bmp = makeQr(code, 700);
        if (bmp != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bmp);
            iv.setBackgroundColor(Color.WHITE);
            int p = Palette.dp(this, 8);
            iv.setPadding(p, p, p, p);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int size = Math.min(Palette.dp(this, 300),
                    getResources().getDisplayMetrics().widthPixels - Palette.dp(this, 64));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.topMargin = Palette.dp(this, 12);
            lp.bottomMargin = Palette.dp(this, 12);
            box.addView(iv, lp);
        }
        box.addView(text(getString(R.string.transfer_send_hint), 14, false));
        box.addView(text(getString(R.string.transfer_waiting), 14, true));
        button(getString(R.string.transfer_back), v -> showChooser());
    }

    private static Bitmap makeQr(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            int w = m.getWidth();
            int h = m.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    px[y * w + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            b.setPixels(px, 0, w, 0, 0, w, h);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** WLAN- bzw. Hotspot-Adresse dieses Geraets (nicht die des Mobilfunks). */
    private static String localIp() {
        try {
            String found = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName().toLowerCase();
                if (!(n.startsWith("wlan") || n.startsWith("swlan") || n.startsWith("ap")
                        || n.startsWith("eth"))) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        if (n.startsWith("wlan")) return a.getHostAddress();
                        if (found == null) found = a.getHostAddress();
                    }
                }
            }
            return found;
        } catch (Exception e) {
            return null;
        }
    }

    private void stopServer() {
        stopped = true;
        ServerSocket ss = server;
        server = null;
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ---------- Empfangen ----------

    private void startReceiving() {
        try {
            GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build();
            GmsBarcodeScanning.getClient(this, options)
                    .startScan()
                    .addOnSuccessListener(b -> onCode(b.getRawValue()))
                    .addOnFailureListener(e -> askManualCode());
        } catch (Exception e) {
            askManualCode();
        }
    }

    private void askManualCode() {
        if (isFinishing()) return;
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(getString(R.string.transfer_enter_code));
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.transfer_scan_unavailable))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> onCode(input.getText().toString()))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void onCode(String codeRaw) {
        if (codeRaw == null) return;
        String[] p = codeRaw.trim().split(";");
        if (p.length != 5 || !PREFIX.equals(p[0])) {
            Toast.makeText(this, getString(R.string.transfer_bad_code), Toast.LENGTH_LONG).show();
            return;
        }
        final String ip = p[1];
        final String tokenHex = p[3];
        final byte[] key = unhex(p[4]);
        final int port;
        try {
            port = Integer.parseInt(p[2]);
        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.transfer_bad_code), Toast.LENGTH_LONG).show();
            return;
        }
        if (!isPrivateIpv4(ip) || port < 1 || port > 65535 || key == null || key.length != 16
                || !tokenHex.matches("[0-9a-f]{16}")) {
            Toast.makeText(this, getString(R.string.transfer_bad_code), Toast.LENGTH_LONG).show();
            return;
        }

        box.removeAllViews();
        box.addView(text(getString(R.string.transfer_connecting), 16, false));
        new Thread(() -> {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, port), 8000);
                s.setSoTimeout(15000);
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                out.write((tokenHex + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                DataInputStream in = new DataInputStream(s.getInputStream());
                int len = in.readInt();
                if (len < 28 || len > MAX_PAYLOAD) throw new IOException("bad length");
                byte[] data = new byte[len];
                in.readFully(data);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                        new GCMParameterSpec(128, data, 0, 12));
                byte[] plain = cipher.doFinal(data, 12, len - 12);
                final String raw = new String(plain, StandardCharsets.UTF_8);
                new JSONObject(raw); // muss eine gueltige Notiz sein
                out.write(1); // Quittung an den Sender
                out.flush();
                runOnUiThread(() -> confirmReplace(raw));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isFinishing()) showMessage(getString(R.string.transfer_failed));
                });
            }
        }, "catatan-receive").start();
    }

    private void confirmReplace(final String raw) {
        if (isFinishing()) return;
        showMessage(getString(R.string.transfer_confirm_title));
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.transfer_confirm_title))
                .setMessage(getString(R.string.transfer_confirm_msg))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.transfer_replace), (d, w) -> {
                    NoteStore.saveRaw(this, raw);
                    Toast.makeText(this, getString(R.string.transfer_done), Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(getString(R.string.cancel), (d, w) -> showChooser())
                .show();
    }

    // ---------- Hilfen ----------

    private static boolean isPrivateIpv4(String ip) {
        java.util.regex.Matcher m = IPV4.matcher(ip);
        if (!m.matches()) return false;
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        for (int i = 1; i <= 4; i++) if (Integer.parseInt(m.group(i)) > 255) return false;
        return a == 10 || (a == 192 && b == 168) || (a == 172 && b >= 16 && b <= 31);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] unhex(String s) {
        if (s == null || s.length() % 2 != 0 || !s.matches("[0-9a-f]+")) return null;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
