package org.koreader.launcher.device.lights

import android.app.Activity
import android.util.Log
import org.koreader.launcher.device.LightController
import java.io.File

/**
 * Custom light controller for Nook Glowlight 3 (bnrv520) on custom ROMs.
 * Uses the LM3630A unified driver paths found in /sys/class/backlight/lm3630a_led/
 */
class NookGlowlight3Controller : LightController {
    companion object {
        private const val TAG = "Lights"
        private const val BRIGHTNESS_FILE = "/sys/class/backlight/lm3630a_led/brightness"
        private const val WARMTH_FILE = "/sys/class/backlight/lm3630a_led/color"
        
        // Calibrated to hardware limits found via sysfs
        private const val BRIGHTNESS_MAX = 100
        private const val WARMTH_MAX = 10
        private const val MIN = 0
    }

    override fun hasWarmth(): Boolean = true

    override fun getBrightness(activity: Activity): Int = readIntFromFile(BRIGHTNESS_FILE)

    override fun getWarmth(activity: Activity): Int {
        // Hardware reports 0 as warmest, 10 as coldest. 
        // We invert this so KOReader sees 10 as warmest.
        val rawValue = readIntFromFile(WARMTH_FILE)
        return (WARMTH_MAX - rawValue).coerceIn(MIN, WARMTH_MAX)
    }

    override fun setBrightness(activity: Activity, brightness: Int) {
        val value = brightness.coerceIn(MIN, BRIGHTNESS_MAX)
        Log.v(TAG, "Setting Nook brightness to $value")
        writeIntWithRoot(BRIGHTNESS_FILE, value)
    }

    override fun setWarmth(activity: Activity, warmth: Int) {
        // KOReader sends 10 for max warmth. 
        // We invert this to 0 for the hardware.
        val invertedValue = (WARMTH_MAX - warmth).coerceIn(MIN, WARMTH_MAX)
        Log.v(TAG, "Setting Nook warmth to $invertedValue (inverted from $warmth)")
        writeIntWithRoot(WARMTH_FILE, invertedValue)
    }

    override fun getMaxBrightness(): Int = BRIGHTNESS_MAX
    override fun getMaxWarmth(): Int = WARMTH_MAX

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
            // Check if we have write access, if not, try to chmod via SU
            if (!file.canWrite()) {
                Log.d(TAG, "Requesting root to unlock $path")
                Runtime.getRuntime().exec("su -c chmod 666 $path")
                // Small sleep to let the system update permissions
                Thread.sleep(80)
            }
            file.writeText(value.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $value to $path: ${e.message}")
            // Fallback: try direct echo via shell
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "echo $value > $path"))
            } catch (suEx: Exception) {
                Log.e(TAG, "Critical: Root echo failed: ${suEx.message}")
            }
        }
    }
}
