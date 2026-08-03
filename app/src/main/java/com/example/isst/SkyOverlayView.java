package com.example.isst;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

public class SkyOverlayView extends View {

    private volatile CelestialObject[] objects;
    private volatile float[] deviceToWorldMatrix = null;
    private volatile float currentVFovPortrait = 70f;

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;

    public SkyOverlayView(Context context) {
        super(context);
        init(context);
    }

    public SkyOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        density = dm.density;

        labelBgPaint.setColor(Color.parseColor("#CC071828"));
        labelBgPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(dp(11));
        labelPaint.setTypeface(Typeface.MONOSPACE);

        emojiPaint.setTextSize(dp(20));

        hudPaint.setStyle(Paint.Style.STROKE);

        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(dp(1.5f));

        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(dp(2));

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(5));
    }

    public void setObjects(CelestialObject[] objects) {
        this.objects = objects;
        postInvalidate();
    }

    /**
     * Keeps the existing three-argument API. Both matrices are accepted for
     * source compatibility, but projection uses correctedMatrix, which must be
     * a true-north Android device-to-world ENU matrix.
     */
    public void setRotationMatrix(
            float[] rawMatrix,
            float[] correctedMatrix,
            float cameraVFovPortrait) {

        this.deviceToWorldMatrix =
                correctedMatrix == null ? null : correctedMatrix.clone();
        this.currentVFovPortrait = cameraVFovPortrait;
        postInvalidate();
    }

    @Deprecated
    public void setRotationMatrix(float[] matrix, float cameraVFovPortrait) {
        setRotationMatrix(matrix, matrix, cameraVFovPortrait);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float[] matrix = deviceToWorldMatrix;
        CelestialObject[] currentObjects = objects;
        if (matrix == null || currentObjects == null) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        drawHUD(canvas, w, h, matrix);
        drawCrosshair(canvas, w, h);

        for (CelestialObject obj : currentObjects) {
            float[] pos = SkyProjection.project(
                    obj.azimuth,
                    obj.elevation,
                    matrix,
                    w,
                    h,
                    currentVFovPortrait);

            if (pos != null) {
                drawObject(canvas, obj, pos[0], pos[1]);
            }
        }
    }

    private void drawObject(Canvas canvas, CelestialObject obj, float x, float y) {
        if (obj.isStar()) {
            drawStar(canvas, obj, x, y);
            return;
        }

        float r = dp(obj.size * 0.6f);

        glowPaint.setColor(obj.color & 0x33FFFFFF);
        canvas.drawCircle(x, y, r + dp(6), glowPaint);

        circlePaint.setColor(obj.color & 0x88FFFFFF);
        circlePaint.setStrokeWidth(dp(3));
        canvas.drawCircle(x, y, r + dp(3), circlePaint);

        circlePaint.setColor(obj.color);
        circlePaint.setStrokeWidth(dp(2));
        canvas.drawCircle(x, y, r, circlePaint);

        float emojiSize = dp(obj.size * 0.75f);
        emojiPaint.setTextSize(emojiSize);
        float ew = emojiPaint.measureText(obj.emoji);
        canvas.drawText(obj.emoji, x - ew / 2f, y + emojiSize * 0.35f, emojiPaint);

        if (!obj.showLabel) return;

        String label = obj.name + "  " + String.format("%.0f°", obj.elevation);
        labelPaint.setTextSize(dp(11));
        float tw = labelPaint.measureText(label);
        float pad = dp(5);
        float lx = x + r + dp(8);
        float ly = y + dp(4);

        RectF bg = new RectF(lx - pad, ly - dp(13), lx + tw + pad, ly + dp(4));
        canvas.drawRoundRect(bg, dp(4), dp(4), labelBgPaint);
        labelPaint.setColor(obj.color);
        canvas.drawText(label, lx, ly, labelPaint);
    }

    private void drawStar(Canvas canvas, CelestialObject obj, float x, float y) {
        float radius = dp(obj.size * 0.35f);

        glowPaint.setColor(obj.color & 0x22FFFFFF);
        glowPaint.setStrokeWidth(dp(2));
        canvas.drawCircle(x, y, radius + dp(2), glowPaint);

        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setColor(obj.color);
        canvas.drawCircle(x, y, Math.max(dp(1.4f), radius), circlePaint);
        circlePaint.setStyle(Paint.Style.STROKE);

        if (!obj.showLabel) return;

        String label = obj.name;
        labelPaint.setTextSize(dp(9));
        float tw = labelPaint.measureText(label);
        float pad = dp(4);
        float lx = x + dp(7);
        float ly = y + dp(3);

        RectF bg = new RectF(lx - pad, ly - dp(11), lx + tw + pad, ly + dp(3));
        canvas.drawRoundRect(bg, dp(3), dp(3), labelBgPaint);
        labelPaint.setColor(obj.color);
        canvas.drawText(label, lx, ly, labelPaint);
    }

    private void drawHUD(Canvas canvas, int w, int h, float[] matrix) {
        drawProjectedHorizon(canvas, w, h, matrix);
        drawCompassTicks(canvas, w, h, matrix);
    }

    private void drawProjectedHorizon(Canvas canvas, int w, int h, float[] matrix) {
        hudPaint.setColor(Color.parseColor("#4400FF9C"));
        hudPaint.setStrokeWidth(dp(1));

        float[] last = null;
        for (int az = 0; az <= 360; az += 2) {
            float[] pos = SkyProjection.project(
                    az,
                    0,
                    matrix,
                    w,
                    h,
                    currentVFovPortrait);

            if (pos != null && last != null) {
                float dx = pos[0] - last[0];
                float dy = pos[1] - last[1];
                if (dx * dx + dy * dy < w * w) {
                    canvas.drawLine(last[0], last[1], pos[0], pos[1], hudPaint);
                }
            }
            last = pos;
        }
    }

    private void drawCompassTicks(Canvas canvas, int w, int h, float[] matrix) {
        hudPaint.setColor(Color.parseColor("#6600D4FF"));
        labelPaint.setColor(Color.parseColor("#BB00D4FF"));
        labelPaint.setTextSize(dp(10));

        String[] cardinals = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

        for (int az = 0; az < 360; az += 5) {
            float[] horizon = SkyProjection.project(
                    az,
                    0,
                    matrix,
                    w,
                    h,
                    currentVFovPortrait);
            if (horizon == null) continue;

            boolean major = az % 45 == 0;
            float tickLen = major ? dp(16) : (az % 10 == 0 ? dp(9) : dp(5));
            hudPaint.setStrokeWidth(major ? dp(1.5f) : dp(0.7f));
            canvas.drawLine(horizon[0], horizon[1], horizon[0], horizon[1] - tickLen, hudPaint);

            if (major) {
                String lbl = cardinals[az / 45];
                float lw = labelPaint.measureText(lbl);
                canvas.drawText(lbl, horizon[0] - lw / 2f, horizon[1] - tickLen - dp(4), labelPaint);
            }
        }
    }

    private void drawCrosshair(Canvas canvas, int w, int h) {
        float cx = w / 2f;
        float cy = h / 2f;
        float len = dp(22);
        float gap = dp(7);

        crosshairPaint.setColor(Color.parseColor("#6600D4FF"));

        canvas.drawLine(cx - len, cy, cx - gap, cy, crosshairPaint);
        canvas.drawLine(cx + gap, cy, cx + len, cy, crosshairPaint);
        canvas.drawLine(cx, cy - len, cx, cy - gap, crosshairPaint);
        canvas.drawLine(cx, cy + gap, cx, cy + len, crosshairPaint);
        canvas.drawCircle(cx, cy, dp(18), crosshairPaint);
    }

    private float dp(float value) {
        return value * density;
    }
}
