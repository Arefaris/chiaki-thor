// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.dualscreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.touchcontrols.ButtonHaptics
import com.metallic.chiaki.touchcontrols.Vector
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import kotlin.math.max

class SecondScreenTouchpadView(context: Context) : View(context)
{
	companion object
	{
		private const val BUTTON_PRESS_MAX_MOVE_DIST_DP = 32.0f
		private const val SHORT_BUTTON_PRESS_DURATION_MS = 200L
		private const val BUTTON_HOLD_DELAY_MS = 500L

		private const val BACKGROUND_COLOR = 0xFF000000.toInt()
		private const val DIVIDER_COLOR = 0x1AFFFFFF
		private const val TOUCH_INDICATOR_COLOR = 0x664FC3F7.toInt()
		private const val TOUCH_GLOW_COLOR = 0x334FC3F7
		private const val TOUCH_INDICATOR_RADIUS_DP = 24.0f
		private const val TOUCH_GLOW_RADIUS_DP = 48.0f
		private const val DIVIDER_WIDTH_DP = 1.0f
	}

	private val haptics = ButtonHaptics(context)
	private val density = resources.displayMetrics.density

	private val state: ControllerState = ControllerState()

	private val backgroundPaint = Paint().apply {
		color = BACKGROUND_COLOR
		style = Paint.Style.FILL
	}

	private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = DIVIDER_COLOR
		style = Paint.Style.STROKE
		strokeWidth = DIVIDER_WIDTH_DP * density
	}

	private var uiAlpha = 1f
	fun setUiAlpha(alpha: Float) {
		uiAlpha = alpha
		dividerPaint.alpha = (0x1A * alpha).toInt()
		invalidate()
	}

	private val touchIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = TOUCH_INDICATOR_COLOR
		style = Paint.Style.FILL
	}

	private val touchGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
	}

	inner class Touch(
		val stateId: UByte,
		private val startX: Float,
		private val startY: Float)
	{
		var lifted = false
		var currentX: Float = startX
		var currentY: Float = startY
		private var maxDist: Float = 0.0f
		val moveInsignificant: Boolean get() = maxDist < BUTTON_PRESS_MAX_MOVE_DIST_DP

		fun onMove(x: Float, y: Float)
		{
			currentX = x
			currentY = y
			val d = (Vector(x, y) - Vector(startX, startY)).length / density
			maxDist = max(d, maxDist)
		}

		val startButtonHoldRunnable = Runnable {
			if(!moveInsignificant || buttonHeld)
				return@Runnable
			haptics.trigger(true)
			state.buttons = state.buttons or ControllerState.BUTTON_TOUCHPAD
			buttonHeld = true
			triggerStateChanged()
		}
	}
	private val pointerTouches = mutableMapOf<Int, Touch>()

	private val stateSubject: Subject<ControllerState>
		= BehaviorSubject.create<ControllerState>().also { it.onNext(state) }
	val controllerState: Observable<ControllerState> get() = stateSubject

	private var shortPressingTouches = listOf<Touch>()
	private val shortButtonPressLiftRunnable = Runnable {
		state.buttons = state.buttons and ControllerState.BUTTON_TOUCHPAD.inv()
		shortPressingTouches.forEach {
			state.stopTouch(it.stateId)
		}
		shortPressingTouches = listOf()
		triggerStateChanged()
	}

	private var buttonHeld = false

	init
	{
		isClickable = true
	}

	override fun onDraw(canvas: Canvas)
	{
		super.onDraw(canvas)

		// Background
		canvas.drawColor(BACKGROUND_COLOR)

		if (uiAlpha > 0f) {
			// Center vertical dividing line
			val centerX = width / 2.0f
			canvas.drawLine(centerX, 0f, centerX, height.toFloat(), dividerPaint)
		}

		// Touch indicators
		val indicatorRadius = TOUCH_INDICATOR_RADIUS_DP * density
		val glowRadius = TOUCH_GLOW_RADIUS_DP * density
		for(touch in pointerTouches.values)
		{
			if(touch.lifted)
				continue

			// Glow effect
			touchGlowPaint.shader = RadialGradient(
				touch.currentX, touch.currentY, glowRadius,
				TOUCH_GLOW_COLOR, Color.TRANSPARENT,
				Shader.TileMode.CLAMP
			)
			canvas.drawCircle(touch.currentX, touch.currentY, glowRadius, touchGlowPaint)

			// Solid indicator
			canvas.drawCircle(touch.currentX, touch.currentY, indicatorRadius, touchIndicatorPaint)
		}
	}

	private fun touchX(event: MotionEvent, index: Int): UShort =
		maxOf(0U.toUShort(), minOf((ControllerState.TOUCHPAD_WIDTH - 1u).toUShort(),
			(ControllerState.TOUCHPAD_WIDTH.toFloat() * event.getX(index) / width.toFloat()).toUInt().toUShort()))

	private fun touchY(event: MotionEvent, index: Int): UShort =
		maxOf(0U.toUShort(), minOf((ControllerState.TOUCHPAD_HEIGHT - 1u).toUShort(),
			(ControllerState.TOUCHPAD_HEIGHT.toFloat() * event.getY(index) / height.toFloat()).toUInt().toUShort()))

	override fun onTouchEvent(event: MotionEvent): Boolean
	{
		when(event.actionMasked)
		{
			MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
				state.startTouch(touchX(event, event.actionIndex), touchY(event, event.actionIndex))?.let {
					haptics.trigger()
					val touch = Touch(it, event.getX(event.actionIndex), event.getY(event.actionIndex))
					pointerTouches[event.getPointerId(event.actionIndex)] = touch
					if(!buttonHeld)
						postDelayed(touch.startButtonHoldRunnable, BUTTON_HOLD_DELAY_MS)
					triggerStateChanged()
				}
			}
			MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
				pointerTouches.remove(event.getPointerId(event.actionIndex))?.let {
					removeCallbacks(it.startButtonHoldRunnable)
					when
					{
						buttonHeld ->
						{
							buttonHeld = false
							state.buttons = state.buttons and ControllerState.BUTTON_TOUCHPAD.inv()
							state.stopTouch(it.stateId)
						}
						it.moveInsignificant -> triggerShortButtonPress(it)
						else -> state.stopTouch(it.stateId)
					}
					triggerStateChanged()
				}
			}
			MotionEvent.ACTION_MOVE -> {
				val changed = pointerTouches.entries.fold(false) { acc, it ->
					val index = event.findPointerIndex(it.key)
					if(index < 0)
						acc
					else
					{
						it.value.onMove(event.getX(index), event.getY(index))
						acc || state.setTouchPos(it.value.stateId, touchX(event, index), touchY(event, index))
					}
				}
				if(changed)
					triggerStateChanged()
			}
		}
		return true
	}

	private fun triggerShortButtonPress(touch: Touch)
	{
		shortPressingTouches = shortPressingTouches + listOf(touch)
		removeCallbacks(shortButtonPressLiftRunnable)
		state.buttons = state.buttons or ControllerState.BUTTON_TOUCHPAD
		postDelayed(shortButtonPressLiftRunnable, SHORT_BUTTON_PRESS_DURATION_MS)
	}

	private fun triggerStateChanged()
	{
		invalidate()
		stateSubject.onNext(state)
	}
}
