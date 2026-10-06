package com.ps2.shell

import android.view.KeyEvent
import android.view.MotionEvent

class ControllerInputMapper {

    // Default keycode -> PS2 bitmask map
    private val keyMapping = mutableMapOf(
        KeyEvent.KEYCODE_BUTTON_A to NativeConstants.PS2_BUTTON_CROSS,
        KeyEvent.KEYCODE_BUTTON_B to NativeConstants.PS2_BUTTON_CIRCLE,
        KeyEvent.KEYCODE_BUTTON_X to NativeConstants.PS2_BUTTON_SQUARE,
        KeyEvent.KEYCODE_BUTTON_Y to NativeConstants.PS2_BUTTON_TRIANGLE,
        KeyEvent.KEYCODE_BUTTON_L1 to NativeConstants.PS2_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1 to NativeConstants.PS2_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2 to NativeConstants.PS2_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_R2 to NativeConstants.PS2_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL to NativeConstants.PS2_BUTTON_L3,
        KeyEvent.KEYCODE_BUTTON_THUMBR to NativeConstants.PS2_BUTTON_R3,
        KeyEvent.KEYCODE_BUTTON_START to NativeConstants.PS2_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT to NativeConstants.PS2_BUTTON_SELECT,
        KeyEvent.KEYCODE_MENU to NativeConstants.PS2_BUTTON_SELECT,
        KeyEvent.KEYCODE_DPAD_UP to NativeConstants.PS2_BUTTON_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN to NativeConstants.PS2_BUTTON_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to NativeConstants.PS2_BUTTON_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to NativeConstants.PS2_BUTTON_DPAD_RIGHT
    )

    // Current State
    var buttonsBitmask = 0
        private set
    var lstickX = 0.0f
        private set
    var lstickY = 0.0f
        private set
    var rstickX = 0.0f
        private set
    var rstickY = 0.0f
        private set
    var l2Pressure = 0
        private set
    var r2Pressure = 0
        private set

    fun setKeyMapping(customMapping: Map<Int, Int>) {
        keyMapping.clear()
        keyMapping.putAll(customMapping)
    }

    fun getKeyMapping(): Map<Int, Int> = keyMapping.toMap()

    fun handleKeyCode(keyCode: Int, action: Int): Boolean {
        val ps2Button = keyMapping[keyCode] ?: return false

        when (action) {
            KeyEvent.ACTION_DOWN -> {
                buttonsBitmask = buttonsBitmask or ps2Button
                if (ps2Button == NativeConstants.PS2_BUTTON_L2) l2Pressure = 255
                if (ps2Button == NativeConstants.PS2_BUTTON_R2) r2Pressure = 255
            }
            KeyEvent.ACTION_UP -> {
                buttonsBitmask = buttonsBitmask and ps2Button.inv()
                if (ps2Button == NativeConstants.PS2_BUTTON_L2) l2Pressure = 0
                if (ps2Button == NativeConstants.PS2_BUTTON_R2) r2Pressure = 0
            }
        }
        return true
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        return handleKeyCode(event.keyCode, event.action)
    }

    fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and android.view.InputDevice.SOURCE_JOYSTICK != android.view.InputDevice.SOURCE_JOYSTICK &&
            event.source and android.view.InputDevice.SOURCE_GAMEPAD != android.view.InputDevice.SOURCE_GAMEPAD
        ) {
            return false
        }

        // Left Analog Stick
        lstickX = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_X))
        lstickY = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Y))

        // Right Analog Stick (Z / RZ or RX / RY)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z).let { if (it != 0.0f) it else event.getAxisValue(MotionEvent.AXIS_RX) }
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ).let { if (it != 0.0f) it else event.getAxisValue(MotionEvent.AXIS_RY) }
        rstickX = applyDeadzone(rx)
        rstickY = applyDeadzone(ry)

        // D-Pad Hat Axes
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        buttonsBitmask = buttonsBitmask and (NativeConstants.PS2_BUTTON_DPAD_UP or
                NativeConstants.PS2_BUTTON_DPAD_DOWN or
                NativeConstants.PS2_BUTTON_DPAD_LEFT or
                NativeConstants.PS2_BUTTON_DPAD_RIGHT).inv()

        if (hatX < -0.5f) buttonsBitmask = buttonsBitmask or NativeConstants.PS2_BUTTON_DPAD_LEFT
        if (hatX > 0.5f) buttonsBitmask = buttonsBitmask or NativeConstants.PS2_BUTTON_DPAD_RIGHT
        if (hatY < -0.5f) buttonsBitmask = buttonsBitmask or NativeConstants.PS2_BUTTON_DPAD_UP
        if (hatY > 0.5f) buttonsBitmask = buttonsBitmask or NativeConstants.PS2_BUTTON_DPAD_DOWN

        // Triggers L2 / R2
        val ltrig = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).let {
            if (it > 0.0f) it else event.getAxisValue(MotionEvent.AXIS_BRAKE)
        }
        val rtrig = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).let {
            if (it > 0.0f) it else event.getAxisValue(MotionEvent.AXIS_GAS)
        }

        l2Pressure = (ltrig * 255).toInt().coerceIn(0, 255)
        r2Pressure = (rtrig * 255).toInt().coerceIn(0, 255)

        if (l2Pressure > 10) buttonsBitmask = buttonsBitmask or NativeConstants.PS2_BUTTON_L2
        if (r2Pressure > 10) buttonsBitmask = buttonsBitmask or NativeConstants.PS2_BUTTON_R2

        return true
    }

    private fun applyDeadzone(value: Float, deadzone: Float = 0.15f): Float {
        return if (kotlin.math.abs(value) < deadzone) 0.0f else value
    }
}
