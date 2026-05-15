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

    public void setFaces(Face[] newFaces, Rect activeArrayRect) {
        if (newFaces == null) {
            faces = new Face[0];
        } else {
            faces = newFaces;
        }
        sensorRect = activeArrayRect;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faces == null || faces.length == 0 || sensorRect == null) {
            return;
        }

        float scaleX = (float) getWidth() / (float) sensorRect.width();
        float scaleY = (float) getHeight() / (float) sensorRect.height();

        for (int i = 0; i < faces.length; i++) {
            Rect r = faces[i].getBounds();
            RectF mapped = new RectF(
                    (r.left - sensorRect.left) * scaleX,
                    (r.top - sensorRect.top) * scaleY,
                    (r.right - sensorRect.left) * scaleX,
                    (r.bottom - sensorRect.top) * scaleY);
            canvas.drawRect(mapped, boxPaint);
            canvas.drawText("#" + i + " s=" + faces[i].getScore(),
                    mapped.left,
                    Math.max(36f, mapped.top - 8f),
                    textPaint);
        }
    }
}
