package dev.minimal.launcher

import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat

/**
 * A plain wallpaper in the same color as Home. It redraws when the phone switches between light and dark,
 * and reports a fixed color to the system so Material You keeps the colors it had when this was set.
 */
class MatchWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MatchEngine()

    private inner class MatchEngine : Engine() {
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { draw() }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            ContextCompat.registerReceiver(
                this@MatchWallpaperService, receiver,
                IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            notifyColorsChanged()
        }

        override fun onDestroy() {
            try { unregisterReceiver(receiver) } catch (_: Exception) { }
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) draw()
        }

        override fun onComputeColors(): WallpaperColors? {
            val seed = getSharedPreferences("minimal", MODE_PRIVATE).getInt("wp_seed", 0xFF6750A4.toInt())
            return WallpaperColors(Color.valueOf(seed), null, null)
        }

        private fun draw() {
            val holder = surfaceHolder
            var canvas: android.graphics.Canvas? = null
            try { canvas = holder.lockCanvas() } catch (_: Exception) { }
            if (canvas == null) return
            try {
                val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val black = dark && getSharedPreferences("minimal", MODE_PRIVATE).getBoolean("pitchBlack", false)
                canvas.drawColor(
                    if (black) Color.BLACK
                    else getColor(if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50),
                )
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
