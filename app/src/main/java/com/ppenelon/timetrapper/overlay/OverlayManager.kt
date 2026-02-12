package com.ppenelon.timetrapper.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import com.ppenelon.timetrapper.R

/**
 * Gère la fenêtre système (overlay) bloquante pour imposer un choix de durée.
 */
class OverlayManager(private val context: Context) {
    companion object {
        private const val TAG = "OverlayManager"
    }

    enum class TimeSelection(val minutes: Int?) {
        ONE_MINUTE(1),
        TWO_MINUTES(2),
        FIVE_MINUTES(5),
        FIFTEEN_MINUTES(15),
        NO_LIMIT(null)
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var showingPackageName: String? = null

    fun canDisplayOverlay(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isShowing(): Boolean {
        return overlayView != null
    }

    fun getShowingPackageName(): String? {
        return showingPackageName
    }

    fun showTimePicker(
        packageName: String,
        appDisplayName: String,
        onSelectionConfirmed: (TimeSelection) -> Unit
    ): Boolean {
        if (!canDisplayOverlay() || overlayView != null) {
            return false
        }

        return try {
            val pickerView = LayoutInflater.from(context).inflate(R.layout.overlay_time_picker, null)
            val packageLabel = pickerView.findViewById<TextView>(R.id.textOverlayPackage)
            val durationsGroup = pickerView.findViewById<RadioGroup>(R.id.radioGroupDurations)
            val confirmButton = pickerView.findViewById<Button>(R.id.buttonConfirmTime)

            packageLabel.text = context.getString(R.string.overlay_package_label, appDisplayName)
            confirmButton.setOnClickListener {
                val selectedDuration = when (durationsGroup.checkedRadioButtonId) {
                    R.id.radio1min -> TimeSelection.ONE_MINUTE
                    R.id.radio2min -> TimeSelection.TWO_MINUTES
                    R.id.radio15min -> TimeSelection.FIFTEEN_MINUTES
                    R.id.radioNoLimit -> TimeSelection.NO_LIMIT
                    else -> TimeSelection.FIVE_MINUTES
                }
                onSelectionConfirmed(selectedDuration)
                hide()
            }

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            windowManager.addView(pickerView, layoutParams)
            overlayView = pickerView
            showingPackageName = packageName
            true
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to display overlay for $packageName", error)
            false
        }
    }

    fun hide() {
        val attachedView = overlayView ?: return
        try {
            windowManager.removeViewImmediate(attachedView)
        } catch (_: Exception) {
            // Ignored: view may already be detached.
        } finally {
            overlayView = null
            showingPackageName = null
        }
    }
}
