/*
 * Copyright 2024 Lambda
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

package com.lambda.config.groups

import net.minecraft.block.Block

interface BuildConfig {
    // General
    val pathing: Boolean
    val stayInRange: Boolean
    val collectDrops: Boolean

    // Breaking
    val rotateForBreak: Boolean
    val breakConfirmation: Boolean
    val maxPendingBreaks: Int
    val breaksPerTick: Int
    val breakWeakBlocks: Boolean
    val forceSilkTouch: Boolean
    val ignoredBlocks: Set<Block>

    // Placing
    val rotateForPlace: Boolean
    val placeConfirmation: Boolean
    val placeTimeout: Int
    val maxPendingPlacements: Int
    val placementsPerTick: Int
}
