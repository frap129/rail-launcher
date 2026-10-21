package feature.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import core.data.widgets.AppWidgetHostCallbacks
import core.data.widgets.AppWidgetId

/** The AppWidgetHost Rail owns for the process lifetime. */
class RailAppWidgetHost(context: Context, hostId: Int, private val callbacks: AppWidgetHostCallbacks) : AppWidgetHost(context, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo): AppWidgetHostView =
        RailAppWidgetHostView(context)

    override fun onProvidersChanged() {
        super.onProvidersChanged()
        callbacks.onProvidersChanged()
    }
}

/**
 * The host view Rail shows for every App Widget. It intercepts a long press
 * before a child can turn it into a click and disables the system's default
 * widget padding, since the Widget Area already has edge insets.
 */
@SuppressLint("ViewConstructor")
class RailAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var downAt: Long = 0L
    private var isLongPressFired: Boolean = false
    private var isLongPressPending: Boolean = false

    private val longPressRunnable: Runnable = Runnable {
        if (!isLongPressPending) {
            return@Runnable
        }
        isLongPressPending = false
        isLongPressFired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        sendCancelToChildren()
        onLongPress?.invoke()
    }

    init {
        setPadding(0, 0, 0, 0)
    }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        setPadding(0, 0, 0, 0)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downAt = SystemClock.uptimeMillis()
                isLongPressFired = false
                isLongPressPending = true
                postDelayed(longPressRunnable, longPressTimeout)
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (isLongPressPending) {
                    val distanceX: Float = event.x - downX
                    val distanceY: Float = event.y - downY
                    if (distanceX * distanceX + distanceY * distanceY > touchSlop * touchSlop) {
                        cancelPendingLongPress()
                    }
                }
                return isLongPressFired
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                return isLongPressFired
            }
        }
        return isLongPressFired
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) {
            cancelPendingLongPress()
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun cancelPendingLongPress() {
        isLongPressPending = false
        removeCallbacks(longPressRunnable)
    }

    private fun sendCancelToChildren() {
        if (isLongPressFired) {
            val cancel: MotionEvent = MotionEvent.obtain(
                downAt,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL,
                downX,
                downY,
                0
            )
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
    }

    private fun detachFromParent() {
        (parent as? ViewGroup)?.removeView(this)
    }
}

/** One RailAppWidgetHostView per App Widget id, kept for the process lifetime. */
class AppWidgetViewCache(private val context: Context, private val host: AppWidgetHost) {
    private val views: MutableMap<Int, RailAppWidgetHostView> = mutableMapOf()
    private val reportedSizes: MutableMap<Int, ReportedSize> = mutableMapOf()

    fun getOrCreate(id: AppWidgetId): RailAppWidgetHostView? {
        views[id.value]?.let { view ->
            view.detachFromParent()
            return view
        }
        val info: AppWidgetProviderInfo = AppWidgetManager.getInstance(context).getAppWidgetInfo(id.value) ?: return null
        val view: RailAppWidgetHostView = host.createView(context, id.value, info) as RailAppWidgetHostView
        view.setPadding(0, 0, 0, 0)
        views[id.value] = view
        return view
    }

    /** Re-applies the provider info after the provider becomes available again. */
    fun refresh(id: AppWidgetId) {
        val view: RailAppWidgetHostView = views[id.value] ?: return
        val info: AppWidgetProviderInfo = AppWidgetManager.getInstance(context).getAppWidgetInfo(id.value) ?: return
        view.setAppWidget(id.value, info)
    }

    fun remove(id: AppWidgetId) {
        views.remove(id.value)?.let { view ->
            view.onLongPress = null
            view.detachFromParent()
        }
        reportedSizes.remove(id.value)
    }

    /** Reports [widthDp] × [heightDp] unless the same size was already sent for this widget. */
    fun reportSizeIfChanged(id: AppWidgetId, widthDp: Int, heightDp: Int) {
        val view: RailAppWidgetHostView = views[id.value] ?: return
        val reported: ReportedSize? = reportedSizes[id.value]
        if (reported != null && reported.heightDp == heightDp && kotlin.math.abs(reported.widthDp - widthDp) <= 1) {
            return
        }
        val options: Bundle = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        }
        view.updateAppWidgetSize(options, listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        reportedSizes[id.value] = ReportedSize(widthDp = widthDp, heightDp = heightDp)
    }

    /** Reports the persisted Widget Area size to the providers of the resized place. */
    fun updateSize(ids: List<AppWidgetId>, widthDp: Int, heightDp: Int) {
        ids.forEach { id -> reportSizeIfChanged(id = id, widthDp = widthDp, heightDp = heightDp) }
    }
}

private data class ReportedSize(val widthDp: Int, val heightDp: Int)

private fun RailAppWidgetHostView.detachFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}
