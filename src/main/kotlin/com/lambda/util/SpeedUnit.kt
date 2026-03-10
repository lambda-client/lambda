/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.util

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