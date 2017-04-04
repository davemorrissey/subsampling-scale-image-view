/*
Copyright 2014 David Morrissey

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.davemorrissey.labs.subscaleview.sample.extension.views;

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

    private PointF vPrevious;
    private PointF vStart;
    private boolean drawing = false;

    private int strokeWidth;

    private List<PointF> sPoints;
    private List<List<PointF>> pointHistory;
    private Path vPath;
    private Paint paint;

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
        strokeWidth = (int) (density / 60f);
        vPath = new Path();
        pointHistory = new ArrayList<List<PointF>>();
        paint = new Paint();
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
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_1_DOWN:
                vStart = new PointF(event.getX(), event.getY());
                vPrevious = new PointF(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_POINTER_2_DOWN:
                // Abort any current drawing, user is zooming
                vStart = null;
                vPrevious = null;
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
                            sPoints = new ArrayList<PointF>();
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
                savePointHistory();
                sPoints = null;
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

        if (pointHistory.size() > 0) {
            for (List<PointF> hPoints : pointHistory) {
                vPath.rewind();
                PointF vPrev = sourceToViewCoord(hPoints.get(0).x, hPoints.get(0).y);
                vPath.moveTo(vPrev.x, vPrev.y);
                for (int i = 1; i < hPoints.size(); i++) {
                    PointF vPoint = sourceToViewCoord(hPoints.get(i).x, hPoints.get(i).y);
                    vPath.quadTo(vPrev.x, vPrev.y, (vPoint.x + vPrev.x) / 2, (vPoint.y + vPrev.y) / 2);
                    vPrev = vPoint;
                }
                setUpFirstPaintStyle();
                canvas.drawPath(vPath, paint);
                setUpSecondPaintStyle();
                canvas.drawPath(vPath, paint);
            }
        }

        if (sPoints != null && sPoints.size() >= 2) {
            vPath.rewind();
            PointF vPrev = sourceToViewCoord(sPoints.get(0).x, sPoints.get(0).y);
            vPath.moveTo(vPrev.x, vPrev.y);
            for (int i = 1; i < sPoints.size(); i++) {
                PointF vPoint = sourceToViewCoord(sPoints.get(i).x, sPoints.get(i).y);
                vPath.quadTo(vPrev.x, vPrev.y, (vPoint.x + vPrev.x) / 2, (vPoint.y + vPrev.y) / 2);
                vPrev = vPoint;
            }
            setUpFirstPaintStyle();
            canvas.drawPath(vPath, paint);
            setUpSecondPaintStyle();
            canvas.drawPath(vPath, paint);
        }
    }

    private void savePointHistory() {
        if (sPoints != null && sPoints.size() >= 2) {
            pointHistory.add(sPoints);
        }
    }

    private void setUpFirstPaintStyle() {
        paint.setAntiAlias(true);
        paint.setStyle(Style.STROKE);
        paint.setStrokeCap(Cap.ROUND);
        paint.setStrokeWidth(strokeWidth * 2);
        paint.setColor(Color.BLACK);
    }

    private void setUpSecondPaintStyle() {
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(Color.argb(255, 51, 181, 229));
    }

    public void reset() {
        this.pointHistory = new ArrayList<List<PointF>>();
        invalidate();
    }

}
