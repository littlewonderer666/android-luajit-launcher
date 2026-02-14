package org.koreader.launcher.device.lights

import android.app.Activity
import android.util.Log
import org.koreader.launcher.device.LightsInterface
import java.io.File

class NookGlowlight3Controller : LightsInterface {
    companion object {
        private const val TAG = "Lights"
        private const val BRIGHTNESS_FILE = "/sys/class/backlight/lm3630a_led/brightness"
        private const val WARMTH_FILE = "/sys/class/backlight/lm3630a_led/color"
        
        private const val BRIGHTNESS_MAX = 100
        private const val WARMTH_MAX = 10
        private const val MIN = 0
    }

    override fun hasWarmth(): Boolean = true
    override fun hasStandaloneWarmth(): Boolean = false // Required for newer interface

    override fun getBrightness(activity: Activity): Int = readIntFromFile(BRIGHTNESS_FILE)

    override fun getWarmth(activity: Activity): Int {
        val rawValue = readIntFromFile(WARMTH_FILE)
        return (WARMTH_MAX - rawValue).coerceIn(MIN, WARMTH_MAX)
    }

    override fun setBrightness(activity: Activity, brightness: Int) {
        val value = brightness.coerceIn(MIN, BRIGHTNESS_MAX)
        Log.v(TAG, "Setting Nook brightness to $value")
        writeIntWithRoot(BRIGHTNESS_FILE, value)
    }

    override fun setWarmth(activity: Activity, warmth: Int) {
        val invertedValue = (WARMTH_MAX - warmth).coerceIn(MIN, WARMTH_MAX)
        Log.v(TAG, "Setting Nook warmth to $invertedValue (inverted from $warmth)")
        writeIntWithRoot(WARMTH_FILE, invertedValue)
    }

    override fun getMinBrightness(): Int = MIN
    override fun getMaxBrightness(): Int = BRIGHTNESS_MAX
    override fun getMinWarmth(): Int = MIN
    override fun getMaxWarmth(): Int = WARMTH_MAX
    
    override fun enableFrontlightSwitch(activity: Activity): Int = 1

    override fun getPlatform(): String = "nook-gl3-root"
    override fun hasFallback(): Boolean = false
    override fun needsPermission(): Boolean = false 

    private fun readIntFromFile(path: String): Int {
        return try {
            File(path).readText().trim().toInt()
        } catch (e: Exception) {
            Log.w(TAG, "Error reading $path: ${e.message}")
            0
        }
    }

    private fun writeIntWithRoot(path: String, value: Int) {
        val file = File(path)
        try {
            if (!file.canWrite()) {
                Log.d(TAG, "Requesting root to unlock $path")
                Runtime.getRuntime().exec("su -c chmod 666 $path")
                Thread.sleep(100)
            }
            file.writeText(value.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed write to $path: ${e.message}")
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "echo $value > $path"))
            } catch (suEx: Exception) {
                Log.e(TAG, "Root echo failed: ${suEx.message}")
            }
        }
    }
}
