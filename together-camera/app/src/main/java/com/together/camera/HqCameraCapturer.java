package com.together.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import org.webrtc.CapturerObserver;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class HqCameraCapturer implements VideoCapturer {
    public interface Listener {
        void onCameraStatus(String message);
        void onCameraCapabilities(float maxZoom, boolean tapFocus, boolean stabilization);
        void onCameraFacingChanged(boolean front);
        void onZoomChanged(float zoom);
    }

    private final Object lock = new Object();
    private final boolean preferFront;
    private final Listener listener;

    private Context context;
    private CameraManager manager;
    private SurfaceTextureHelper textureHelper;
    private CapturerObserver observer;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder requestBuilder;
    private Surface cameraSurface;
    private CameraCharacteristics characteristics;
    private String cameraId;

    private int requestedWidth = 1920;
    private int requestedHeight = 1080;
    private int requestedFps = 30;
    private Size captureSize = new Size(1920, 1080);

    private boolean started = false;
    private boolean disposed = false;
    private boolean frontFacing = false;
    private boolean focusLocked = false;
    private float zoom = 1f;
    private float maxZoom = 1f;
    private Rect activeArray;
    private int sensorOrientation = 0;
    private int maxAfRegions = 0;
    private int maxAeRegions = 0;
    private int continuousAfMode = CaptureRequest.CONTROL_AF_MODE_OFF;
    private boolean autoAfSupported = false;
    private boolean stabilizationSupported = false;
    private int generation = 0;

    public HqCameraCapturer(boolean preferFront, Listener listener) {
        this.preferFront = preferFront;
        this.listener = listener;
        this.frontFacing = preferFront;
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
        synchronized (lock) {
            this.textureHelper = surfaceTextureHelper;
            this.context = applicationContext;
            this.manager = (CameraManager) applicationContext.getSystemService(Context.CAMERA_SERVICE);
            this.observer = capturerObserver;
        }

        surfaceTextureHelper.startListening(frame -> {
            CapturerObserver obs;
            boolean deliver;
            synchronized (lock) {
                obs = observer;
                deliver = started && !disposed && obs != null;
            }
            if (!deliver) return;

            VideoFrame.Buffer buffer = frame.getBuffer();
            buffer.retain();
            VideoFrame rotated = new VideoFrame(buffer, currentFrameRotation(), frame.getTimestampNs());
            try {
                obs.onFrameCaptured(rotated);
            } finally {
                rotated.release();
            }
        });
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        synchronized (lock) {
            if (disposed) return;
            requestedWidth = Math.max(320, width);
            requestedHeight = Math.max(240, height);
            requestedFps = Math.max(10, Math.min(60, framerate));
            started = true;
            ensureThreadLocked();
        }
        if (observer != null) observer.onCapturerStarted(true);
        post(this::openSelectedCamera);
    }

    @Override
    public void stopCapture() {
        synchronized (lock) {
            started = false;
            generation++;
        }
        post(this::closeCameraInternal);
        if (observer != null) observer.onCapturerStopped();
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {
        synchronized (lock) {
            requestedWidth = Math.max(320, width);
            requestedHeight = Math.max(240, height);
            requestedFps = Math.max(10, Math.min(60, framerate));
            generation++;
        }
        post(() -> {
            closeCameraInternal();
            if (isStarted()) openSelectedCamera();
        });
    }

    @Override
    public void dispose() {
        synchronized (lock) {
            if (disposed) return;
            disposed = true;
            started = false;
            generation++;
        }
        post(this::closeCameraInternal);
        try {
            if (textureHelper != null) textureHelper.stopListening();
        } catch (Exception ignored) {}
        stopThread();
    }

    @Override
    public boolean isScreencast() {
        return false;
    }

    public void switchCamera() {
        synchronized (lock) {
            frontFacing = !frontFacing;
            zoom = 1f;
            focusLocked = false;
            generation++;
        }
        post(() -> {
            closeCameraInternal();
            if (isStarted()) openSelectedCamera();
        });
    }

    public void setZoom(float requestedZoom) {
        synchronized (lock) {
            zoom = Math.max(1f, Math.min(Math.max(1f, maxZoom), requestedZoom));
        }
        post(() -> {
            applyControls();
            notifyZoom();
        });
    }

    public float getZoom() {
        synchronized (lock) { return zoom; }
    }

    public float getMaxZoom() {
        synchronized (lock) { return maxZoom; }
    }

    public boolean isFrontFacing() {
        synchronized (lock) { return frontFacing; }
    }

    public void setFocusLocked(boolean locked) {
        synchronized (lock) {
            focusLocked = locked;
        }
        post(() -> {
            if (requestBuilder == null || captureSession == null) return;
            if (!autoAfSupported) {
                status("This camera has fixed focus");
                return;
            }
            try {
                if (locked) {
                    requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                    requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                    captureSession.capture(requestBuilder.build(), null, cameraHandler);
                    requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                } else {
                    requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                    captureSession.capture(requestBuilder.build(), null, cameraHandler);
                    requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                    requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                    captureSession.setRepeatingRequest(requestBuilder.build(), null, cameraHandler);
                }
                status(locked ? "Focus locked" : "Continuous autofocus");
            } catch (Exception e) {
                status("Focus control is not available on this camera");
            }
        });
    }

    public void focusAt(float normalizedX, float normalizedY) {
        final float nx = Math.max(0f, Math.min(1f, normalizedX));
        final float ny = Math.max(0f, Math.min(1f, normalizedY));
        post(() -> focusAtInternal(nx, ny));
    }

    public void refreshOrientation() {
        // Rotation metadata is computed per-frame, so no restart is needed.
    }

    private void focusAtInternal(float nx, float ny) {
        if (requestBuilder == null || captureSession == null || characteristics == null) return;
        if (!autoAfSupported) {
            status("This camera has fixed focus");
            return;
        }
        try {
            Rect sensor = activeArray;
            if (sensor != null && (maxAfRegions > 0 || maxAeRegions > 0)) {
                float[] mapped = mapTouchToSensor(nx, ny);
                int cx = sensor.left + Math.round(mapped[0] * sensor.width());
                int cy = sensor.top + Math.round(mapped[1] * sensor.height());
                int side = Math.max(80, Math.min(sensor.width(), sensor.height()) / 10);
                int half = side / 2;
                Rect box = new Rect(
                        clamp(cx - half, sensor.left, sensor.right - 2),
                        clamp(cy - half, sensor.top, sensor.bottom - 2),
                        clamp(cx + half, sensor.left + 2, sensor.right),
                        clamp(cy + half, sensor.top + 2, sensor.bottom)
                );
                MeteringRectangle region = new MeteringRectangle(box, MeteringRectangle.METERING_WEIGHT_MAX);
                if (maxAfRegions > 0) requestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{region});
                if (maxAeRegions > 0) requestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{region});
            }

            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            captureSession.capture(requestBuilder.build(), null, cameraHandler);
            requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            captureSession.capture(requestBuilder.build(), null, cameraHandler);
            requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            captureSession.setRepeatingRequest(requestBuilder.build(), null, cameraHandler);
            status("Focused");

            if (!focusLocked) {
                cameraHandler.postDelayed(() -> {
                    if (requestBuilder == null || captureSession == null || focusLocked) return;
                    try {
                        requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                        captureSession.capture(requestBuilder.build(), null, cameraHandler);
                        requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, continuousAfMode);
                        captureSession.setRepeatingRequest(requestBuilder.build(), null, cameraHandler);
                    } catch (Exception ignored) {}
                }, 1700);
            }
        } catch (Exception e) {
            status("Tap focus unavailable; continuous autofocus is still active");
        }
    }

    private float[] mapTouchToSensor(float x, float y) {
        int rotation = deviceRotationDegrees();
        float rx = x;
        float ry = y;
        if (rotation == 90) {
            rx = y;
            ry = 1f - x;
        } else if (rotation == 180) {
            rx = 1f - x;
            ry = 1f - y;
        } else if (rotation == 270) {
            rx = 1f - y;
            ry = x;
        }
        if (frontFacing) rx = 1f - rx;
        return new float[]{rx, ry};
    }

    private void ensureThreadLocked() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("TogetherHQ-Camera2");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopThread() {
        HandlerThread thread;
        synchronized (lock) {
            thread = cameraThread;
            cameraThread = null;
            cameraHandler = null;
        }
        if (thread != null) {
            thread.quitSafely();
            try { thread.join(1500); } catch (InterruptedException ignored) {}
        }
    }

    private void post(Runnable runnable) {
        Handler h;
        synchronized (lock) {
            h = cameraHandler;
            if (h == null && !disposed) {
                ensureThreadLocked();
                h = cameraHandler;
            }
        }
        if (h != null) h.post(runnable);
    }

    private boolean isStarted() {
        synchronized (lock) { return started && !disposed; }
    }

    @SuppressLint("MissingPermission")
    private void openSelectedCamera() {
        if (!isStarted() || manager == null || textureHelper == null) return;
        final int openGeneration;
        synchronized (lock) { openGeneration = generation; }

        try {
            cameraId = chooseCameraId(frontFacing);
            if (cameraId == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "No camera found");
            characteristics = manager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            frontFacing = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;

            Integer sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = sensor == null ? 0 : sensor;

            activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Float availableZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            maxZoom = availableZoom == null ? 1f : Math.max(1f, Math.min(10f, availableZoom));

            int[] afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            continuousAfMode = CaptureRequest.CONTROL_AF_MODE_OFF;
            autoAfSupported = false;
            if (afModes != null) {
                for (int mode : afModes) {
                    if (mode == CaptureRequest.CONTROL_AF_MODE_AUTO) autoAfSupported = true;
                    if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
                        continuousAfMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
                    } else if (continuousAfMode == CaptureRequest.CONTROL_AF_MODE_OFF &&
                            mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
                        continuousAfMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
                    } else if (continuousAfMode == CaptureRequest.CONTROL_AF_MODE_OFF &&
                            mode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
                        continuousAfMode = CaptureRequest.CONTROL_AF_MODE_AUTO;
                    }
                }
            }

            Integer afRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            Integer aeRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
            maxAfRegions = afRegions == null ? 0 : afRegions;
            maxAeRegions = aeRegions == null ? 0 : aeRegions;

            stabilizationSupported = false;
            int[] stabilizationModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
            if (stabilizationModes != null) {
                for (int mode : stabilizationModes) {
                    if (mode == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) {
                        stabilizationSupported = true;
                        break;
                    }
                }
            }

            captureSize = chooseSize(characteristics, requestedWidth, requestedHeight);
            textureHelper.setTextureSize(captureSize.getWidth(), captureSize.getHeight());
            SurfaceTexture surfaceTexture = textureHelper.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(captureSize.getWidth(), captureSize.getHeight());
            cameraSurface = new Surface(surfaceTexture);

            notifyCapabilities();
            status("Opening " + (frontFacing ? "front" : "rear") + " camera…");

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    if (!isStarted() || openGeneration != generation) {
                        camera.close();
                        return;
                    }
                    cameraDevice = camera;
                    createSession(openGeneration);
                }

                @Override public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    if (cameraDevice == camera) cameraDevice = null;
                    status("Camera disconnected — reconnecting…");
                    cameraHandler.postDelayed(() -> {
                        if (isStarted() && openGeneration == generation) openSelectedCamera();
                    }, 900);
                }

                @Override public void onError(CameraDevice camera, int error) {
                    camera.close();
                    if (cameraDevice == camera) cameraDevice = null;
                    status("Camera error " + error + " — retrying…");
                    cameraHandler.postDelayed(() -> {
                        if (isStarted() && openGeneration == generation) openSelectedCamera();
                    }, 1200);
                }
            }, cameraHandler);
        } catch (Exception e) {
            status("Could not open camera — retrying");
            if (cameraHandler != null) cameraHandler.postDelayed(() -> {
                if (isStarted() && openGeneration == generation) openSelectedCamera();
            }, 1500);
        }
    }

    private void createSession(int openGeneration) {
        CameraDevice device = cameraDevice;
        Surface surface = cameraSurface;
        if (device == null || surface == null) return;
        try {
            requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            requestBuilder.addTarget(surface);
            applyControlsToBuilder();

            device.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (!isStarted() || openGeneration != generation || cameraDevice == null) {
                        session.close();
                        return;
                    }
                    captureSession = session;
                    try {
                        applyControlsToBuilder();
                        session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler);
                        status(captureSize.getWidth() + "×" + captureSize.getHeight() + " • " + requestedFps + "fps • continuous AF");
                        if (listener != null) listener.onCameraFacingChanged(frontFacing);
                    } catch (Exception e) {
                        status("Camera stream could not start");
                    }
                }

                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    status("Camera mode failed — falling back");
                    cameraHandler.postDelayed(() -> {
                        requestedWidth = 1280;
                        requestedHeight = 720;
                        requestedFps = Math.min(requestedFps, 30);
                        closeCameraInternal();
                        if (isStarted()) openSelectedCamera();
                    }, 600);
                }
            }, cameraHandler);
        } catch (Exception e) {
            status("Could not configure camera");
        }
    }

    private void applyControls() {
        if (requestBuilder == null || captureSession == null) return;
        try {
            applyControlsToBuilder();
            captureSession.setRepeatingRequest(requestBuilder.build(), null, cameraHandler);
        } catch (Exception ignored) {}
    }

    private void applyControlsToBuilder() {
        if (requestBuilder == null) return;

        requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                focusLocked && autoAfSupported ? CaptureRequest.CONTROL_AF_MODE_AUTO : continuousAfMode);

        Range<Integer> fpsRange = chooseFpsRange(characteristics, requestedFps);
        if (fpsRange != null) requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);

        if (stabilizationSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
        }

        if (activeArray != null) {
            float z;
            synchronized (lock) { z = zoom; }
            int cropW = Math.max(2, Math.round(activeArray.width() / z));
            int cropH = Math.max(2, Math.round(activeArray.height() / z));
            int left = activeArray.left + (activeArray.width() - cropW) / 2;
            int top = activeArray.top + (activeArray.height() - cropH) / 2;
            Rect crop = new Rect(left, top, left + cropW, top + cropH);
            requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, crop);
        }
    }

    private void closeCameraInternal() {
        try { if (captureSession != null) captureSession.stopRepeating(); } catch (Exception ignored) {}
        try { if (captureSession != null) captureSession.abortCaptures(); } catch (Exception ignored) {}
        try { if (captureSession != null) captureSession.close(); } catch (Exception ignored) {}
        captureSession = null;
        requestBuilder = null;

        try { if (cameraDevice != null) cameraDevice.close(); } catch (Exception ignored) {}
        cameraDevice = null;

        try { if (cameraSurface != null) cameraSurface.release(); } catch (Exception ignored) {}
        cameraSurface = null;
    }

    private String chooseCameraId(boolean wantFront) throws CameraAccessException {
        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (fallback == null) fallback = id;
            if (facing != null && (facing == CameraCharacteristics.LENS_FACING_FRONT) == wantFront) return id;
        }
        return fallback;
    }

    private Size chooseSize(CameraCharacteristics c, int wantedW, int wantedH) {
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) return new Size(wantedW, wantedH);
        Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
        if (sizes == null || sizes.length == 0) return new Size(wantedW, wantedH);

        final double wantedAspect = wantedW / (double) wantedH;
        final long wantedArea = (long) wantedW * wantedH;
        List<Size> candidates = new ArrayList<>();
        for (Size s : sizes) {
            if (s.getWidth() > 2560 || s.getHeight() > 1440) continue;
            candidates.add(s);
        }
        if (candidates.isEmpty()) candidates.addAll(Arrays.asList(sizes));

        candidates.sort(Comparator.comparingDouble(s -> {
            double aspectPenalty = Math.abs(s.getWidth() / (double) s.getHeight() - wantedAspect) * 8_000_000d;
            double areaPenalty = Math.abs((long) s.getWidth() * s.getHeight() - wantedArea);
            double oversizePenalty = ((long) s.getWidth() * s.getHeight() > wantedArea * 1.35) ? 4_000_000d : 0d;
            return aspectPenalty + areaPenalty + oversizePenalty;
        }));
        return candidates.get(0);
    }

    private Range<Integer> chooseFpsRange(CameraCharacteristics c, int target) {
        if (c == null) return null;
        Range<Integer>[] ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (ranges == null || ranges.length == 0) return null;
        Range<Integer> best = ranges[0];
        int bestScore = Integer.MAX_VALUE;
        for (Range<Integer> r : ranges) {
            int score = Math.abs(r.getUpper() - target) * 20 + Math.abs(r.getLower() - Math.min(target, 15));
            if (r.contains(target)) score -= 1000;
            if (score < bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    private int currentFrameRotation() {
        int device = deviceRotationDegrees();
        int result;
        if (frontFacing) {
            result = (sensorOrientation + device) % 360;
        } else {
            result = (sensorOrientation - device + 360) % 360;
        }
        return result;
    }

    private int deviceRotationDegrees() {
        if (context == null) return 0;
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            int rotation = display.getRotation();
            if (rotation == Surface.ROTATION_90) return 90;
            if (rotation == Surface.ROTATION_180) return 180;
            if (rotation == Surface.ROTATION_270) return 270;
        } catch (Exception ignored) {}
        return 0;
    }

    private int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private void notifyCapabilities() {
        if (listener == null) return;
        float z;
        synchronized (lock) { z = maxZoom; }
        listener.onCameraCapabilities(z, autoAfSupported, stabilizationSupported);
        listener.onCameraFacingChanged(frontFacing);
        notifyZoom();
    }

    private void notifyZoom() {
        if (listener == null) return;
        float z;
        synchronized (lock) { z = zoom; }
        listener.onZoomChanged(z);
    }

    private void status(String message) {
        if (listener != null) listener.onCameraStatus(message);
    }
}
