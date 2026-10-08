package com.together.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.Display;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class CameraController {
    public interface Callback {
        void onStatus(String message);
        void onStreamInfo(int width, int height, int fps, int quality);
        void onCapabilities(float maxZoom, Range<Integer> exposureRange, boolean torch, boolean stabilization);
    }

    private final Context context;
    private final AspectTextureView preview;
    private final MjpegServer server;
    private final Callback callback;
    private final CameraManager manager;

    private HandlerThread cameraThread;
    private Handler handler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private CaptureRequest.Builder request;
    private CameraCharacteristics characteristics;
    private String cameraId;
    private int lensFacing = CameraCharacteristics.LENS_FACING_BACK;

    private int wantedWidth = 1280;
    private int wantedHeight = 720;
    private int wantedFps = 15;
    private int jpegQuality = 88;
    private float zoom = 1f;
    private int exposure = 0;
    private int awbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO;
    private boolean torch = false;
    private boolean stabilization = true;
    private boolean focusLocked = false;
    private int manualRotation = 90; // Samsung A12 landscape correction: field-tested 90° clockwise

    private float maxZoom = 1f;
    private Range<Integer> exposureRange = new Range<>(0, 0);
    private boolean torchSupported = false;
    private boolean stabilizationSupported = false;
    private Size streamSize = new Size(1280, 720);

    public CameraController(Context context, AspectTextureView preview, MjpegServer server, Callback callback) {
        this.context = context;
        this.preview = preview;
        this.server = server;
        this.callback = callback;
        this.manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    public void startThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("TogetherCamera-Camera");
        cameraThread.start();
        handler = new Handler(cameraThread.getLooper());
    }

    public void stopThread() {
        close();
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try { cameraThread.join(1500); } catch (InterruptedException ignored) {}
            cameraThread = null;
            handler = null;
        }
    }

    public void setResolution(int width, int height) {
        wantedWidth = width;
        wantedHeight = height;
        reopen();
    }

    public void setFps(int fps) {
        wantedFps = Math.max(5, Math.min(30, fps));
        updateRepeating();
    }

    public void setJpegQuality(int quality) {
        jpegQuality = Math.max(55, Math.min(98, quality));
        updateRepeating();
    }

    public void setZoom(float value) {
        zoom = Math.max(1f, Math.min(maxZoom, value));
        updateRepeating();
    }

    public void setExposure(int value) {
        exposure = Math.max(exposureRange.getLower(), Math.min(exposureRange.getUpper(), value));
        updateRepeating();
    }

    public void setAwbMode(int mode) {
        awbMode = mode;
        updateRepeating();
    }

    public void setTorch(boolean enabled) {
        torch = enabled && torchSupported;
        updateRepeating();
    }

    public void setStabilization(boolean enabled) {
        stabilization = enabled;
        updateRepeating();
    }

    public void setFocusLocked(boolean locked) {
        focusLocked = locked;
        if (request == null || session == null) return;
        try {
            if (locked) {
                request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                session.capture(request.build(), null, handler);
                request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            } else {
                request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                session.capture(request.build(), null, handler);
                request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            }
            session.setRepeatingRequest(request.build(), null, handler);
        } catch (Exception e) {
            callback.onStatus("Focus control unavailable on this camera");
        }
    }

    public int rotate90() {
        manualRotation = (manualRotation + 90) % 360;
        applyPreviewOrientation();
        updateRepeating();
        return manualRotation;
    }

    public void refreshPreviewOrientation() {
        applyPreviewOrientation();
        updateRepeating();
    }

    public void switchCamera() {
        lensFacing = lensFacing == CameraCharacteristics.LENS_FACING_BACK
                ? CameraCharacteristics.LENS_FACING_FRONT
                : CameraCharacteristics.LENS_FACING_BACK;
        torch = false;
        zoom = 1f;
        reopen();
    }

    private void reopen() {
        if (handler == null) return;
        handler.post(() -> {
            closeInternal();
            if (preview.isAvailable()) open();
        });
    }

    @SuppressLint("MissingPermission")
    public void open() {
        startThread();
        if (camera != null) return;
        if (!preview.isAvailable()) {
            callback.onStatus("Waiting for camera preview…");
            return;
        }
        try {
            cameraId = findCameraId(lensFacing);
            if (cameraId == null) {
                callback.onStatus("Requested camera is not available");
                return;
            }
            characteristics = manager.getCameraCharacteristics(cameraId);
            readCapabilities();
            streamSize = chooseJpegSize(characteristics, wantedWidth, wantedHeight);
            preview.setAspectRatio(streamSize.getWidth(), streamSize.getHeight());
            applyPreviewOrientation();
            reader = ImageReader.newInstance(streamSize.getWidth(), streamSize.getHeight(), ImageFormat.JPEG, 3);
            reader.setOnImageAvailableListener(this::onImage, handler);
            callback.onStatus("Opening camera…");
            manager.openCamera(cameraId, stateCallback, handler);
        } catch (Exception e) {
            callback.onStatus("Camera open failed: " + safe(e.getMessage()));
        }
    }

    public void close() {
        if (handler != null) handler.post(this::closeInternal);
        else closeInternal();
    }

    private void closeInternal() {
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        session = null;
        try { if (camera != null) camera.close(); } catch (Exception ignored) {}
        camera = null;
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        reader = null;
        request = null;
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice device) {
            camera = device;
            createSession();
        }
        @Override public void onDisconnected(CameraDevice device) {
            device.close();
            camera = null;
            callback.onStatus("Camera disconnected");
        }
        @Override public void onError(CameraDevice device, int error) {
            device.close();
            camera = null;
            callback.onStatus("Camera error " + error);
        }
    };

    private void createSession() {
        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            if (texture == null || camera == null || reader == null) return;
            texture.setDefaultBufferSize(streamSize.getWidth(), streamSize.getHeight());
            Surface previewSurface = new Surface(texture);
            request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            request.addTarget(previewSurface);
            request.addTarget(reader.getSurface());
            applyControls(request);
            List<Surface> outputs = Arrays.asList(previewSurface, reader.getSurface());
            camera.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    if (camera == null) return;
                    session = s;
                    try {
                        session.setRepeatingRequest(request.build(), null, handler);
                        server.setStreamInfo(streamSize.getWidth(), streamSize.getHeight(), wantedFps, jpegQuality);
                        callback.onStreamInfo(streamSize.getWidth(), streamSize.getHeight(), wantedFps, jpegQuality);
                        callback.onStatus("Camera ready • local stream running");
                    } catch (CameraAccessException e) {
                        callback.onStatus("Could not start camera stream");
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) {
                    callback.onStatus("Camera configuration failed — try 720p");
                }
            }, handler);
        } catch (Exception e) {
            callback.onStatus("Camera session failed: " + safe(e.getMessage()));
        }
    }

    private void updateRepeating() {
        if (handler == null) return;
        handler.post(() -> {
            if (request == null || session == null) return;
            try {
                applyControls(request);
                session.setRepeatingRequest(request.build(), null, handler);
                server.setStreamInfo(streamSize.getWidth(), streamSize.getHeight(), wantedFps, jpegQuality);
                callback.onStreamInfo(streamSize.getWidth(), streamSize.getHeight(), wantedFps, jpegQuality);
            } catch (Exception ignored) {}
        });
    }

    private void applyControls(CaptureRequest.Builder b) {
        b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        b.set(CaptureRequest.CONTROL_AF_MODE, focusLocked ? CaptureRequest.CONTROL_AF_MODE_AUTO : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        b.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
        if (exposureRange.contains(exposure)) b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposure);
        b.set(CaptureRequest.JPEG_QUALITY, (byte) jpegQuality);
        b.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());

        if (characteristics != null) {
            Rect active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (active != null && zoom > 1.001f) {
                int cropW = (int) (active.width() / zoom);
                int cropH = (int) (active.height() / zoom);
                int left = active.left + (active.width() - cropW) / 2;
                int top = active.top + (active.height() - cropH) / 2;
                b.set(CaptureRequest.SCALER_CROP_REGION, new Rect(left, top, left + cropW, top + cropH));
            } else if (active != null) {
                b.set(CaptureRequest.SCALER_CROP_REGION, active);
            }
        }

        if (torchSupported) b.set(CaptureRequest.FLASH_MODE, torch ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
        if (stabilizationSupported) b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                stabilization ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);

        Range<Integer> fpsRange = chooseFpsRange(characteristics, wantedFps);
        if (fpsRange != null) b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
    }

    private void onImage(ImageReader imageReader) {
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return;
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            server.updateFrame(bytes);
        } catch (Exception ignored) {
        } finally {
            if (image != null) image.close();
        }
    }

    private int displayRotationDegrees() {
        int rotation = Surface.ROTATION_0;
        try {
            Display d = preview.getDisplay();
            if (d != null) rotation = d.getRotation();
        } catch (Exception ignored) {}
        switch (rotation) {
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            default: return 0;
        }
    }

    private int sensorOrientationDegrees() {
        if (characteristics == null) return 0;
        Integer sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        return sensor == null ? 0 : sensor;
    }

    private int previewOrientationDegrees() {
        if (characteristics == null) return manualRotation;
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        int sensor = sensorOrientationDegrees();
        int display = displayRotationDegrees();
        int base;
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
            base = (sensor + display) % 360;
        } else {
            base = (sensor - display + 360) % 360;
        }
        return (base + manualRotation) % 360;
    }

    private void applyPreviewOrientation() {
        preview.post(() -> {
            int degrees = previewOrientationDegrees();
            preview.setRotation(degrees);
        });
    }

    private int jpegOrientation() {
        if (characteristics == null) return manualRotation;
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        int sensor = sensorOrientationDegrees();
        int display = displayRotationDegrees();

        // Camera2 JPEG orientation expressed as clockwise rotation needed for an upright image.
        int base;
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
            base = (sensor + display) % 360;
        } else {
            base = (sensor - display + 360) % 360;
        }
        return (base + manualRotation) % 360;
    }

    private String findCameraId(int facing) throws CameraAccessException {
        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer f = c.get(CameraCharacteristics.LENS_FACING);
            if (fallback == null) fallback = id;
            if (f != null && f == facing) return id;
        }
        return fallback;
    }

    private void readCapabilities() {
        Float mz = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        maxZoom = mz == null ? 1f : Math.max(1f, mz);
        Range<Integer> er = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        exposureRange = er == null ? new Range<>(0, 0) : er;
        Boolean flash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        torchSupported = Boolean.TRUE.equals(flash) && lensFacing == CameraCharacteristics.LENS_FACING_BACK;
        stabilizationSupported = false;
        int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        if (modes != null) for (int m : modes) if (m == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) stabilizationSupported = true;
        callback.onCapabilities(maxZoom, exposureRange, torchSupported, stabilizationSupported);
    }

    private Size chooseJpegSize(CameraCharacteristics c, int wantedW, int wantedH) {
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) return new Size(wantedW, wantedH);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        if (sizes == null || sizes.length == 0) return new Size(wantedW, wantedH);
        List<Size> candidates = new ArrayList<>(Arrays.asList(sizes));
        candidates.sort(Comparator.comparingLong(s -> Math.abs((long) s.getWidth() * s.getHeight() - (long) wantedW * wantedH)));
        for (Size s : candidates) {
            double ratio = s.getWidth() / (double) s.getHeight();
            if (Math.abs(ratio - 16.0 / 9.0) < 0.08 && s.getWidth() <= 2560) return s;
        }
        return candidates.get(0);
    }

    private Range<Integer> chooseFpsRange(CameraCharacteristics c, int target) {
        if (c == null) return null;
        Range<Integer>[] ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (ranges == null || ranges.length == 0) return null;
        Range<Integer> best = ranges[0];
        int bestScore = Integer.MAX_VALUE;
        for (Range<Integer> r : ranges) {
            int score = Math.abs(r.getUpper() - target) * 10 + Math.abs(r.getLower() - target);
            if (r.contains(target)) score -= 1000;
            if (score < bestScore) { bestScore = score; best = r; }
        }
        return best;
    }

    private String safe(String s) { return s == null || s.isEmpty() ? "unknown error" : s; }
}
