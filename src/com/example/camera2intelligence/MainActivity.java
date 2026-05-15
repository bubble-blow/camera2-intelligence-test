package com.example.camera2intelligence;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.graphics.Rect;
import android.hardware.camera2.params.Face;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private TextureView previewView;
    private TextView facesText;
    private EditText cameraIdInput;
    private Switch rawSwitch;
    private FaceOverlayView faceOverlayView;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest previewRequest;

    private ImageReader jpegReader;
    private ImageReader rawReader;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private Rect activeArrayRect;

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int width, int height) {
        }

        @Override
        public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = (TextureView) findViewById(R.id.texture_preview);
        facesText = (TextView) findViewById(R.id.text_faces);
        cameraIdInput = (EditText) findViewById(R.id.edit_camera_id);
        rawSwitch = (Switch) findViewById(R.id.switch_raw);
        faceOverlayView = (FaceOverlayView) findViewById(R.id.face_overlay);
        Button startButton = (Button) findViewById(R.id.button_start);

        previewView.setSurfaceTextureListener(surfaceTextureListener);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAndStartSession();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void startBackgroundThread() {
        cameraThread = new HandlerThread("Camera2Thread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try {
                cameraThread.join();
            } catch (InterruptedException ignored) {
            }
            cameraThread = null;
            cameraHandler = null;
        }
    }

    private void openAndStartSession() {
        if (!previewView.isAvailable()) {
            return;
        }
        closeCamera();
        final String cameraId = cameraIdInput.getText().toString();
        if (TextUtils.isEmpty(cameraId)) {
            facesText.setText("请输入 Camera ID");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    createSession(rawSwitch.isChecked());
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    if (camera == cameraDevice) {
                        cameraDevice = null;
                    }
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    if (camera == cameraDevice) {
                        cameraDevice = null;
                    }
                    facesText.setText("打开相机失败: " + error);
                }
            }, cameraHandler);
        } catch (Exception e) {
            facesText.setText("openCamera 异常: " + e.getMessage());
        }
    }

    private void createSession(boolean enableRaw) {
        if (cameraDevice == null) {
            return;
        }
        try {
            Surface previewSurface = new Surface(previewView.getSurfaceTexture());
            jpegReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2);

            List<Surface> outputs = new ArrayList<Surface>();
            outputs.add(previewSurface);
            outputs.add(jpegReader.getSurface());

            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            CameraCharacteristics cc = manager.getCameraCharacteristics(cameraDevice.getId());
            activeArrayRect = cc.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            if (enableRaw) {
                int[] rawCaps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean supportsRaw = false;
                if (rawCaps != null) {
                    for (int cap : rawCaps) {
                        if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) {
                            supportsRaw = true;
                            break;
                        }
                    }
                }
                if (supportsRaw) {
                    rawReader = ImageReader.newInstance(1920, 1080, ImageFormat.RAW_SENSOR, 2);
                    outputs.add(rawReader.getSurface());
                } else {
                    facesText.setText("该相机不支持 RAW，已仅创建预览+JPEG");
                }
            }

            cameraDevice.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    captureSession = session;
                    startPreviewRequest();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    facesText.setText("createCaptureSession 失败");
                }
            }, cameraHandler);
        } catch (Exception e) {
            facesText.setText("createSession 异常: " + e.getMessage());
        }
    }

    private void startPreviewRequest() {
        if (cameraDevice == null || captureSession == null) {
            return;
        }
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface previewSurface = new Surface(previewView.getSurfaceTexture());
            builder.addTarget(previewSurface);
            builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                    CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE);
            previewRequest = builder.build();

            captureSession.setRepeatingRequest(previewRequest, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    updateFaces(result);
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            facesText.setText("setRepeatingRequest 异常: " + e.getMessage());
        }
    }

    private void updateFaces(TotalCaptureResult result) {
        Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
        String text;
        if (faces == null || faces.length == 0) {
            text = "人脸结果：0";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("人脸结果：").append(faces.length);
            for (int i = 0; i < faces.length; i++) {
                sb.append(" #").append(i)
                        .append(" score=").append(faces[i].getScore())
                        .append(" rect=").append(faces[i].getBounds().toShortString());
            }
            text = sb.toString();
        }
        final String finalText = text;
        final Face[] finalFaces = faces;
        final Rect finalActiveRect = activeArrayRect;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                facesText.setText(finalText);
                faceOverlayView.setFaces(finalFaces, finalActiveRect);
            }
        });
    }

    private void closeCamera() {
        if (faceOverlayView != null) {
            faceOverlayView.setFaces(null, null);
        }
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (jpegReader != null) {
            jpegReader.close();
            jpegReader = null;
        }
        if (rawReader != null) {
            rawReader.close();
            rawReader = null;
        }
    }
}
