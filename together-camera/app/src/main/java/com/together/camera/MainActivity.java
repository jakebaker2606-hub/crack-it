package com.together.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONObject;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.RendererCommon;

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
    private Button reconnectButton;
    private Button switchButton;
    private Button mirrorButton;
    private Spinner qualitySpinner;

    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private SurfaceTextureHelper textureHelper;
    private VideoCapturer videoCapturer;
    private CameraVideoCapturer cameraCapturer;
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
    private boolean destroyed = false;
    private String connectionState = "idle";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(3, 10, 16));
        getWindow().setNavigationBarColor(Color.rgb(3, 10, 16));

        buildUi();
        initWebRtc();
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
        stopPeer();
        stopLocalCamera();
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

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14), dp(10), dp(14), dp(10));
        top.setBackgroundColor(0x99030A10);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        root.addView(top, topLp);

        TextView title = label("TOGETHER CAMERA HQ", 19, true, Color.WHITE);
        top.addView(title);
        status = label("Starting camera…", 13, true, Color.rgb(150, 234, 255));
        top.addView(status);
        detail = label("Native local WebRTC • no cloud • no mobile data • no certificate", 11, false, Color.rgb(190, 207, 220));
        top.addView(detail);

        controlsScroller = new ScrollView(this);
        controlsScroller.setFillViewport(true);
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(10), dp(12), dp(14));
        controls.setBackgroundColor(0xE611202C);
        controlsScroller.addView(controls);

        controls.addView(section("PAIR WITH TOGETHER"));
        pairing = label("Scan the HQ camera QR in Together.", 12, true, Color.rgb(200, 218, 230));
        controls.addView(pairing);
        reconnectButton = button("START / RECONNECT HQ VIDEO");
        reconnectButton.setOnClickListener(v -> startCall());
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

        TextView qNote = label(
                "WebRTC prefers hardware H.264 when both the Samsung and Together computer support it. " +
                "If H.264 is unavailable it automatically falls back to another real-time video codec.",
                10, false, Color.rgb(165, 190, 207));
        qNote.setPadding(0, dp(6), 0, dp(6));
        controls.addView(qNote);

        controls.addView(section("CAMERA"));
        switchButton = button("SWITCH FRONT / REAR");
        switchButton.setOnClickListener(v -> switchCamera());
        controls.addView(switchButton);

        mirrorButton = button("MIRROR PREVIEW: OFF");
        mirrorButton.setOnClickListener(v -> {
            mirrorPreview = !mirrorPreview;
            renderer.setMirror(mirrorPreview);
            mirrorButton.setText("MIRROR PREVIEW: " + (mirrorPreview ? "ON" : "OFF"));
        });
        controls.addView(mirrorButton);

        TextView offline = label(
                "Keep Together Camera HQ open during the game. Video goes only across the private Together Wi‑Fi/hotspot. " +
                "No internet, cloud, USB, certificate, STUN or TURN server is used.",
                10, false, Color.rgb(175, 199, 215));
        offline.setPadding(0, dp(10), 0, 0);
        controls.addView(offline);

        root.addView(controlsScroller);
        setContentView(root);
        applyControlLayout(getResources().getConfiguration().orientation);
    }

    private void applyControlLayout(int orientation) {
        if (controlsScroller == null) return;
        FrameLayout.LayoutParams lp;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330), Gravity.BOTTOM);
        } else {
            lp = new FrameLayout.LayoutParams(dp(330), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
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
        if (u != null && "togethercamera".equalsIgnoreCase(u.getScheme()) && "pair".equalsIgnoreCase(u.getHost())) {
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
                pairing.setText("Paired with Together at " + togetherHost);
                setStatus("Pairing received — starting HQ video…", false);
                return;
            }
        }

        togetherHost = getPreferences(MODE_PRIVATE).getString("host", "");
        togetherToken = getPreferences(MODE_PRIVATE).getString("token", "");
        togetherPort = getPreferences(MODE_PRIVATE).getInt("port", 8765);
        if (!togetherHost.isEmpty() && !togetherToken.isEmpty()) {
            pairing.setText("Previous Together pairing saved • tap START / RECONNECT if needed");
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
        if (!togetherHost.isEmpty() && !togetherToken.isEmpty()) startCall();
        else setStatus("Camera ready • scan the HQ QR in Together", false);
    }

    private void startLocalCamera() {
        if (localCameraStarted || factory == null) return;
        try {
            CameraEnumerator enumerator = Camera2Enumerator.isSupported(this)
                    ? new Camera2Enumerator(this)
                    : new Camera1Enumerator(true);

            String selected = null;
            for (String name : enumerator.getDeviceNames()) {
                if (enumerator.isBackFacing(name)) { selected = name; frontCamera = false; break; }
            }
            if (selected == null && enumerator.getDeviceNames().length > 0) {
                selected = enumerator.getDeviceNames()[0];
                frontCamera = enumerator.isFrontFacing(selected);
            }
            if (selected == null) throw new IllegalStateException("No camera found");

            videoCapturer = enumerator.createCapturer(selected, null);
            if (!(videoCapturer instanceof CameraVideoCapturer)) throw new IllegalStateException("Camera capture unavailable");
            cameraCapturer = (CameraVideoCapturer) videoCapturer;

            textureHelper = SurfaceTextureHelper.create("TogetherHQ-Capture", eglBase.getEglBaseContext());
            videoSource = factory.createVideoSource(false);
            videoCapturer.initialize(textureHelper, getApplicationContext(), videoSource.getCapturerObserver());
            videoTrack = factory.createVideoTrack("together-video", videoSource);
            videoTrack.addSink(renderer);

            videoCapturer.startCapture(captureWidth, captureHeight, captureFps);
            videoSource.adaptOutputFormat(captureWidth, captureHeight, captureFps);
            localCameraStarted = true;
            renderer.setMirror(mirrorPreview);
            setDetail(String.format(Locale.US, "Camera ready • %dx%d @ %dfps • WebRTC hardware video", captureWidth, captureHeight, captureFps));
        } catch (Exception e) {
            setStatus("Could not start the Samsung camera", true);
            setDetail(e.getMessage() == null ? "Camera start error" : e.getMessage());
        }
    }

    private void stopLocalCamera() {
        localCameraStarted = false;
        try { if (videoTrack != null) videoTrack.removeSink(renderer); } catch (Exception ignored) {}
        try { if (videoCapturer != null) videoCapturer.stopCapture(); } catch (Exception ignored) {}
        try { if (videoCapturer != null) videoCapturer.dispose(); } catch (Exception ignored) {}
        try { if (videoSource != null) videoSource.dispose(); } catch (Exception ignored) {}
        try { if (textureHelper != null) textureHelper.dispose(); } catch (Exception ignored) {}
        videoTrack = null;
        videoSource = null;
        videoCapturer = null;
        cameraCapturer = null;
        textureHelper = null;
    }

    private void setQuality(int width, int height, int fps, int bitrate) {
        captureWidth = width;
        captureHeight = height;
        captureFps = fps;
        targetBitrate = bitrate;
        if (videoCapturer != null && localCameraStarted) {
            try {
                videoCapturer.changeCaptureFormat(width, height, fps);
                if (videoSource != null) videoSource.adaptOutputFormat(width, height, fps);
                setDetail(String.format(Locale.US, "HQ target • %dx%d @ %dfps • H.264 preferred", width, height, fps));
                pingSoon();
            } catch (Exception e) {
                setStatus("This camera mode is not available; try 720p", true);
            }
        }
    }

    private void switchCamera() {
        if (cameraCapturer == null) return;
        switchButton.setEnabled(false);
        cameraCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override public void onCameraSwitchDone(boolean isFrontCamera) {
                frontCamera = isFrontCamera;
                ui.post(() -> {
                    switchButton.setEnabled(true);
                    renderer.setMirror(mirrorPreview);
                    setStatus(isFrontCamera ? "Front camera active" : "Rear camera active", false);
                    pingSoon();
                });
            }

            @Override public void onCameraSwitchError(String errorDescription) {
                ui.post(() -> {
                    switchButton.setEnabled(true);
                    setStatus("Could not switch camera", true);
                });
            }
        });
    }

    private void startCall() {
        if (destroyed) return;
        if (togetherHost.isEmpty() || togetherToken.isEmpty()) {
            setStatus("Scan the HQ camera QR in Together first", true);
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
            return;
        }
        if (!localCameraStarted) startLocalCamera();
        if (videoTrack == null) return;

        stopPeer();
        offerSent = false;
        answerSet = false;
        connectionState = "starting";

        ArrayList<PeerConnection.IceServer> noServers = new ArrayList<>();
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(noServers);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;

        peer = factory.createPeerConnection(config, new PeerObserver());
        if (peer == null) {
            setStatus("Could not create local video connection", true);
            return;
        }

        sender = peer.addTrack(videoTrack, Collections.singletonList("together-camera"));
        setStatus("Creating 1080p local video link…", false);

        MediaConstraints constraints = new MediaConstraints();
        peer.createOffer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription original) {
                if (peer == null) return;
                String preferred = preferH264(original.description);
                SessionDescription offer = new SessionDescription(original.type, preferred);
                peer.setLocalDescription(new SimpleSdpObserver() {
                    @Override public void onSetSuccess() {
                        ui.postDelayed(MainActivity.this::sendOfferWhenReady, 3500);
                        if (peer != null && peer.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
                            sendOfferWhenReady();
                        }
                    }
                }, offer);
            }

            @Override public void onCreateFailure(String error) {
                setStatus("Could not create HQ video offer", true);
                setDetail(error);
            }
        }, constraints);

        pingSoon();
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

            JSONObject meta = cameraMeta();
            JSONObject body = new JSONObject();
            body.put("token", togetherToken);
            body.put("state", "camera-on");
            body.put("offer", offer);
            body.put("meta", meta);

            network.execute(() -> {
                try {
                    JSONObject response = postJson("/api/camera/offer", body);
                    if (!response.optBoolean("ok", false)) throw new Exception(response.optString("error", "Pairing failed"));
                    setStatus("Waiting for Together computer…", false);
                    ui.post(MainActivity.this::pollAnswer);
                } catch (Exception e) {
                    offerSent = false;
                    setStatus("Could not reach Together on local Wi‑Fi", true);
                    setDetail(e.getMessage() == null ? "Local signalling failed" : e.getMessage());
                }
            });
        } catch (Exception e) {
            offerSent = false;
            setStatus("Could not prepare video offer", true);
        }
    }

    private void pollAnswer() {
        if (destroyed || peer == null || answerSet || answerPollBusy) return;
        answerPollBusy = true;
        network.execute(() -> {
            try {
                JSONObject d = getJson("/api/camera/answer?t=" + Uri.encode(togetherToken));
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
                                setStatus("Together video answer was rejected", true);
                                setDetail(error);
                            }
                        }, remote);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                answerPollBusy = false;
                if (!answerSet && peer != null && !destroyed) ui.postDelayed(MainActivity.this::pollAnswer, 500);
            }
        });
    }

    private void pingSoon() {
        ui.removeCallbacks(pingRunnable);
        ui.postDelayed(pingRunnable, 200);
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
                    postJson("/api/camera/ping", body);
                } catch (Exception ignored) {}
            });
            ui.postDelayed(this, 1500);
        }
    };

    private JSONObject cameraMeta() throws Exception {
        JSONObject meta = new JSONObject();
        meta.put("name", "Together Camera HQ");
        meta.put("width", captureWidth);
        meta.put("height", captureHeight);
        meta.put("fps", captureFps);
        meta.put("bitrate", targetBitrate);
        meta.put("codec", "H264 preferred");
        meta.put("facing", frontCamera ? "front" : "rear");
        return meta;
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
        c.setConnectTimeout(1800);
        c.setReadTimeout(2600);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-store");
        c.setRequestProperty("X-Together-Camera", "HQ");
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
                setStatus("LIVE • high-quality Together video connected", false);
                setDetail(String.format(Locale.US, "%dx%d @ %dfps target • orientation follows phone • H.264 preferred",
                        captureWidth, captureHeight, captureFps));
            } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                setStatus("Video connection failed — tap START / RECONNECT", true);
            } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                setStatus("Video link interrupted — reconnecting…", false);
            }
            pingSoon();
        }
        @Override public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            connectionState = newState.name().toLowerCase(Locale.US);
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
