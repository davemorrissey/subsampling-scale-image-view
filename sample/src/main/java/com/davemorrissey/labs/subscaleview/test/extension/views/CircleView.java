package com.davemorrissey.labs.subscaleview.test.extension.views;

import android.content.Context;
import android.graphics.*;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

public class CircleView extends SubsamplingScaleImageView {

    private int strokeWidth;

    private final PointF sCenter = new PointF();
    private final PointF vCenter = new PointF();
    private final Paint paint = new Paint();

    public CircleView(Context context) {
        this(context, null);
    }

    public CircleView(Context context, AttributeSet attr) {
        super(context, attr);
        initialise();
    }

    private void initialise() {
        float density = getResources().getDisplayMetrics().densityDpi;
        strokeWidth = (int)(density/60f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Don't draw pin before image is ready so it doesn't move around during setup.
        if (!isReady()) {
            return;
        }

        sCenter.set(getSWidth()/2, getSHeight()/2);
        sourceToViewCoord(sCenter, vCenter);
        float radius = (getScale() * getSWidth()) * 0.25f;

        paint.setAntiAlias(true);
        paint.setStyle(Style.STROKE);
        paint.setStrokeCap(Cap.ROUND);
        paint.setStrokeWidth(strokeWidth * 2);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(vCenter.x, vCenter.y, radius, paint);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(Color.argb(255, 51, 181, 229));
        canvas.drawCircle(vCenter.x, vCenter.y, radius, paint);
    }

}
