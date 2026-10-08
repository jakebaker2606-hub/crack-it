package com.together.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CaptureRequest;
import android.net.Uri;
import android.os.Bundle;
import android.util.Range;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity implements CameraController.Callback {
    private static final int CAMERA_PERMISSION = 44;
    private final int bg = Color.rgb(5, 13, 20);
    private final int panel = Color.rgb(13, 27, 39);
    private final int text = Color.rgb(239, 248, 255);
    private final int muted = Color.rgb(170, 195, 211);

    private AspectTextureView preview;
    private TextView status;
    private TextView network;
    private TextView pairStatus;
    private TextView zoomLabel;
    private TextView exposureLabel;
    private TextView qualityLabel;
    private Button focusButton;
    private Switch torchSwitch;
    private Switch stabSwitch;
    private SeekBar zoomBar;
    private SeekBar exposureBar;

    private MjpegServer mjpegServer;
    private CameraController camera;
    private PairingClient pairingClient;

    private String togetherHost = "";
    private int togetherPort = 8765;
    private String togetherToken = "";
    private int streamWidth = 1280, streamHeight = 720, streamFps = 15, streamQuality = 88;
    private boolean focusLocked = false;
    private boolean mirrorPreview = false;
    private float maxZoom = 1f;
    private int exposureMin = 0, exposureMax = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        buildUi();

        mjpegServer = new MjpegServer();
        pairingClient = new PairingClient();
        camera = new CameraController(this, preview, mjpegServer, this);
        try {
            mjpegServer.start();
        } catch (Exception e) {
            setStatus("Could not start local camera server");
        }

        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { maybeStartCamera(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });

        processIntent(getIntent());
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        } else {
            maybeStartCamera();
        }
        updateNetworkText();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNetworkText();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) maybeStartCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (camera != null) camera.stopThread();
        if (mjpegServer != null) mjpegServer.stop();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) maybeStartCamera();
            else setStatus("Camera permission is required");
        }
    }

    private void maybeStartCamera() {
        if (camera == null || !preview.isAvailable()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        camera.open();
    }

    private void processIntent(Intent intent) {
        if (intent == null || intent.getData() == null) {
            loadSavedPairing();
            return;
        }
        Uri u = intent.getData();
        if (!"togethercamera".equalsIgnoreCase(u.getScheme()) || !"pair".equalsIgnoreCase(u.getHost())) return;
        String host = u.getQueryParameter("host");
        String port = u.getQueryParameter("port");
        String token = u.getQueryParameter("token");
        if (host != null && NetworkUtil.isPrivate(host) && token != null && token.matches("[A-Za-z0-9_-]{8,128}")) {
            togetherHost = host;
            try { togetherPort = Integer.parseInt(port == null ? "8765" : port); } catch (Exception ignored) { togetherPort = 8765; }
            togetherToken = token;
            getPreferences(MODE_PRIVATE).edit().putString("host", togetherHost).putInt("port", togetherPort).putString("token", togetherToken).apply();
            pairStatus.setText("Pairing with Together…");
            registerWithTogether();
        }
    }

    private void loadSavedPairing() {
        togetherHost = getPreferences(MODE_PRIVATE).getString("host", "");
        togetherPort = getPreferences(MODE_PRIVATE).getInt("port", 8765);
        togetherToken = getPreferences(MODE_PRIVATE).getString("token", "");
        if (!togetherHost.isEmpty() && !togetherToken.isEmpty()) {
            pairStatus.setText("Previous Together session saved • tap PAIR AGAIN if needed");
        }
    }

    private void registerWithTogether() {
        if (togetherHost.isEmpty() || togetherToken.isEmpty()) {
            pairStatus.setText("Scan the camera QR in Together first");
            return;
        }
        pairingClient.register(togetherHost, togetherPort, togetherToken, MjpegServer.PORT,
                streamWidth, streamHeight, streamFps, streamQuality,
                (ok, message) -> runOnUiThread(() -> {
                    pairStatus.setText(message);
                    if (ok) pairStatus.setTextColor(Color.rgb(146, 255, 181));
                    else pairStatus.setTextColor(Color.rgb(255, 179, 184));
                }));
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(0, 0, dp(10), 0);
        root.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.55f));

        TextView title = textView("TOGETHER CAMERA", 20, true, text);
        left.addView(title);
        TextView sub = textView("Offline local camera • no cloud • no mobile data • no certificate", 12, false, muted);
        left.addView(sub);

        preview = new AspectTextureView(this);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        previewLp.topMargin = dp(8);
        left.addView(preview, previewLp);

        status = textView("Starting…", 13, true, text);
        status.setPadding(dp(10), dp(9), dp(10), dp(9));
        status.setBackgroundColor(panel);
        left.addView(status, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        network = textView("Local stream: checking Wi-Fi…", 12, false, muted);
        network.setPadding(0, dp(7), 0, 0);
        left.addView(network);

        ScrollView scroller = new ScrollView(this);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(10), dp(8), dp(10), dp(12));
        controls.setBackgroundColor(panel);
        scroller.addView(controls);
        root.addView(scroller, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        controls.addView(sectionTitle("PAIR WITH TOGETHER"));
        pairStatus = textView("Scan the camera QR in Together", 12, true, muted);
        pairStatus.setPadding(0, dp(3), 0, dp(7));
        controls.addView(pairStatus);
        Button pairAgain = button("PAIR AGAIN");
        pairAgain.setOnClickListener(v -> registerWithTogether());
        controls.addView(pairAgain);

        controls.addView(sectionTitle("CAMERA"));
        LinearLayout cameraButtons = row();
        Button switchCamera = button("FRONT / REAR");
        switchCamera.setOnClickListener(v -> camera.switchCamera());
        cameraButtons.addView(switchCamera, weighted());
        Button mirror = button("MIRROR PREVIEW");
        mirror.setOnClickListener(v -> {
            mirrorPreview = !mirrorPreview;
            preview.setScaleX(mirrorPreview ? -1f : 1f);
            mirror.setText(mirrorPreview ? "PREVIEW MIRRORED" : "MIRROR PREVIEW");
        });
        cameraButtons.addView(mirror, weighted());
        controls.addView(cameraButtons);

        controls.addView(label("Resolution"));
        Spinner resolution = spinner(new String[]{"720p Smooth • 1280×720", "1080p High • 1920×1080", "480p Low bandwidth • 854×480"});
        resolution.setSelection(0);
        resolution.setOnItemSelectedListener(new SimpleSelected() {
            @Override public void selected(int pos) {
                int[][] vals = {{1280,720},{1920,1080},{854,480}};
                if (camera != null) camera.setResolution(vals[pos][0], vals[pos][1]);
            }
        });
        controls.addView(resolution);

        controls.addView(label("Frame rate"));
        Spinner fps = spinner(new String[]{"10 fps • strongest Wi-Fi reliability", "15 fps • recommended", "20 fps • smoother"});
        fps.setSelection(1);
        fps.setOnItemSelectedListener(new SimpleSelected() {
            @Override public void selected(int pos) {
                int[] values = {10,15,20};
                streamFps = values[pos];
                if (camera != null) camera.setFps(streamFps);
            }
        });
        controls.addView(fps);

        qualityLabel = label("JPEG quality • 88%");
        controls.addView(qualityLabel);
        SeekBar quality = new SeekBar(this);
        quality.setMax(40);
        quality.setProgress(33);
        quality.setOnSeekBarChangeListener(new SeekListener() {
            @Override public void changed(int value, boolean fromUser) {
                streamQuality = 55 + value;
                qualityLabel.setText("JPEG quality • " + streamQuality + "%");
                if (fromUser && camera != null) camera.setJpegQuality(streamQuality);
            }
        });
        controls.addView(quality);

        zoomLabel = label("Zoom • 1.0×");
        controls.addView(zoomLabel);
        zoomBar = new SeekBar(this);
        zoomBar.setMax(1000);
        zoomBar.setProgress(0);
        zoomBar.setOnSeekBarChangeListener(new SeekListener() {
            @Override public void changed(int value, boolean fromUser) {
                float z = 1f + (maxZoom - 1f) * value / 1000f;
                zoomLabel.setText(String.format(Locale.US, "Zoom • %.1f×", z));
                if (fromUser && camera != null) camera.setZoom(z);
            }
        });
        controls.addView(zoomBar);

        exposureLabel = label("Exposure • 0");
        controls.addView(exposureLabel);
        exposureBar = new SeekBar(this);
        exposureBar.setMax(0);
        exposureBar.setProgress(0);
        exposureBar.setOnSeekBarChangeListener(new SeekListener() {
            @Override public void changed(int value, boolean fromUser) {
                int ev = exposureMin + value;
                exposureLabel.setText("Exposure • " + (ev > 0 ? "+" : "") + ev);
                if (fromUser && camera != null) camera.setExposure(ev);
            }
        });
        controls.addView(exposureBar);

        controls.addView(label("White balance"));
        Spinner wb = spinner(new String[]{"Auto", "Daylight", "Cloudy", "Incandescent", "Fluorescent"});
        wb.setOnItemSelectedListener(new SimpleSelected() {
            @Override public void selected(int pos) {
                int[] modes = {CaptureRequest.CONTROL_AWB_MODE_AUTO, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
                        CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
                        CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT};
                if (camera != null) camera.setAwbMode(modes[pos]);
            }
        });
        controls.addView(wb);

        LinearLayout featureRow = row();
        focusButton = button("AF CONTINUOUS");
        focusButton.setOnClickListener(v -> {
            focusLocked = !focusLocked;
            if (camera != null) camera.setFocusLocked(focusLocked);
            focusButton.setText(focusLocked ? "AF LOCKED" : "AF CONTINUOUS");
        });
        featureRow.addView(focusButton, weighted());
        controls.addView(featureRow);

        torchSwitch = switchView("Torch");
        torchSwitch.setOnCheckedChangeListener((b, checked) -> { if (camera != null) camera.setTorch(checked); });
        controls.addView(torchSwitch);
        stabSwitch = switchView("Video stabilisation");
        stabSwitch.setChecked(true);
        stabSwitch.setOnCheckedChangeListener((b, checked) -> { if (camera != null) camera.setStabilization(checked); });
        controls.addView(stabSwitch);

        TextView note = textView("Keep this app open while using the camera. Together receives the picture only across your private local Wi‑Fi/hotspot.", 11, false, muted);
        note.setPadding(0, dp(10), 0, 0);
        controls.addView(note);

        setContentView(root);
    }

    private void updateNetworkText() {
        String ip = NetworkUtil.localIpv4();
        if (ip.isEmpty()) network.setText("Local stream: connect this phone to the Together Wi‑Fi/hotspot");
        else network.setText("Local stream: http://" + ip + ":" + MjpegServer.PORT + "/video/mjpeg");
    }

    @Override public void onStatus(String message) { runOnUiThread(() -> setStatus(message)); }

    @Override
    public void onStreamInfo(int width, int height, int fps, int quality) {
        streamWidth = width; streamHeight = height; streamFps = fps; streamQuality = quality;
        runOnUiThread(() -> {
            updateNetworkText();
            if (!togetherHost.isEmpty() && !togetherToken.isEmpty()) registerWithTogether();
        });
    }

    @Override
    public void onCapabilities(float maxZoomValue, Range<Integer> exposureRange, boolean torch, boolean stabilization) {
        maxZoom = Math.max(1f, maxZoomValue);
        exposureMin = exposureRange.getLower();
        exposureMax = exposureRange.getUpper();
        runOnUiThread(() -> {
            zoomBar.setEnabled(maxZoom > 1.01f);
            zoomLabel.setText(String.format(Locale.US, "Zoom • 1.0× (max %.1f×)", maxZoom));
            int span = Math.max(0, exposureMax - exposureMin);
            exposureBar.setMax(span);
            int neutral = Math.max(0, Math.min(span, -exposureMin));
            exposureBar.setProgress(neutral);
            exposureBar.setEnabled(span > 0);
            torchSwitch.setEnabled(torch);
            if (!torch) torchSwitch.setChecked(false);
            stabSwitch.setEnabled(stabilization);
            if (!stabilization) stabSwitch.setChecked(false);
        });
    }

    private void setStatus(String s) { status.setText(s); }

    private TextView textView(String s, int sp, boolean bold, int colour) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(colour);
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        return v;
    }

    private TextView sectionTitle(String s) {
        TextView v = textView(s, 13, true, Color.rgb(117, 230, 255));
        v.setPadding(0, dp(9), 0, dp(4));
        return v;
    }

    private TextView label(String s) {
        TextView v = textView(s, 11, true, muted);
        v.setPadding(0, dp(7), 0, dp(2));
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(42));
        return b;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
        s.setAdapter(adapter);
        return s;
    }

    private Switch switchView(String label) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(text);
        s.setTextSize(12);
        s.setPadding(0, dp(3), 0, dp(3));
        return s;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private abstract class SimpleSelected implements AdapterView.OnItemSelectedListener {
        public abstract void selected(int pos);
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selected(position); }
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }

    private abstract class SeekListener implements SeekBar.OnSeekBarChangeListener {
        public abstract void changed(int value, boolean fromUser);
        @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { changed(progress, fromUser); }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
