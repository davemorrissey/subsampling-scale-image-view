package com.davemorrissey.labs.subscaleview.test.extension.views;

import android.content.Context;
import android.graphics.*;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.ArrayList;
import java.util.List;

public class FreehandView extends SubsamplingScaleImageView implements OnTouchListener {

    private final Paint paint = new Paint();
    private final Path vPath = new Path();
    private final PointF vPoint = new PointF();
    private PointF vPrev = new PointF();
    private PointF vPrevious;
    private PointF vStart;
    private boolean drawing = false;

    private int strokeWidth;

    private List<PointF> sPoints;

    public FreehandView(Context context, AttributeSet attr) {
        super(context, attr);
        initialise();
    }

    public FreehandView(Context context) {
        this(context, null);
    }

    private void initialise() {
        setOnTouchListener(this);
        float density = getResources().getDisplayMetrics().densityDpi;
        strokeWidth = (int)(density/60f);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (sPoints != null && !drawing) {
            return super.onTouchEvent(event);
        }
        boolean consumed = false;
        int touchCount = event.getPointerCount();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (event.getActionIndex() == 0) {
                    vStart = new PointF(event.getX(), event.getY());
                    vPrevious = new PointF(event.getX(), event.getY());
                } else {
                    vStart = null;
                    vPrevious = null;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                PointF sCurrentF = viewToSourceCoord(event.getX(), event.getY());
                PointF sCurrent = new PointF(sCurrentF.x, sCurrentF.y);
                PointF sStart = vStart == null ? null : new PointF(viewToSourceCoord(vStart).x, viewToSourceCoord(vStart).y);

                if (touchCount == 1 && vStart != null) {
                    float vDX = Math.abs(event.getX() - vPrevious.x);
                    float vDY = Math.abs(event.getY() - vPrevious.y);
                    if (vDX >= strokeWidth * 5 || vDY >= strokeWidth * 5) {
                        if (sPoints == null) {
                            sPoints = new ArrayList<>();
                            sPoints.add(sStart);
                        }
                        sPoints.add(sCurrent);
                        vPrevious.x = event.getX();
                        vPrevious.y = event.getY();
                        drawing = true;
                    }
                    consumed = true;
                    invalidate();
                } else if (touchCount == 1) {
                    // Consume all one touch drags to prevent odd panning effects handled by the superclass.
                    consumed = true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                invalidate();
                drawing = false;
                vPrevious = null;
                vStart = null;
        }
        // Use parent to handle pinch and two-finger pan.
        return consumed || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Don't draw anything before image is ready.
        if (!isReady()) {
            return;
        }

        paint.setAntiAlias(true);

        if (sPoints != null && sPoints.size() >= 2) {
            vPath.reset();
            sourceToViewCoord(sPoints.get(0).x, sPoints.get(0).y, vPrev);
            vPath.moveTo(vPrev.x, vPrev.y);
            for (int i = 1; i < sPoints.size(); i++) {
                sourceToViewCoord(sPoints.get(i).x, sPoints.get(i).y, vPoint);
                vPath.quadTo(vPrev.x, vPrev.y, (vPoint.x + vPrev.x) / 2, (vPoint.y + vPrev.y) / 2);
                vPrev = vPoint;
            }
            paint.setStyle(Style.STROKE);
            paint.setStrokeCap(Cap.ROUND);
            paint.setStrokeWidth(strokeWidth * 2);
            paint.setColor(Color.BLACK);
            canvas.drawPath(vPath, paint);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor(Color.argb(255, 51, 181, 229));
            canvas.drawPath(vPath, paint);
        }

    }

    public void reset() {
        this.sPoints = null;
        invalidate();
    }

}
