package com.lambda.util.player.prediction

import com.lambda.context.SafeContext

/**
 * Builds the player movement prediction engine based on minecraft physics logic
 *
 * Currently not implemented:
 * - Elytra movement
 * - Movement in liquids
 * - Ladder climbing
 * - Movement in webs
 * - Sneaking safewalk
 *
 * And im fucking tired of merging all shit from minecraft
 */
fun SafeContext.buildPlayerPrediction(): PredictionTick =
    PredictionEntity(player).lastTick