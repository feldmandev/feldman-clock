package com.feldman.clock.ui.standby.widgets

import com.feldman.clock.R

import android.content.Context
import android.appwidget.AppWidgetHostView
import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.RemoteViews
import kotlin.math.abs

private const val CLOCK_WIDGET_TOUCH_TAG = "ClockWidgetTouch"

class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    private val downPoint = PointF()
    private val longPressRunnable = Runnable {
        if (parent != null && !longPressTriggered) {
            longPressTriggered = true
            logTouch("longPress fired")
            onLongPress?.invoke()
        }
    }
    private var longPressTriggered = false
    private var parentInterceptDisallowed = false
    private var touchStreamStarted = false
    private var forwardingTouchToParent = false
    private var bridgedDispatchDepth = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    var onLongPress: (() -> Unit)? = null
    var blockUnbridgedTouchEvents: Boolean = false

    fun dispatchBridgedTouchEvent(ev: MotionEvent): Boolean {
        bridgedDispatchDepth++
        return try {
            dispatchTouchEvent(ev)
        } finally {
            bridgedDispatchDepth--
        }
    }

    init {
        // Hardware Acceleration - deactivate on android O or higher
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        //    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        //}
    }

    private fun widgetLabel(): String =
        "appWidgetId=${runCatching { getAppWidgetId() }.getOrDefault(-1)} view=${System.identityHashCode(this)}"

    private fun logTouch(message: String) {
        Log.d(CLOCK_WIDGET_TOUCH_TAG, "${widgetLabel()} $message")
    }

    private fun setParentInterceptDisallowed(disallowed: Boolean) {
        if (parentInterceptDisallowed == disallowed) return
        parentInterceptDisallowed = disallowed
        logTouch("requestDisallowInterceptTouchEvent($disallowed) parent=${parent?.javaClass?.simpleName}")
        parent?.requestDisallowInterceptTouchEvent(disallowed)
    }


    override fun updateAppWidget(remoteViews: RemoteViews?) {
        // can happen, no idea why (maybe if the widget itself has a bug?)... we better catch it to avoid that a widget can crash the whole app
        try {
            Log.d(CLOCK_WIDGET_TOUCH_TAG, "${widgetLabel()} updateAppWidget package=${remoteViews?.`package`} remoteViews=$remoteViews")
            super.updateAppWidget(remoteViews)
        } catch (e: Exception) {
            L.d { "Error updating widget: ${e.message}" }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val intercepted = super.onInterceptTouchEvent(ev)
        if (intercepted ||
            ev.actionMasked == MotionEvent.ACTION_DOWN ||
            ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            logTouch("onInterceptTouchEvent action=${MotionEvent.actionToString(ev.actionMasked)} intercepted=$intercepted")
        }
        return intercepted
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val actionName = MotionEvent.actionToString(ev.actionMasked)
        if (blockUnbridgedTouchEvents && bridgedDispatchDepth == 0) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN ||
                ev.actionMasked == MotionEvent.ACTION_UP ||
                ev.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                logTouch(
                    "dispatch $actionName blocked unbridged x=${ev.x.toInt()} y=${ev.y.toInt()}"
                )
            }
            return true
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStreamStarted = true
                forwardingTouchToParent = false
                longPressTriggered = false
                downPoint.set(ev.x, ev.y)
                removeCallbacks(longPressRunnable)
                logTouch("dispatch $actionName x=${ev.x.toInt()} y=${ev.y.toInt()} onLongPress=${onLongPress != null}")
                setParentInterceptDisallowed(true)
                if (onLongPress != null) {
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!blockUnbridgedTouchEvents &&
                    !forwardingTouchToParent &&
                    (abs(ev.x - downPoint.x) > touchSlop || abs(ev.y - downPoint.y) > touchSlop)
                ) {
                    logTouch(
                        "dispatch $actionName crossedSlop dx=${(ev.x - downPoint.x).toInt()} " +
                            "dy=${(ev.y - downPoint.y).toInt()} -> cancel widget touch and let parent handle swipe"
                    )
                    removeCallbacks(longPressRunnable)
                    setParentInterceptDisallowed(false)
                    val cancelEvent = MotionEvent.obtain(ev).apply {
                        action = MotionEvent.ACTION_CANCEL
                    }
                    super.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                    forwardingTouchToParent = true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                logTouch("dispatch $actionName ending stream")
                setParentInterceptDisallowed(false)
            }
        }

        if (longPressTriggered) {
            logTouch("dispatch $actionName consumed by longPress")
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                longPressTriggered = false
                touchStreamStarted = false
                forwardingTouchToParent = false
            }
            return true
        }

        if (forwardingTouchToParent) {
            logTouch("dispatch $actionName returning false because stream was released to parent")
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchStreamStarted = false
                forwardingTouchToParent = false
            }
            return false
        }

        val handled = super.dispatchTouchEvent(ev)
        val keepTouchInWidget = touchStreamStarted
        val result = handled || keepTouchInWidget

        if (ev.actionMasked == MotionEvent.ACTION_DOWN ||
            ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            logTouch(
                "dispatch $actionName superHandled=$handled keepTouchInWidget=$keepTouchInWidget " +
                    "return=$result"
            )
        }

        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            touchStreamStarted = false
            forwardingTouchToParent = false
        }

        return result
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        logTouch("attached parent=${parent?.javaClass?.simpleName}")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        logTouch("detached")
        removeCallbacks(longPressRunnable)
        longPressTriggered = false
        touchStreamStarted = false
        forwardingTouchToParent = false
        setParentInterceptDisallowed(false)
    }

    //override fun getDescendantFocusability() = ViewGroup.FOCUS_BLOCK_DESCENDANTS
}
