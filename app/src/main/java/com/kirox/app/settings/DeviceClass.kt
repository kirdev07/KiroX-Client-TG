package com.kirox.app.settings

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager

/** Whether this phone should get light effects: Android "low RAM" devices, under 3 GB RAM, or battery saver on. */
fun prefersLightEffects(context: Context): Boolean {
    val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memory = ActivityManager.MemoryInfo().also { activity?.getMemoryInfo(it) }
    val lowRam = activity?.isLowRamDevice == true || (memory.totalMem in 1 until 3L * 1024 * 1024 * 1024)
    val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    return lowRam || powerSave
}
