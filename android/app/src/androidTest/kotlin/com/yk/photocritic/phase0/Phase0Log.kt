package com.yk.photocritic.phase0

import android.util.Log

/** Phase 0 device checks (design file 50) log what they observe; read with `adb logcat -s PC-PHASE0`. */
internal fun observe(message: String) {
    Log.i("PC-PHASE0", message)
}
