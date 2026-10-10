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

package com.lambda.module.modules.debug

import com.lambda.module.Module
import com.lambda.module.ModuleTag

@Suppress("unused")
object DebugRendererModule: Module(
    name = "Debug Renderer",
    description = "Renders debug information of minecraft internals",
    tag = ModuleTag.DEBUG,
) {
    private val waterDebugRenderer by setting("Water Debug Renderer", false)
    private val chunkBorderDebugRenderer by setting("Chunk Border Debug Renderer", false)
    private val heightmapDebugRenderer by setting("Heightmap Debug Renderer", false)
    private val collisionDebugRenderer by setting("Collision Debug Renderer", false)
    private val supportingBlockDebugRenderer by setting("Supporting Block Debug Renderer", false)
    private val neighborUpdateDebugRenderer by setting("Neighbor Update Debug Renderer", false)
    private val redstoneUpdateOrderDebugRenderer by setting("Redstone Update Order Debug Renderer", false)
    private val structureDebugRenderer by setting("Structure Debug Renderer", false)
    private val skyLightDebugRenderer by setting("Sky Light Debug Renderer", false)
    private val worldGenAttemptDebugRenderer by setting("World Gen Attempt Debug Renderer", false)
    private val blockOutlineDebugRenderer by setting("Block Outline Debug Renderer", false)
    private val chunkLoadingDebugRenderer by setting("Chunk Loading Debug Renderer", false)
    private val villageDebugRenderer by setting("Village Debug Renderer", false)
    private val villageSectionsDebugRenderer by setting("Village Sections Debug Renderer", false)
    private val beeDebugRenderer by setting("Bee Debug Renderer", false)
    private val raidCenterDebugRenderer by setting("Raid Center Debug Renderer", false)
    private val goalSelectorDebugRenderer by setting("Goal Selector Debug Renderer", false)
    private val gameTestDebugRenderer by setting("Game Test Debug Renderer", false)
    private val gameEventDebugRenderer by setting("Game Event Debug Renderer", false)
    private val lightDebugRenderer by setting("Light Debug Renderer", false)
//    private val breezeDebugRenderer by setting("Breeze Debug Renderer", false)
//    private val chunkDebugRenderer by setting("Chunk Debug Renderer", false)
//    private val octreeDebugRenderer by setting("Octree Debug Renderer", false)

    // ToDo: Was changed in 1.21.11 -> now we have editable HUD but we may want to add all the hidden options here eg pathfinder
//    @JvmStatic
//    fun render(
//        matrices: MatrixStack,
//        vertexConsumers: VertexConsumerProvider.Immediate,
//        cameraX: Double,
//        cameraY: Double, cameraZ: Double
//    ) {
//        val renderers = mc.worldRenderer.debugRenderer
//        mutableListOf<DebugRenderer.Renderer>().apply {
//            if (waterDebugRenderer) add(renderers.waterDebugRenderer)
//            if (chunkBorderDebugRenderer) add(renderers.chunkBorderDebugRenderer)
//            if (heightmapDebugRenderer) add(renderers.heightmapDebugRenderer)
//            if (collisionDebugRenderer) add(renderers.collisionDebugRenderer)
//            if (supportingBlockDebugRenderer) add(renderers.supportingBlockDebugRenderer)
//            if (neighborUpdateDebugRenderer) add(renderers.neighborUpdateDebugRenderer)
//            if (redstoneUpdateOrderDebugRenderer) add(renderers.redstoneUpdateOrderDebugRenderer)
//            if (structureDebugRenderer) add(renderers.structureDebugRenderer)
//            if (skyLightDebugRenderer) add(renderers.skyLightDebugRenderer)
//            if (worldGenAttemptDebugRenderer) add(renderers.worldGenAttemptDebugRenderer)
//            if (blockOutlineDebugRenderer) add(renderers.blockOutlineDebugRenderer)
//            if (chunkLoadingDebugRenderer) add(renderers.chunkLoadingDebugRenderer)
//            if (villageDebugRenderer) add(renderers.villageDebugRenderer)
//            if (villageSectionsDebugRenderer) add(renderers.villageSectionsDebugRenderer)
//            if (beeDebugRenderer) add(renderers.beeDebugRenderer)
//            if (raidCenterDebugRenderer) add(renderers.raidCenterDebugRenderer)
//            if (goalSelectorDebugRenderer) add(renderers.goalSelectorDebugRenderer)
//            if (gameTestDebugRenderer) add(renderers.gameTestDebugRenderer)
//            if (gameEventDebugRenderer) add(renderers.gameEventDebugRenderer)
//            if (lightDebugRenderer) add(renderers.lightDebugRenderer)
//        }.forEach { it.render(matrices, vertexConsumers, cameraX, cameraY, cameraZ) }
//    }
}