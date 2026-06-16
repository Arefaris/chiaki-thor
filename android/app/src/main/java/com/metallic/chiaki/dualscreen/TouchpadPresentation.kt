// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.dualscreen

import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.metallic.chiaki.R
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.touchcontrols.ButtonHaptics
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables.combineLatest
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject

class TouchpadPresentation(outerContext: Context, display: Display)
	: Presentation(outerContext, display)
{
	companion object
	{
		private const val BACKGROUND_COLOR = 0xFF000000.toInt()
		private const val BUTTON_BAR_COLOR = 0xFF000000.toInt()
		private const val BUTTON_BAR_CORNER_RADIUS_DP = 16.0f
		private const val BUTTON_SIZE_DP = 56
		private const val BUTTON_PADDING_DP = 16
		private const val BUTTON_BAR_HEIGHT_DP = 88
	}

	private lateinit var touchpadView: SecondScreenTouchpadView
	private lateinit var buttonBar: LinearLayout

	private val hideUIRunnable = Runnable {
		if(!this::touchpadView.isInitialized || !this::buttonBar.isInitialized) return@Runnable
		touchpadView.setUiAlpha(0f)
		buttonBar.animate().alpha(0f).setDuration(500).start()
	}

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		if (this::touchpadView.isInitialized && this::buttonBar.isInitialized) {
			touchpadView.setUiAlpha(1f)
			buttonBar.animate().alpha(1f).setDuration(200).start()
			buttonBar.removeCallbacks(hideUIRunnable)
			buttonBar.postDelayed(hideUIRunnable, 10000L)
		}
		return super.dispatchTouchEvent(ev)
	}
	private val haptics by lazy { ButtonHaptics(context) }

	private var buttonState = ControllerState()
	private val buttonStateSubject: Subject<ControllerState>
		= BehaviorSubject.create<ControllerState>().also { it.onNext(buttonState) }

	val controllerState: Observable<ControllerState> by lazy {
		combineLatest(touchpadView.controllerState, buttonStateSubject) { a, b -> a or b }
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		// Fullscreen, no system UI
		window?.let { win ->
			win.setFlags(
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
			)
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
			{
				win.setDecorFitsSystemWindows(false)
			}
			else
			{
				@Suppress("DEPRECATION")
				win.decorView.systemUiVisibility = (
					View.SYSTEM_UI_FLAG_FULLSCREEN
					or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
					or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				)
			}
		}

		val density = context.resources.displayMetrics.density

		// Root layout
		val rootLayout = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setBackgroundColor(BACKGROUND_COLOR)
		}

		// Touchpad view (fills remaining space above button bar)
		touchpadView = SecondScreenTouchpadView(context)
		rootLayout.addView(touchpadView, LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
		))

		// Button bar
		val buttonBarHeight = (BUTTON_BAR_HEIGHT_DP * density).toInt()
		val cornerRadius = BUTTON_BAR_CORNER_RADIUS_DP * density

		buttonBar = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER
			setBackgroundColor(BUTTON_BAR_COLOR)

			// Rounded top corners
			outlineProvider = object : ViewOutlineProvider()
			{
				override fun getOutline(view: View, outline: Outline)
				{
					outline.setRoundRect(
						0, 0, view.width, (view.height + cornerRadius).toInt(),
						cornerRadius
					)
				}
			}
			clipToOutline = true
		}

		val buttonSize = (BUTTON_SIZE_DP * density).toInt()
		val buttonPadding = (BUTTON_PADDING_DP * density).toInt()
		val buttonSpacing = (32 * density).toInt()

		// Share button
		val shareButton = createButtonView(
			context,
			R.drawable.control_button_share,
			R.drawable.control_button_share_pressed,
			ControllerState.BUTTON_SHARE,
			buttonSize, buttonPadding
		)
		val shareParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
			marginEnd = buttonSpacing
		}
		buttonBar.addView(shareButton, shareParams)

		// PS button
		val psButton = createButtonView(
			context,
			R.drawable.control_button_home,
			R.drawable.control_button_home_pressed,
			ControllerState.BUTTON_PS,
			buttonSize, buttonPadding
		)
		val psParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
			marginStart = buttonSpacing
			marginEnd = buttonSpacing
		}
		buttonBar.addView(psButton, psParams)

		// Options button
		val optionsButton = createButtonView(
			context,
			R.drawable.control_button_options,
			R.drawable.control_button_options_pressed,
			ControllerState.BUTTON_OPTIONS,
			buttonSize, buttonPadding
		)
		val optionsParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
			marginStart = buttonSpacing
		}
		buttonBar.addView(optionsButton, optionsParams)

		rootLayout.addView(buttonBar, LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, buttonBarHeight
		))

		setContentView(rootLayout)

		// Start the auto-hide timer initially
		buttonBar.postDelayed(hideUIRunnable, 10000L)
	}

	private fun createButtonView(
		context: Context,
		idleDrawableRes: Int,
		pressedDrawableRes: Int,
		buttonMask: UInt,
		size: Int,
		padding: Int
	): ImageView
	{
		val idleDrawable = ContextCompat.getDrawable(context, idleDrawableRes)
		val pressedDrawable = ContextCompat.getDrawable(context, pressedDrawableRes)

		return ImageView(context).apply {
			setImageDrawable(idleDrawable)
			scaleType = ImageView.ScaleType.FIT_CENTER
			setPadding(padding, padding, padding, padding)
			isClickable = true

			setOnTouchListener { _, event ->
				when(event.actionMasked)
				{
					MotionEvent.ACTION_DOWN -> {
						setImageDrawable(pressedDrawable)
						haptics.trigger()
						buttonState = buttonState.copy().apply {
							buttons = buttons or buttonMask
						}
						buttonStateSubject.onNext(buttonState)
						true
					}
					MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
						setImageDrawable(idleDrawable)
						buttonState = buttonState.copy().apply {
							buttons = buttons and buttonMask.inv()
						}
						buttonStateSubject.onNext(buttonState)
						true
					}
					else -> false
				}
			}
		}
	}
}
