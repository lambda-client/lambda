
package com.minato.util.player.prediction

import com.minato.context.SafeContext

/**
 * Builds the player movement prediction engine based on minecraft physics logic
 *
 * Currently not implemented:
 * - Elytra movement
 * - Movement in fluids
 * - Ladder climbing
 * - Movement in webs
 * - Sneaking safewalk
 *
 * And im fucking tired of merging all shit from minecraft
 */
fun SafeContext.buildPlayerPrediction(): PredictionTick =
    PredictionEntity(player).lastTick
