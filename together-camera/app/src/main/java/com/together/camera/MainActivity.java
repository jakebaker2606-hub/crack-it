package com.together.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION = 44;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private SurfaceViewRenderer renderer;
    private ScrollView controlsScroller;
    private LinearLayout controls;
    private TextView status;
    private TextView detail;
    private TextView pairing;
    private TextView zoomLabel;
    private Button reconnectButton;
    private Button switchButton;
    private Button mirrorButton;
    private Button focusLockButton;
    private Spinner qualitySpinner;
    private SeekBar zoomBar;
    private TextView focusRing;

    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private SurfaceTextureHelper textureHelper;
    private HqCameraCapturer cameraCapturer;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private PeerConnection peer;
    private RtpSender sender;

    private String togetherHost = "";
    private int togetherPort = 8765;
    private String togetherToken = "";

    private int captureWidth = 1920;
    private int captureHeight = 1080;
    private int captureFps = 30;
    private int targetBitrate = 8000000;
    private boolean localCameraStarted = false;
    private boolean offerSent = false;
    private boolean answerSet = false;
    private boolean answerPollBusy = false;
    private boolean mirrorPreview = false;
    private boolean frontCamera = false;
    private boolean focusLocked = false;
    private boolean destroyed = false;
    private boolean callStarting = false;
    private boolean touchWasScale = false;
    private float maxZoom = 1f;
    private String connectionState = "idle";
    private long lastCallStartAt = 0L;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Network boundWifi;
    private ScaleGestureDetector scaleDetector;

    private final Runnable reconnectRunnable = () -> {
        if (!destroyed && !togetherToken.isEmpty()) startCall();
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(3, 10, 16));
        getWindow().setNavigationBarColor(Color.rgb(3, 10, 16));

        buildUi();
        initWebRtc();
        bindToWifiAndWatch();
        processIntent(getIntent());

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        } else {
            maybeStart();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processIntent(intent);
        maybeStart();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyControlLayout(newConfig.orientation);
        if (cameraCapturer != null) cameraCapturer.refreshOrientation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                maybeStart();
            } else {
                setStatus("Camera permission is required", true);
            }
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ui.removeCallbacks(reconnectRunnable);
        ui.removeCallbacks(pingRunnable);
        stopPeer();
        stopLocalCamera();
        unbindWifiWatcher();
        try { if (renderer != null) renderer.release(); } catch (Exception ignored) {}
        try { if (factory != null) factory.dispose(); } catch (Exception ignored) {}
        try { if (eglBase != null) eglBase.release(); } catch (Exception ignored) {}
        network.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        renderer = new SurfaceViewRenderer(this);
        root.addView(renderer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        focusRing = label("◎", 48, true, Color.WHITE);
        focusRing.setGravity(Gravity.CENTER);
        focusRing.setVisibility(android.view.View.GONE);
        FrameLayout.LayoutParams focusLp = new FrameLayout.LayoutParams(dp(72), dp(72));
        root.addView(focusRing, focusLp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14), dp(10), dp(14), dp(10));
        top.setBackgroundColor(0x99030A10);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        root.addView(top, topLp);

        TextView title = label("TOGETHER CAMERA HQ • RELIABLE", 19, true, Color.WHITE);
        top.addView(title);
        status = label("Starting camera…", 13, true, Color.rgb(150, 234, 255));
        top.addView(status);
        detail = label("Tap picture to focus • pinch to zoom • automatic local reconnect", 11, false, Color.rgb(190, 207, 220));
        top.addView(detail);

        controlsScroller = new ScrollView(this);
        controlsScroller.setFillViewport(true);
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(10), dp(12), dp(14));
        controls.setBackgroundColor(0xE611202C);
        controlsScroller.addView(controls);

        controls.addView(section("PAIR WITH TOGETHER"));
        pairing = label("Scan the new HQ camera QR in Together.", 12, true, Color.rgb(200, 218, 230));
        controls.addView(pairing);
        reconnectButton = button("START / RECONNECT VIDEO");
        reconnectButton.setOnClickListener(v -> {
            ui.removeCallbacks(reconnectRunnable);
            startCall();
        });
        controls.addView(reconnectButton);

        controls.addView(section("VIDEO QUALITY"));
        qualitySpinner = new Spinner(this);
        ArrayAdapter<String> qualityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "1080p HIGH • 1920×1080 • 30fps",
                        "1080p STABLE • 1920×1080 • 24fps",
                        "720p SMOOTH • 1280×720 • 30fps",
                        "720p RELIABLE • 1280×720 • 20fps"
                });
        qualitySpinner.setAdapter(qualityAdapter);
        qualitySpinner.setSelection(0);
        qualitySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int position, long id) {
                if (position == 0) setQuality(1920, 1080, 30, 8000000);
                else if (position == 1) setQuality(1920, 1080, 24, 6500000);
                else if (position == 2) setQuality(1280, 720, 30, 4500000);
                else setQuality(1280, 720, 20, 3000000);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        controls.addView(qualitySpinner);

        controls.addView(section("FOCUS & ZOOM"));
        TextView focusHelp = label("Tap anywhere on the camera picture to focus there. Pinch the picture or use the slider to zoom.", 10, false, Color.rgb(175, 199, 215));
        focusHelp.setPadding(0, 0, 0, dp(6));
        controls.addView(focusHelp);

        focusLockButton = button("FOCUS: CONTINUOUS AUTO");
        focusLockButton.setOnClickListener(v -> {
            focusLocked = !focusLocked;
            if (cameraCapturer != null) cameraCapturer.setFocusLocked(focusLocked);
            focusLockButton.setText(focusLocked ? "FOCUS: LOCKED" : "FOCUS: CONTINUOUS AUTO");
        });
        controls.addView(focusLockButton);

        zoomLabel = label("Zoom • 1.0×", 12, true, Color.rgb(220, 235, 246));
        zoomLabel.setPadding(0, dp(7), 0, 0);
        controls.addView(zoomLabel);

        zoomBar = new SeekBar(this);
        zoomBar.setMax(1000);
        zoomBar.setProgress(0);
        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float z = 1f + (Math.max(1f, maxZoom) - 1f) * progress / 1000f;
                setCameraZoom(z);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        controls.addView(zoomBar);

        LinearLayout zoomButtons = new LinearLayout(this);
        zoomButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button zoomOut = button("− ZOOM");
        Button zoomIn = button("+ ZOOM");
        zoomOut.setOnClickListener(v -> setCameraZoom(Math.max(1f, currentZoom() - 0.25f)));
        zoomIn.setOnClickListener(v -> setCameraZoom(Math.min(maxZoom, currentZoom() + 0.25f)));
        zoomButtons.addView(zoomOut, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        zoomButtons.addView(zoomIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        controls.addView(zoomButtons);

        controls.addView(section("CAMERA"));
        switchButton = button("SWITCH FRONT / REAR");
        switchButton.setOnClickListener(v -> switchCamera());
        controls.addView(switchButton);

        mirrorButton = button("MIRROR PREVIEW: OFF");
        mirrorButton.setOnClickListener(v -> {
            mirrorPreview = !mirrorPreview;
            updatePreviewMirror();
            mirrorButton.setText("MIRROR PREVIEW: " + (mirrorPreview ? "ON" : "OFF"));
        });
        controls.addView(mirrorButton);

        TextView offline = label(
                "Reliability mode keeps the phone on the local Wi‑Fi route and automatically rebuilds the video connection after a brief Wi‑Fi interruption. " +
                "No internet, cloud, USB, certificate, STUN or TURN server is used.",
                10, false, Color.rgb(175, 199, 215));
        offline.setPadding(0, dp(10), 0, 0);
        controls.addView(offline);

        root.addView(controlsScroller);
        setContentView(root);
        applyControlLayout(getResources().getConfiguration().orientation);
        installCameraGestures();
    }

    private void installCameraGestures() {
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                touchWasScale = true;
                return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                float next = currentZoom() * detector.getScaleFactor();
                setCameraZoom(Math.max(1f, Math.min(maxZoom, next)));
                return true;
            }

            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                ui.postDelayed(() -> touchWasScale = false, 150);
            }
        });

        renderer.setOnTouchListener((v, event) -> {
            if (scaleDetector != null) scaleDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP && !touchWasScale && event.getPointerCount() == 1) {
                if (cameraCapturer != null && v.getWidth() > 0 && v.getHeight() > 0) {
                    float nx = event.getX() / v.getWidth();
                    float ny = event.getY() / v.getHeight();
                    cameraCapturer.focusAt(nx, ny);
                    showFocusRing(event.getX(), event.getY());
                    if (!focusLocked) focusLockButton.setText("FOCUS: CONTINUOUS AUTO");
                }
            }
            return true;
        });
    }

    private void showFocusRing(float x, float y) {
        if (focusRing == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) focusRing.getLayoutParams();
        lp.leftMargin = Math.max(0, Math.min(root.getWidth() - dp(72), Math.round(x - dp(36))));
        lp.topMargin = Math.max(0, Math.min(root.getHeight() - dp(72), Math.round(y - dp(36))));
        focusRing.setLayoutParams(lp);
        focusRing.setAlpha(1f);
        focusRing.setVisibility(android.view.View.VISIBLE);
        focusRing.animate().alpha(0f).setStartDelay(650).setDuration(450).withEndAction(() -> {
            focusRing.setVisibility(android.view.View.GONE);
            focusRing.setAlpha(1f);
        }).start();
    }

    private void applyControlLayout(int orientation) {
        if (controlsScroller == null) return;
        FrameLayout.LayoutParams lp;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(370), Gravity.BOTTOM);
        } else {
            lp = new FrameLayout.LayoutParams(dp(350), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        }
        controlsScroller.setLayoutParams(lp);
    }

    private TextView label(String text, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView section(String text) {
        TextView t = label(text, 12, true, Color.rgb(112, 230, 255));
        t.setPadding(0, dp(12), 0, dp(6));
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void setStatus(String text, boolean error) {
        runOnUiThread(() -> {
            if (status != null) {
                status.setText(text);
                status.setTextColor(error ? Color.rgb(255, 170, 180) : Color.rgb(150, 234, 255));
            }
        });
    }

    private void setDetail(String text) {
        runOnUiThread(() -> { if (detail != null) detail.setText(text); });
    }

    private void processIntent(Intent intent) {
        Uri u = intent == null ? null : intent.getData();
        if (u != null && "togethercamerahq".equalsIgnoreCase(u.getScheme()) && "pair".equalsIgnoreCase(u.getHost())) {
            String host = u.getQueryParameter("host");
            String token = u.getQueryParameter("token");
            String port = u.getQueryParameter("port");
            if (host != null && isPrivateIpv4(host) && token != null && token.matches("[A-Za-z0-9_-]{8,128}")) {
                togetherHost = host;
                togetherToken = token;
                try { togetherPort = Integer.parseInt(port == null ? "8765" : port); }
                catch (Exception ignored) { togetherPort = 8765; }
                getPreferences(MODE_PRIVATE).edit()
                        .putString("host", togetherHost)
                        .putString("token", togetherToken)
                        .putInt("port", togetherPort)
                        .apply();
                pairing.setText("Paired with Together at " + togetherHost + " • auto reconnect ON");
                setStatus("Pairing received — starting reliable HQ video…", false);
                scheduleReconnect(100);
                return;
            }
        }

        togetherHost = getPreferences(MODE_PRIVATE).getString("host", "");
        togetherToken = getPreferences(MODE_PRIVATE).getString("token", "");
        togetherPort = getPreferences(MODE_PRIVATE).getInt("port", 8765);
        if (!togetherHost.isEmpty() && !togetherToken.isEmpty()) {
            pairing.setText("Previous Together pairing saved • auto reconnect ON");
        }
    }

    private boolean isPrivateIpv4(String ip) {
        try {
            String[] p = ip.split("\\.");
            if (p.length != 4) return false;
            int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
            return a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) || (a == 169 && b == 254);
        } catch (Exception e) {
            return false;
        }
    }

    private void bindToWifiAndWatch() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        bindBestWifi();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    boundWifi = network;
                    try { connectivityManager.bindProcessToNetwork(network); } catch (Exception ignored) {}
                    setDetail("Local Wi‑Fi ready • tap to focus • pinch to zoom • auto reconnect ON");
                    scheduleReconnect(350);
                }
            }

            @Override public void onLost(Network network) {
                if (boundWifi != null && boundWifi.equals(network)) {
                    boundWifi = null;
                    try { connectivityManager.bindProcessToNetwork(null); } catch (Exception ignored) {}
                    setStatus("Together Wi‑Fi changed — waiting to reconnect…", false);
                    ui.postDelayed(() -> {
                        bindBestWifi();
                        scheduleReconnect(400);
                    }, 650);
                }
            }

            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    boundWifi = network;
                    try { connectivityManager.bindProcessToNetwork(network); } catch (Exception ignored) {}
                }
            }
        };

        try {
            NetworkRequest wifiRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            connectivityManager.registerNetworkCallback(wifiRequest, networkCallback);
        } catch (Exception ignored) {}
    }

    private void bindBestWifi() {
        if (connectivityManager == null) return;
        try {
            for (Network n : connectivityManager.getAllNetworks()) {
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    boundWifi = n;
                    connectivityManager.bindProcessToNetwork(n);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private void unbindWifiWatcher() {
        try {
            if (connectivityManager != null && networkCallback != null) connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {}
        try { if (connectivityManager != null) connectivityManager.bindProcessToNetwork(null); } catch (Exception ignored) {}
        networkCallback = null;
        boundWifi = null;
    }

    private void initWebRtc() {
        PeerConnectionFactory.InitializationOptions init =
                PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                        .setEnableInternalTracer(false)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(init);

        eglBase = EglBase.create();
        renderer.init(eglBase.getEglBaseContext(), null);
        renderer.setEnableHardwareScaler(true);
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

        DefaultVideoEncoderFactory encoderFactory =
                new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory =
                new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    private void maybeStart() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        if (!localCameraStarted) startLocalCamera();
        if (!togetherHost.isEmpty() && !togetherToken.isEmpty()) scheduleReconnect(250);
        else setStatus("Camera ready • scan the new HQ QR in Together", false);
    }

    private void startLocalCamera() {
        if (localCameraStarted || factory == null) return;
        try {
            cameraCapturer = new HqCameraCapturer(false, new HqCameraCapturer.Listener() {
                @Override public void onCameraStatus(String message) {
                    setDetail(message + " • local Wi‑Fi auto reconnect ON");
                }

                @Override public void onCameraCapabilities(float availableMaxZoom, boolean tapFocus, boolean stabilization) {
                    maxZoom = Math.max(1f, availableMaxZoom);
                    runOnUiThread(() -> {
                        zoomBar.setEnabled(maxZoom > 1.01f);
                        focusLockButton.setEnabled(true);
                        updateZoomUi(currentZoom());
                    });
                }

                @Override public void onCameraFacingChanged(boolean front) {
                    frontCamera = front;
                    runOnUiThread(() -> {
                        switchButton.setEnabled(true);
                        updatePreviewMirror();
                        setStatus(front ? "Front camera active" : "Rear camera active", false);
                    });
                    pingSoon();
                }

                @Override public void onZoomChanged(float zoom) {
                    runOnUiThread(() -> updateZoomUi(zoom));
                    pingSoon();
                }
            });

            textureHelper = SurfaceTextureHelper.create("TogetherHQ-Camera2", eglBase.getEglBaseContext());
            videoSource = factory.createVideoSource(false);
            cameraCapturer.initialize(textureHelper, getApplicationContext(), videoSource.getCapturerObserver());
            videoTrack = factory.createVideoTrack("together-video", videoSource);
            videoTrack.addSink(renderer);

            cameraCapturer.startCapture(captureWidth, captureHeight, captureFps);
            videoSource.adaptOutputFormat(captureWidth, captureHeight, captureFps);
            localCameraStarted = true;
            updatePreviewMirror();
            setDetail(String.format(Locale.US, "Camera2 ready • %dx%d @ %dfps • tap focus • pinch zoom", captureWidth, captureHeight, captureFps));
        } catch (Exception e) {
            setStatus("Could not start the Samsung camera", true);
            setDetail(e.getMessage() == null ? "Camera start error" : e.getMessage());
        }
    }

    private void stopLocalCamera() {
        localCameraStarted = false;
        try { if (videoTrack != null) videoTrack.removeSink(renderer); } catch (Exception ignored) {}
        try { if (cameraCapturer != null) cameraCapturer.stopCapture(); } catch (Exception ignored) {}
        try { if (cameraCapturer != null) cameraCapturer.dispose(); } catch (Exception ignored) {}
        try { if (videoSource != null) videoSource.dispose(); } catch (Exception ignored) {}
        try { if (textureHelper != null) textureHelper.dispose(); } catch (Exception ignored) {}
        videoTrack = null;
        videoSource = null;
        cameraCapturer = null;
        textureHelper = null;
    }

    private void setQuality(int width, int height, int fps, int bitrate) {
        captureWidth = width;
        captureHeight = height;
        captureFps = fps;
        targetBitrate = bitrate;
        if (cameraCapturer != null && localCameraStarted) {
            try {
                cameraCapturer.changeCaptureFormat(width, height, fps);
                if (videoSource != null) videoSource.adaptOutputFormat(width, height, fps);
                applySenderBitrate();
                setDetail(String.format(Locale.US, "HQ target • %dx%d @ %dfps • %.1f Mbps • H.264 preferred",
                        width, height, fps, bitrate / 1000000f));
                pingSoon();
            } catch (Exception e) {
                setStatus("This camera mode is not available; try 720p", true);
            }
        }
    }

    private void switchCamera() {
        if (cameraCapturer == null) return;
        switchButton.setEnabled(false);
        cameraCapturer.switchCamera();
        ui.postDelayed(() -> switchButton.setEnabled(true), 1600);
    }

    private float currentZoom() {
        return cameraCapturer == null ? 1f : cameraCapturer.getZoom();
    }

    private void setCameraZoom(float zoom) {
        if (cameraCapturer == null) return;
        cameraCapturer.setZoom(Math.max(1f, Math.min(maxZoom, zoom)));
    }

    private void updateZoomUi(float zoom) {
        if (zoomLabel != null) zoomLabel.setText(String.format(Locale.US, "Zoom • %.1f× (max %.1f×)", zoom, maxZoom));
        if (zoomBar != null && maxZoom > 1.001f && !zoomBar.isPressed()) {
            int progress = Math.round((zoom - 1f) / (maxZoom - 1f) * 1000f);
            zoomBar.setProgress(Math.max(0, Math.min(1000, progress)));
        }
    }

    private void updatePreviewMirror() {
        if (renderer != null) renderer.setMirror(mirrorPreview);
    }

    private void scheduleReconnect(long delayMs) {
        if (destroyed || togetherToken.isEmpty()) return;
        ui.removeCallbacks(reconnectRunnable);
        ui.postDelayed(reconnectRunnable, Math.max(100, delayMs));
    }

    private void startCall() {
        if (destroyed || callStarting) return;
        long now = System.currentTimeMillis();
        if (now - lastCallStartAt < 650) return;
        lastCallStartAt = now;

        if (togetherHost.isEmpty() || togetherToken.isEmpty()) {
            setStatus("Scan the new HQ camera QR in Together first", true);
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
            return;
        }
        if (!localCameraStarted) startLocalCamera();
        if (videoTrack == null) {
            scheduleReconnect(900);
            return;
        }

        callStarting = true;
        stopPeer();
        offerSent = false;
        answerSet = false;
        connectionState = "starting";

        ArrayList<PeerConnection.IceServer> noServers = new ArrayList<>();
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(noServers);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        peer = factory.createPeerConnection(config, new PeerObserver());
        if (peer == null) {
            callStarting = false;
            setStatus("Could not create local video connection — retrying", true);
            scheduleReconnect(1200);
            return;
        }

        sender = peer.addTrack(videoTrack, Collections.singletonList("together-camera"));
        applySenderBitrate();
        setStatus("Creating reliable local video link…", false);

        MediaConstraints constraints = new MediaConstraints();
        peer.createOffer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription original) {
                PeerConnection p = peer;
                if (p == null) { callStarting = false; return; }
                String preferred = preferH264(original.description);
                SessionDescription offer = new SessionDescription(original.type, preferred);
                p.setLocalDescription(new SimpleSdpObserver() {
                    @Override public void onSetSuccess() {
                        callStarting = false;
                        ui.postDelayed(MainActivity.this::sendOfferWhenReady, 2800);
                        PeerConnection current = peer;
                        if (current != null && current.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
                            sendOfferWhenReady();
                        }
                    }

                    @Override public void onSetFailure(String error) {
                        callStarting = false;
                        setStatus("Could not prepare local video — retrying", true);
                        scheduleReconnect(900);
                    }
                }, offer);
            }

            @Override public void onCreateFailure(String error) {
                callStarting = false;
                setStatus("Could not create HQ video offer — retrying", true);
                setDetail(error);
                scheduleReconnect(900);
            }
        }, constraints);

        pingSoon();
    }

    private void applySenderBitrate() {
        RtpSender s = sender;
        if (s == null) return;
        try {
            RtpParameters p = s.getParameters();
            if (p.encodings != null && !p.encodings.isEmpty()) {
                for (RtpParameters.Encoding e : p.encodings) {
                    e.maxBitrateBps = targetBitrate;
                    e.maxFramerate = captureFps;
                }
                s.setParameters(p);
            }
        } catch (Exception ignored) {}
    }

    private void stopPeer() {
        PeerConnection old = peer;
        peer = null;
        sender = null;
        offerSent = false;
        answerSet = false;
        answerPollBusy = false;
        connectionState = "idle";
        if (old != null) {
            try { old.close(); } catch (Exception ignored) {}
            try { old.dispose(); } catch (Exception ignored) {}
        }
    }

    private void sendOfferWhenReady() {
        PeerConnection p = peer;
        if (p == null || offerSent) return;
        SessionDescription local = p.getLocalDescription();
        if (local == null || local.description == null || local.description.isEmpty()) return;

        offerSent = true;
        setStatus("Sending HQ video to Together…", false);

        try {
            JSONObject offer = new JSONObject();
            offer.put("type", "offer");
            offer.put("sdp", local.description);

            JSONObject body = new JSONObject();
            body.put("token", togetherToken);
            body.put("state", "camera-on");
            body.put("offer", offer);
            body.put("meta", cameraMeta());

            network.execute(() -> {
                try {
                    JSONObject response = postJsonRetry("/api/camera/offer", body, 3);
                    if (!response.optBoolean("ok", false)) throw new Exception(response.optString("error", "Pairing failed"));
                    setStatus("Together found • completing video connection…", false);
                    ui.post(MainActivity.this::pollAnswer);
                } catch (Exception e) {
                    offerSent = false;
                    setStatus("Local Wi‑Fi link interrupted — reconnecting…", false);
                    setDetail(e.getMessage() == null ? "Local signalling retry" : e.getMessage());
                    scheduleReconnect(1100);
                }
            });
        } catch (Exception e) {
            offerSent = false;
            setStatus("Could not prepare video offer — retrying", true);
            scheduleReconnect(900);
        }
    }

    private void pollAnswer() {
        if (destroyed || peer == null || answerSet || answerPollBusy) return;
        answerPollBusy = true;
        network.execute(() -> {
            try {
                JSONObject d = getJsonRetry("/api/camera/answer?t=" + Uri.encode(togetherToken), 2);
                JSONObject answer = d.optJSONObject("answer");
                if (answer != null && "answer".equals(answer.optString("type")) && !answer.optString("sdp").isEmpty()) {
                    answerSet = true;
                    SessionDescription remote = new SessionDescription(
                            SessionDescription.Type.ANSWER, answer.optString("sdp"));
                    PeerConnection p = peer;
                    if (p != null) {
                        p.setRemoteDescription(new SimpleSdpObserver() {
                            @Override public void onSetSuccess() {
                                setStatus("Connecting high-quality video…", false);
                            }

                            @Override public void onSetFailure(String error) {
                                answerSet = false;
                                setStatus("Video answer rejected — reconnecting", true);
                                setDetail(error);
                                scheduleReconnect(700);
                            }
                        }, remote);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                answerPollBusy = false;
                if (!answerSet && peer != null && !destroyed) ui.postDelayed(MainActivity.this::pollAnswer, 400);
            }
        });
    }

    private void pingSoon() {
        ui.removeCallbacks(pingRunnable);
        ui.postDelayed(pingRunnable, 150);
    }

    private final Runnable pingRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed || togetherToken.isEmpty()) return;
            final String state = connectionState;
            network.execute(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("token", togetherToken);
                    body.put("state", state);
                    body.put("meta", cameraMeta());
                    postJsonRetry("/api/camera/ping", body, 2);
                } catch (Exception ignored) {}
            });
            ui.postDelayed(this, 1000);
        }
    };

    private JSONObject cameraMeta() throws Exception {
        JSONObject meta = new JSONObject();
        meta.put("name", "Together Camera HQ Reliable");
        meta.put("width", captureWidth);
        meta.put("height", captureHeight);
        meta.put("fps", captureFps);
        meta.put("bitrate", targetBitrate);
        meta.put("codec", "H264 preferred");
        meta.put("facing", frontCamera ? "front" : "rear");
        meta.put("zoom", currentZoom());
        meta.put("focus", focusLocked ? "locked" : "continuous");
        return meta;
    }

    private JSONObject postJsonRetry(String path, JSONObject body, int attempts) throws Exception {
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return postJson(path, body);
            } catch (Exception e) {
                last = e;
                if (i + 1 < attempts) {
                    try { Thread.sleep(180L * (i + 1)); } catch (InterruptedException ignored) {}
                }
            }
        }
        throw last == null ? new Exception("Local request failed") : last;
    }

    private JSONObject getJsonRetry(String path, int attempts) throws Exception {
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return getJson(path);
            } catch (Exception e) {
                last = e;
                if (i + 1 < attempts) {
                    try { Thread.sleep(140L * (i + 1)); } catch (InterruptedException ignored) {}
                }
            }
        }
        throw last == null ? new Exception("Local request failed") : last;
    }

    private JSONObject postJson(String path, JSONObject body) throws Exception {
        HttpURLConnection c = open(path);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        return readJson(c);
    }

    private JSONObject getJson(String path) throws Exception {
        HttpURLConnection c = open(path);
        c.setRequestMethod("GET");
        return readJson(c);
    }

    private HttpURLConnection open(String path) throws Exception {
        URL url = new URL("http://" + togetherHost + ":" + togetherPort + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(1200);
        c.setReadTimeout(2000);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-store");
        c.setRequestProperty("Connection", "close");
        c.setRequestProperty("X-Together-Camera", "HQ-Reliable");
        return c;
    }

    private JSONObject readJson(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream(),
                StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null && b.length() < 300000) b.append(line);
        c.disconnect();
        JSONObject json = b.length() == 0 ? new JSONObject() : new JSONObject(b.toString());
        if (code < 200 || code >= 300) throw new Exception(json.optString("error", "HTTP " + code));
        return json;
    }

    private String preferH264(String sdp) {
        if (sdp == null || sdp.isEmpty()) return sdp;
        String[] lines = sdp.split("\\r?\\n");
        Set<String> h264 = new LinkedHashSet<>();
        Set<String> h264Rtx = new LinkedHashSet<>();

        for (String line : lines) {
            if (line.startsWith("a=rtpmap:") && line.toUpperCase(Locale.US).contains(" H264/")) {
                int colon = line.indexOf(':');
                int space = line.indexOf(' ', colon + 1);
                if (colon >= 0 && space > colon) h264.add(line.substring(colon + 1, space));
            }
        }
        for (String line : lines) {
            if (!line.startsWith("a=fmtp:")) continue;
            for (String pt : h264) {
                if (line.contains("apt=" + pt)) {
                    int colon = line.indexOf(':');
                    int space = line.indexOf(' ', colon + 1);
                    if (colon >= 0 && space > colon) h264Rtx.add(line.substring(colon + 1, space));
                }
            }
        }
        if (h264.isEmpty()) return sdp;

        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("m=video ")) {
                String[] parts = line.split(" ");
                if (parts.length > 3) {
                    StringBuilder m = new StringBuilder(parts[0]).append(' ').append(parts[1]).append(' ').append(parts[2]);
                    Set<String> used = new LinkedHashSet<>();
                    for (String pt : h264) { m.append(' ').append(pt); used.add(pt); }
                    for (String pt : h264Rtx) { m.append(' ').append(pt); used.add(pt); }
                    for (int i = 3; i < parts.length; i++) if (!used.contains(parts[i])) m.append(' ').append(parts[i]);
                    line = m.toString();
                }
            }
            out.append(line).append("\r\n");
        }
        return out.toString();
    }

    private class PeerObserver implements PeerConnection.Observer {
        @Override public void onSignalingChange(PeerConnection.SignalingState newState) {}

        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
            connectionState = newState.name().toLowerCase(Locale.US);
            if (newState == PeerConnection.IceConnectionState.CONNECTED ||
                    newState == PeerConnection.IceConnectionState.COMPLETED) {
                ui.removeCallbacks(reconnectRunnable);
                setStatus("LIVE • reliable high-quality video connected", false);
                setDetail(String.format(Locale.US,
                        "%dx%d @ %dfps • %.1f Mbps target • tap focus • pinch zoom • auto reconnect ON",
                        captureWidth, captureHeight, captureFps, targetBitrate / 1000000f));
            } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                setStatus("Video link dropped — reconnecting automatically…", false);
                scheduleReconnect(500);
            } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                setStatus("Wi‑Fi video interrupted — reconnecting automatically…", false);
                scheduleReconnect(1300);
            }
            pingSoon();
        }

        @Override public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            connectionState = newState.name().toLowerCase(Locale.US);
            if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                ui.removeCallbacks(reconnectRunnable);
            } else if (newState == PeerConnection.PeerConnectionState.FAILED ||
                    newState == PeerConnection.PeerConnectionState.DISCONNECTED) {
                scheduleReconnect(newState == PeerConnection.PeerConnectionState.FAILED ? 450 : 1400);
            }
            pingSoon();
        }

        @Override public void onIceConnectionReceivingChange(boolean receiving) {}
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            if (newState == PeerConnection.IceGatheringState.COMPLETE) ui.post(MainActivity.this::sendOfferWhenReady);
        }
        @Override public void onIceCandidate(IceCandidate candidate) {}
        @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
        @Override public void onAddStream(MediaStream stream) {}
        @Override public void onRemoveStream(MediaStream stream) {}
        @Override public void onDataChannel(DataChannel dataChannel) {}
        @Override public void onRenegotiationNeeded() {}
        @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {}
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String error) {}
        @Override public void onSetFailure(String error) {}
    }
}
