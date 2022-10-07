package com.lambda.client.module.modules.client

import com.lambda.client.event.events.BaritoneSettingsInitEvent
import com.lambda.client.event.events.RenderRadarEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.math.BlockPos

object Baritone : Module(
    name = "Baritone",
    description = "Configures Baritone settings",
    category = Category.CLIENT,
    showOnArray = false,
    alwaysEnabled = true
) {
/*
 * Baritone varibles can be found here.
 * https://github.com/cabaletta/baritone/blob/master/src/api/java/baritone/api/Settings.java
 */
    private val page by setting("Page", Page.BASIC)

    /* Basic */
    private val allowBreak by setting("Allow Break", true, { page == Page.BASIC }, description = "Allow Baritone to break blocks.")
    private val allowSprint by setting("Allow Sprint", true, { page == Page.BASIC }, description = "Allow Baritone to sprint.")
    private val allowPlace by setting("Allow Place", true, { page == Page.BASIC }, description = "Allow Baritone to place blocks.")
    private val allowInventory by setting("Allow Inventory", false, { page == Page.BASIC }, description = "Allow Baritone to move items in your inventory to your hotbar.")
    private val freeLook by setting("Free Look", true, { page == Page.BASIC }, description = "Move without having to force the client-sided rotations.")
    private val allowDownwardTunneling by setting("Downward Tunneling", true, { page == Page.BASIC }, description = "Allow mining blocks directly beneath you.")
    private val allowParkour by setting("Allow Parkour", true, { page == Page.BASIC })
    private val allowParkourPlace by setting("Allow Parkour Place", true, { allowParkour && page == Page.BASIC })

    /* Visual */
    private val showOnRadar by setting("Show Path on Radar", true, { page == Page.VISUAL }, description = "Show the current path on radar.")
    private val color by setting("Path Color", ColorHolder(32, 250, 32), visibility = { showOnRadar && page == Page.VISUAL }, description = "Path color for the radar.")
    private val renderGoal by setting("Render Goals", true, { page == Page.VISUAL }, description = "Render the goal.")
    private val renderGoalAnimated by setting("Render Goal Animated", true, { page == Page.VISUAL }, description = "Animate the rendered goal.")

    /* Fall */
    private val maxFallHeightNoWater by setting("Max Fall Height", 3, 3..5, 1, { page == Page.FALL }, description = "Distance baritone can fall without water.")
    private val allowWaterBucketFall by setting("Water Bucket Clutch", true, { page == Page.FALL }, description = "Uses a water bucket to get down quickly.")
    private val maxFallHeightBucket by setting("Max Bucket Height", 20, 10..250, 10, { allowWaterBucketFall && page == Page.FALL }, description = "Max height that baritone can use a water bucket.")

    /* AI */
    private val blockReachDistance by setting("Reach Distance", 4.5f, 1.0f..10.0f, 0.5f, { page == Page.AI }, description = "Max distance baritone can place blocks.")
    private val enterPortals by setting("Enter Portals", true, { page == Page.AI }, description = "Baritone will walk all the way into the portal, instead of stopping one block before.")
    /*private val avoidance by setting("Avoidance", false, { page == Page.AI }, description = "Enables the 4 avoidance settings. It's disabled by default because of the noticeable performance impact.")
    private val mobAvoidanceRadius by setting("Mob Avoidance Radius", 15, 0..65, 5, visibility = { avoidance && page == Page.AI }, description = "Distance to avoid mobs.")
    private val mobAvoidanceCoefficient by setting("Mob Avoidance Coefficient", 1.5, 0..5, 0.5, visibility = { avoidance && page == Page.AI }, description = "Set to 1.0 to effectively disable this feature. Set below 1.0 to go out of your way to walk near mobs.")
    private val mobSpawnerAvoidanceRadius by setting("Mob Spawner Avoidance Radius", 10, 0..60, 10, visibility = { avoidance && page == Page.AI }, description = "Distance to avoid mob spawners.")
    private val mobSpawnerAvoidanceCoefficient by setting("Mob Spawner Avoidance Coefficient", 1.5, 0..5, 0.5, visibility = { avoidance && page == Page.AI }, description = "Set to 1.0 to effectively disable this feature. Set below 1.0 to go out of your way to walk near mob spawners.")
    private val blockPlacementPenalty by setting("Block Placement Penalty", 20.0D, 0.0D..40.0D, 5.0D, { page == Page.AI }, description = "Decrease to make Baritone more often consider paths that would require placing blocks.")
    private val blockBreakAdditionalPenalty by setting("Block Break Additional Penalty", 2.0D, 0.0D..10.0D, 1.0D, { page == Page.AI }, description = "Lower chance to break blocks. This is a tiebreaker.")
    private val jumpPenalty by setting("Jump Penalty", 2.0D, 0.0D..10.0D, 1.0D, { page == Page.AI }, description = "Additional penalty for hitting the space bar (ascend, pillar, or parkour) because it uses hunger.")*/
    private val assumeWalkOnWater by setting("Assume Walk On Water", false, { page == Page.AI }, description = "Allow Baritone to assume it can walk on still water just like any other block. Requires jesus to be enabled.")
    private val failureTimeout by setting("Fail Timeout", 2, 1..20, 1, unit = "s", { page == Page.AI })

    private enum class Page {
        BASIC, VISUAL, FALL, AI
    }

    init {
        settingList.forEach {
            it.listeners.add { sync() }
        }

        listener<BaritoneSettingsInitEvent> {
            sync()
        }

        safeListener<RenderRadarEvent> {
            if (!showOnRadar || !BaritoneUtils.isPathing) return@safeListener

            val path = BaritoneUtils.primary?.pathingBehavior?.path ?: return@safeListener

            if (!path.isPresent) return@safeListener

            val playerOffset = Vec2d(player.position.x.toDouble(), player.position.z.toDouble())

            for (movement in path.get().movements()) {
                val positionFrom = getPos(movement.src, playerOffset, it.scale)
                val positionTo = getPos(movement.dest, playerOffset, it.scale)

                if (positionFrom.length() < it.radius && positionTo.length() < it.radius) {
                    RenderUtils2D.drawLine(it.vertexHelper, positionFrom, positionTo, color = color, lineWidth = 3f)
                }
            }
        }
    }

    private fun getPos(blockPos: BlockPos, playerOffset: Vec2d, scale: Float): Vec2d {
        return Vec2d(blockPos.x.toDouble(), blockPos.z.toDouble()).minus(playerOffset).div(scale.toDouble())
    }

    private fun sync() {
        BaritoneUtils.settings?.let {
            it.chatControl.value = false // enable chatControlAnyway if you want to use it
            it.allowBreak.value = allowBreak
            it.allowSprint.value = allowSprint
            it.allowPlace.value = allowPlace
            it.allowInventory.value = allowInventory
            /*it.blockPlacementPenalty.value = blockPlacementPenalty
            it.blockBreakAdditionalPenalty.value = blockBreakAdditionalPenalty
            it.jumpPenalty.value = jumpPenalty*/
            it.assumeWalkOnWater.value = assumeWalkOnWater
            /*it.avoidance.value = avoidance
            it.mobAvoidanceRadius.value = mobAvoidanceRadius
            it.mobAvoidanceCoefficient.value = mobAvoidanceCoefficient
            it.mobSpawnerAvoidanceRadius.value = mobSpawnerAvoidanceRadius
            it.mobSpawnerAvoidanceCoefficient.value = mobSpawnerAvoidanceCoefficient*/
            it.freeLook.value = freeLook
            it.maxFallHeightNoWater.value = maxFallHeightNoWater
            it.allowWaterBucketFall.value = allowWaterBucketFall
            it.maxFallHeightBucket.value = maxFallHeightBucket
            it.allowDownward.value = allowDownwardTunneling
            it.allowParkour.value = allowParkour
            it.allowParkourPlace.value = allowParkourPlace
            it.enterPortal.value = enterPortals
            it.renderGoal.value = renderGoal

            it.failureTimeoutMS.value = failureTimeout * 1000L
            it.blockReachDistance.value = blockReachDistance
        }
    }
}
