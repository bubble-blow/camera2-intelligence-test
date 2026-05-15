package com.example.camera2intelligence;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.params.Face;
import android.util.AttributeSet;
import android.view.View;

public class FaceOverlayView extends View {

    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private Face[] faces = new Face[0];
    private Rect sensorRect;
    private int relativeRotation;
    private boolean mirrorX;

    public FaceOverlayView(Context context) {
        super(context);
        init();
    }

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);
        boxPaint.setColor(Color.GREEN);
        boxPaint.setAntiAlias(true);

        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(36f);
        textPaint.setAntiAlias(true);
    }

    public void setFaces(Face[] newFaces, Rect activeArrayRect, int rotationDegrees, boolean needMirrorX) {
        if (newFaces == null) {
            faces = new Face[0];
        } else {
            faces = newFaces;
        }
        sensorRect = activeArrayRect;
        relativeRotation = rotationDegrees;
        mirrorX = needMirrorX;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faces == null || faces.length == 0 || sensorRect == null) {
            return;
        }

        for (int i = 0; i < faces.length; i++) {
            Rect r = faces[i].getBounds();
            RectF mapped = mapRectForDisplay(r);
            canvas.drawRect(mapped, boxPaint);
            canvas.drawText("#" + i + " s=" + faces[i].getScore(),
                    mapped.left,
                    Math.max(36f, mapped.top - 8f),
                    textPaint);
        }
    }

    private RectF mapRectForDisplay(Rect faceRect) {
        float left = faceRect.left - sensorRect.left;
        float top = faceRect.top - sensorRect.top;
        float right = faceRect.right - sensorRect.left;
        float bottom = faceRect.bottom - sensorRect.top;

        float w = sensorRect.width();
        float h = sensorRect.height();

        float outLeft;
        float outTop;
        float outRight;
        float outBottom;

        int rotation = ((relativeRotation % 360) + 360) % 360;
        if (rotation == 90) {
            outLeft = top / h * getWidth();
            outTop = (w - right) / w * getHeight();
            outRight = bottom / h * getWidth();
            outBottom = (w - left) / w * getHeight();
        } else if (rotation == 180) {
            outLeft = (w - right) / w * getWidth();
            outTop = (h - bottom) / h * getHeight();
            outRight = (w - left) / w * getWidth();
            outBottom = (h - top) / h * getHeight();
        } else if (rotation == 270) {
            outLeft = (h - bottom) / h * getWidth();
            outTop = left / w * getHeight();
            outRight = (h - top) / h * getWidth();
            outBottom = right / w * getHeight();
        } else {
            outLeft = left / w * getWidth();
            outTop = top / h * getHeight();
            outRight = right / w * getWidth();
            outBottom = bottom / h * getHeight();
        }

        RectF rect = new RectF(Math.min(outLeft, outRight), Math.min(outTop, outBottom),
                Math.max(outLeft, outRight), Math.max(outTop, outBottom));

        if (mirrorX) {
            float newLeft = getWidth() - rect.right;
            float newRight = getWidth() - rect.left;
            rect.left = newLeft;
            rect.right = newRight;
        }
        return rect;
    }
}

