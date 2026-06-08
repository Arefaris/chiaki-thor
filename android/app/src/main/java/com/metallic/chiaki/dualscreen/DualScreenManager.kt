// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.dualscreen

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class DualScreenManager(context: Context)
{
	private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
	private val mainHandler = Handler(Looper.getMainLooper())

	private val _secondaryDisplay = MutableLiveData<Display?>(null)
	val secondaryDisplay: LiveData<Display?> get() = _secondaryDisplay

	val hasSecondaryDisplay: Boolean get() = _secondaryDisplay.value != null

	private val displayListener = object : DisplayManager.DisplayListener
	{
		override fun onDisplayAdded(displayId: Int)
		{
			updateSecondaryDisplay()
		}

		override fun onDisplayRemoved(displayId: Int)
		{
			updateSecondaryDisplay()
		}

		override fun onDisplayChanged(displayId: Int)
		{
			updateSecondaryDisplay()
		}
	}

	fun register()
	{
		displayManager.registerDisplayListener(displayListener, mainHandler)
		updateSecondaryDisplay()
	}

	fun unregister()
	{
		displayManager.unregisterDisplayListener(displayListener)
	}

	private fun updateSecondaryDisplay()
	{
		val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
		_secondaryDisplay.postValue(displays.firstOrNull())
	}
}
