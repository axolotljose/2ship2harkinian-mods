package com.ps2.shell

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.CombinedVibration
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class TouchOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface InputListener {
        fun onInputChanged(
            buttons: Int,
            lstickX: Float, lstickY: Float,
            rstickX: Float, rstickY: Float,
            l2Pressure: Int, r2Pressure: Int
        )
    }

    var inputListener: InputListener? = null

    /* Control Element Layout Definition */
    data class ControlElement(
        val id: String,
        val label: String,
        var xNorm: Float,    // Center X as fraction of view width [0..1]
        var yNorm: Float,    // Center Y as fraction of view height [0..1]
        var radiusDp: Float, // Base radius / size in DP
        var scale: Float = 1.0f,
        var opacity: Float = 0.8f,
        val bitmask: Int = 0
    ) {
        fun currentRadiusPx(density: Float): Float = radiusDp * density * scale
    }

    var isEditMode: Boolean = false
    var globalOpacity: Float = 0.8f
    var hapticsEnabled: Boolean = true

    val elements = mutableMapOf<String, ControlElement>()

    // Current State
    private var currentButtonsBitmask = 0
    private var lstickX = 0.0f
    private var lstickY = 0.0f
    private var rstickX = 0.0f
    private var rstickY = 0.0f
    private var l2Pressure = 0
    private var r2Pressure = 0

    // Stick visual offsets
    private var lstickTouchX = 0.0f
    private var lstickTouchY = 0.0f
    private var rstickTouchX = 0.0f
    private var rstickTouchY = 0.0f

    // Touch pointer mapping: pointerId -> elementId
    private val activePointers = mutableMapOf<Int, String>()

    // Selected element for edit mode
    var selectedElementId: String? = null

    // Paints
    private val paintButton = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintPressed = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintSelected = Paint(Paint.ANTI_ALIAS_FLAG)

    // Vibrator
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        }
    }

    init {
        initDefaultLayout()

        paintButton.style = Paint.Style.FILL
        paintPressed.style = Paint.Style.FILL
        paintPressed.color = Color.parseColor("#8000E5FF")

        paintText.color = Color.WHITE
        paintText.textAlign = Paint.Align.CENTER
        paintText.textSize = 32.0f

        paintSelected.style = Paint.Style.STROKE
        paintSelected.strokeWidth = 6.0f
        paintSelected.color = Color.YELLOW
    }

    fun initDefaultLayout() {
        elements.clear()

        // Left Stick & Right Stick
        elements["lstick"] = ControlElement("lstick", "L-Stick", 0.18f, 0.70f, 60.0f)
        elements["rstick"] = ControlElement("rstick", "R-Stick", 0.82f, 0.70f, 60.0f)

        // D-Pad
        elements["dpad_up"] = ControlElement("dpad_up", "▲", 0.18f, 0.28f, 28.0f, bitmask = NativeConstants.PS2_BUTTON_DPAD_UP)
        elements["dpad_down"] = ControlElement("dpad_down", "▼", 0.18f, 0.44f, 28.0f, bitmask = NativeConstants.PS2_BUTTON_DPAD_DOWN)
        elements["dpad_left"] = ControlElement("dpad_left", "◀", 0.10f, 0.36f, 28.0f, bitmask = NativeConstants.PS2_BUTTON_DPAD_LEFT)
        elements["dpad_right"] = ControlElement("dpad_right", "▶", 0.26f, 0.36f, 28.0f, bitmask = NativeConstants.PS2_BUTTON_DPAD_RIGHT)

        // Face Buttons
        elements["cross"] = ControlElement("cross", "✕", 0.82f, 0.44f, 30.0f, bitmask = NativeConstants.PS2_BUTTON_CROSS)
        elements["circle"] = ControlElement("circle", "◯", 0.90f, 0.36f, 30.0f, bitmask = NativeConstants.PS2_BUTTON_CIRCLE)
        elements["square"] = ControlElement("square", "▢", 0.74f, 0.36f, 30.0f, bitmask = NativeConstants.PS2_BUTTON_SQUARE)
        elements["triangle"] = ControlElement("triangle", "△", 0.82f, 0.28f, 30.0f, bitmask = NativeConstants.PS2_BUTTON_TRIANGLE)

        // Shoulder Buttons
        elements["l1"] = ControlElement("l1", "L1", 0.15f, 0.10f, 32.0f, bitmask = NativeConstants.PS2_BUTTON_L1)
        elements["l2"] = ControlElement("l2", "L2", 0.30f, 0.10f, 32.0f, bitmask = NativeConstants.PS2_BUTTON_L2)
        elements["r1"] = ControlElement("r1", "R1", 0.85f, 0.10f, 32.0f, bitmask = NativeConstants.PS2_BUTTON_R1)
        elements["r2"] = ControlElement("r2", "R2", 0.70f, 0.10f, 32.0f, bitmask = NativeConstants.PS2_BUTTON_R2)

        // System Buttons
        elements["select"] = ControlElement("select", "SELECT", 0.42f, 0.88f, 26.0f, bitmask = NativeConstants.PS2_BUTTON_SELECT)
        elements["start"] = ControlElement("start", "START", 0.58f, 0.88f, 26.0f, bitmask = NativeConstants.PS2_BUTTON_START)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density

        for (elem in elements.values) {
            val cx = elem.xNorm * width
            val cy = elem.yNorm * height
            val radius = elem.currentRadiusPx(density)
            val alpha = (elem.opacity * globalOpacity * 255).toInt().coerceIn(0, 255)

            // Highlight if active/pressed
            val isPressed = activePointers.containsValue(elem.id)
            if (isPressed) {
                paintPressed.alpha = alpha
                canvas.drawCircle(cx, cy, radius, paintPressed)
            } else {
                paintButton.color = Color.parseColor("#60222222")
                paintButton.alpha = alpha
                canvas.drawCircle(cx, cy, radius, paintButton)
            }

            // Draw Stick Knobs
            if (elem.id == "lstick") {
                val knobX = cx + lstickTouchX * radius
                val knobY = cy + lstickTouchY * radius
                paintButton.color = Color.parseColor("#A0444444")
                paintButton.alpha = alpha
                canvas.drawCircle(knobX, knobY, radius * 0.5f, paintButton)
            } else if (elem.id == "rstick") {
                val knobX = cx + rstickTouchX * radius
                val knobY = cy + rstickTouchY * radius
                paintButton.color = Color.parseColor("#A0444444")
                paintButton.alpha = alpha
                canvas.drawCircle(knobX, knobY, radius * 0.5f, paintButton)
            }

            // Text Label
            paintText.alpha = alpha
            paintText.textSize = radius * 0.5f
            val textY = cy - (paintText.descent() + paintText.ascent()) / 2.0f
            canvas.drawText(elem.label, cx, textY, paintText)

            // Selection Outline in Edit Mode
            if (isEditMode && elem.id == selectedElementId) {
                canvas.drawCircle(cx, cy, radius + 8.0f, paintSelected)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        val touchX = event.getX(pointerIndex)
        val touchY = event.getY(pointerIndex)

        if (isEditMode) {
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val hit = findElementAt(touchX, touchY, density)
                    if (hit != null) {
                        selectedElementId = hit.id
                        activePointers[pointerId] = hit.id
                        invalidate()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until event.pointerCount) {
                        val pid = event.getPointerId(i)
                        val elemId = activePointers[pid]
                        if (elemId != null && elemId == selectedElementId) {
                            val elem = elements[elemId]
                            if (elem != null) {
                                elem.xNorm = (event.getX(i) / width).coerceIn(0.02f, 0.98f)
                                elem.yNorm = (event.getY(i) / height).coerceIn(0.02f, 0.98f)
                                invalidate()
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    activePointers.remove(pointerId)
                    invalidate()
                }
            }
            return true
        }

        // Gameplay Input Mode
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val hit = findElementAt(touchX, touchY, density)
                if (hit != null) {
                    activePointers[pointerId] = hit.id
                    triggerHaptic()
                    updateTouchState(hit, touchX, touchY, density)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val elemId = activePointers[pid]
                    if (elemId != null) {
                        val elem = elements[elemId]
                        if (elem != null) {
                            updateTouchState(elem, event.getX(i), event.getY(i), density)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val releasedElemId = activePointers.remove(pointerId)
                if (releasedElemId == "lstick") {
                    lstickX = 0.0f
                    lstickY = 0.0f
                    lstickTouchX = 0.0f
                    lstickTouchY = 0.0f
                } else if (releasedElemId == "rstick") {
                    rstickX = 0.0f
                    rstickY = 0.0f
                    rstickTouchX = 0.0f
                    rstickTouchY = 0.0f
                }
            }
        }

        recalculateInputState()
        invalidate()
        return true
    }

    private fun updateTouchState(elem: ControlElement, touchX: Float, touchY: Float, density: Float) {
        val cx = elem.xNorm * width
        val cy = elem.yNorm * height
        val radius = elem.currentRadiusPx(density)

        val dx = touchX - cx
        val dy = touchY - cy
        val dist = hypot(dx, dy)

        if (elem.id == "lstick") {
            val normDist = (dist / radius).coerceIn(0.0f, 1.0f)
            val angle = atan2(dy, dx)
            lstickTouchX = normDist * cos(angle)
            lstickTouchY = normDist * sin(angle)
            lstickX = lstickTouchX
            lstickY = lstickTouchY
        } else if (elem.id == "rstick") {
            val normDist = (dist / radius).coerceIn(0.0f, 1.0f)
            val angle = atan2(dy, dx)
            rstickTouchX = normDist * cos(angle)
            rstickTouchY = normDist * sin(angle)
            rstickX = rstickTouchX
            rstickY = rstickTouchY
        }
    }

    private fun recalculateInputState() {
        var buttons = 0
        var l2 = 0
        var r2 = 0

        for (elemId in activePointers.values) {
            val elem = elements[elemId] ?: continue
            buttons = buttons or elem.bitmask
            if (elem.id == "l2") l2 = 255
            if (elem.id == "r2") r2 = 255
        }

        currentButtonsBitmask = buttons
        l2Pressure = l2
        r2Pressure = r2

        inputListener?.onInputChanged(
            currentButtonsBitmask,
            lstickX, lstickY,
            rstickX, rstickY,
            l2Pressure, r2Pressure
        )
    }

    private fun findElementAt(x: Float, y: Float, density: Float): ControlElement? {
        for (elem in elements.values) {
            val cx = elem.xNorm * width
            val cy = elem.yNorm * height
            val radius = elem.currentRadiusPx(density)
            if (hypot(x - cx, y - cy) <= radius) {
                return elem
            }
        }
        return null
    }

    fun getSelectedElement(): ControlElement? {
        val id = selectedElementId ?: return null
        return elements[id]
    }

    fun setSelectedElementScale(scale: Float) {
        val id = selectedElementId ?: return
        elements[id]?.let {
            it.scale = scale.coerceIn(0.5f, 2.5f)
            invalidate()
        }
    }

    fun setSelectedElementOpacity(opacity: Float) {
        val id = selectedElementId ?: return
        elements[id]?.let {
            it.opacity = opacity.coerceIn(0.1f, 1.0f)
            invalidate()
        }
    }

    private fun triggerHaptic() {
        if (!hapticsEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (_: Exception) {}
    }
}
