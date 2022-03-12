package com.example.nativelib;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public class YYCamera {
    private static final String TAG = "YYCamera";
    private Context context;
    private CameraManager cameraManager;
    private CameraDevice mCamera;
    private CameraCaptureSession mSession;

    private ChildThread imageReaderThread;
    private ChildThread cameraManagerThread;

    private String frontCameraId = "";
    private String backCameraId = "";
    private ImageReader imageReader;
    private SessionConfiguration sessionConfiguration;
    private CaptureRequest.Builder captureRequestBuilder;

    public YYCamera(Context context) {
        this.context = context;
    }

    private YYMediaCodec.MediaDecodeListener mediaDecodeCallback;

    public void setMediaDecodeListener(YYMediaCodec.MediaDecodeListener listener) {
        this.mediaDecodeCallback = listener;
    }

    public void init() {
        if (null == imageReaderThread) {
            imageReaderThread = new ChildThread();
            imageReaderThread.start();
        }
        if (null == cameraManagerThread) {
            cameraManagerThread = new ChildThread();
            cameraManagerThread.start();
        }

        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] strings = cameraManager.getCameraIdList();
            mediaDecodeCallback.logMediaInfo("相机数量：" + strings.length);
            for (String cameraId : strings) {
                isHardwareSupported(cameraId);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 1);
        imageReader.setOnImageAvailableListener(imageAvailableCallback, new Handler(imageReaderThread.childLooper));
    }

    public void open() {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                mediaDecodeCallback.logMediaInfo("权限不足");
                return;
            }
            mediaDecodeCallback.readVideoComplete(1920, 1080, 0, 0);
            cameraManager.openCamera(backCameraId, stateCallback, new Handler(cameraManagerThread.childLooper));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // CameraCharacteristics  可通过 CameraManager.getCameraCharacteristics() 获取
    private void isHardwareSupported(String cameraId) throws CameraAccessException {
        mediaDecodeCallback.logMediaInfo("相机id：" + cameraId);
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        int lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            frontCameraId = cameraId;
            mediaDecodeCallback.logMediaInfo("前置摄像头");
        } else if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            backCameraId = cameraId;
            mediaDecodeCallback.logMediaInfo("后置摄像头");
        }

        Integer deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (deviceLevel == null) {
            mediaDecodeCallback.logMediaInfo("can not get INFO_SUPPORTED_HARDWARE_LEVEL");
            return;
        }
        switch (deviceLevel) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                mediaDecodeCallback.logMediaInfo("hardware supported level:LEVEL_FULL");
                break;
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                mediaDecodeCallback.logMediaInfo("hardware supported level:LEVEL_LEGACY");
                break;
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                mediaDecodeCallback.logMediaInfo("hardware supported level:LEVEL_3");
                break;
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                mediaDecodeCallback.logMediaInfo("hardware supported level:LEVEL_LIMITED");
                break;
        }

        StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = streamConfigurationMap.getOutputSizes(ImageReader.class);
        StringBuilder builder = new StringBuilder();
        for (Size s : sizes) {
            builder.append(s.toString()).append("，");

        }
        mediaDecodeCallback.logMediaInfo("支持的视频尺寸：" + builder.toString());

        boolean YUV = streamConfigurationMap.isOutputSupportedFor(ImageFormat.YUV_420_888);
        if (YUV) {
            mediaDecodeCallback.logMediaInfo("支持的视频格式：YUV_420_888");
        }
    }


    public void release() {
        if (null != mCamera) {
            mCamera.close();
            mCamera = null;

        }
        if (null != imageReaderThread && imageReaderThread.isAlive()) {
            imageReaderThread.interrupt();
        }
        if (null != cameraManagerThread && cameraManagerThread.isAlive()) {
            cameraManagerThread.interrupt();
        }
    }


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mediaDecodeCallback.logMediaInfo("设备开启");
            mCamera = camera;
            try {
                captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuilder.addTarget(imageReader.getSurface());
                camera.createCaptureSession(Collections.singletonList(imageReader.getSurface()), StateCallback, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mediaDecodeCallback.logMediaInfo("设备断开");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            //发生异常，关闭摄像头
            camera.close();
            mediaDecodeCallback.logMediaInfo("设备异常");
        }
    };

    private final ImageReader.OnImageAvailableListener imageAvailableCallback = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = imageReader.acquireNextImage();
            if (image != null) {
                Image.Plane[] planes = image.getPlanes();
                Image.Plane yPlane = planes[0];
                Image.Plane uvPlane = planes[1];
                Image.Plane vuPlane = planes[2];

//                int yRStride = yPlane.getRowStride();
//                int uvRStride = uvPlane.getRowStride();
//                int vuRStride = vuPlane.getRowStride();
//
//                int yPStride = yPlane.getPixelStride();
//                int uvPStride = uvPlane.getPixelStride();
//                int vuPStride = vuPlane.getPixelStride();


                ByteBuffer yBuffer = yPlane.getBuffer(); // Data from Y channel
                ByteBuffer uvBuffer = uvPlane.getBuffer(); // Data from UV channel
                ByteBuffer vuBuffer = vuPlane.getBuffer(); // Data from VU channel

                int yLen = yBuffer.remaining();
                int uvLen = uvBuffer.remaining();
                int vuLen = vuBuffer.remaining();

                byte[] yBytes = new byte[yLen];
                byte[] uvBytes = new byte[uvLen];
                yBuffer.get(yBytes);
                uvBuffer.get(uvBytes);

                byte[] yuvBytes = new byte[yBytes.length + uvBytes.length];
                System.arraycopy(yBytes, 0, yuvBytes, 0, yBytes.length);
                System.arraycopy(uvBytes, 0, yuvBytes, yBytes.length, uvBytes.length);

                mediaDecodeCallback.onVideoOutput(yuvBytes);

                yBuffer.clear();
                uvBuffer.clear();
                vuBuffer.clear();
//                yuvData.clear();
                image.close();
            }

        }
    };
    private final CameraCaptureSession.StateCallback StateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mSession = session;
            try {
                session.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
        }
    };

    private static class ChildThread extends Thread {
        Looper childLooper;

        @Override
        public void run() {
            super.run();
            Looper.prepare();
            childLooper = Looper.myLooper();
            Looper.loop();
        }
    }
}
