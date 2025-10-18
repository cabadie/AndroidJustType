package com.justtype.nativeapp.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Converts an analog stick into 8 directions with configurable angular widths.
 *
 * - Cardinals (UP/DOWN/LEFT/RIGHT) get `cardinalWidthDeg`
 * - Diagonals get `diagonalWidthDeg`
 *
 * Notes:
 * - Default widths (40°/50°) exactly tile the circle (4*40 + 4*50 = 360).
 * - If you choose widths that leave gaps or overlaps, we still return a
 *   direction by snapping to the nearest center.
 *
 * Wire: call handleMotionEvent() from onGenericMotionEvent, and optionally handleKeyEvent() for L3.
 */
class GamepadDirectionDetector(
    private val deadZone: Float = 0.25f,
    private val useLeftStick: Boolean = true,
    private val cardinalWidthDeg: Float = 40f,
    private val diagonalWidthDeg: Float = 50f,
    private val onDirectionChanged: (StickState?) -> Unit,
    private val onStickClick: ((Boolean) -> Unit)? = null
) {

    enum class Direction {
        UP, DOWN, LEFT, RIGHT,
        UP_RIGHT, UP_LEFT, DOWN_RIGHT, DOWN_LEFT
    }

    data class StickState(
        val direction: Direction,
        val magnitude: Float,
        /** 0° = RIGHT, 90° = UP, 180° = LEFT, 270° = DOWN */
        val angleDegrees: Float
    )

    private var lastDirection: Direction? = null

    fun handleMotionEvent(e: MotionEvent): Boolean {
        if (!isFromJoystick(e)) return false
        if (e.actionMasked != MotionEvent.ACTION_MOVE &&
            e.actionMasked != MotionEvent.ACTION_HOVER_MOVE) return false

        val device = e.device ?: return false
        val (axisX, axisY) = if (useLeftStick) {
            InputDeviceCompat.AXIS_LEFT_STICK
        } else {
            InputDeviceCompat.AXIS_RIGHT_STICK
        }

        val x = getCenteredAxis(e, device, axisX)
        val y = getCenteredAxis(e, device, axisY)
        val mag = hypot(x.toDouble(), y.toDouble()).toFloat()

        if (mag < deadZone) {
            if (lastDirection != null) {
                lastDirection = null
                onDirectionChanged.invoke(null)
            }
            return true
        }

        // Android Y+ is down; invert Y so UP is +90°
        val angleDeg = Math.toDegrees(atan2(-y, x).toDouble()).toFloat()
        val dir = angleToEightWay(angleDeg)

        if (dir != lastDirection) {
            lastDirection = dir
            onDirectionChanged.invoke(StickState(dir, mag, angleDeg))
        }
        return true
    }

    fun handleKeyEvent(e: KeyEvent): Boolean {
        if (onStickClick == null) return false
        if (!isFromGamepad(e)) return false

        val key = if (useLeftStick) KeyEvent.KEYCODE_BUTTON_THUMBL else KeyEvent.KEYCODE_BUTTON_THUMBR
        if (e.keyCode != key) return false

        onStickClick.invoke(e.action == KeyEvent.ACTION_DOWN)
        return true
    }

    // ----- Direction math -----

    private data class Sector(
        val dir: Direction,
        val center: Float,   // degrees in [0, 360)
        val halfWidth: Float // degrees
    ) {
        val start: Float get() = wrapDeg(center - halfWidth)
        val end: Float get() = wrapDeg(center + halfWidth)
        fun contains(a: Float): Boolean = inRangeWrap(a, start, end)
    }

    private fun angleToEightWay(angle: Float): Direction {
        val a = wrapDeg(angle)

        val sectors = listOf(
            Sector(Direction.RIGHT,      0f,   cardinalWidthDeg / 2),
            Sector(Direction.UP_RIGHT,  45f,   diagonalWidthDeg / 2),
            Sector(Direction.UP,        90f,   cardinalWidthDeg / 2),
            Sector(Direction.UP_LEFT,  135f,   diagonalWidthDeg / 2),
            Sector(Direction.LEFT,     180f,   cardinalWidthDeg / 2),
            Sector(Direction.DOWN_LEFT,225f,   diagonalWidthDeg / 2),
            Sector(Direction.DOWN,     270f,   cardinalWidthDeg / 2),
            Sector(Direction.DOWN_RIGHT,315f,  diagonalWidthDeg / 2),
        )

        // 1) Prefer sector membership (handles perfect tiling or overlaps)
        val hits = sectors.filter { it.contains(a) }
        if (hits.size == 1) return hits.first().dir
        if (hits.size > 1) {
            // Overlap: pick nearest center
            return hits.minBy { angDistDeg(a, it.center) }.dir
        }

        // 2) Gap: snap to nearest center so we always return something
        return sectors.minBy { angDistDeg(a, it.center) }.dir
    }

    // ----- Utilities -----

    private fun isFromJoystick(e: MotionEvent): Boolean {
        val src = e.source
        return (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
               (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
    }

    private fun isFromGamepad(e: KeyEvent): Boolean {
        val src = e.source
        return (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
               (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    private fun getCenteredAxis(event: MotionEvent, device: InputDevice, axis: Int): Float {
        val range = device.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        return if (kotlin.math.abs(value) > range.flat) value else 0f
    }

    private object InputDeviceCompat {
        val AXIS_LEFT_STICK = Pair(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)
        val AXIS_RIGHT_STICK = Pair(MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ) // swap to RX/RY if your device needs it
    }
}

private fun wrapDeg(d: Float): Float {
    var x = d % 360f
    if (x < 0) x += 360f
    return x
}

private fun inRangeWrap(a: Float, start: Float, end: Float): Boolean {
    return if (start <= end) a in start..end else (a >= start || a <= end)
}

private fun angDistDeg(a: Float, b: Float): Float {
    val diff = abs(wrapDeg(a) - wrapDeg(b))
    return min(diff, 360f - diff)
}
