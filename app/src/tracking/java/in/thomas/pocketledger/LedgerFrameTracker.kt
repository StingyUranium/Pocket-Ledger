package `in`.thomas.pocketledger

import android.util.Log
import android.view.Window
import androidx.metrics.performance.JankStats
import java.util.concurrent.atomic.AtomicLong

/** Local debug diagnostics only. No financial values or persistent analytics. */
class LedgerFrameTracker(window: Window) {
    private val frames = AtomicLong()
    private val janky = AtomicLong()
    private val stats = JankStats.createAndTrack(window) { frame ->
        frames.incrementAndGet()
        if (frame.isJank) janky.incrementAndGet()
    }

    fun resume() { stats.isTrackingEnabled = true }
    fun pause() {
        stats.isTrackingEnabled = false
        val count = frames.getAndSet(0)
        val misses = janky.getAndSet(0)
        if (count > 0) Log.d("LedgerFrames", "Frames=$count heuristicJank=$misses")
    }
}
