
package com.minato.util

enum class SpeedUnit(override val displayName: String, val conversionFromBlocksPerTick: Float, val unitName: String) : NamedEnum {
    BlocksPerTick("Blocks Per Tick",1.0F, "bpt"),
    BlocksPerSecond("Blocks Per Second", 0.05F, "bps"),
    MetersPerSecond("Meters Per Second", 0.05F, "ms"),
    KilometersPerHour("Kilometers Per Hour", 0.277778F * 0.05F, "kmh"),
    MilesPerHour("Miles Per Hour", 0.44704F * 0.05F, "mph"),
    Boeing787AtTakeoffSpeed("Boeing 787 Takeoff Speed", 84.9F * 0.05F, "Boeing 787s");

    /**
     * Converts the given speed in blocks per tick to the unit of this SpeedUnit.
     * @param speedInBlocksPerTick
     * @return
     */
    fun convertFromMinecraft(speedInBlocksPerTick: Double): Double {
        return speedInBlocksPerTick / conversionFromBlocksPerTick
    }
}