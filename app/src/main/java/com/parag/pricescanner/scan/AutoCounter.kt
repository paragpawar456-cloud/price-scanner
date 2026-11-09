package com.parag.pricescanner.scan

/**
 * Turns a stream of per-frame detections into discrete "item scanned" events.
 *
 * An item is counted once it is seen in [stableFrames] consecutive frames. The
 * same item is only counted again after the camera has seen nothing for
 * [clearMillis] (the pack was moved away and the next one brought in), so
 * holding one packet in front of the camera never inflates the count.
 * A different item is counted as soon as it is stable.
 */
class AutoCounter(
    private val stableFrames: Int = 3,
    private val clearMillis: Long = 700L,
) {
    private var candidate: String? = null
    private var streak = 0
    private var lastCounted: String? = null
    private var lastSeenAt = Long.MIN_VALUE / 2

    /** @return the key to count on this frame, or null. */
    fun onFrame(key: String?, nowMillis: Long): String? {
        if (key == null) {
            if (nowMillis - lastSeenAt >= clearMillis) {
                lastCounted = null
                candidate = null
                streak = 0
            }
            return null
        }
        lastSeenAt = nowMillis
        if (key == candidate) streak++ else {
            candidate = key
            streak = 1
        }
        if (streak >= stableFrames && key != lastCounted) {
            lastCounted = key
            return key
        }
        return null
    }

    fun reset() {
        candidate = null
        streak = 0
        lastCounted = null
        lastSeenAt = Long.MIN_VALUE / 2
    }
}
