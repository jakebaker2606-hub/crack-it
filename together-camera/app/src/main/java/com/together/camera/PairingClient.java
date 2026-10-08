package com.together.camera;

import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PairingClient {
    public interface Callback { void done(boolean ok, String message); }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void register(String host, int port, String token, int streamPort, int width, int height, int fps, int quality, Callback cb) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String url = "http://" + host + ":" + port + "/api/camera/app-register" +
                        "?t=" + Uri.encode(token) +
                        "&port=" + streamPort +
                        "&path=" + Uri.encode("/video/mjpeg") +
                        "&name=" + Uri.encode("Together Camera") +
                        "&width=" + width +
                        "&height=" + height +
                        "&fps=" + fps +
                        "&quality=" + quality;
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(1800);
                conn.setReadTimeout(2600);
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("X-Together-Camera", "1");
                conn.setDoOutput(true);
                conn.getOutputStream().write("pair=1".getBytes(StandardCharsets.UTF_8));
                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null && body.length() < 4096) body.append(line);
                boolean ok = code >= 200 && code < 300 && body.toString().contains("\"ok\":true");
                cb.done(ok, ok ? "Paired with Together" : "Together could not connect to this camera");
            } catch (Exception e) {
                cb.done(false, "Together not reachable on the local Wi-Fi");
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
}
