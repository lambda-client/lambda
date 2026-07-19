
package com.minato.module.modules.debug

import com.minato.module.Module
import com.minato.module.tag.ModuleTag

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
    // TODO: Revisit in 1.21.11+ — editable HUD added, may want to expose hidden debug renderers.
}