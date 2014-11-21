package zvikabh.rccarcontroller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A view representing a circular throttle. The user can adjust to any position within the
 * circle by tapping or dragging. Releasing the throttle moves it back to the origin.
 */
public class SpeedControllerView extends View {

    public SpeedControllerView(Context context) {
        super(context);
        init();
    }

    public SpeedControllerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpeedControllerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setThrottleChangedListener(ThrottleChangedListener listener) {
        mThrottleChangedListener = listener;
    }

    public interface ThrottleChangedListener {
        /**
         * Called whenever the throttle position has changed.
         * Will be called many times repeatedly if the throttle is dragged.
         * @param x New X position, between -1 and 1.
         * @param y New Y position, between -1 and 1.
         */
        public void throttleChanged(float x, float y);
    }

    private void init() {
        mPaintBlackPen = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintBlackPen.setColor(0xff000000);
        mPaintBlackPen.setStyle(Style.STROKE);

        mPaintThickBlackPen = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintThickBlackPen.setColor(0xff000000);
        mPaintThickBlackPen.setStyle(Style.STROKE);
        mPaintThickBlackPen.setStrokeWidth(7);

        mPaintYellowFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintYellowFill.setColor(0xffffffc0);
        mPaintYellowFill.setStyle(Style.FILL);

        mPaintBlueFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintBlueFill.setColor(0xff4040ff);
        mPaintBlueFill.setStyle(Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float xpad = (float) (getPaddingLeft() + getPaddingRight());
        float ypad = (float) (getPaddingTop() + getPaddingBottom());
        float ww = (float) w - xpad;
        float hh = (float) h - ypad;
        mRadius = Math.min(ww, hh) / 2;
        mThrottleRadius = mRadius / 10;
        mCenterX = ww / 2 + getPaddingLeft();
        mCenterY = hh / 2 + getPaddingTop();

        resetThrottle();
    }

    void resetThrottle() {
        mThrottleX = mCenterX;
        mThrottleY = mCenterY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw large yellow circle.
        canvas.drawCircle(mCenterX, mCenterY, mRadius, mPaintYellowFill);
        canvas.drawCircle(mCenterX, mCenterY, mRadius, mPaintBlackPen);

        // Draw throttle position.
        canvas.drawLine(mCenterX, mCenterY, mThrottleX, mThrottleY, mPaintThickBlackPen);
        canvas.drawCircle(mThrottleX, mThrottleY, mThrottleRadius, mPaintBlueFill);
        canvas.drawCircle(mThrottleX, mThrottleY, mThrottleRadius, mPaintThickBlackPen);
    }

    @SuppressLint("ClickableViewAccessibility") 
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x, y;

        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_MOVE:
            x = event.getX();
            y = event.getY();
            break;

        case MotionEvent.ACTION_UP:
        default:
            x = mCenterX;
            y = mCenterY;
            break;
        }

        float dx = x - mCenterX;
        float dy = y - mCenterY;
        double radius = Math.sqrt(dx*dx + dy*dy);
        if (radius > mRadius) {
            dx *= (mRadius / radius);
            dy *= (mRadius / radius);
        }

        updateThrottlePos(mCenterX + dx, mCenterY + dy);
        return true;
    }

    private void updateThrottlePos(float newX, float newY) {
        mThrottleX = newX;
        mThrottleY = newY;
        invalidate();

        if (mThrottleChangedListener != null) {
            float normalizedX = (mThrottleX - mCenterX) / mRadius;
            float normalizedY = (mThrottleY - mCenterY) / mRadius;
            mThrottleChangedListener.throttleChanged(normalizedX, normalizedY);
        }
    }

    private ThrottleChangedListener mThrottleChangedListener;

    private Paint mPaintBlackPen, mPaintThickBlackPen, mPaintYellowFill, mPaintBlueFill;

    private float mRadius, mCenterX, mCenterY;
    private float mThrottleRadius, mThrottleX, mThrottleY;
}
