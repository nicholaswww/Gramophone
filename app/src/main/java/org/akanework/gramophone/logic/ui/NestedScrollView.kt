/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.akanework.gramophone.logic.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.hardware.SensorManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.FocusFinder
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EdgeEffect
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.R
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.core.view.DifferentialMotionFlingController
import androidx.core.view.DifferentialMotionFlingTarget
import androidx.core.view.MotionEventCompat
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ScrollFeedbackProviderCompat
import androidx.core.view.ScrollingView
import androidx.core.view.ViewCompat
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import androidx.core.view.size
import androidx.core.widget.EdgeEffectCompat
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * NestedScrollView is just like ScrollView, but it supports acting
 * as child on both new and old versions of Android.
 * Nested scrolling is enabled by default.
 */
class NestedScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.nestedScrollViewStyle
) : FrameLayout(context, attrs, defStyleAttr), NestedScrollingChild3, ScrollingView {
    private val mPhysicalCoeff: Float

    /**
     * Interface definition for a callback to be invoked when the scroll
     * X or Y positions of a view change.
     *
     *
     * This version of the interface works on all versions of Android, back to API v4.
     *
     * @see .setOnScrollChangeListener
     */
    interface OnScrollChangeListener {
        /**
         * Called when the scroll position of a view changes.
         * @param v The view whose scroll position has changed.
         * @param scrollX Current horizontal scroll origin.
         * @param scrollY Current vertical scroll origin.
         * @param oldScrollX Previous horizontal scroll origin.
         * @param oldScrollY Previous vertical scroll origin.
         */
        fun onScrollChange(
            v: NestedScrollView, scrollX: Int, scrollY: Int,
            oldScrollX: Int, oldScrollY: Int
        )
    }

    private var mLastScroll: Long = 0

    private val mTempRect = Rect()
    private var mScroller: OverScroller? = null

    private var mEdgeGlowTop: EdgeEffect = EdgeEffectCompat.create(context, attrs)
    private var mEdgeGlowBottom: EdgeEffect = EdgeEffectCompat.create(context, attrs)

    private var mScrollFeedbackProvider: ScrollFeedbackProviderCompat? = null

    /**
     * Position of the last motion event; only used with touch related events (usually to assist
     * in movement changes in a drag gesture).
     */
    private var mLastMotionY = 0

    /**
     * True when the layout has changed but the traversal has not come through yet.
     * Ideally the view hierarchy would keep track of this for us.
     */
    private var mIsLayoutDirty = true
    private var mIsLaidOut = false

    /**
     * The child to give focus to in the event that a child has requested focus while the
     * layout is dirty. This prevents the scroll from being wrong if the child has not been
     * laid out before requesting focus.
     */
    private var mChildToScrollTo: View? = null

    /**
     * True if the user is currently dragging this ScrollView around. This is
     * not the same as 'is being flinged', which can be checked by
     * mScroller.isFinished() (flinging begins when the user lifts their finger).
     */
    private var mIsBeingDragged = false

    /**
     * Determines speed during touch scrolling
     */
    private var mVelocityTracker: VelocityTracker? = null

    /**
     * When set to true, the scroll view measure its child to make it fill the currently
     * visible area.
     */
    private var mFillViewport = false

    /**
     * Whether arrow scrolling is animated.
     */
    var isSmoothScrollingEnabled: Boolean = true

    private var mTouchSlop = 0
    private var mMinimumVelocity = 0
    private var mMaximumVelocity = 0

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private var mActivePointerId: Int = INVALID_POINTER

    /**
     * Used during scrolling to retrieve the new offset within the window. Saves memory by saving
     * x, y changes to this array (0 position = x, 1 position = y) vs. reallocating an x and y
     * every time.
     */
    private val mScrollOffset = IntArray(2)

    /*
     * Used during scrolling to retrieve the new consumed offset within the window.
     * Uses same memory saving strategy as mScrollOffset.
     */
    private val mScrollConsumed = IntArray(2)

    // Used to track the position of the touch only events relative to the container.
    private var mNestedYOffset = 0

    private var mLastScrollerY = 0

    private var mSavedState: SavedState? = null

    private val mChildHelper: NestedScrollingChildHelper

    private var mVerticalScrollFactor = 0f

    private var mOnScrollChangeListener: OnScrollChangeListener? = null

    private val mDifferentialMotionFlingTarget: DifferentialMotionFlingTargetImpl =
        DifferentialMotionFlingTargetImpl()

    private var mDifferentialMotionFlingController: DifferentialMotionFlingController =
        DifferentialMotionFlingController(getContext(), mDifferentialMotionFlingTarget)


    var isFillViewport: Boolean
        /**
         * Indicates whether this ScrollView's content is stretched to fill the viewport.
         *
         * @return True if the content fills the viewport, false otherwise.
         *
         * @attr name android:fillViewport
         */
        get() = mFillViewport
        /**
         * Set whether this ScrollView should stretch its content height to fill the viewport or not.
         *
         * True to stretch the content's height to the viewport's
         * boundaries, false otherwise.
         */
        set(fillViewport) {
            if (fillViewport != mFillViewport) {
                mFillViewport = fillViewport
                requestLayout()
            }
        }

    init {
        val ppi = context.resources.displayMetrics.density * 160.0f
        mPhysicalCoeff = (SensorManager.GRAVITY_EARTH // g (m/s^2)
                * 39.37f // inch/meter
                * ppi
                * 0.84f) // look and feel tuning

        initScrollView()

        context.withStyledAttributes(
            attrs, SCROLLVIEW_STYLEABLE, defStyleAttr, 0
        ) {
            this@NestedScrollView.isFillViewport = getBoolean(0, false)
        }

        mChildHelper = NestedScrollingChildHelper(this)

        // ...because why else would you be using this widget?
        isNestedScrollingEnabled = true
    }

    // NestedScrollingChild3
    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, offsetInWindow: IntArray?, type: Int, consumed: IntArray
    ) {
        mChildHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
            offsetInWindow, type, consumed
        )
    }

    // NestedScrollingChild2
    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return mChildHelper.startNestedScroll(axes, type)
    }

    override fun stopNestedScroll(type: Int) {
        mChildHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return mChildHelper.hasNestedScrollingParent(type)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, offsetInWindow: IntArray?, type: Int
    ): Boolean {
        return mChildHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
            offsetInWindow, type
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    // NestedScrollingChild
    override fun setNestedScrollingEnabled(enabled: Boolean) {
        mChildHelper.setNestedScrollingEnabled(enabled)
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return mChildHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return startNestedScroll(axes, ViewCompat.TYPE_TOUCH)
    }

    override fun stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
    }

    override fun hasNestedScrollingParent(): Boolean {
        return hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, offsetInWindow: IntArray?
    ): Boolean {
        return mChildHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
            offsetInWindow
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int, consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean {
        return dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return mChildHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    // ScrollView import
    override fun getTopFadingEdgeStrength(): Float {
        val length = getVerticalFadingEdgeLength()
        val scrollY = getScrollY()
        if (scrollY < length) {
            return scrollY / length.toFloat()
        }

        return 1.0f
    }

    override fun getBottomFadingEdgeStrength(): Float {
        if (isEmpty()) {
            return 0.0f
        }

        val child = getChildAt(0)
        val lp = child.layoutParams as LayoutParams
        val length = getVerticalFadingEdgeLength()
        val bottomEdge = height - paddingBottom
        val span = child.bottom + lp.bottomMargin - scrollY - bottomEdge
        if (span < length) {
            return span / length.toFloat()
        }

        return 1.0f
    }

    val maxScrollAmount: Int
        /**
         * @return The maximum amount this scroll view will scroll in response to
         * an arrow event.
         */
        get() = (MAX_SCROLL_FACTOR * height).toInt()

    private fun initScrollView() {
        mScroller = OverScroller(context)
        setFocusable(true)
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS)
        setWillNotDraw(false)
        val configuration = ViewConfiguration.get(context)
        mTouchSlop = configuration.scaledTouchSlop
        mMinimumVelocity = configuration.scaledMinimumFlingVelocity
        mMaximumVelocity = configuration.scaledMaximumFlingVelocity
    }

    override fun addView(child: View) {
        check(size <= 0) { "ScrollView can host only one direct child" }

        super.addView(child)
    }

    override fun addView(child: View, index: Int) {
        check(size <= 0) { "ScrollView can host only one direct child" }

        super.addView(child, index)
    }

    override fun addView(child: View, params: ViewGroup.LayoutParams) {
        check(size <= 0) { "ScrollView can host only one direct child" }

        super.addView(child, params)
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        check(size <= 0) { "ScrollView can host only one direct child" }

        super.addView(child, index, params)
    }

    /**
     * Register a callback to be invoked when the scroll X or Y positions of
     * this view change.
     *
     * This version of the method works on all versions of Android, back to API v4.
     *
     * @param l The listener to notify when the scroll X or Y position changes.
     * @see View.getScrollX
     * @see View.getScrollY
     */
    fun setOnScrollChangeListener(l: OnScrollChangeListener?) {
        mOnScrollChangeListener = l
    }

    /**
     * @return Returns true this ScrollView can be scrolled
     */
    private fun canScroll(): Boolean {
        if (isNotEmpty()) {
            val child = getChildAt(0)
            val lp = child.layoutParams as LayoutParams
            val childSize = child.height + lp.topMargin + lp.bottomMargin
            val parentSpace = height - paddingTop - paddingBottom
            return childSize > parentSpace
        }
        return false
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)

        if (mOnScrollChangeListener != null) {
            mOnScrollChangeListener!!.onScrollChange(this, l, t, oldl, oldt)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (!mFillViewport) {
            return
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            return
        }

        if (isNotEmpty()) {
            val child = getChildAt(0)
            val lp = child.layoutParams as LayoutParams

            val childSize = child.measuredHeight
            val parentSpace = (measuredHeight
                    - paddingTop
                    - paddingBottom
                    - lp.topMargin
                    - lp.bottomMargin)

            if (childSize < parentSpace) {
                val childWidthMeasureSpec = getChildMeasureSpec(
                    widthMeasureSpec,
                    getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin,
                    lp.width
                )
                val childHeightMeasureSpec =
                    MeasureSpec.makeMeasureSpec(parentSpace, MeasureSpec.EXACTLY)
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Let the focused view and/or our descendants get the key first
        return super.dispatchKeyEvent(event) || executeKeyEvent(event)
    }

    /**
     * You can call this function yourself to have the scroll view perform
     * scrolling from a key event, just as if the event had been dispatched to
     * it by the view hierarchy.
     *
     * @param event The key event to execute.
     * @return Return true if the event was handled, else false.
     */
    fun executeKeyEvent(event: KeyEvent): Boolean {
        mTempRect.setEmpty()

        if (!canScroll()) {
            if (isFocused && event.keyCode != KeyEvent.KEYCODE_BACK) {
                var currentFocused = findFocus()
                if (currentFocused === this) currentFocused = null
                val nextFocused = FocusFinder.getInstance().findNextFocus(
                    this,
                    currentFocused, FOCUS_DOWN
                )
                return nextFocused != null && nextFocused !== this && nextFocused.requestFocus(FOCUS_DOWN)
            }
            return false
        }

        var handled = false
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> handled = if (event.isAltPressed) {
                    fullScroll(FOCUS_UP)
                } else {
                    arrowScroll(FOCUS_UP)
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> handled = if (event.isAltPressed) {
                    fullScroll(FOCUS_DOWN)
                } else {
                    arrowScroll(FOCUS_DOWN)
                }

                KeyEvent.KEYCODE_PAGE_UP -> handled = fullScroll(FOCUS_UP)
                KeyEvent.KEYCODE_PAGE_DOWN -> handled = fullScroll(FOCUS_DOWN)
                KeyEvent.KEYCODE_SPACE -> pageScroll(if (event.isShiftPressed) FOCUS_UP else FOCUS_DOWN)
                KeyEvent.KEYCODE_MOVE_HOME -> pageScroll(FOCUS_UP)
                KeyEvent.KEYCODE_MOVE_END -> pageScroll(FOCUS_DOWN)
            }
        }

        return handled
    }

    private fun inChild(x: Int, y: Int): Boolean {
        if (isNotEmpty()) {
            val scrollY = getScrollY()
            val child = getChildAt(0)
            return !(y < child.top - scrollY || y >= child.bottom - scrollY || x < child.left || x >= child.right)
        }
        return false
    }

    private fun initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        } else {
            mVelocityTracker!!.clear()
        }
    }

    private fun initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
    }

    private fun recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) {
            recycleVelocityTracker()
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */

        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and they are moving their finger.  We want to intercept this
         * motion.
         */

        val action = ev.action
        if ((action == MotionEvent.ACTION_MOVE) && mIsBeingDragged) {
            return true
        }

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_MOVE -> {
                /*
                                * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                                * whether the user has moved far enough from their original down touch.
                                */

                /*
                 * Locally do absolute value. mLastMotionY is set to the y value
                 * of the down event.
                 */
                val activePointerId = mActivePointerId
                // If we don't have a valid id, the touch down wasn't on content.
                if (activePointerId != INVALID_POINTER) {
                    val pointerIndex = ev.findPointerIndex(activePointerId)
                    if (pointerIndex == -1) {
                        Log.e(
                            TAG, ("Invalid pointerId=" + activePointerId
                                    + " in onInterceptTouchEvent")
                        )
                    } else {

                        val y = ev.getY(pointerIndex).toInt()
                        val yDiff = abs((y - mLastMotionY).toDouble()).toInt()
                        if (yDiff > mTouchSlop
                            && (nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL) == 0
                        ) {
                            mIsBeingDragged = true
                            mLastMotionY = y
                            initVelocityTrackerIfNotExists()
                            mVelocityTracker!!.addMovement(ev)
                            mNestedYOffset = 0
                            val parent = getParent()
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
            }

            MotionEvent.ACTION_DOWN -> {
                val y = ev.y.toInt()
                if (!inChild(ev.x.toInt(), y)) {
                    mIsBeingDragged = stopGlowAnimations(ev) || !mScroller!!.isFinished
                    recycleVelocityTracker()
                } else {
                    /*
                     * Remember location of down touch.
                     * ACTION_DOWN always refers to pointer index 0.
                     */
                    mLastMotionY = y
                    mActivePointerId = ev.getPointerId(0)

                    initOrResetVelocityTracker()
                    mVelocityTracker!!.addMovement(ev)
                    /*
                     * If being flinged and user touches the screen, initiate drag;
                     * otherwise don't. mScroller.isFinished should be false when
                     * being flinged. We also want to catch the edge glow and start dragging
                     * if one is being animated. We need to call computeScrollOffset() first so that
                     * isFinished() is correct.
                     */
                    mScroller!!.computeScrollOffset()
                    mIsBeingDragged = stopGlowAnimations(ev) || !mScroller!!.isFinished
                    startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                /* Release the drag */
                mIsBeingDragged = false
                mActivePointerId = INVALID_POINTER
                recycleVelocityTracker()
                if (mScroller!!.springBack(scrollX, scrollY, 0, 0, 0, this.scrollRange)) {
                    postInvalidateOnAnimation()
                }
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
            }

            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mIsBeingDragged
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        initVelocityTrackerIfNotExists()

        val actionMasked = motionEvent.actionMasked

        if (actionMasked == MotionEvent.ACTION_DOWN) {
            mNestedYOffset = 0
        }

        val velocityTrackerMotionEvent = MotionEvent.obtain(motionEvent)
        velocityTrackerMotionEvent.offsetLocation(0f, mNestedYOffset.toFloat())

        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isEmpty()) {
                    return false
                }

                // If additional fingers touch the screen while a drag is in progress, this block
                // of code will make sure the drag isn't interrupted.
                if (mIsBeingDragged) {
                    val parent = getParent()
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!mScroller!!.isFinished) {
                    abortAnimatedScroll()
                }

                initializeTouchDrag(
                    motionEvent.y.toInt(),
                    motionEvent.getPointerId(0)
                )
            }

            MotionEvent.ACTION_MOVE -> {
                val activePointerIndex = motionEvent.findPointerIndex(mActivePointerId)
                if (activePointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=$mActivePointerId in onTouchEvent")
                } else {

                    val y = motionEvent.getY(activePointerIndex).toInt()
                    var deltaY = mLastMotionY - y
                    deltaY -= releaseVerticalGlow(deltaY, motionEvent.getX(activePointerIndex))

                    // Changes to dragged state if delta is greater than the slop (and not in
                    // the dragged state).
                    if (!mIsBeingDragged && abs(deltaY.toDouble()) > mTouchSlop) {
                        val parent = getParent()
                        parent?.requestDisallowInterceptTouchEvent(true)
                        mIsBeingDragged = true
                        if (deltaY > 0) {
                            deltaY -= mTouchSlop
                        } else {
                            deltaY += mTouchSlop
                        }
                    }

                    if (mIsBeingDragged) {
                        val x = motionEvent.getX(activePointerIndex).toInt()
                        val scrollOffset =
                            scrollBy(
                                deltaY, MotionEvent.AXIS_Y, motionEvent, x,
                                ViewCompat.TYPE_TOUCH, false
                            )
                        // Updates the global positions (used by later move events to properly scroll).
                        mLastMotionY = y - scrollOffset
                        mNestedYOffset += scrollOffset
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                val velocityTracker = mVelocityTracker
                velocityTracker!!.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
                val initialVelocity = velocityTracker.getYVelocity(mActivePointerId).toInt()
                if ((abs(initialVelocity.toDouble()) >= mMinimumVelocity)) {
                    if (!edgeEffectFling(initialVelocity)
                        && !dispatchNestedPreFling(0f, -initialVelocity.toFloat())
                    ) {
                        dispatchNestedFling(0f, -initialVelocity.toFloat(), true)
                        fling(-initialVelocity)
                    }
                } else if (mScroller!!.springBack(
                        scrollX, scrollY, 0, 0, 0,
                        this.scrollRange
                    )
                ) {
                    postInvalidateOnAnimation()
                }
                endTouchDrag()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mIsBeingDragged && isNotEmpty()) {
                    if (mScroller!!.springBack(
                            scrollX, scrollY, 0, 0, 0,
                            this.scrollRange
                        )
                    ) {
                        postInvalidateOnAnimation()
                    }
                }
                endTouchDrag()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = motionEvent.actionIndex
                mLastMotionY = motionEvent.getY(index).toInt()
                mActivePointerId = motionEvent.getPointerId(index)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(motionEvent)
                mLastMotionY = motionEvent.getY(motionEvent.findPointerIndex(mActivePointerId)).toInt()
            }
        }

        if (mVelocityTracker != null) {
            mVelocityTracker!!.addMovement(velocityTrackerMotionEvent)
        }
        // Returns object back to be re-used by others.
        velocityTrackerMotionEvent.recycle()

        return true
    }

    private fun initializeTouchDrag(lastMotionY: Int, activePointerId: Int) {
        mLastMotionY = lastMotionY
        mActivePointerId = activePointerId
        startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
    }

    // Ends drag in a nested scroll.
    private fun endTouchDrag() {
        mActivePointerId = INVALID_POINTER
        mIsBeingDragged = false

        recycleVelocityTracker()
        stopNestedScroll(ViewCompat.TYPE_TOUCH)

        mEdgeGlowTop.onRelease()
        mEdgeGlowBottom.onRelease()
    }

    /**
     * Same as [.scrollBy], but with no entry for
     * the vertical motion axis as well as the [MotionEvent].
     *
     *
     * Use this method (instead of the other overload) if the [MotionEvent] that caused
     * this scroll request is not known.
     */
    private fun scrollBy(
        verticalScrollDistance: Int,
        x: Int,
        touchType: Int,
        isSourceMouseOrKeyboard: Boolean
    ): Int {
        return scrollBy(
            verticalScrollDistance,  /* verticalScrollAxis= */-1, null, x, touchType,
            isSourceMouseOrKeyboard
        )
    }

    /**
     * Handles scroll events for both touch and non-touch events (mouse scroll wheel,
     * rotary button, keyboard, etc.).
     *
     *
     * Note: This function returns the total scroll offset for this scroll event which is required
     * for calculating the total scroll between multiple move events (touch). This returned value
     * is NOT needed for non-touch events since a scroll is a one time event (vs. touch where a
     * drag may be triggered multiple times with the movement of the finger).
     *
     * @param verticalScrollDistance the amount of distance (in pixels) to scroll vertically.
     * @param verticalScrollAxis the motion axis that triggered the vertical scroll. This is not
     * always [MotionEvent.AXIS_Y], because there could be other
     * axes that trigger a vertical scroll on the view. For example,
     * generic motion events reported via [MotionEvent.AXIS_SCROLL]
     * or [MotionEvent.AXIS_VSCROLL]. Use `-1` if the vertical
     * scroll axis is not known.
     * @param ev the [MotionEvent] that caused this scroll. `null` if the event is not
     * known.
     * @param x the target location on the x axis.
     * @param touchType the [ViewCompat.NestedScrollType] for this scroll.
     * @param isSourceMouseOrKeyboard whether or not the scroll was caused by a mouse or a keyboard.
     */
    // TODO: You should rename this to nestedScrollBy() so it is different from View.scrollBy
    @VisibleForTesting
    fun scrollBy(
        verticalScrollDistance: Int,
        verticalScrollAxis: Int,
        ev: MotionEvent?,
        x: Int,
        @ViewCompat.NestedScrollType touchType: Int,
        isSourceMouseOrKeyboard: Boolean
    ): Int {
        var verticalScrollDistance = verticalScrollDistance
        var totalScrollOffset = 0

        /*
         * Starts nested scrolling for non-touch events (mouse scroll wheel, rotary button, etc.).
         * This is in contrast to a touch event which would trigger the start of nested scrolling
         * with a touch down event outside of this method, since for a single gesture scrollBy()
         * might be called several times for a move event for a single drag gesture.
         */
        if (touchType == ViewCompat.TYPE_NON_TOUCH) {
            startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, touchType)
        }

        // Dispatches scrolling delta amount available to parent (to consume what it needs).
        // Note: The amounts the parent consumes are saved in arrays named mScrollConsumed and
        // mScrollConsumed to save space.
        if (dispatchNestedPreScroll(
                0,
                verticalScrollDistance,
                mScrollConsumed,
                mScrollOffset,
                touchType
            )
        ) {
            // Deducts the scroll amount (y) consumed by the parent (x in position 0,
            // y in position 1). Nested scroll only works with Y position (so we don't use x).
            verticalScrollDistance -= mScrollConsumed[1]
            totalScrollOffset += mScrollOffset[1]
        }

        // Retrieves the scroll y position (top position of this view) and scroll Y range (how far
        // the scroll can go).
        val initialScrollY = scrollY
        val scrollRangeY = this.scrollRange

        // Overscroll is for adding animations at the top/bottom of a view when the user scrolls
        // beyond the beginning/end of the view. Overscroll is not used with a mouse.
        val canOverscroll = canOverScroll() && !isSourceMouseOrKeyboard

        // Scrolls content in the current View, but clamps it if it goes too far.
        var hitScrollBarrier =
            overScrollByCompat(
                0,
                verticalScrollDistance,
                0,
                initialScrollY,
                0,
                scrollRangeY,
                0,
                0,
                true
            ) && !hasNestedScrollingParent(touchType)

        // The position may have been adjusted in the previous call, so we must revise our values.
        val scrollYDelta = scrollY - initialScrollY
        if (ev != null && scrollYDelta != 0) {
            this.scrollFeedbackProvider.onScrollProgress(
                ev.deviceId, ev.source, verticalScrollAxis, scrollYDelta
            )
        }
        val unconsumedY = verticalScrollDistance - scrollYDelta

        // Reset the Y consumed scroll to zero
        mScrollConsumed[1] = 0

        //  Dispatch the unconsumed delta Y to the children to consume.
        dispatchNestedScroll(
            0,
            scrollYDelta,
            0,
            unconsumedY,
            mScrollOffset,
            touchType,
            mScrollConsumed
        )

        totalScrollOffset += mScrollOffset[1]

        // Handle overscroll of the children.
        verticalScrollDistance -= mScrollConsumed[1]
        val newScrollY = initialScrollY + verticalScrollDistance

        if (newScrollY < 0) {
            if (canOverscroll) {
                EdgeEffectCompat.onPullDistance(
                    mEdgeGlowTop,
                    -verticalScrollDistance.toFloat() / height,
                    x.toFloat() / width
                )
                if (ev != null) {
                    this.scrollFeedbackProvider.onScrollLimit(
                        ev.deviceId, ev.source, verticalScrollAxis,  /* isStart= */
                        true
                    )
                }

                if (!mEdgeGlowBottom.isFinished) {
                    mEdgeGlowBottom.onRelease()
                }
            }
        } else if (newScrollY > scrollRangeY) {
            if (canOverscroll) {
                EdgeEffectCompat.onPullDistance(
                    mEdgeGlowBottom,
                    verticalScrollDistance.toFloat() / height,
                    1f - (x.toFloat() / width)
                )
                if (ev != null) {
                    this.scrollFeedbackProvider.onScrollLimit(
                        ev.deviceId, ev.source, verticalScrollAxis,  /* isStart= */
                        false
                    )
                }

                if (!mEdgeGlowTop.isFinished) {
                    mEdgeGlowTop.onRelease()
                }
            }
        }

        if (!mEdgeGlowTop.isFinished || !mEdgeGlowBottom.isFinished) {
            postInvalidateOnAnimation()
            hitScrollBarrier = false
        }

        if (hitScrollBarrier && (touchType == ViewCompat.TYPE_TOUCH)) {
            // Break our velocity if we hit a scroll barrier.
            if (mVelocityTracker != null) {
                mVelocityTracker!!.clear()
            }
        }

        /*
         * Ends nested scrolling for non-touch events (mouse scroll wheel, rotary button, etc.).
         * As noted above, this is in contrast to a touch event.
         */
        if (touchType == ViewCompat.TYPE_NON_TOUCH) {
            stopNestedScroll(touchType)

            // Required for scrolling with Rotary Device stretch top/bottom to work properly
            mEdgeGlowTop.onRelease()
            mEdgeGlowBottom.onRelease()
        }

        return totalScrollOffset
    }

    /**
     * Returns true if edgeEffect should call onAbsorb() with veclocity or false if it should
     * animate with a fling. It will animate with a fling if the velocity will remove the
     * EdgeEffect through its normal operation.
     *
     * @param edgeEffect The EdgeEffect that might absorb the velocity.
     * @param velocity The velocity of the fling motion
     * @return true if the velocity should be absorbed or false if it should be flung.
     */
    private fun shouldAbsorb(edgeEffect: EdgeEffect, velocity: Int): Boolean {
        if (velocity > 0) {
            return true
        }
        val distance = EdgeEffectCompat.getDistance(edgeEffect) * height

        // This is flinging without the spring, so let's see if it will fling past the overscroll
        val flingDistance = getSplineFlingDistance(-velocity)

        return flingDistance < distance
    }

    /**
     * If mTopGlow or mBottomGlow is currently active and the motion will remove some of the
     * stretch, this will consume any of unconsumedY that the glow can. If the motion would
     * increase the stretch, or the EdgeEffect isn't a stretch, then nothing will be consumed.
     *
     * @param unconsumedY The vertical delta that might be consumed by the vertical EdgeEffects
     * @return The remaining unconsumed delta after the edge effects have consumed.
     */
    fun consumeFlingInVerticalStretch(unconsumedY: Int): Int {
        val height = getHeight()
        if (unconsumedY > 0 && EdgeEffectCompat.getDistance(mEdgeGlowTop) != 0f) {
            val deltaDistance: Float = -unconsumedY * FLING_DESTRETCH_FACTOR / height
            val consumed: Int = (-height / FLING_DESTRETCH_FACTOR
                    * EdgeEffectCompat.onPullDistance(mEdgeGlowTop, deltaDistance, 0.5f)).roundToInt()
            if (consumed != unconsumedY) {
                mEdgeGlowTop.finish()
            }
            return unconsumedY - consumed
        }
        if (unconsumedY < 0 && EdgeEffectCompat.getDistance(mEdgeGlowBottom) != 0f) {
            val deltaDistance: Float = unconsumedY * FLING_DESTRETCH_FACTOR / height
            val consumed: Int = (height / FLING_DESTRETCH_FACTOR
                    * EdgeEffectCompat.onPullDistance(mEdgeGlowBottom, deltaDistance, 0.5f)).roundToInt()
            if (consumed != unconsumedY) {
                mEdgeGlowBottom.finish()
            }
            return unconsumedY - consumed
        }
        return unconsumedY
    }

    /**
     * Copied from OverScroller, this returns the distance that a fling with the given velocity
     * will go.
     * @param velocity The velocity of the fling
     * @return The distance that will be traveled by a fling of the given velocity.
     */
    private fun getSplineFlingDistance(velocity: Int): Float {
        val l = ln((INFLEXION * abs(velocity.toDouble()) / (SCROLL_FRICTION * mPhysicalCoeff)).toDouble())
        val decelMinusOne: Double = DECELERATION_RATE - 1.0
        return ((SCROLL_FRICTION * mPhysicalCoeff
                * exp(DECELERATION_RATE / decelMinusOne * l))).toFloat()
    }

    private fun edgeEffectFling(velocityY: Int): Boolean {
        var consumed = true
        if (EdgeEffectCompat.getDistance(mEdgeGlowTop) != 0f) {
            if (shouldAbsorb(mEdgeGlowTop, velocityY)) {
                mEdgeGlowTop.onAbsorb(velocityY)
            } else {
                fling(-velocityY)
            }
        } else if (EdgeEffectCompat.getDistance(mEdgeGlowBottom) != 0f) {
            if (shouldAbsorb(mEdgeGlowBottom, -velocityY)) {
                mEdgeGlowBottom.onAbsorb(-velocityY)
            } else {
                fling(-velocityY)
            }
        } else {
            consumed = false
        }
        return consumed
    }

    /**
     * This stops any edge glow animation that is currently running by applying a
     * 0 length pull at the displacement given by the provided MotionEvent. On pre-S devices,
     * this method does nothing, allowing any animating edge effect to continue animating and
     * returning `false` always.
     *
     * @param e The motion event to use to indicate the finger position for the displacement of
     * the current pull.
     * @return `true` if any edge effect had an existing effect to be drawn ond the
     * animation was stopped or `false` if no edge effect had a value to display.
     */
    private fun stopGlowAnimations(e: MotionEvent): Boolean {
        var stopped = false
        if (EdgeEffectCompat.getDistance(mEdgeGlowTop) != 0f) {
            EdgeEffectCompat.onPullDistance(mEdgeGlowTop, 0f, e.x / width)
            stopped = true
        }
        if (EdgeEffectCompat.getDistance(mEdgeGlowBottom) != 0f) {
            EdgeEffectCompat.onPullDistance(mEdgeGlowBottom, 0f, 1 - e.x / width)
            stopped = true
        }
        return stopped
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mLastMotionY = ev.getY(newPointerIndex).toInt()
            mActivePointerId = ev.getPointerId(newPointerIndex)
            if (mVelocityTracker != null) {
                mVelocityTracker!!.clear()
            }
        }
    }

    override fun onGenericMotionEvent(motionEvent: MotionEvent): Boolean {
        if (motionEvent.action == MotionEvent.ACTION_SCROLL && !mIsBeingDragged) {
            val verticalScroll: Float
            val x: Int
            val axis: Int

            if (MotionEventCompat.isFromSource(motionEvent, InputDevice.SOURCE_CLASS_POINTER)) {
                verticalScroll = motionEvent.getAxisValue(MotionEvent.AXIS_VSCROLL)
                x = motionEvent.x.toInt()
                axis = MotionEvent.AXIS_VSCROLL
            } else if (MotionEventCompat.isFromSource(motionEvent, InputDevice.SOURCE_ROTARY_ENCODER)) {
                verticalScroll = motionEvent.getAxisValue(@Suppress("InlinedApi") MotionEvent.AXIS_SCROLL)
                // Since a Wear rotary event doesn't have a true X and we want to support proper
                // overscroll animations, we put the x at the center of the screen.
                x = width / 2
                axis = @Suppress("InlinedApi") MotionEvent.AXIS_SCROLL
            } else {
                verticalScroll = 0f
                x = 0
                axis = 0
            }

            if (verticalScroll != 0f) {
                // Rotary and Mouse scrolls are inverted from a touch scroll.
                val invertedDelta = (verticalScroll * this.verticalScrollFactorCompat).toInt()

                val isSourceMouse =
                    MotionEventCompat.isFromSource(motionEvent, InputDevice.SOURCE_MOUSE)

                scrollBy(
                    -invertedDelta, axis, motionEvent, x, ViewCompat.TYPE_NON_TOUCH,
                    isSourceMouse
                )
                mDifferentialMotionFlingController.onMotionEvent(motionEvent, axis)

                return true
            }
        }
        return false
    }

    /**
     * Returns true if the NestedScrollView supports over scroll.
     */
    private fun canOverScroll(): Boolean {
        val mode = overScrollMode
        return mode == OVER_SCROLL_ALWAYS
                || (mode == OVER_SCROLL_IF_CONTENT_SCROLLS && this.scrollRange > 0)
    }

    @get:VisibleForTesting
    val verticalScrollFactorCompat: Float
        get() {
            if (mVerticalScrollFactor == 0f) {
                val outValue = TypedValue()
                val context = getContext()
                check(
                    context.theme.resolveAttribute(
                        android.R.attr.listPreferredItemHeight, outValue, true
                    )
                ) { "Expected theme to define listPreferredItemHeight." }
                mVerticalScrollFactor = outValue.getDimension(
                    context.resources.displayMetrics
                )
            }
            return mVerticalScrollFactor
        }

    override fun onOverScrolled(
        scrollX: Int, scrollY: Int,
        clampedX: Boolean, clampedY: Boolean
    ) {
        super.scrollTo(scrollX, scrollY)
    }

    @Suppress("unused")
    fun overScrollByCompat(
        deltaX: Int, deltaY: Int,
        scrollX: Int, scrollY: Int,
        scrollRangeX: Int, scrollRangeY: Int,
        maxOverScrollX: Int, maxOverScrollY: Int,
        isTouchEvent: Boolean
    ): Boolean {
        var maxOverScrollX = maxOverScrollX
        var maxOverScrollY = maxOverScrollY
        val overScrollMode = getOverScrollMode()
        val canScrollHorizontal =
            computeHorizontalScrollRange() > computeHorizontalScrollExtent()
        val canScrollVertical =
            computeVerticalScrollRange() > computeVerticalScrollExtent()

        val overScrollHorizontal = overScrollMode == OVER_SCROLL_ALWAYS
                || (overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && canScrollHorizontal)
        val overScrollVertical = overScrollMode == OVER_SCROLL_ALWAYS
                || (overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && canScrollVertical)

        var newScrollX = scrollX + deltaX
        if (!overScrollHorizontal) {
            maxOverScrollX = 0
        }

        var newScrollY = scrollY + deltaY
        if (!overScrollVertical) {
            maxOverScrollY = 0
        }

        // Clamp values if at the limits and record
        val left = -maxOverScrollX
        val right = maxOverScrollX + scrollRangeX
        val top = -maxOverScrollY
        val bottom = maxOverScrollY + scrollRangeY

        var clampedX = false
        if (newScrollX > right) {
            newScrollX = right
            clampedX = true
        } else if (newScrollX < left) {
            newScrollX = left
            clampedX = true
        }

        var clampedY = false
        if (newScrollY > bottom) {
            newScrollY = bottom
            clampedY = true
        } else if (newScrollY < top) {
            newScrollY = top
            clampedY = true
        }

        if (clampedY && !hasNestedScrollingParent(ViewCompat.TYPE_NON_TOUCH)) {
            mScroller!!.springBack(newScrollX, newScrollY, 0, 0, 0, this.scrollRange)
        }

        onOverScrolled(newScrollX, newScrollY, clampedX, clampedY)

        return clampedX || clampedY
    }

    val scrollRange: Int
        get() {
            var scrollRange = 0
            if (isNotEmpty()) {
                val child = getChildAt(0)
                val lp = child.layoutParams as LayoutParams
                val childSize = child.height + lp.topMargin + lp.bottomMargin
                val parentSpace = height - paddingTop - paddingBottom
                scrollRange = max(0.0, (childSize - parentSpace).toDouble()).toInt()
            }
            return scrollRange
        }

    /**
     *
     *
     * Finds the next focusable component that fits in the specified bounds.
     *
     *
     * @param topFocus look for a candidate is the one at the top of the bounds
     * if topFocus is true, or at the bottom of the bounds if topFocus is
     * false
     * @param top      the top offset of the bounds in which a focusable must be
     * found
     * @param bottom   the bottom offset of the bounds in which a focusable must
     * be found
     * @return the next focusable component in the bounds or null if none can
     * be found
     */
    private fun findFocusableViewInBounds(topFocus: Boolean, top: Int, bottom: Int): View? {
        val focusables: MutableList<View> = getFocusables(FOCUS_FORWARD)
        var focusCandidate: View? = null

        /*
         * A fully contained focusable is one where its top is below the bound's
         * top, and its bottom is above the bound's bottom. A partially
         * contained focusable is one where some part of it is within the
         * bounds, but it also has some part that is not within bounds.  A fully contained
         * focusable is preferred to a partially contained focusable.
         */
        var foundFullyContainedFocusable = false

        val count = focusables.size
        for (i in 0..<count) {
            val view = focusables[i]
            val viewTop = view.top
            val viewBottom = view.bottom

            if (top < viewBottom && viewTop < bottom) {
                /*
                 * the focusable is in the target area, it is a candidate for
                 * focusing
                 */

                val viewIsFullyContained = (top < viewTop) && (viewBottom < bottom)

                if (focusCandidate == null) {
                    /* No candidate, take this one */
                    focusCandidate = view
                    foundFullyContainedFocusable = viewIsFullyContained
                } else {
                    val viewIsCloserToBoundary =
                        (topFocus && viewTop < focusCandidate.top)
                                || (!topFocus && viewBottom > focusCandidate.bottom)

                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained && viewIsCloserToBoundary) {
                            /*
                             * We're dealing with only fully contained views, so
                             * it has to be closer to the boundary to beat our
                             * candidate
                             */
                            focusCandidate = view
                        }
                    } else {
                        if (viewIsFullyContained) {
                            /* Any fully contained view beats a partially contained view */
                            focusCandidate = view
                            foundFullyContainedFocusable = true
                        } else if (viewIsCloserToBoundary) {
                            /*
                             * Partially contained view beats another partially
                             * contained view if it's closer
                             */
                            focusCandidate = view
                        }
                    }
                }
            }
        }

        return focusCandidate
    }

    /**
     *
     * Handles scrolling in response to a "page up/down" shortcut press. This
     * method will scroll the view by one page up or down and give the focus
     * to the topmost/bottommost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.
     *
     * @param direction the scroll direction: [View.FOCUS_UP]
     * to go one page up or
     * [View.FOCUS_DOWN] to go one page down
     * @return true if the key event is consumed by this method, false otherwise
     */
    fun pageScroll(direction: Int): Boolean {
        val down = direction == FOCUS_DOWN
        val height = getHeight()

        if (down) {
            mTempRect.top = scrollY + height
            val count = size
            if (count > 0) {
                val view = getChildAt(count - 1)
                val lp = view.layoutParams as LayoutParams
                val bottom = view.bottom + lp.bottomMargin + paddingBottom
                if (mTempRect.top + height > bottom) {
                    mTempRect.top = bottom - height
                }
            }
        } else {
            mTempRect.top = scrollY - height
            if (mTempRect.top < 0) {
                mTempRect.top = 0
            }
        }
        mTempRect.bottom = mTempRect.top + height

        return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom)
    }

    /**
     *
     * Handles scrolling in response to a "home/end" shortcut press. This
     * method will scroll the view to the top or bottom and give the focus
     * to the topmost/bottommost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.
     *
     * @param direction the scroll direction: [View.FOCUS_UP]
     * to go the top of the view or
     * [View.FOCUS_DOWN] to go the bottom
     * @return true if the key event is consumed by this method, false otherwise
     */
    fun fullScroll(direction: Int): Boolean {
        val down = direction == FOCUS_DOWN
        val height = getHeight()

        mTempRect.top = 0
        mTempRect.bottom = height

        if (down) {
            val count = size
            if (count > 0) {
                val view = getChildAt(count - 1)
                val lp = view.layoutParams as LayoutParams
                mTempRect.bottom = view.bottom + lp.bottomMargin + paddingBottom
                mTempRect.top = mTempRect.bottom - height
            }
        }
        return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom)
    }

    /**
     *
     * Scrolls the view to make the area defined by `top` and
     * `bottom` visible. This method attempts to give the focus
     * to a component visible in this area. If no component can be focused in
     * the new visible area, the focus is reclaimed by this ScrollView.
     *
     * @param direction the scroll direction: [View.FOCUS_UP]
     * to go upward, [View.FOCUS_DOWN] to downward
     * @param top       the top offset of the new area to be made visible
     * @param bottom    the bottom offset of the new area to be made visible
     * @return true if the key event is consumed by this method, false otherwise
     */
    private fun scrollAndFocus(direction: Int, top: Int, bottom: Int): Boolean {
        var handled = true

        val height = getHeight()
        val containerTop = scrollY
        val containerBottom = containerTop + height
        val up = direction == FOCUS_UP

        var newFocused = findFocusableViewInBounds(up, top, bottom)
        if (newFocused == null) {
            newFocused = this
        }

        if (top >= containerTop && bottom <= containerBottom) {
            handled = false
        } else {
            val delta = if (up) (top - containerTop) else (bottom - containerBottom)
            scrollBy(delta, 0, ViewCompat.TYPE_NON_TOUCH, true)
        }

        if (newFocused !== findFocus()) newFocused.requestFocus(direction)

        return handled
    }

    /**
     * Handle scrolling in response to an up or down arrow click.
     *
     * @param direction The direction corresponding to the arrow key that was
     * pressed
     * @return True if we consumed the event, false otherwise
     */
    fun arrowScroll(direction: Int): Boolean {
        var currentFocused = findFocus()
        if (currentFocused === this) currentFocused = null

        val nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction)

        val maxJump = this.maxScrollAmount

        if (nextFocused != null && isWithinDeltaOfScreen(nextFocused, maxJump, height)) {
            nextFocused.getDrawingRect(mTempRect)
            offsetDescendantRectToMyCoords(nextFocused, mTempRect)
            val scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect)

            scrollBy(scrollDelta, 0, ViewCompat.TYPE_NON_TOUCH, true)
            nextFocused.requestFocus(direction)
        } else {
            // no new focus
            var scrollDelta = maxJump

            if (direction == FOCUS_UP && scrollY < scrollDelta) {
                scrollDelta = scrollY
            } else if (direction == FOCUS_DOWN) {
                if (isNotEmpty()) {
                    val child = getChildAt(0)
                    val lp = child.layoutParams as LayoutParams
                    val daBottom = child.bottom + lp.bottomMargin
                    val screenBottom = scrollY + height - paddingBottom
                    scrollDelta = min((daBottom - screenBottom).toDouble(), maxJump.toDouble()).toInt()
                }
            }
            if (scrollDelta == 0) {
                return false
            }

            val finalScrollDelta = if (direction == FOCUS_DOWN) scrollDelta else -scrollDelta
            scrollBy(finalScrollDelta, 0, ViewCompat.TYPE_NON_TOUCH, true)
        }

        if (currentFocused != null && currentFocused.isFocused
            && isOffScreen(currentFocused)
        ) {
            // previously focused item still has focus and is off screen, give
            // it up (take it back to ourselves)
            // (also, need to temporarily force FOCUS_BEFORE_DESCENDANTS so we are
            // sure to
            // get it)
            val descendantFocusability = getDescendantFocusability() // save
            setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS)
            requestFocus()
            setDescendantFocusability(descendantFocusability) // restore
        }
        return true
    }

    /**
     * @return whether the descendant of this scroll view is scrolled off
     * screen.
     */
    private fun isOffScreen(descendant: View): Boolean {
        return !isWithinDeltaOfScreen(descendant, 0, height)
    }

    /**
     * @return whether the descendant of this scroll view is within delta
     * pixels of being on the screen.
     */
    private fun isWithinDeltaOfScreen(descendant: View, delta: Int, height: Int): Boolean {
        descendant.getDrawingRect(mTempRect)
        offsetDescendantRectToMyCoords(descendant, mTempRect)

        return (mTempRect.bottom + delta) >= scrollY
                && (mTempRect.top - delta) <= (scrollY + height)
    }

    /**
     * Smooth scroll by a Y delta
     *
     * @param delta the number of pixels to scroll by on the Y axis
     */
    private fun doScrollY(delta: Int) {
        if (delta != 0) {
            if (this.isSmoothScrollingEnabled) {
                smoothScrollBy(0, delta)
            } else {
                scrollBy(0, delta)
            }
        }
    }

    /**
     * Like [View.scrollBy], but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    fun smoothScrollBy(dx: Int, dy: Int) {
        smoothScrollBy(dx, dy, DEFAULT_SMOOTH_SCROLL_DURATION, false)
    }

    /**
     * Like [View.scrollBy], but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     * @param scrollDurationMs the duration of the smooth scroll operation in milliseconds
     */
    fun smoothScrollBy(dx: Int, dy: Int, scrollDurationMs: Int) {
        smoothScrollBy(dx, dy, scrollDurationMs, false)
    }

    /**
     * Like [View.scrollBy], but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     * @param scrollDurationMs the duration of the smooth scroll operation in milliseconds
     * @param withNestedScrolling whether to include nested scrolling operations.
     */
    private fun smoothScrollBy(dx: Int, dy: Int, scrollDurationMs: Int, withNestedScrolling: Boolean) {
        var dy = dy
        if (isEmpty()) {
            // Nothing to do.
            return
        }
        val duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll
        if (duration > ANIMATED_SCROLL_GAP) {
            val child = getChildAt(0)
            val lp = child.layoutParams as LayoutParams
            val childSize = child.height + lp.topMargin + lp.bottomMargin
            val parentSpace = height - paddingTop - paddingBottom
            val scrollY = getScrollY()
            val maxY = max(0.0, (childSize - parentSpace).toDouble()).toInt()
            dy = (max(0.0, min((scrollY + dy).toDouble(), maxY.toDouble())) - scrollY).toInt()
            mScroller!!.startScroll(scrollX, scrollY, 0, dy, scrollDurationMs)
            runAnimatedScroll(withNestedScrolling)
        } else {
            if (!mScroller!!.isFinished) {
                abortAnimatedScroll()
            }
            scrollBy(dx, dy)
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis()
    }

    /**
     * Like [.scrollTo], but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     */
    fun smoothScrollTo(x: Int, y: Int) {
        smoothScrollTo(x, y, DEFAULT_SMOOTH_SCROLL_DURATION, false)
    }

    /**
     * Like [.scrollTo], but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     * @param scrollDurationMs the duration of the smooth scroll operation in milliseconds
     */
    fun smoothScrollTo(x: Int, y: Int, scrollDurationMs: Int) {
        smoothScrollTo(x, y, scrollDurationMs, false)
    }

    /**
     * Like [.scrollTo], but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     * @param withNestedScrolling whether to include nested scrolling operations.
     */
    // This should be considered private, it is package private to avoid a synthetic ancestor.
    fun smoothScrollTo(x: Int, y: Int, withNestedScrolling: Boolean) {
        smoothScrollTo(x, y, DEFAULT_SMOOTH_SCROLL_DURATION, withNestedScrolling)
    }

    /**
     * Like [.scrollTo], but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     * @param scrollDurationMs the duration of the smooth scroll operation in milliseconds
     * @param withNestedScrolling whether to include nested scrolling operations.
     */
    // This should be considered private, it is package private to avoid a synthetic ancestor.
    fun smoothScrollTo(x: Int, y: Int, scrollDurationMs: Int, withNestedScrolling: Boolean) {
        smoothScrollBy(x - scrollX, y - scrollY, scrollDurationMs, withNestedScrolling)
    }

    /**
     *
     * The scroll range of a scroll view is the overall height of all of its
     * children.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override fun computeVerticalScrollRange(): Int {
        val count = size
        val parentSpace = height - paddingBottom - paddingTop
        if (count == 0) {
            return parentSpace
        }

        val child = getChildAt(0)
        val lp = child.layoutParams as LayoutParams
        var scrollRange = child.bottom + lp.bottomMargin
        val scrollY = getScrollY()
        val overscrollBottom = max(0.0, (scrollRange - parentSpace).toDouble()).toInt()
        if (scrollY < 0) {
            scrollRange -= scrollY
        } else if (scrollY > overscrollBottom) {
            scrollRange += scrollY - overscrollBottom
        }

        return scrollRange
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override fun computeVerticalScrollOffset(): Int {
        return max(0.0, super.computeVerticalScrollOffset().toDouble()).toInt()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override fun computeVerticalScrollExtent(): Int {
        return super.computeVerticalScrollExtent()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override fun computeHorizontalScrollRange(): Int {
        return super.computeHorizontalScrollRange()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override fun computeHorizontalScrollOffset(): Int {
        return super.computeHorizontalScrollOffset()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override fun computeHorizontalScrollExtent(): Int {
        return super.computeHorizontalScrollExtent()
    }

    override fun measureChild(
        child: View, parentWidthMeasureSpec: Int,
        parentHeightMeasureSpec: Int
    ) {
        val lp = child.layoutParams

        val childWidthMeasureSpec: Int = getChildMeasureSpec(
            parentWidthMeasureSpec, getPaddingLeft()
                    + getPaddingRight(), lp.width
        )

        val childHeightMeasureSpec: Int = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    override fun measureChildWithMargins(
        child: View, parentWidthMeasureSpec: Int, widthUsed: Int,
        parentHeightMeasureSpec: Int, heightUsed: Int
    ) {
        val lp = child.layoutParams as MarginLayoutParams

        val childWidthMeasureSpec = getChildMeasureSpec(
            parentWidthMeasureSpec,
            (getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin
                    + widthUsed), lp.width
        )
        val childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
            lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED
        )

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    override fun computeScroll() {
        if (mScroller!!.isFinished) {
            return
        }

        mScroller!!.computeScrollOffset()
        val y = mScroller!!.currY
        var unconsumed = consumeFlingInVerticalStretch(y - mLastScrollerY)
        mLastScrollerY = y

        // Nested Scrolling Pre Pass
        mScrollConsumed[1] = 0
        dispatchNestedPreScroll(
            0, unconsumed, mScrollConsumed, null,
            ViewCompat.TYPE_NON_TOUCH
        )
        unconsumed -= mScrollConsumed[1]

        val range = this.scrollRange

        if (Build.VERSION.SDK_INT >= 35) {
            Api35Impl.setFrameContentVelocity(
                this@NestedScrollView,
                abs(mScroller!!.currVelocity.toDouble()).toFloat()
            )
        }

        if (unconsumed != 0) {
            // Internal Scroll
            val oldScrollY = scrollY
            overScrollByCompat(0, unconsumed, scrollX, oldScrollY, 0, range, 0, 0, false)
            val scrolledByMe = scrollY - oldScrollY
            unconsumed -= scrolledByMe

            // Nested Scrolling Post Pass
            mScrollConsumed[1] = 0
            dispatchNestedScroll(
                0, scrolledByMe, 0, unconsumed, mScrollOffset,
                ViewCompat.TYPE_NON_TOUCH, mScrollConsumed
            )
            unconsumed -= mScrollConsumed[1]
        }

        if (unconsumed != 0) {
            val mode = overScrollMode
            val canOverscroll = mode == OVER_SCROLL_ALWAYS
                    || (mode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0)
            if (canOverscroll) {
                if (unconsumed < 0) {
                    if (mEdgeGlowTop.isFinished) {
                        mEdgeGlowTop.onAbsorb(mScroller!!.currVelocity.toInt())
                    }
                } else {
                    if (mEdgeGlowBottom.isFinished) {
                        mEdgeGlowBottom.onAbsorb(mScroller!!.currVelocity.toInt())
                    }
                }
            }
            abortAnimatedScroll()
        }

        if (!mScroller!!.isFinished) {
            postInvalidateOnAnimation()
        } else {
            stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
        }
    }

    /**
     * If either of the vertical edge glows are currently active, this consumes part or all of
     * deltaY on the edge glow.
     *
     * @param deltaY The pointer motion, in pixels, in the vertical direction, positive
     * for moving down and negative for moving up.
     * @param x The vertical position of the pointer.
     * @return The amount of `deltaY` that has been consumed by the
     * edge glow.
     */
    private fun releaseVerticalGlow(deltaY: Int, x: Float): Int {
        // First allow releasing existing overscroll effect:
        var consumed = 0f
        val displacement = x / width
        val pullDistance = deltaY.toFloat() / height
        if (EdgeEffectCompat.getDistance(mEdgeGlowTop) != 0f) {
            consumed = -EdgeEffectCompat.onPullDistance(mEdgeGlowTop, -pullDistance, displacement)
            if (EdgeEffectCompat.getDistance(mEdgeGlowTop) == 0f) {
                mEdgeGlowTop.onRelease()
            }
        } else if (EdgeEffectCompat.getDistance(mEdgeGlowBottom) != 0f) {
            consumed = EdgeEffectCompat.onPullDistance(
                mEdgeGlowBottom, pullDistance,
                1 - displacement
            )
            if (EdgeEffectCompat.getDistance(mEdgeGlowBottom) == 0f) {
                mEdgeGlowBottom.onRelease()
            }
        }
        val pixelsConsumed = (consumed * height).roundToInt()
        if (pixelsConsumed != 0) {
            invalidate()
        }
        return pixelsConsumed
    }

    private fun runAnimatedScroll(participateInNestedScrolling: Boolean) {
        if (participateInNestedScrolling) {
            startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH)
        } else {
            stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
        }
        mLastScrollerY = scrollY
        postInvalidateOnAnimation()
    }

    private fun abortAnimatedScroll() {
        mScroller!!.abortAnimation()
        stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
    }

    /**
     * Scrolls the view to the given child.
     *
     * @param child the View to scroll to
     */
    private fun scrollToChild(child: View) {
        child.getDrawingRect(mTempRect)

        /* Offset from child's local coordinates to ScrollView coordinates */
        offsetDescendantRectToMyCoords(child, mTempRect)

        val scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect)

        if (scrollDelta != 0) {
            scrollBy(0, scrollDelta)
        }
    }

    /**
     * If rect is off screen, scroll just enough to get it (or at least the
     * first screen size chunk of it) on screen.
     *
     * @param rect      The rectangle.
     * @param immediate True to scroll immediately without animation
     * @return true if scrolling was performed
     */
    private fun scrollToChildRect(rect: Rect, immediate: Boolean): Boolean {
        val delta = computeScrollDeltaToGetChildRectOnScreen(rect)
        val scroll = delta != 0
        if (scroll) {
            if (immediate) {
                scrollBy(0, delta)
            } else {
                smoothScrollBy(0, delta)
            }
        }
        return scroll
    }

    /**
     * Compute the amount to scroll in the Y direction in order to get
     * a rectangle completely on the screen (or, if taller than the screen,
     * at least the first screen size chunk of it).
     *
     * @param rect The rect.
     * @return The scroll delta.
     */
    private fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (isEmpty()) return 0

        val height = getHeight()
        var screenTop = scrollY
        var screenBottom = screenTop + height
        val actualScreenBottom = screenBottom

        val fadingEdge = getVerticalFadingEdgeLength()

        // TODO: screenTop should be incremented by fadingEdge * getTopFadingEdgeStrength (but for
        // the target scroll distance).
        // leave room for top fading edge as long as rect isn't at very top
        if (rect.top > 0) {
            screenTop += fadingEdge
        }

        // TODO: screenBottom should be decremented by fadingEdge * getBottomFadingEdgeStrength (but
        // for the target scroll distance).
        // leave room for bottom fading edge as long as rect isn't at very bottom
        val child = getChildAt(0)
        val lp = child.layoutParams as LayoutParams
        if (rect.bottom < child.height + lp.topMargin + lp.bottomMargin) {
            screenBottom -= fadingEdge
        }

        var scrollYDelta = 0

        if (rect.bottom > screenBottom && rect.top > screenTop) {
            // need to move down to get it in view: move down just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).

            scrollYDelta += if (rect.height() > height) {
                // just enough to get screen size chunk on
                (rect.top - screenTop)
            } else {
                // get entire rect at bottom of screen
                (rect.bottom - screenBottom)
            }

            // make sure we aren't scrolling beyond the end of our content
            val bottom = child.bottom + lp.bottomMargin
            val distanceToBottom = bottom - actualScreenBottom
            scrollYDelta = min(scrollYDelta.toDouble(), distanceToBottom.toDouble()).toInt()
        } else if (rect.top < screenTop && rect.bottom < screenBottom) {
            // need to move up to get it in view: move up just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).

            scrollYDelta -= if (rect.height() > height) {
                // screen size chunk
                (screenBottom - rect.bottom)
            } else {
                // entire rect at top
                (screenTop - rect.top)
            }

            // make sure we aren't scrolling any further than the top our content
            scrollYDelta = max(scrollYDelta.toDouble(), -scrollY.toDouble()).toInt()
        }
        return scrollYDelta
    }

    override fun requestChildFocus(child: View?, focused: View) {
        if (!mIsLayoutDirty) {
            scrollToChild(focused)
        } else {
            // The child may not be laid out yet, we can't compute the scroll yet
            mChildToScrollTo = focused
        }
        super.requestChildFocus(child, focused)
    }


    /**
     * When looking for focus in children of a scroll view, need to be a little
     * more careful not to give focus to something that is scrolled off screen.
     *
     * This is more expensive than the default [ViewGroup]
     * implementation, otherwise this behavior might have been made the default.
     */
    override fun onRequestFocusInDescendants(
        direction: Int,
        previouslyFocusedRect: Rect?
    ): Boolean {
        // convert from forward / backward notation to up / down / left / right
        // (ugh).

        var direction = direction
        if (direction == FOCUS_FORWARD) {
            direction = FOCUS_DOWN
        } else if (direction == FOCUS_BACKWARD) {
            direction = FOCUS_UP
        }

        val nextFocus = if (previouslyFocusedRect == null)
            FocusFinder.getInstance().findNextFocus(this, null, direction)
        else
            FocusFinder.getInstance().findNextFocusFromRect(
                this, previouslyFocusedRect, direction
            )

        if (nextFocus == null) {
            return false
        }

        if (isOffScreen(nextFocus)) {
            return false
        }

        return nextFocus.requestFocus(direction, previouslyFocusedRect)
    }

    override fun requestChildRectangleOnScreen(
        child: View, rectangle: Rect,
        immediate: Boolean
    ): Boolean {
        // offset into coordinate space of this scroll view
        rectangle.offset(
            child.left - child.scrollX,
            child.top - child.scrollY
        )

        return scrollToChildRect(rectangle, immediate)
    }

    override fun requestLayout() {
        mIsLayoutDirty = true
        super.requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        mIsLayoutDirty = false
        // Give a child focus if it needs it
        if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo!!, this)) {
            scrollToChild(mChildToScrollTo!!)
        }
        mChildToScrollTo = null

        if (!mIsLaidOut) {
            // If there is a saved state, scroll to the position saved in that state.
            if (mSavedState != null) {
                scrollTo(scrollX, mSavedState!!.scrollPosition)
                mSavedState = null
            } // mScrollY default value is "0"


            // Make sure current scrollY position falls into the scroll range.  If it doesn't,
            // scroll such that it does.
            var childSize = 0
            if (isNotEmpty()) {
                val child = getChildAt(0)
                val lp = child.layoutParams as LayoutParams
                childSize = child.measuredHeight + lp.topMargin + lp.bottomMargin
            }
            val parentSpace = b - t - paddingTop - paddingBottom
            val currentScrollY = scrollY
            val newScrollY: Int = clamp(currentScrollY, parentSpace, childSize)
            if (newScrollY != currentScrollY) {
                scrollTo(scrollX, newScrollY)
            }
        }

        // Calling this with the present values causes it to re-claim them
        scrollTo(scrollX, scrollY)
        mIsLaidOut = true
    }

    public override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        mIsLaidOut = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val currentFocused = findFocus()
        if (null == currentFocused || this === currentFocused) {
            return
        }

        // If the currently-focused view was visible on the screen when the
        // screen was at the old height, then scroll the screen to make that
        // view visible with the new screen height.
        if (isWithinDeltaOfScreen(currentFocused, 0, oldh)) {
            currentFocused.getDrawingRect(mTempRect)
            offsetDescendantRectToMyCoords(currentFocused, mTempRect)
            val scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect)
            doScrollY(scrollDelta)
        }
    }

    /**
     * Fling the scroll view
     *
     * @param velocityY The initial velocity in the Y direction. Positive
     * numbers mean that the finger/cursor is moving down the screen,
     * which means we want to scroll towards the top.
     */
    fun fling(velocityY: Int) {
        if (isNotEmpty()) {
            mScroller!!.fling(
                scrollX, scrollY,  // start
                0, velocityY,  // velocities
                0, 0,  // x
                Int.Companion.MIN_VALUE, Int.Companion.MAX_VALUE,  // y
                0, 0
            ) // overscroll
            runAnimatedScroll(true)
            if (Build.VERSION.SDK_INT >= 35) {
                Api35Impl.setFrameContentVelocity(
                    this@NestedScrollView,
                    abs(mScroller!!.currVelocity.toDouble()).toFloat()
                )
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * This version also clamps the scrolling to the bounds of our child.
     */
    override fun scrollTo(x: Int, y: Int) {
        // we rely on the fact the View.scrollBy calls scrollTo.
        var x = x
        var y = y
        if (isNotEmpty()) {
            val child = getChildAt(0)
            val lp = child.layoutParams as LayoutParams
            val parentSpaceHorizontal = width - getPaddingLeft() - getPaddingRight()
            val childSizeHorizontal = child.width + lp.leftMargin + lp.rightMargin
            val parentSpaceVertical = height - paddingTop - paddingBottom
            val childSizeVertical = child.height + lp.topMargin + lp.bottomMargin
            x = clamp(x, parentSpaceHorizontal, childSizeHorizontal)
            y = clamp(y, parentSpaceVertical, childSizeVertical)
            if (x != scrollX || y != scrollY) {
                super.scrollTo(x, y)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val scrollY = getScrollY()
        if (!mEdgeGlowTop.isFinished) {
            canvas.withTranslation(0f, min(0.0, scrollY.toDouble()).toFloat()) {
                mEdgeGlowTop.setSize(this@NestedScrollView.width, this@NestedScrollView.height)
                if (mEdgeGlowTop.draw(canvas)) {
                    postInvalidateOnAnimation()
                }
            }
        }
        if (!mEdgeGlowBottom.isFinished) {
            canvas.withSave {
                val width = this@NestedScrollView.width
                val height = this@NestedScrollView.height
                val yTranslation: Int = (max(this@NestedScrollView.scrollRange.toDouble(), scrollY.toDouble()) + height).toInt()
                canvas.translate(-width.toFloat(), yTranslation.toFloat())
                canvas.rotate(180f, width.toFloat(), 0f)
                mEdgeGlowBottom.setSize(width, height)
                if (mEdgeGlowBottom.draw(canvas)) {
                    postInvalidateOnAnimation()
                }
            }
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        val ss = state
        super.onRestoreInstanceState(ss.superState)
        mSavedState = ss
        requestLayout()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.scrollPosition = scrollY
        return ss
    }

    internal class SavedState : BaseSavedState {
        var scrollPosition: Int = 0

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel) : super(source) {
            scrollPosition = source.readInt()
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(scrollPosition)
        }

        override fun toString(): String {
            return ("HorizontalScrollView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " scrollPosition=" + scrollPosition + "}")
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState?> = object : Parcelable.Creator<SavedState?> {
                override fun createFromParcel(`in`: Parcel): SavedState {
                    return SavedState(`in`)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls<SavedState>(size)
                }
            }
        }
    }

    private val scrollFeedbackProvider: ScrollFeedbackProviderCompat
        get() {
            if (mScrollFeedbackProvider == null) {
                mScrollFeedbackProvider = ScrollFeedbackProviderCompat.createProvider(this)
            }
            return mScrollFeedbackProvider!!
        }

    internal inner class DifferentialMotionFlingTargetImpl : DifferentialMotionFlingTarget {
        override fun startDifferentialMotionFling(velocity: Float): Boolean {
            if (velocity == 0f) {
                return false
            }
            stopDifferentialMotionFling()
            fling(velocity.toInt())
            return true
        }

        override fun stopDifferentialMotionFling() {
            mScroller!!.abortAnimation()
        }

        override fun getScaledScrollFactor(): Float {
            return -this@NestedScrollView.verticalScrollFactorCompat
        }
    }

    @RequiresApi(35)
    private object Api35Impl {
        fun setFrameContentVelocity(view: View, velocity: Float) {
            try {
                view.frameContentVelocity = velocity
            } catch (_: LinkageError) {
                // The setFrameContentVelocity method is unavailable on this device.
            }
        }
    }

    companion object {
        const val ANIMATED_SCROLL_GAP: Int = 250

        const val MAX_SCROLL_FACTOR: Float = 0.5f

        private const val TAG = "NestedScrollView"
        private const val DEFAULT_SMOOTH_SCROLL_DURATION = 250

        /**
         * The following are copied from OverScroller to determine how far a fling will go.
         */
        private const val SCROLL_FRICTION = 0.015f
        private const val INFLEXION = 0.35f // Tension lines cross at (INFLEXION, 1)
        private val DECELERATION_RATE = (ln(0.78) / ln(0.9)).toFloat()

        /**
         * When flinging the stretch towards scrolling content, it should destretch quicker than the
         * fling would normally do. The visual effect of flinging the stretch looks strange as little
         * appears to happen at first and then when the stretch disappears, the content starts
         * scrolling quickly.
         */
        private const val FLING_DESTRETCH_FACTOR = 4f

        /**
         * Sentinel value for no current active pointer.
         * Used by [.mActivePointerId].
         */
        private const val INVALID_POINTER = -1

        private val SCROLLVIEW_STYLEABLE = intArrayOf(
            android.R.attr.fillViewport
        )

        /**
         * Return true if child is a descendant of parent, (or equal to the parent).
         */
        private fun isViewDescendantOf(child: View, parent: View?): Boolean {
            if (child === parent) {
                return true
            }

            val theParent = child.parent
            return (theParent is ViewGroup) && isViewDescendantOf(theParent as View, parent)
        }

        private fun clamp(n: Int, my: Int, child: Int): Int {
            if (my >= child || n < 0) {
                /* my >= child is this case:
             *                    |--------------- me ---------------|
             *     |------ child ------|
             * or
             *     |--------------- me ---------------|
             *            |------ child ------|
             * or
             *     |--------------- me ---------------|
             *                                  |------ child ------|
             *
             * n < 0 is this case:
             *     |------ me ------|
             *                    |-------- child --------|
             *     |-- mScrollX --|
             */
                return 0
            }
            if ((my + n) > child) {
                /* this case:
             *                    |------ me ------|
             *     |------ child ------|
             *     |-- mScrollX --|
             */
                return child - my
            }
            return n
        }
    }
}
