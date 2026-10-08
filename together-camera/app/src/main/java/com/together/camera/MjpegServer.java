package com.together.camera;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MjpegServer {
    public static final int PORT = 8767;
    private static final String TAG = "TogetherMjpeg";
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final Object frameLock = new Object();
    private volatile byte[] latestFrame;
    private volatile long frameVersion = 0;
    private volatile boolean running = false;
    private volatile int width = 1280, height = 720, fps = 15, quality = 88;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public void setStreamInfo(int width, int height, int fps, int quality) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.quality = quality;
    }

    public void updateFrame(byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) return;
        latestFrame = jpeg;
        synchronized (frameLock) {
            frameVersion++;
            frameLock.notifyAll();
        }
    }

    public synchronized void start() throws Exception {
        if (running) return;
        serverSocket = new ServerSocket(PORT);
        serverSocket.setReuseAddress(true);
        running = true;
        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(10000);
                    clients.execute(() -> handle(socket));
                } catch (Exception e) {
                    if (running) Log.w(TAG, "accept", e);
                }
            }
        }, "TogetherCamera-Accept");
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        serverSocket = null;
        synchronized (frameLock) { frameLock.notifyAll(); }
    }

    public boolean isRunning() { return running; }

    private void handle(Socket socket) {
        try (Socket s = socket;
             BufferedInputStream in = new BufferedInputStream(s.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(s.getOutputStream())) {
            String request = readHeaders(in);
            String first = request.split("\\r?\\n", 2)[0];
            String path = "/";
            String[] parts = first.split(" ");
            if (parts.length >= 2) path = parts[1].split("\\?", 2)[0];

            if ("/video/mjpeg".equals(path) || "/mjpeg".equals(path) || "/video".equals(path)) {
                stream(out);
            } else if ("/snapshot.jpg".equals(path) || "/shot.jpg".equals(path)) {
                snapshot(out);
            } else if ("/info.json".equals(path)) {
                String ip = NetworkUtil.localIpv4();
                String json = String.format(Locale.US,
                        "{\"app\":\"Together Camera\",\"version\":\"1.0.4\",\"width\":%d,\"height\":%d,\"fps\":%d,\"quality\":%d,\"port\":%d,\"stream\":\"http://%s:%d/video/mjpeg\"}",
                        width, height, fps, quality, PORT, ip, PORT);
                send(out, "200 OK", "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
            } else {
                String html = "<!doctype html><meta name=viewport content='width=device-width'><title>Together Camera</title><style>body{background:#071018;color:#eef7ff;font-family:sans-serif;padding:24px}img{max-width:100%;border-radius:12px}</style><h1>Together Camera</h1><p>Local offline stream is running.</p><img src='/video/mjpeg'>";
                send(out, "200 OK", "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    private String readHeaders(BufferedInputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int prev3 = -1, prev2 = -1, prev1 = -1, cur;
        while ((cur = in.read()) != -1 && b.size() < 16384) {
            b.write(cur);
            if (prev3 == '\r' && prev2 == '\n' && prev1 == '\r' && cur == '\n') break;
            prev3 = prev2; prev2 = prev1; prev1 = cur;
        }
        return b.toString(StandardCharsets.ISO_8859_1.name());
    }

    private void snapshot(OutputStream out) throws Exception {
        byte[] frame = latestFrame;
        if (frame == null) {
            send(out, "503 Service Unavailable", "text/plain; charset=utf-8", "Camera frame not ready".getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(out, "200 OK", "image/jpeg", frame);
    }

    private void stream(OutputStream out) throws Exception {
        String headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                "Pragma: no-cache\r\n" +
                "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        long seen = -1;
        while (running) {
            byte[] frame;
            long version;
            synchronized (frameLock) {
                while (running && (latestFrame == null || frameVersion == seen)) frameLock.wait(1500);
                if (!running) return;
                frame = latestFrame;
                version = frameVersion;
            }
            if (frame == null || version == seen) continue;
            seen = version;
            String head = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: " + frame.length + "\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(frame);
            out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }
    }

    private void send(OutputStream out, String status, String type, byte[] body) throws Exception {
        String h = "HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
