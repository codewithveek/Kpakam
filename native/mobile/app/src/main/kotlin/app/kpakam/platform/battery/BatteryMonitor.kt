package app.kpakam.platform.battery

import android.content.Context
import android.os.BatteryManager
import app.kpakam.core.routing.BatteryState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where services read the battery. `null` means the reading is not available. */
fun interface BatterySource {
    fun current(): BatteryState?
}

/** BatteryManager adapter. */
@Singleton
class BatteryMonitor
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : BatterySource {
        private val manager = context.getSystemService(BatteryManager::class.java)

        override fun current(): BatteryState? {
            val percent = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: return null
            if (percent !in 0..FULL) return null
            return BatteryState(level = percent / FULL.toDouble(), charging = manager.isCharging)
        }

        private companion object {
            const val FULL = 100
        }
    }
