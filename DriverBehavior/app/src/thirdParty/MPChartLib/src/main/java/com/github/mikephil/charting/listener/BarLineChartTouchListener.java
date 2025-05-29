package com.github.mikephil.charting.listener;

import android.annotation.SuppressLint;
import android.graphics.Matrix;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.AnimationUtils;

import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarLineScatterCandleBubbleData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IBarLineScatterCandleBubbleDataSet;
import com.github.mikephil.charting.interfaces.datasets.IDataSet;
import com.github.mikephil.charting.utils.MPPointD;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.Utils;
import com.github.mikephil.charting.utils.ViewPortHandler;

/**
 * TouchListener for Bar-, Line-, Scatter- and CandleStickChart with handles all
 * touch interaction. Longpress == Zoom out. Double-Tap == Zoom in.
 *
 * @author Philipp Jahoda
 */
public class BarLineChartTouchListener extends ChartTouchListener<BarLineChartBase<? extends BarLineScatterCandleBubbleData<?
        extends IBarLineScatterCandleBubbleDataSet<? extends Entry>>>> {

    /**
     * the original touch-matrix from the chart
     */
    private Matrix mMatrix = new Matrix();

    /**
     * matrix for saving the original matrix state
     */
    private Matrix mSavedMatrix = new Matrix();

    /**
     * point where the touch action started
     */
    private MPPointF mTouchStartPoint = MPPointF.getInstance(0,0);

    /**
     * center between two pointers (fingers on the display)
     */
    private MPPointF mTouchPointCenter = MPPointF.getInstance(0,0);

    private float mSavedXDist = 1f;
    private float mSavedYDist = 1f;
    private float mSavedDist = 1f;

    private IDataSet mClosestDataSetToTouch;

    /**
     * used for tracking velocity of dragging
     */
    private VelocityTracker mVelocityTracker;

    private long mDecelerationLastTime = 0;
    private MPPointF mDecelerationCurrentPoint = MPPointF.getInstance(0,0);
    private MPPointF mDecelerationVelocity = MPPointF.getInstance(0,0);

    /**
     * the distance of movement that will be counted as a drag
     */
    private float mDragTriggerDist;

    /**
     * the minimum distance between the pointers that will trigger a zoom gesture
     */
    private float mMinScalePointerDistance;

    private float mLeftThreshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 7, mChart.getDisplayMetrics());
    private boolean mUserTouchesFirstOnMapSurface;  // in pointer down, check is first touched point is on the left margin or not
    private boolean mShouldScrollHorizontally = false;
    private boolean mHasBeenOneFingerScrolledHorizontally = false;
    private double mPreviousXEvent = 0;
    private double mPreviousYEvent = 0;
    // point translated which represents the previous X event for each finger
    private MPPointF mPrevXPointer0 =null;

    // previous distance between pointers translate
    private float mPreviousXDist;

    // physical gap to differentiate zoom and pan
    private static final double mSizeInMMZoomPan = 0.5;


    /**
     * Constructor with initialization parameters.
     *
     * @param chart               instance of the chart
     * @param touchMatrix         the touch-matrix of the chart
     * @param dragTriggerDistance the minimum movement distance that will be interpreted as a "drag" gesture in dp (3dp equals
     *                            to about 9 pixels on a 5.5" FHD screen)
     */
    public BarLineChartTouchListener(BarLineChartBase<? extends BarLineScatterCandleBubbleData<? extends
            IBarLineScatterCandleBubbleDataSet<? extends Entry>>> chart, Matrix touchMatrix, float dragTriggerDistance) {
        super(chart);
        this.mMatrix = touchMatrix;

        this.mDragTriggerDist = Utils.convertDpToPixel(dragTriggerDistance);

        this.mMinScalePointerDistance = Utils.convertDpToPixel(3.5f);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event)
    {
        if (mTouchMode == NONE)
        {
            mGestureDetector.onTouchEvent(event);
        }

        if (!mChart.isDragEnabled() && (!mChart.isScaleXEnabled() && !mChart.isScaleYEnabled()))
        {
            return true;
        }

        // Handle touch events here...
        switch (event.getAction() & MotionEvent.ACTION_MASK)
        {
            case MotionEvent.ACTION_DOWN:
            {
                startAction(event);

                stopDeceleration();

                saveTouchStart(event);

                mChart.disableScroll();

                mUserTouchesFirstOnMapSurface = (event.getX() > mLeftThreshold);

                if (mChart.isPinOnLeftSide() && (Math.abs(event.getY() - getYValue(mChart.getRatio()))) > mChart.getDistanceAroundPin())
                {
                    mShouldScrollHorizontally = true;
                }
                else
                {
                    mShouldScrollHorizontally = (!mChart.isPinOnLeftSide());
                }

                mPreviousXEvent = event.getX();
                mPreviousYEvent = event.getY();

                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
            {
                if (event.getPointerCount() >= 2)
                {
                    if (mHasBeenOneFingerScrolledHorizontally)
                    {
                        if (mChart.isScaleXEnabled() != mChart.isScaleYEnabled())
                        {
                            mTouchMode = mChart.isScaleXEnabled() ? X_ZOOM : Y_ZOOM;
                        }
                        else
                        {
                            mTouchMode = mSavedXDist > mSavedYDist ? X_ZOOM : Y_ZOOM;
                        }
                    }

                    mChart.disableScroll();

                    mPreviousXDist = getXDist(event);

                    saveTouchStart(event);

                    // get the distance between the pointers on the x-axis
                    mSavedXDist = getXDist(event);

                    // get the distance between the pointers on the y-axis
                    mSavedYDist = getYDist(event);

                    // get the total distance between the pointers
                    mSavedDist = spacing(event);

                    if (mSavedDist > 10f)
                    {
                        if (mChart.isPinchZoomEnabled())
                        {
                            mTouchMode = PINCH_ZOOM;
                        }
                        else
                        {
                            if (mChart.isScaleXEnabled() != mChart.isScaleYEnabled())
                            {
                                mTouchMode = mChart.isScaleXEnabled() ? X_ZOOM : Y_ZOOM;
                            }
                            else
                            {
                                mTouchMode = mSavedXDist > mSavedYDist ? X_ZOOM : Y_ZOOM;
                            }
                        }
                    }

                    // determine the touch-pointer center
                    midPoint(mTouchPointCenter, event);

                    if (mVelocityTracker == null)
                    {
                        // Retrieve a new VelocityTracker object to watch the
                        // velocity of a motion.
                        mVelocityTracker = VelocityTracker.obtain();
                    }
                    else
                    {
                        // Reset the velocity tracker back to its initial state.
                        mVelocityTracker.clear();
                    }

                    mPrevXPointer0 = getTrans(event.getX(0), event.getY(0));
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
            {
                if (mTouchMode == X_ZOOM || mTouchMode == Y_ZOOM || mTouchMode == PINCH_ZOOM)
                {
                    mChart.disableScroll();
                    if (mChart.isScaleXEnabled() || mChart.isScaleYEnabled())
                    {
                        ViewPortHandler h = mChart.getViewPortHandler();

                        // get the points of both fingers (pointers) translated
                        MPPointF firstPointer = getTrans(event.getX(0), event.getY(0));
                        MPPointF secondPointer = getTrans(event.getX(1), event.getY(1));

                        // center between the two pointers (fingers on the display) translated
                        MPPointF t = getTrans(mTouchPointCenter.x, mTouchPointCenter.y);

                        // dx1 =  difference between the current and the previous pixel touched for the first finger (pointer)  TRANSLATED
                        float dx1 = firstPointer.x - mPrevXPointer0.x;

                        // Add a movement to the tracker.
                        mVelocityTracker.addMovement(event);

                        // Compute the current velocity based on the points that have been collected.
                        //  A value of 1000 provides pixels per second (1000ms).  A value of 100 provides pixels per 0.1 seconds
                        mVelocityTracker.computeCurrentVelocity(100);

                        //  Retrieve the last computed X velocity for the first pointer (finger)
                        double x1Vel = (mVelocityTracker.getXVelocity(0));

                        double x2Vel = (mVelocityTracker.getXVelocity(1));

                        // current translated distance between the two pointers
                        float newDist = Math.abs(firstPointer.x - secondPointer.x);

                        //ration between current distance translated and previous distance translated
                        float scale = (newDist / mPreviousXDist);

                        //  threshold used to differentiate zoom to pan
                        float thresholdZoomToPan = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, (float) mSizeInMMZoomPan, mChart.getDisplayMetrics());

                        OnChartGestureListener listener = mChart.getOnChartGestureListener();

                        // hasBeenZoomed = true;
                        mHasBeenOneFingerScrolledHorizontally = false;

                        switch (mFingersMode)
                        {
                            case EQUIDIST_MOVING:
                            {
                                if ((Math.abs(newDist - mPreviousXDist) > thresholdZoomToPan) && ((x1Vel * x2Vel) < 0))
                                {
                                    mFingersMode = BOTH_MOVING_RANDOMLY;
                                }
                                else
                                {
                                    mFingersMode = EQUIDIST_MOVING;
                                    mLastGesture = ChartGesture.DRAG;

                                    if (mChart.getLowestVisibleX() > mChart.getXAxis().getAxisMinimum() && mChart.getHighestVisibleX() < mChart.getXAxis().getAxisMaximum())
                                    {
                                        mMatrix.postTranslate(dx1, 0);
                                    }
                                    else if (mChart.getLowestVisibleX() > mChart.getXAxis().getAxisMinimum() && mChart.getHighestVisibleX() == mChart.getXAxis().getAxisMaximum())
                                    {
                                        if (event.getX() > mTouchStartPoint.x)
                                        {
                                            mMatrix.postTranslate(dx1, 0);
                                        }
                                    }
                                    else if (mChart.getLowestVisibleX() == mChart.getXAxis().getAxisMinimum() && mChart.getHighestVisibleX() < mChart.getXAxis().getAxisMaximum())
                                    {
                                        if (event.getX() < mTouchStartPoint.x)
                                        {
                                            mMatrix.postTranslate(dx1, 0);
                                        }
                                    }
                                    mMatrix = mChart.getViewPortHandler().refresh(mMatrix, mChart, true);

                                    if (listener != null)
                                    {
                                        listener.onChartTranslate(event, dx1, 0);
                                    }
                                }
                                break;
                            }
                            case BOTH_MOVING_RANDOMLY:
                            {
                                if ((Math.abs(newDist - mPreviousXDist) < thresholdZoomToPan) && ((x1Vel * x2Vel) > 0))
                                {
                                    mFingersMode = EQUIDIST_MOVING;
                                }
                                else
                                {
                                    mFingersMode = BOTH_MOVING_RANDOMLY;
                                    mLastGesture = ChartGesture.X_ZOOM;

                                    boolean isZoomingOut = (scale < 1);
                                    boolean canZoomMoreX = isZoomingOut ? h.canZoomOutMoreX() : h.canZoomInMoreX();

                                    if (canZoomMoreX)
                                    {
                                        mMatrix.postScale(scale, 1f, t.x, 0);
                                        mMatrix = mChart.getViewPortHandler().refresh(mMatrix, mChart, true);

                                        if (listener != null)
                                        {
                                            listener.onChartScale(event, scale, 1f);
                                        }
                                    }
                                }
                                break;
                            }
                            case NONE:
                            {
                                mFingersMode = BOTH_MOVING_RANDOMLY;
                                break;
                            }
                        }

                        mPreviousXDist = Math.abs(firstPointer.x - secondPointer.x);  // previous distance between pointers

                        // get pointers translated used for next call of  case MotionEvent.ACTION_MOVE
                        // to calculate the distance between consecutive pointers
                        mPrevXPointer0 = getTrans(event.getX(0), event.getY(0));
                    }
                }
                else if (mTouchMode == NONE && Math.abs(distance(event.getX(), mTouchStartPoint.x)) > mDragTriggerDist)
                {
                    if (mHasBeenOneFingerScrolledHorizontally == false)
                    {
                        /*
                        final Handler handler = new Handler();
                        handler.postDelayed(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                if (event.getPointerCount() == 1)
                                {
                                    hasBeenOneFingerScrolledHorizontally = true;
                                    performScroll(event);
                                }
                            }
                        }, 100);
                        */
                        mHasBeenOneFingerScrolledHorizontally = true;
                        performScroll(event);
                    }
                    else
                    {
                        performScroll(event);
                    }
                }
                else if (mTouchMode == NONE && Math.abs(distance(event.getY(), mTouchStartPoint.y)) > mDragTriggerDist)
                {
                    if (mHasBeenOneFingerScrolledHorizontally/* || hasBeenZoomed*/)
                    {
                        if (Math.abs(event.getX() - mPreviousXEvent) > Math.abs(event.getY() - mPreviousYEvent))
                        {
                            performScroll(event);
                        }
                    }
                    else
                    {
                        mChart.enableScroll();
                    }

                    mPreviousXEvent = event.getX();
                    mPreviousYEvent = event.getY();
                }
                else
                {
                    if (/*hasBeenZoomed || */mHasBeenOneFingerScrolledHorizontally)
                    {
                        performScroll(event);
                    }
                }

                break;
            }
            case MotionEvent.ACTION_UP:
            {
                if (mTouchMode == X_ZOOM ||
                    mTouchMode == DRAG ||
                    mTouchMode == Y_ZOOM ||
                    mTouchMode == PINCH_ZOOM ||
                    mTouchMode == POST_ZOOM)
                {

                    // Range might have changed, which means that Y-axis labels
                    // could have changed in size, affecting Y-axis size.
                    // So we need to recalculate offsets.
                    mChart.calculateOffsets();
                    mChart.postInvalidate();
                }

                mTouchMode = NONE;
                mChart.enableScroll();
                mHasBeenOneFingerScrolledHorizontally = false;
                //hasBeenZoomed = false;

                if (mVelocityTracker != null)
                {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }

                endAction(event);

                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
            {
                Utils.velocityTrackerPointerUpCleanUpIfNecessary(event, mVelocityTracker);
                mTouchMode = POST_ZOOM;
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            {
                mTouchMode = NONE;
                endAction(event);
                break;
            }
        }

        // perform the transformation, update the chart
        mMatrix = mChart.getViewPortHandler().refresh(mMatrix, mChart, true);

        return true; // indicate event was handled
    }

    private void performScroll(MotionEvent event)
    {
        if (!mShouldScrollHorizontally)
        {
            mChart.disableScroll();
            performHighlightDrag(event);
        }
        else
        {
            if (mUserTouchesFirstOnMapSurface)
            {
                mChart.disableScroll();
                performHighlightDrag(event);
            }
            else
            {
                mChart.enableScroll();
            }
        }
    }

    private double getYValue(float ratio)
    {
        MPPointD pointUp = mChart.getTransformer(YAxis.AxisDependency.LEFT).getPixelForValues(0, mChart.getYChartMax());
        MPPointD pointDown = mChart.getTransformer(YAxis.AxisDependency.LEFT).getPixelForValues(0, mChart.getYChartMin());

        return (pointDown.y - (ratio * (pointDown.y - pointUp.y)));

    }

    /**
     * ################ ################ ################ ################
     */
    /** BELOW CODE PERFORMS THE ACTUAL TOUCH ACTIONS */

    /**
     * Saves the current Matrix state and the touch-start point.
     *
     * @param event
     */
    private void saveTouchStart(MotionEvent event) {

        mSavedMatrix.set(mMatrix);
        mTouchStartPoint.x = event.getX();
        mTouchStartPoint.y = event.getY();

        mClosestDataSetToTouch = mChart.getDataSetByTouchPoint(event.getX(), event.getY());
    }

    /**
     * Performs all necessary operations needed for dragging.
     *
     * @param event
     */
    private void performDrag(MotionEvent event, float distanceX, float distanceY) {

        mLastGesture = ChartGesture.DRAG;

        mMatrix.set(mSavedMatrix);

        OnChartGestureListener l = mChart.getOnChartGestureListener();

        // check if axis is inverted
        if (inverted()) {

            // if there is an inverted horizontalbarchart
            if (mChart instanceof HorizontalBarChart) {
                distanceX = -distanceX;
            } else {
                distanceY = -distanceY;
            }
        }

        mMatrix.postTranslate(distanceX, distanceY);

        if (l != null)
            l.onChartTranslate(event, distanceX, distanceY);
    }

    /**
     * Performs the all operations necessary for pinch and axis zoom.
     *
     * @param event
     */
    private void performZoom(MotionEvent event) {

        if (event.getPointerCount() >= 2) { // two finger zoom

            OnChartGestureListener l = mChart.getOnChartGestureListener();

            // get the distance between the pointers of the touch event
            float totalDist = spacing(event);

            if (totalDist > mMinScalePointerDistance) {

                // get the translation
                MPPointF t = getTrans(mTouchPointCenter.x, mTouchPointCenter.y);
                ViewPortHandler h = mChart.getViewPortHandler();

                // take actions depending on the activated touch mode
                if (mTouchMode == PINCH_ZOOM) {

                    mLastGesture = ChartGesture.PINCH_ZOOM;

                    float scale = totalDist / mSavedDist; // total scale

                    boolean isZoomingOut = (scale < 1);

                    boolean canZoomMoreX = isZoomingOut ?
                            h.canZoomOutMoreX() :
                            h.canZoomInMoreX();

                    boolean canZoomMoreY = isZoomingOut ?
                            h.canZoomOutMoreY() :
                            h.canZoomInMoreY();

                    float scaleX = (mChart.isScaleXEnabled()) ? scale : 1f;
                    float scaleY = (mChart.isScaleYEnabled()) ? scale : 1f;

                    if (canZoomMoreY || canZoomMoreX) {

                        mMatrix.set(mSavedMatrix);
                        mMatrix.postScale(scaleX, scaleY, t.x, t.y);

                        if (l != null)
                            l.onChartScale(event, scaleX, scaleY);
                    }

                } else if (mTouchMode == X_ZOOM && mChart.isScaleXEnabled()) {

                    mLastGesture = ChartGesture.X_ZOOM;

                    float xDist = getXDist(event);
                    float scaleX = xDist / mSavedXDist; // x-axis scale

                    boolean isZoomingOut = (scaleX < 1);
                    boolean canZoomMoreX = isZoomingOut ?
                            h.canZoomOutMoreX() :
                            h.canZoomInMoreX();

                    if (canZoomMoreX) {

                        mMatrix.set(mSavedMatrix);
                        mMatrix.postScale(scaleX, 1f, t.x, t.y);

                        if (l != null)
                            l.onChartScale(event, scaleX, 1f);
                    }

                } else if (mTouchMode == Y_ZOOM && mChart.isScaleYEnabled()) {

                    mLastGesture = ChartGesture.Y_ZOOM;

                    float yDist = getYDist(event);
                    float scaleY = yDist / mSavedYDist; // y-axis scale

                    boolean isZoomingOut = (scaleY < 1);
                    boolean canZoomMoreY = isZoomingOut ?
                            h.canZoomOutMoreY() :
                            h.canZoomInMoreY();

                    if (canZoomMoreY) {

                        mMatrix.set(mSavedMatrix);
                        mMatrix.postScale(1f, scaleY, t.x, t.y);

                        if (l != null)
                            l.onChartScale(event, 1f, scaleY);
                    }
                }

                MPPointF.recycleInstance(t);
            }
        }
    }

    /**
     * Highlights upon dragging, generates callbacks for the selection-listener.
     *
     * @param e
     */
    private void performHighlightDrag(MotionEvent e) {

        Highlight h = mChart.getHighlightByTouchPoint(e.getX(), e.getY());

        if (h != null && !h.equalTo(mLastHighlighted)) {
            mLastHighlighted = h;
            mChart.highlightValue(h, true);
        }
    }

    /**
     * ################ ################ ################ ################
     */
    /** DOING THE MATH BELOW ;-) */


    /**
     * Determines the center point between two pointer touch points.
     *
     * @param point
     * @param event
     */
    private static void midPoint(MPPointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.x = (x / 2f);
        point.y = (y / 2f);
    }

    /**
     * returns the distance between two pointer touch points
     *
     * @param event
     * @return
     */
    private static float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * calculates the distance on the x-axis between two pointers (fingers on
     * the display)
     *
     * @param e
     * @return
     */
    private static float getXDist(MotionEvent e) {
        float x = Math.abs(e.getX(0) - e.getX(1));
        return x;
    }

    /**
     * calculates the distance on the y-axis between two pointers (fingers on
     * the display)
     *
     * @param e
     * @return
     */
    private static float getYDist(MotionEvent e) {
        float y = Math.abs(e.getY(0) - e.getY(1));
        return y;
    }

    /**
     * Returns a recyclable MPPointF instance.
     * returns the correct translation depending on the provided x and y touch
     * points
     *
     * @param x
     * @param y
     * @return
     */
    public MPPointF getTrans(float x, float y) {

        ViewPortHandler vph = mChart.getViewPortHandler();

        float xTrans = x - vph.offsetLeft();
        float yTrans = 0f;

        // check if axis is inverted
        if (inverted()) {
            yTrans = -(y - vph.offsetTop());
        } else {
            yTrans = -(mChart.getMeasuredHeight() - y - vph.offsetBottom());
        }

        return MPPointF.getInstance(xTrans, yTrans);
    }

    /**
     * Returns true if the current touch situation should be interpreted as inverted, false if not.
     *
     * @return
     */
    private boolean inverted() {
        return (mClosestDataSetToTouch == null && mChart.isAnyAxisInverted()) || (mClosestDataSetToTouch != null
                && mChart.isInverted(mClosestDataSetToTouch.getAxisDependency()));
    }

    /**
     * ################ ################ ################ ################
     */
    /** GETTERS AND GESTURE RECOGNITION BELOW */

    /**
     * returns the matrix object the listener holds
     *
     * @return
     */
    public Matrix getMatrix() {
        return mMatrix;
    }

    /**
     * Sets the minimum distance that will be interpreted as a "drag" by the chart in dp.
     * Default: 3dp
     *
     * @param dragTriggerDistance
     */
    public void setDragTriggerDist(float dragTriggerDistance) {
        this.mDragTriggerDist = Utils.convertDpToPixel(dragTriggerDistance);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {

        mLastGesture = ChartGesture.DOUBLE_TAP;

        OnChartGestureListener l = mChart.getOnChartGestureListener();

        if (l != null) {
            l.onChartDoubleTapped(e);
        }

        // check if double-tap zooming is enabled
        if (mChart.isDoubleTapToZoomEnabled() && mChart.getData().getEntryCount() > 0) {

            MPPointF trans = getTrans(e.getX(), e.getY());

            float scaleX = mChart.isScaleXEnabled() ? 1.4f : 1f;
            float scaleY = mChart.isScaleYEnabled() ? 1.4f : 1f;

            mChart.zoom(scaleX, scaleY, trans.x, trans.y);

            if (mChart.isLogEnabled())
                Log.i("BarlineChartTouch", "Double-Tap, Zooming In, x: " + trans.x + ", y: "
                        + trans.y);

            if (l != null) {
                l.onChartScale(e, scaleX, scaleY);
            }

            MPPointF.recycleInstance(trans);
        }

        return super.onDoubleTap(e);
    }

    @Override
    public void onLongPress(MotionEvent e) {

        mLastGesture = ChartGesture.LONG_PRESS;

        OnChartGestureListener l = mChart.getOnChartGestureListener();

        if (l != null) {

            l.onChartLongPressed(e);
        }
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {

        mLastGesture = ChartGesture.SINGLE_TAP;

        OnChartGestureListener l = mChart.getOnChartGestureListener();

        if (l != null) {
            l.onChartSingleTapped(e);
        }

        if (!mChart.isHighlightPerTapEnabled()) {
            return false;
        }

        Highlight h = mChart.getHighlightByTouchPoint(e.getX(), e.getY());
        performHighlight(h, e);

        return super.onSingleTapUp(e);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

        mLastGesture = ChartGesture.FLING;

        OnChartGestureListener l = mChart.getOnChartGestureListener();

        if (l != null) {
            l.onChartFling(e1, e2, velocityX, velocityY);
        }

        return super.onFling(e1, e2, velocityX, velocityY);
    }

    public void stopDeceleration() {
        mDecelerationVelocity.x = 0;
        mDecelerationVelocity.y = 0;
    }

    public void computeScroll() {

        if (mDecelerationVelocity.x == 0.f && mDecelerationVelocity.y == 0.f)
            return; // There's no deceleration in progress

        final long currentTime = AnimationUtils.currentAnimationTimeMillis();

        mDecelerationVelocity.x *= mChart.getDragDecelerationFrictionCoef();
        mDecelerationVelocity.y *= mChart.getDragDecelerationFrictionCoef();

        final float timeInterval = (float) (currentTime - mDecelerationLastTime) / 1000.f;

        float distanceX = mDecelerationVelocity.x * timeInterval;
        float distanceY = mDecelerationVelocity.y * timeInterval;

        mDecelerationCurrentPoint.x += distanceX;
        mDecelerationCurrentPoint.y += distanceY;

        MotionEvent event = MotionEvent.obtain(currentTime, currentTime, MotionEvent.ACTION_MOVE, mDecelerationCurrentPoint.x,
                mDecelerationCurrentPoint.y, 0);

        float dragDistanceX = mChart.isDragXEnabled() ? mDecelerationCurrentPoint.x - mTouchStartPoint.x : 0.f;
        float dragDistanceY = mChart.isDragYEnabled() ? mDecelerationCurrentPoint.y - mTouchStartPoint.y : 0.f;

        performDrag(event, dragDistanceX, dragDistanceY);

        event.recycle();
        mMatrix = mChart.getViewPortHandler().refresh(mMatrix, mChart, false);

        mDecelerationLastTime = currentTime;

        if (Math.abs(mDecelerationVelocity.x) >= 0.01 || Math.abs(mDecelerationVelocity.y) >= 0.01)
            Utils.postInvalidateOnAnimation(mChart); // This causes computeScroll to fire, recommended for this by Google
        else {
            // Range might have changed, which means that Y-axis labels
            // could have changed in size, affecting Y-axis size.
            // So we need to recalculate offsets.
            mChart.calculateOffsets();
            mChart.postInvalidate();

            stopDeceleration();
        }
    }
}
