
package com.minato.interaction.managers

import net.minecraft.util.math.BlockPos

/**
 * Provides a [blockedPositions] list to inform other managers what positions should not be processed.
 *
 * Reasons a position could be blocked include pending interactions and or active interactions.
 */
interface PositionBlocking {
	val blockedPositions: List<BlockPos>
}