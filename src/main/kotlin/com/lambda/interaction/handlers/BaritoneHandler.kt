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

package com.lambda.interaction.handlers

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.Settings
import baritone.api.pathing.goals.Goal
import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group
import com.lambda.config.Tab
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.RotationSettings
import com.lambda.config.categories.LambdaCategory
import com.lambda.config.entries.Setting.Companion.onValueChange
import com.lambda.context.Automated
import com.lambda.util.BlockUtils.blockPos
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation

@Suppress("unused")
object BaritoneHandler : Config(
    "baritone",
	LambdaCategory
), Automated by AutomationConfig.DEFAULT {
    val baritoneAvailable by lazy {
        runCatching {
            Class.forName("baritone.api.BaritoneAPI")
            true
        }.getOrDefault(false)
    }

    private val baritone = if (baritoneAvailable) BaritoneAPI.getProvider() else null
    val baritoneSettings: Settings? = if (baritoneAvailable) BaritoneAPI.getSettings() else null

    // The new config system, as its using reflections to gather metadata about the settings before registering them, does not allow for nullability.
    // Partially because it would be a lot of work to account for all edge cases, but also because we use the by keyword to register ConfigBlock's as delegates.
    // This doesnt allow for nullability either.
//    val settings by baritoneSettings?.let { configBlock(BaritoneConfigSettings(this, it)) }
    private const val ROTATION_TAB = "Rotation"
    @Tab(ROTATION_TAB) override val rotationConfig by configBlock(RotationSettings(this))

    @JvmStatic
    val primary: IBaritone? = baritone?.primaryBaritone

    /**
     * Whether Baritone is currently pathing
     */
    val isPathing: Boolean
        get() = baritoneAvailable && primary?.pathingBehavior?.isPathing == true

    /**
     * Whether Baritone is active (pathing, calculating goal, etc.)
     */
    @JvmStatic val isActive: Boolean
        get() = baritoneAvailable &&
                (primary?.customGoalProcess?.isActive == true ||
                        primary?.pathingBehavior?.isPathing == true ||
                        primary?.pathingControlManager?.mostRecentInControl()?.orElse(null)?.isActive == true ||
                        primary?.elytraProcess?.isActive == true)

    /**
     * Sets the current Baritone goal and starts pathing
     */
    fun setGoalAndPath(goal: Goal) {
        if (!baritoneAvailable) return
        primary?.customGoalProcess?.setGoalAndPath(goal)
    }

    /**
     * Sets the current Baritone goal without starting pathing
     */
    fun setGoal(goal: Goal) {
        if (!baritoneAvailable || primary?.elytraProcess?.isLoaded != true) return
	    primary.customGoalProcess?.goal = goal
    }

    fun setGoalAndElytraPath(goal: Goal) {
        if (!baritoneAvailable || primary?.elytraProcess?.isLoaded != true) return
        primary.elytraProcess?.pathTo(goal)
    }

    /**
     * Force cancel Baritone
     */
    fun cancel() {
        if (!baritoneAvailable) return
        primary?.pathingBehavior?.cancelEverything()
        primary?.elytraProcess?.resetState()
    }

    class BaritoneConfigSettings(
	    override val c: Config,
	    private val bSettings: Settings
    ) : ConfigBlock {
        companion object {
            private const val GENERAL_TAB = "General"
            private const val PATHING_TAB = "Pathing"
            private const val BEHAVIOR_TAB = "Behavior"
            private const val BUILDING_TAB = "Building"
            private const val RENDERING_TAB = "Rendering"
            private const val ELYTRA_TAB = "Elytra"

            private const val CHAT_AND_CONTROL_GROUP = "Chat & Control"
            private const val WAYPOINTS_GROUP = "Waypoints"
            private const val ASSUMPTIONS_GROUP = "Assumptions"
            private const val MOVEMENT_GROUP = "Movement"
            private const val BLOCK_RULES_GROUP = "Block Rules"
            private const val MINING_AND_FARMING_GROUP = "Mining & Farming"
            private const val INTERACTION_GROUP = "Interaction"
            private const val PENALTIES_GROUP = "Penalties"
            private const val EXPLORATION_GROUP = "Exploration"
            private const val MISC_GROUP = "Misc"
            private const val RENDERING_GROUP = "Rendering"
            private const val RENDERING_COLORS_GROUP = "Rendering Colors"
            private const val RENDERING_SELECTION_GROUP = "Rendering Selection"
            private const val PATHING_PERFORMANCE_GROUP = "Pathing Performance"
            private const val PATHING_CORE_GROUP = "Pathing Core"
            private const val FOLLOW_GROUP = "Follow"
            private const val SCHEMATIC_GROUP = "Schematic"
        }

        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val logAsToast by c.setting("Log As Toast", bSettings.logAsToast.value).onValueChange { _, it -> bSettings.logAsToast.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val chatDebug by c.setting("Chat Debug", bSettings.chatDebug.value).onValueChange { _, it -> bSettings.chatDebug.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val chatControl by c.setting("Chat Control", bSettings.chatControl.value).onValueChange { _, it -> bSettings.chatControl.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val chatControlAnyway by c.setting("Chat Control Anyway", bSettings.chatControlAnyway.value).onValueChange { _, it -> bSettings.chatControlAnyway.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val prefixControl by c.setting("Prefix Control", bSettings.prefixControl.value).onValueChange { _, it -> bSettings.prefixControl.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val prefix by c.setting("Prefix", bSettings.prefix.value).onValueChange { _, it -> bSettings.prefix.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val shortBaritonePrefix by c.setting("Short Baritone Prefix", bSettings.shortBaritonePrefix.value).onValueChange { _, it -> bSettings.shortBaritonePrefix.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val useMessageTag by c.setting("Use Message Tag", bSettings.useMessageTag.value).onValueChange { _, it -> bSettings.useMessageTag.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val echoCommands by c.setting("Echo Commands", bSettings.echoCommands.value).onValueChange { _, it -> bSettings.echoCommands.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val censorCoordinates by c.setting("Censor Coordinates", bSettings.censorCoordinates.value).onValueChange { _, it -> bSettings.censorCoordinates.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val censorRanCommands by c.setting("Censor Ran Commands", bSettings.censorRanCommands.value).onValueChange { _, it -> bSettings.censorRanCommands.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val desktopNotifications by c.setting("Desktop Notifications", bSettings.desktopNotifications.value).onValueChange { _, it -> bSettings.desktopNotifications.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val notificationOnPathComplete by c.setting("Notification On Path Complete", bSettings.notificationOnPathComplete.value).onValueChange { _, it -> bSettings.notificationOnPathComplete.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val notificationOnFarmFail by c.setting("Notification On Farm Fail", bSettings.notificationOnFarmFail.value).onValueChange { _, it -> bSettings.notificationOnFarmFail.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val notificationOnBuildFinished by c.setting("Notification On Build Finished", bSettings.notificationOnBuildFinished.value).onValueChange { _, it -> bSettings.notificationOnBuildFinished.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val notificationOnExploreFinished by c.setting("Notification On Explore Finished", bSettings.notificationOnExploreFinished.value).onValueChange { _, it -> bSettings.notificationOnExploreFinished.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val notificationOnMineFail by c.setting("Notification On Mine Fail", bSettings.notificationOnMineFail.value).onValueChange { _, it -> bSettings.notificationOnMineFail.value = it }
        @Tab(GENERAL_TAB) @Group(CHAT_AND_CONTROL_GROUP) val verboseCommandExceptions by c.setting("Verbose Command Exceptions", bSettings.verboseCommandExceptions.value).onValueChange { _, it -> bSettings.verboseCommandExceptions.value = it }
        @Tab(GENERAL_TAB) @Group(WAYPOINTS_GROUP) val doBedWaypoints by c.setting("Do Bed Waypoints", bSettings.doBedWaypoints.value).onValueChange { _, it -> bSettings.doBedWaypoints.value = it }
        @Tab(GENERAL_TAB) @Group(WAYPOINTS_GROUP) val doDeathWaypoints by c.setting("Do Death Waypoints", bSettings.doDeathWaypoints.value).onValueChange { _, it -> bSettings.doDeathWaypoints.value = it }
        @Tab(GENERAL_TAB) @Group(MISC_GROUP) val antiCheatCompatibility by c.setting("Anti Cheat Compatibility", bSettings.antiCheatCompatibility.value).onValueChange { _, it -> bSettings.antiCheatCompatibility.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val pathingMaxChunkBorderFetch by c.setting("Pathing Max Chunk Border Fetch", bSettings.pathingMaxChunkBorderFetch.value, 0..64).onValueChange { _, it -> bSettings.pathingMaxChunkBorderFetch.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val pathingMapDefaultSize by c.setting("Pathing Map Default Size", bSettings.pathingMapDefaultSize.value, 0..2048).onValueChange { _, it -> bSettings.pathingMapDefaultSize.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val pathingMapLoadFactor by c.setting("Pathing Map Load Factor", bSettings.pathingMapLoadFactor.value, 0f..1f, 0.05f).onValueChange { _, it -> bSettings.pathingMapLoadFactor.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val distanceTrim by c.setting("Distance Trim", bSettings.distanceTrim.value).onValueChange { _, it -> bSettings.distanceTrim.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val simplifyUnloadedYCoord by c.setting("Simplify Unloaded Y Coord", bSettings.simplifyUnloadedYCoord.value).onValueChange { _, it -> bSettings.simplifyUnloadedYCoord.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val repackOnAnyBlockChange by c.setting("Repack On Any Block Change", bSettings.repackOnAnyBlockChange.value).onValueChange { _, it -> bSettings.repackOnAnyBlockChange.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val avoidance by c.setting("Avoidance", bSettings.avoidance.value).onValueChange { _, it -> bSettings.avoidance.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val pruneRegionsFromRAM by c.setting("Prune Regions From RAM", bSettings.pruneRegionsFromRAM.value).onValueChange { _, it -> bSettings.pruneRegionsFromRAM.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val backfill by c.setting("Backfill", bSettings.backfill.value).onValueChange { _, it -> bSettings.backfill.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val maxFallHeightNoWater by c.setting("Max Fall Height No Water", bSettings.maxFallHeightNoWater.value, 0..256).onValueChange { _, it -> bSettings.maxFallHeightNoWater.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val maxFallHeightBucket by c.setting("Max Fall Height Bucket", bSettings.maxFallHeightBucket.value, 0..256).onValueChange { _, it -> bSettings.maxFallHeightBucket.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val axisHeight by c.setting("Axis Height", bSettings.axisHeight.value, 0..256).onValueChange { _, it -> bSettings.axisHeight.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val disconnectOnArrival by c.setting("Disconnect On Arrival", bSettings.disconnectOnArrival.value).onValueChange { _, it -> bSettings.disconnectOnArrival.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val splicePath by c.setting("Splice Path", bSettings.splicePath.value).onValueChange { _, it -> bSettings.splicePath.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val maxPathHistoryLength by c.setting("Max Path History Length", bSettings.maxPathHistoryLength.value, 0..10000).onValueChange { _, it -> bSettings.maxPathHistoryLength.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val pathHistoryCutoffAmount by c.setting("Path History Cutoff Amount", bSettings.pathHistoryCutoffAmount.value, 0..10000).onValueChange { _, it -> bSettings.pathHistoryCutoffAmount.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val mineGoalUpdateInterval by c.setting("Mine Goal Update Interval", bSettings.mineGoalUpdateInterval.value, 0..10000).onValueChange { _, it -> bSettings.mineGoalUpdateInterval.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val maxCachedWorldScanCount by c.setting("Max Cached World Scan Count", bSettings.maxCachedWorldScanCount.value, 0..100000).onValueChange { _, it -> bSettings.maxCachedWorldScanCount.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val mineMaxOreLocationsCount by c.setting("Mine Max Ore Locations Count", bSettings.mineMaxOreLocationsCount.value, 0..100000).onValueChange { _, it -> bSettings.mineMaxOreLocationsCount.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val cachedChunksExpirySeconds by c.setting("Cached Chunks Expiry Seconds", bSettings.cachedChunksExpirySeconds.value, -1L..86400L).onValueChange { _, it -> bSettings.cachedChunksExpirySeconds.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val yLevelBoxSize by c.setting("Y Level Box Size", bSettings.yLevelBoxSize.value, 0.0..256.0).onValueChange { _, it -> bSettings.yLevelBoxSize.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val extendCacheOnThreshold by c.setting("Extend Cache On Threshold", bSettings.extendCacheOnThreshold.value).onValueChange { _, it -> bSettings.extendCacheOnThreshold.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val cancelOnGoalInvalidation by c.setting("Cancel On Goal Invalidation", bSettings.cancelOnGoalInvalidation.value).onValueChange { _, it -> bSettings.cancelOnGoalInvalidation.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val chunkCaching by c.setting("Chunk Caching", bSettings.chunkCaching.value).onValueChange { _, it -> bSettings.chunkCaching.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val chunkPackerQueueMaxSize by c.setting("Chunk Packer Queue Max Size", bSettings.chunkPackerQueueMaxSize.value, 0..10000).onValueChange { _, it -> bSettings.chunkPackerQueueMaxSize.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val pathThroughCachedOnly by c.setting("Path Through Cached Only", bSettings.pathThroughCachedOnly.value).onValueChange { _, it -> bSettings.pathThroughCachedOnly.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val blacklistClosestOnFailure by c.setting("Blacklist Closest On Failure", bSettings.blacklistClosestOnFailure.value).onValueChange { _, it -> bSettings.blacklistClosestOnFailure.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val pathCutoffMinimumLength by c.setting("Path Cutoff Minimum Length", bSettings.pathCutoffMinimumLength.value, 0..1000).onValueChange { _, it -> bSettings.pathCutoffMinimumLength.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val cutoffAtLoadBoundary by c.setting("Cutoff At Load Boundary", bSettings.cutoffAtLoadBoundary.value).onValueChange { _, it -> bSettings.cutoffAtLoadBoundary.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val minimumImprovementRepropagation by c.setting("Minimum Improvement Repropagation", bSettings.minimumImprovementRepropagation.value).onValueChange { _, it -> bSettings.minimumImprovementRepropagation.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_CORE_GROUP) val costVerificationLookahead by c.setting("Cost Verification Lookahead", bSettings.costVerificationLookahead.value, 0..1000).onValueChange { _, it -> bSettings.costVerificationLookahead.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_PERFORMANCE_GROUP) val primaryTimeoutMS by c.setting("Primary Timeout", bSettings.primaryTimeoutMS.value, 0L..600000L, unit = " ms").onValueChange { _, it -> bSettings.primaryTimeoutMS.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_PERFORMANCE_GROUP) val failureTimeoutMS by c.setting("Failure Timeout", bSettings.failureTimeoutMS.value, 0L..600000L, unit = " ms").onValueChange { _, it -> bSettings.failureTimeoutMS.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_PERFORMANCE_GROUP) val planAheadPrimaryTimeoutMS by c.setting("Plan Ahead Primary Timeout", bSettings.planAheadPrimaryTimeoutMS.value, 0L..600000L, unit = " ms").onValueChange { _, it -> bSettings.planAheadPrimaryTimeoutMS.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_PERFORMANCE_GROUP) val planAheadFailureTimeoutMS by c.setting("Plan Ahead Failure Timeout", bSettings.planAheadFailureTimeoutMS.value, 0L..600000L, unit = " ms").onValueChange { _, it -> bSettings.planAheadFailureTimeoutMS.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_PERFORMANCE_GROUP) val slowPath by c.setting("Slow Path", bSettings.slowPath.value).onValueChange { _, it -> bSettings.slowPath.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_PERFORMANCE_GROUP) val slowPathTimeDelayMS by c.setting("Slow Path Time Delay", bSettings.slowPathTimeDelayMS.value, 0L..600000L, unit = " ms").onValueChange { _, it -> bSettings.slowPathTimeDelayMS.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_PERFORMANCE_GROUP) val slowPathTimeoutMS by c.setting("Slow Path Timeout", bSettings.slowPathTimeoutMS.value, 0L..600000L, unit = " ms").onValueChange { _, it -> bSettings.slowPathTimeoutMS.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_PERFORMANCE_GROUP) val planningTickLookahead by c.setting("Planning Tick Lookahead", bSettings.planningTickLookahead.value, 0..200).onValueChange { _, it -> bSettings.planningTickLookahead.value = it }
        @Tab(PATHING_TAB) @Group(PATHING_PERFORMANCE_GROUP) val movementTimeoutTicks by c.setting("Movement Timeout Ticks", bSettings.movementTimeoutTicks.value, 0..2000).onValueChange { _, it -> bSettings.movementTimeoutTicks.value = it }
        @Tab(PATHING_TAB) @Group(FOLLOW_GROUP) val followOffsetDistance by c.setting("Follow Offset Distance", bSettings.followOffsetDistance.value, 0.0..256.0).onValueChange { _, it -> bSettings.followOffsetDistance.value = it }
        @Tab(PATHING_TAB) @Group(FOLLOW_GROUP) val followOffsetDirection by c.setting("Follow Offset Direction", bSettings.followOffsetDirection.value, -180f..180f, 1f).onValueChange { _, it -> bSettings.followOffsetDirection.value = it }
        @Tab(PATHING_TAB) @Group(FOLLOW_GROUP) val followRadius by c.setting("Follow Radius", bSettings.followRadius.value, 0..1000).onValueChange { _, it -> bSettings.followRadius.value = it }
        @Tab(PATHING_TAB) @Group(FOLLOW_GROUP) val followTargetMaxDistance by c.setting("Follow Target Max Distance", bSettings.followTargetMaxDistance.value, 0..10000).onValueChange { _, it -> bSettings.followTargetMaxDistance.value = it }
        @Tab(PATHING_TAB) @Group(FOLLOW_GROUP) val disableCompletionCheck by c.setting("Disable Completion Check", bSettings.disableCompletionCheck.value).onValueChange { _, it -> bSettings.disableCompletionCheck.value = it }
        @Tab(BEHAVIOR_TAB) @Group(ASSUMPTIONS_GROUP) val strictLiquidCheck by c.setting("Strict Liquid Check", bSettings.strictLiquidCheck.value).onValueChange { _, it -> bSettings.strictLiquidCheck.value = it }
        @Tab(BEHAVIOR_TAB) @Group(ASSUMPTIONS_GROUP) val assumeWalkOnWater by c.setting("Assume Walk On Water", bSettings.assumeWalkOnWater.value).onValueChange { _, it -> bSettings.assumeWalkOnWater.value = it }
        @Tab(BEHAVIOR_TAB) @Group(ASSUMPTIONS_GROUP) val assumeWalkOnLava by c.setting("Assume Walk On Lava", bSettings.assumeWalkOnLava.value).onValueChange { _, it -> bSettings.assumeWalkOnLava.value = it }
        @Tab(BEHAVIOR_TAB) @Group(ASSUMPTIONS_GROUP) val assumeStep by c.setting("Assume Step", bSettings.assumeStep.value).onValueChange { _, it -> bSettings.assumeStep.value = it }
        @Tab(BEHAVIOR_TAB) @Group(ASSUMPTIONS_GROUP) val assumeSafeWalk by c.setting("Assume Safe Walk", bSettings.assumeSafeWalk.value).onValueChange { _, it -> bSettings.assumeSafeWalk.value = it }
        @Tab(BEHAVIOR_TAB) @Group(ASSUMPTIONS_GROUP) val assumeExternalAutoTool by c.setting("Assume External Auto Tool", bSettings.assumeExternalAutoTool.value).onValueChange { _, it -> bSettings.assumeExternalAutoTool.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val allowParkourAscend by c.setting("Allow Parkour Ascend", bSettings.allowParkourAscend.value).onValueChange { _, it -> bSettings.allowParkourAscend.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val allowDiagonalDescend by c.setting("Allow Diagonal Descend", bSettings.allowDiagonalDescend.value).onValueChange { _, it -> bSettings.allowDiagonalDescend.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val allowDiagonalAscend by c.setting("Allow Diagonal Ascend", bSettings.allowDiagonalAscend.value).onValueChange { _, it -> bSettings.allowDiagonalAscend.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val allowDownward by c.setting("Allow Downward", bSettings.allowDownward.value).onValueChange { _, it -> bSettings.allowDownward.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val allowVines by c.setting("Allow Vines", bSettings.allowVines.value).onValueChange { _, it -> bSettings.allowVines.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val allowWalkOnBottomSlab by c.setting("Allow Walk On Bottom Slab", bSettings.allowWalkOnBottomSlab.value).onValueChange { _, it -> bSettings.allowWalkOnBottomSlab.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val allowParkour by c.setting("Allow Parkour", bSettings.allowParkour.value).onValueChange { _, it -> bSettings.allowParkour.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val allowParkourPlace by c.setting("Allow Parkour Place", bSettings.allowParkourPlace.value).onValueChange { _, it -> bSettings.allowParkourPlace.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val sprintAscends by c.setting("Sprint Ascends", bSettings.sprintAscends.value).onValueChange { _, it -> bSettings.sprintAscends.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val overshootTraverse by c.setting("Overshoot Traverse", bSettings.overshootTraverse.value).onValueChange { _, it -> bSettings.overshootTraverse.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val pauseMiningForFallingBlocks by c.setting("Pause Mining For Falling Blocks", bSettings.pauseMiningForFallingBlocks.value).onValueChange { _, it -> bSettings.pauseMiningForFallingBlocks.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val allowOvershootDiagonalDescend by c.setting("Allow Overshoot Diagonal Descend", bSettings.allowOvershootDiagonalDescend.value).onValueChange { _, it -> bSettings.allowOvershootDiagonalDescend.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MOVEMENT_GROUP) val sprintInWater by c.setting("Sprint In Water", bSettings.sprintInWater.value).onValueChange { _, it -> bSettings.sprintInWater.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val allowBreak by c.setting("Allow Break", bSettings.allowBreak.value).onValueChange { _, it -> bSettings.allowBreak.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val allowBreakAnyway by c.setting("Allow Break Anyway", bSettings.allowBreakAnyway.value).onValueChange { _, it -> bSettings.allowBreakAnyway.value = it.toList() }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val allowSprint by c.setting("Allow Sprint", bSettings.allowSprint.value).onValueChange { _, it -> bSettings.allowSprint.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val allowPlace by c.setting("Allow Place", bSettings.allowPlace.value).onValueChange { _, it -> bSettings.allowPlace.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val allowPlaceInFluidsSource by c.setting("Allow Place In Fluids Source", bSettings.allowPlaceInFluidsSource.value).onValueChange { _, it -> bSettings.allowPlaceInFluidsSource.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val allowPlaceInFluidsFlow by c.setting("Allow Place In Fluids Flow", bSettings.allowPlaceInFluidsFlow.value).onValueChange { _, it -> bSettings.allowPlaceInFluidsFlow.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val allowInventory by c.setting("Allow Inventory", bSettings.allowInventory.value).onValueChange { _, it -> bSettings.allowInventory.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val ticksBetweenInventoryMoves by c.setting("Ticks Between Inventory Moves", bSettings.ticksBetweenInventoryMoves.value, 0..20).onValueChange { _, it -> bSettings.ticksBetweenInventoryMoves.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val inventoryMoveOnlyIfStationary by c.setting("Inventory Move Only If Stationary", bSettings.inventoryMoveOnlyIfStationary.value).onValueChange { _, it -> bSettings.inventoryMoveOnlyIfStationary.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val autoTool by c.setting("Auto Tool", bSettings.autoTool.value).onValueChange { _, it -> bSettings.autoTool.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val allowWaterBucketFall by c.setting("Allow Water Bucket Fall", bSettings.allowWaterBucketFall.value).onValueChange { _, it -> bSettings.allowWaterBucketFall.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val allowJumpAtBuildLimit by c.setting("Allow Jump At Build Limit", bSettings.allowJumpAtBuildLimit.value).onValueChange { _, it -> bSettings.allowJumpAtBuildLimit.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val rightClickContainerOnArrival by c.setting("Right Click Container On Arrival", bSettings.rightClickContainerOnArrival.value).onValueChange { _, it -> bSettings.rightClickContainerOnArrival.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val enterPortal by c.setting("Enter Portal", bSettings.enterPortal.value).onValueChange { _, it -> bSettings.enterPortal.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val walkWhileBreaking by c.setting("Walk While Breaking", bSettings.walkWhileBreaking.value).onValueChange { _, it -> bSettings.walkWhileBreaking.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val useSwordToMine by c.setting("Use Sword To Mine", bSettings.useSwordToMine.value).onValueChange { _, it -> bSettings.useSwordToMine.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val rightClickSpeed by c.setting("Right Click Speed", bSettings.rightClickSpeed.value, 0..10).onValueChange { _, it -> bSettings.rightClickSpeed.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val blockReachDistance by c.setting("Block Reach Distance", bSettings.blockReachDistance.value, 0f..10f, 0.1f).onValueChange { _, it -> bSettings.blockReachDistance.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val blockBreakSpeed by c.setting("Block Break Speed", bSettings.blockBreakSpeed.value, 0..10).onValueChange { _, it -> bSettings.blockBreakSpeed.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val randomLooking113 by c.setting("Random Looking 1.13", bSettings.randomLooking113.value, 0.0..5.0, 0.01).onValueChange { _, it -> bSettings.randomLooking113.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val randomLooking by c.setting("Random Looking", bSettings.randomLooking.value, 0.0..1.0, 0.01).onValueChange { _, it -> bSettings.randomLooking.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val freeLook by c.setting("Free Look", bSettings.freeLook.value).onValueChange { _, it -> bSettings.freeLook.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val blockFreeLook by c.setting("Block Free Look", bSettings.blockFreeLook.value).onValueChange { _, it -> bSettings.blockFreeLook.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val elytraFreeLook by c.setting("Elytra Free Look", bSettings.elytraFreeLook.value).onValueChange { _, it -> bSettings.elytraFreeLook.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val smoothLook by c.setting("Smooth Look", bSettings.smoothLook.value).onValueChange { _, it -> bSettings.smoothLook.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val elytraSmoothLook by c.setting("Elytra Smooth Look", bSettings.elytraSmoothLook.value).onValueChange { _, it -> bSettings.elytraSmoothLook.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val smoothLookTicks by c.setting("Smooth Look Ticks", bSettings.smoothLookTicks.value, 0..200).onValueChange { _, it -> bSettings.smoothLookTicks.value = it }
        @Tab(BEHAVIOR_TAB) @Group(INTERACTION_GROUP) val remainWithExistingLookDirection by c.setting("Remain With Existing Look Direction", bSettings.remainWithExistingLookDirection.value).onValueChange { _, it -> bSettings.remainWithExistingLookDirection.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val blockPlacementPenalty by c.setting("Block Placement Penalty", bSettings.blockPlacementPenalty.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.blockPlacementPenalty.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val blockBreakAdditionalPenalty by c.setting("Block Break Additional Penalty", bSettings.blockBreakAdditionalPenalty.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.blockBreakAdditionalPenalty.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val jumpPenalty by c.setting("Jump Penalty", bSettings.jumpPenalty.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.jumpPenalty.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val walkOnWaterOnePenalty by c.setting("Walk On Water One Penalty", bSettings.walkOnWaterOnePenalty.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.walkOnWaterOnePenalty.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val avoidBreakingMultiplier by c.setting("Avoid Breaking Multiplier", bSettings.avoidBreakingMultiplier.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.avoidBreakingMultiplier.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val maxCostIncrease by c.setting("Max Cost Increase", bSettings.maxCostIncrease.value, 0.0..100.0).onValueChange { _, it -> bSettings.maxCostIncrease.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val backtrackCostFavoringCoefficient by c.setting("Backtrack Cost Favoring Coefficient", bSettings.backtrackCostFavoringCoefficient.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.backtrackCostFavoringCoefficient.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val mobSpawnerAvoidanceCoefficient by c.setting("Mob Spawner Avoidance Coefficient", bSettings.mobSpawnerAvoidanceCoefficient.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.mobSpawnerAvoidanceCoefficient.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val mobSpawnerAvoidanceRadius by c.setting("Mob Spawner Avoidance Radius", bSettings.mobSpawnerAvoidanceRadius.value, 0..1000).onValueChange { _, it -> bSettings.mobSpawnerAvoidanceRadius.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val mobAvoidanceCoefficient by c.setting("Mob Avoidance Coefficient", bSettings.mobAvoidanceCoefficient.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.mobAvoidanceCoefficient.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val mobAvoidanceRadius by c.setting("Mob Avoidance Radius", bSettings.mobAvoidanceRadius.value, 0..1000).onValueChange { _, it -> bSettings.mobAvoidanceRadius.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val pathCutoffFactor by c.setting("Path Cutoff Factor", bSettings.pathCutoffFactor.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.pathCutoffFactor.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val breakCorrectBlockPenaltyMultiplier by c.setting("Break Correct Block Penalty Multiplier", bSettings.breakCorrectBlockPenaltyMultiplier.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.breakCorrectBlockPenaltyMultiplier.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val placeIncorrectBlockPenaltyMultiplier by c.setting("Place Incorrect Block Penalty Multiplier", bSettings.placeIncorrectBlockPenaltyMultiplier.value, 0.0..100.0, 0.1).onValueChange { _, it -> bSettings.placeIncorrectBlockPenaltyMultiplier.value = it }
        @Tab(BEHAVIOR_TAB) @Group(PENALTIES_GROUP) val costHeuristic by c.setting("Cost Heuristic", bSettings.costHeuristic.value, 0.0..10.0, 0.001).onValueChange { _, it -> bSettings.costHeuristic.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val itemSaver by c.setting("Item Saver", bSettings.itemSaver.value).onValueChange { _, it -> bSettings.itemSaver.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val itemSaverThreshold by c.setting("Item Saver Threshold", bSettings.itemSaverThreshold.value, 0..100).onValueChange { _, it -> bSettings.itemSaverThreshold.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val preferSilkTouch by c.setting("Prefer Silk Touch", bSettings.preferSilkTouch.value).onValueChange { _, it -> bSettings.preferSilkTouch.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val mineScanDroppedItems by c.setting("Mine Scan Dropped Items", bSettings.mineScanDroppedItems.value).onValueChange { _, it -> bSettings.mineScanDroppedItems.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val mineDropLoiterDurationMSThanksLouca by c.setting("Mine Drop Loiter Duration", bSettings.mineDropLoiterDurationMSThanksLouca.value, 0L..600000L, unit = " ms").onValueChange { _, it -> bSettings.mineDropLoiterDurationMSThanksLouca.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val legitMine by c.setting("Legit Mine", bSettings.legitMine.value).onValueChange { _, it -> bSettings.legitMine.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val legitMineYLevel by c.setting("Legit Mine Y Level", bSettings.legitMineYLevel.value, 0..256).onValueChange { _, it -> bSettings.legitMineYLevel.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val legitMineIncludeDiagonals by c.setting("Legit Mine Include Diagonals", bSettings.legitMineIncludeDiagonals.value).onValueChange { _, it -> bSettings.legitMineIncludeDiagonals.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val forceInternalMining by c.setting("Force Internal Mining", bSettings.forceInternalMining.value).onValueChange { _, it -> bSettings.forceInternalMining.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val internalMiningAirException by c.setting("Internal Mining Air Exception", bSettings.internalMiningAirException.value).onValueChange { _, it -> bSettings.internalMiningAirException.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val minYLevelWhileMining by c.setting("Min Y Level While Mining", bSettings.minYLevelWhileMining.value, 0..2048).onValueChange { _, it -> bSettings.minYLevelWhileMining.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val maxYLevelWhileMining by c.setting("Max Y Level While Mining", bSettings.maxYLevelWhileMining.value, 0..2048).onValueChange { _, it -> bSettings.maxYLevelWhileMining.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val allowOnlyExposedOres by c.setting("Allow Only Exposed Ores", bSettings.allowOnlyExposedOres.value).onValueChange { _, it -> bSettings.allowOnlyExposedOres.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val allowOnlyExposedOresDistance by c.setting("Allow Only Exposed Ores Distance", bSettings.allowOnlyExposedOresDistance.value, 0..16).onValueChange { _, it -> bSettings.allowOnlyExposedOresDistance.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val replantCrops by c.setting("Replant Crops", bSettings.replantCrops.value).onValueChange { _, it -> bSettings.replantCrops.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val replantNetherWart by c.setting("Replant Nether Wart", bSettings.replantNetherWart.value).onValueChange { _, it -> bSettings.replantNetherWart.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val farmMaxScanSize by c.setting("Farm Max Scan Size", bSettings.farmMaxScanSize.value, 0..1024).onValueChange { _, it -> bSettings.farmMaxScanSize.value = it }
        @Tab(BEHAVIOR_TAB) @Group(MINING_AND_FARMING_GROUP) val considerPotionEffects by c.setting("Consider Potion Effects", bSettings.considerPotionEffects.value).onValueChange { _, it -> bSettings.considerPotionEffects.value = it }
        @Tab(BEHAVIOR_TAB) @Group(EXPLORATION_GROUP) val exploreForBlocks by c.setting("Explore For Blocks", bSettings.exploreForBlocks.value).onValueChange { _, it -> bSettings.exploreForBlocks.value = it }
        @Tab(BEHAVIOR_TAB) @Group(EXPLORATION_GROUP) val worldExploringChunkOffset by c.setting("World Exploring Chunk Offset", bSettings.worldExploringChunkOffset.value, 0..32).onValueChange { _, it -> bSettings.worldExploringChunkOffset.value = it }
        @Tab(BEHAVIOR_TAB) @Group(EXPLORATION_GROUP) val exploreChunkSetMinimumSize by c.setting("Explore Chunk Set Minimum Size", bSettings.exploreChunkSetMinimumSize.value, 0..10000).onValueChange { _, it -> bSettings.exploreChunkSetMinimumSize.value = it }
        @Tab(BEHAVIOR_TAB) @Group(EXPLORATION_GROUP) val exploreMaintainY by c.setting("Explore Maintain Y", bSettings.exploreMaintainY.value, 0..256).onValueChange { _, it -> bSettings.exploreMaintainY.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val buildInLayers by c.setting("Build In Layers", bSettings.buildInLayers.value).onValueChange { _, it -> bSettings.buildInLayers.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val layerOrder by c.setting("Layer Order", bSettings.layerOrder.value).onValueChange { _, it -> bSettings.layerOrder.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val layerHeight by c.setting("Layer Height", bSettings.layerHeight.value, 0..256).onValueChange { _, it -> bSettings.layerHeight.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val startAtLayer by c.setting("Start At Layer", bSettings.startAtLayer.value, 0..256).onValueChange { _, it -> bSettings.startAtLayer.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val skipFailedLayers by c.setting("Skip Failed Layers", bSettings.skipFailedLayers.value).onValueChange { _, it -> bSettings.skipFailedLayers.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val buildOnlySelection by c.setting("Build Only Selection", bSettings.buildOnlySelection.value).onValueChange { _, it -> bSettings.buildOnlySelection.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val buildRepeat by c.setting("Build Repeat", bSettings.buildRepeat.value.blockPos).onValueChange { _, it -> bSettings.buildRepeat.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val buildRepeatCount by c.setting("Build Repeat Count", bSettings.buildRepeatCount.value, 0..1000).onValueChange { _, it -> bSettings.buildRepeatCount.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val buildRepeatSneaky by c.setting("Build Repeat Sneaky", bSettings.buildRepeatSneaky.value).onValueChange { _, it -> bSettings.buildRepeatSneaky.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val breakFromAbove by c.setting("Break From Above", bSettings.breakFromAbove.value).onValueChange { _, it -> bSettings.breakFromAbove.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val goalBreakFromAbove by c.setting("Goal Break From Above", bSettings.goalBreakFromAbove.value).onValueChange { _, it -> bSettings.goalBreakFromAbove.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val mapArtMode by c.setting("Map Art Mode", bSettings.mapArtMode.value).onValueChange { _, it -> bSettings.mapArtMode.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val okIfWater by c.setting("Ok If Water", bSettings.okIfWater.value).onValueChange { _, it -> bSettings.okIfWater.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val incorrectSize by c.setting("Incorrect Size", bSettings.incorrectSize.value, 0..1000).onValueChange { _, it -> bSettings.incorrectSize.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val schematicOrientationX by c.setting("Schematic Orientation X", bSettings.schematicOrientationX.value).onValueChange { _, it -> bSettings.schematicOrientationX.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val schematicOrientationY by c.setting("Schematic Orientation Y", bSettings.schematicOrientationY.value).onValueChange { _, it -> bSettings.schematicOrientationY.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val schematicOrientationZ by c.setting("Schematic Orientation Z", bSettings.schematicOrientationZ.value).onValueChange { _, it -> bSettings.schematicOrientationZ.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val buildSchematicRotation: BlockRotation by c.setting("Build Schematic Rotation", bSettings.buildSchematicRotation.value).onValueChange { _, it -> bSettings.buildSchematicRotation.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val buildSchematicMirror: BlockMirror by c.setting("Build Schematic Mirror", bSettings.buildSchematicMirror.value).onValueChange { _, it -> bSettings.buildSchematicMirror.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val schematicFallbackExtension by c.setting("Schematic Fallback Extension", bSettings.schematicFallbackExtension.value).onValueChange { _, it -> bSettings.schematicFallbackExtension.value = it }
        @Tab(BUILDING_TAB) @Group(SCHEMATIC_GROUP) val builderTickScanRadius by c.setting("Builder Tick Scan Radius", bSettings.builderTickScanRadius.value, 0..64).onValueChange { _, it -> bSettings.builderTickScanRadius.value = it }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val acceptableThrowawayItems by c.setting("Acceptable Throwaway Items", bSettings.acceptableThrowawayItems.value).onValueChange { _, it -> bSettings.acceptableThrowawayItems.value = it.toList() }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val blocksToAvoid by c.setting("Blocks To Avoid", bSettings.blocksToAvoid.value).onValueChange { _, it -> bSettings.blocksToAvoid.value = it.toList() }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val blocksToDisallowBreaking by c.setting("Blocks To Disallow Breaking", bSettings.blocksToDisallowBreaking.value).onValueChange { _, it -> bSettings.blocksToDisallowBreaking.value = it.toList() }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val blocksToAvoidBreaking by c.setting("Blocks To Avoid Breaking", bSettings.blocksToAvoidBreaking.value).onValueChange { _, it -> bSettings.blocksToAvoidBreaking.value = it.toList() }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val buildIgnoreBlocks by c.setting("Build Ignore Blocks", bSettings.buildIgnoreBlocks.value).onValueChange { _, it -> bSettings.buildIgnoreBlocks.value = it.toList() }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val buildSkipBlocks by c.setting("Build Skip Blocks", bSettings.buildSkipBlocks.value).onValueChange { _, it -> bSettings.buildSkipBlocks.value = it.toList() }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val buildValidSubstitutes by c.setting("Build Valid Substitutes", bSettings.buildValidSubstitutes.value).onValueChange { _, it -> bSettings.buildValidSubstitutes.value = it.mapValues { (_, v) -> v.toList() } }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val buildSubstitutes by c.setting("Build Substitutes", bSettings.buildSubstitutes.value).onValueChange { _, it -> bSettings.buildSubstitutes.value = it.mapValues { (_, v) -> v.toList() } }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val okIfAir by c.setting("Ok If Air", bSettings.okIfAir.value).onValueChange { _, it -> bSettings.okIfAir.value = it.toList() }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val buildIgnoreExisting by c.setting("Build Ignore Existing", bSettings.buildIgnoreExisting.value).onValueChange { _, it -> bSettings.buildIgnoreExisting.value = it }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val buildIgnoreDirection by c.setting("Build Ignore Direction", bSettings.buildIgnoreDirection.value).onValueChange { _, it -> bSettings.buildIgnoreDirection.value = it }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val buildIgnoreProperties by c.setting("Build Ignore Properties", bSettings.buildIgnoreProperties.value).onValueChange { _, it -> bSettings.buildIgnoreProperties.value = it.toList() }
        @Tab(BUILDING_TAB) @Group(BLOCK_RULES_GROUP) val avoidUpdatingFallingBlocks by c.setting("Avoid Updating Falling Blocks", bSettings.avoidUpdatingFallingBlocks.value).onValueChange { _, it -> bSettings.avoidUpdatingFallingBlocks.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val renderPath by c.setting("Render Path", bSettings.renderPath.value).onValueChange { _, it -> bSettings.renderPath.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val renderPathAsLine by c.setting("Render Path As Line", bSettings.renderPathAsLine.value).onValueChange { _, it -> bSettings.renderPathAsLine.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val renderGoal by c.setting("Render Goal", bSettings.renderGoal.value).onValueChange { _, it -> bSettings.renderGoal.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val renderGoalAnimated by c.setting("Render Goal Animated", bSettings.renderGoalAnimated.value).onValueChange { _, it -> bSettings.renderGoalAnimated.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val renderGoalIgnoreDepth by c.setting("Render Goal Ignore Depth", bSettings.renderGoalIgnoreDepth.value).onValueChange { _, it -> bSettings.renderGoalIgnoreDepth.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val renderGoalXZBeacon by c.setting("Render Goal XZ Beacon", bSettings.renderGoalXZBeacon.value).onValueChange { _, it -> bSettings.renderGoalXZBeacon.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val pathRenderLineWidthPixels by c.setting("Path Render Line Width Pixels", bSettings.pathRenderLineWidthPixels.value, 0f..10f, 0.1f).onValueChange { _, it -> bSettings.pathRenderLineWidthPixels.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val goalRenderLineWidthPixels by c.setting("Goal Render Line Width Pixels", bSettings.goalRenderLineWidthPixels.value, 0f..10f, 0.1f).onValueChange { _, it -> bSettings.goalRenderLineWidthPixels.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val fadePath by c.setting("Fade Path", bSettings.fadePath.value).onValueChange { _, it -> bSettings.fadePath.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val renderCachedChunks by c.setting("Render Cached Chunks", bSettings.renderCachedChunks.value).onValueChange { _, it -> bSettings.renderCachedChunks.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val cachedChunksOpacity by c.setting("Cached Chunks Opacity", bSettings.cachedChunksOpacity.value, 0f..1f, 0.05f).onValueChange { _, it -> bSettings.cachedChunksOpacity.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val renderPathIgnoreDepth by c.setting("Render Path Ignore Depth", bSettings.renderPathIgnoreDepth.value).onValueChange { _, it -> bSettings.renderPathIgnoreDepth.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_GROUP) val renderSelectionBoxes by c.setting("Render Selection Boxes", bSettings.renderSelectionBoxes.value).onValueChange { _, it -> bSettings.renderSelectionBoxes.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_COLORS_GROUP) val colorCurrentPath by c.setting("Color Current Path", bSettings.colorCurrentPath.value).onValueChange { _, it -> bSettings.colorCurrentPath.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_COLORS_GROUP) val colorNextPath by c.setting("Color Next Path", bSettings.colorNextPath.value).onValueChange { _, it -> bSettings.colorNextPath.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_COLORS_GROUP) val colorBlocksToBreak by c.setting("Color Blocks To Break", bSettings.colorBlocksToBreak.value).onValueChange { _, it -> bSettings.colorBlocksToBreak.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_COLORS_GROUP) val colorBlocksToPlace by c.setting("Color Blocks To Place", bSettings.colorBlocksToPlace.value).onValueChange { _, it -> bSettings.colorBlocksToPlace.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_COLORS_GROUP) val colorBlocksToWalkInto by c.setting("Color Blocks To Walk Into", bSettings.colorBlocksToWalkInto.value).onValueChange { _, it -> bSettings.colorBlocksToWalkInto.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_COLORS_GROUP) val colorBestPathSoFar by c.setting("Color Best Path So Far", bSettings.colorBestPathSoFar.value).onValueChange { _, it -> bSettings.colorBestPathSoFar.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_COLORS_GROUP) val colorMostRecentConsidered by c.setting("Color Most Recent Considered", bSettings.colorMostRecentConsidered.value).onValueChange { _, it -> bSettings.colorMostRecentConsidered.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_COLORS_GROUP) val colorGoalBox by c.setting("Color Goal Box", bSettings.colorGoalBox.value).onValueChange { _, it -> bSettings.colorGoalBox.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_COLORS_GROUP) val colorInvertedGoalBox by c.setting("Color Inverted Goal Box", bSettings.colorInvertedGoalBox.value).onValueChange { _, it -> bSettings.colorInvertedGoalBox.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_SELECTION_GROUP) val renderSelection by c.setting("Render Selection", bSettings.renderSelection.value).onValueChange { _, it -> bSettings.renderSelection.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_SELECTION_GROUP) val colorSelection by c.setting("Color Selection", bSettings.colorSelection.value).onValueChange { _, it -> bSettings.colorSelection.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_SELECTION_GROUP) val colorSelectionPos1 by c.setting("Color Selection Pos1", bSettings.colorSelectionPos1.value).onValueChange { _, it -> bSettings.colorSelectionPos1.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_SELECTION_GROUP) val colorSelectionPos2 by c.setting("Color Selection Pos2", bSettings.colorSelectionPos2.value).onValueChange { _, it -> bSettings.colorSelectionPos2.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_SELECTION_GROUP) val selectionOpacity by c.setting("Selection Opacity", bSettings.selectionOpacity.value, 0f..1f, 0.05f).onValueChange { _, it -> bSettings.selectionOpacity.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_SELECTION_GROUP) val selectionLineWidth by c.setting("Selection Line Width", bSettings.selectionLineWidth.value, 0f..10f, 0.1f).onValueChange { _, it -> bSettings.selectionLineWidth.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_SELECTION_GROUP) val renderSelectionIgnoreDepth by c.setting("Render Selection Ignore Depth", bSettings.renderSelectionIgnoreDepth.value).onValueChange { _, it -> bSettings.renderSelectionIgnoreDepth.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_SELECTION_GROUP) val renderSelectionCorners by c.setting("Render Selection Corners", bSettings.renderSelectionCorners.value).onValueChange { _, it -> bSettings.renderSelectionCorners.value = it }
        @Tab(RENDERING_TAB) @Group(RENDERING_SELECTION_GROUP) val renderSelectionBoxesIgnoreDepth by c.setting("Render Selection Boxes Ignore Depth", bSettings.renderSelectionBoxesIgnoreDepth.value).onValueChange { _, it -> bSettings.renderSelectionBoxesIgnoreDepth.value = it }
        @Tab(ELYTRA_TAB) val elytraSimulationTicks by c.setting("Simulation Ticks", bSettings.elytraSimulationTicks.value, 0..200).onValueChange { _, it -> bSettings.elytraSimulationTicks.value = it }
        @Tab(ELYTRA_TAB) val elytraPitchRange by c.setting("Pitch Range", bSettings.elytraPitchRange.value, 0..90).onValueChange { _, it -> bSettings.elytraPitchRange.value = it }
        @Tab(ELYTRA_TAB) val elytraFireworkSpeed by c.setting("Firework Speed", bSettings.elytraFireworkSpeed.value, 0.0..3.0, 0.1).onValueChange { _, it -> bSettings.elytraFireworkSpeed.value = it }
        @Tab(ELYTRA_TAB) val elytraFireworkSetbackUseDelay by c.setting("Firework Setback Use Delay", bSettings.elytraFireworkSetbackUseDelay.value, 0..600).onValueChange { _, it -> bSettings.elytraFireworkSetbackUseDelay.value = it }
        @Tab(ELYTRA_TAB) val elytraMinimumAvoidance by c.setting("Minimum Avoidance", bSettings.elytraMinimumAvoidance.value, 0.0..10.0, 0.1).onValueChange { _, it -> bSettings.elytraMinimumAvoidance.value = it }
        @Tab(ELYTRA_TAB) val elytraConserveFireworks by c.setting("Conserve Fireworks", bSettings.elytraConserveFireworks.value).onValueChange { _, it -> bSettings.elytraConserveFireworks.value = it }
        @Tab(ELYTRA_TAB) val elytraRenderRaytraces by c.setting("Render Raytraces", bSettings.elytraRenderRaytraces.value).onValueChange { _, it -> bSettings.elytraRenderRaytraces.value = it }
        @Tab(ELYTRA_TAB) val elytraRenderHitboxRaytraces by c.setting("Render Hitbox Raytraces", bSettings.elytraRenderHitboxRaytraces.value).onValueChange { _, it -> bSettings.elytraRenderHitboxRaytraces.value = it }
        @Tab(ELYTRA_TAB) val elytraRenderSimulation by c.setting("Render Simulation", bSettings.elytraRenderSimulation.value).onValueChange { _, it -> bSettings.elytraRenderSimulation.value = it }
        @Tab(ELYTRA_TAB) val elytraAutoJump by c.setting("Auto Jump", bSettings.elytraAutoJump.value).onValueChange { _, it -> bSettings.elytraAutoJump.value = it }
        @Tab(ELYTRA_TAB) val elytraNetherSeed by c.setting("Nether Seed", bSettings.elytraNetherSeed.value, Long.MIN_VALUE..Long.MAX_VALUE).onValueChange { _, it -> bSettings.elytraNetherSeed.value = it }
        @Tab(ELYTRA_TAB) val elytraPredictTerrain by c.setting("Predict Terrain", bSettings.elytraPredictTerrain.value).onValueChange { _, it -> bSettings.elytraPredictTerrain.value = it }
        @Tab(ELYTRA_TAB) val elytraAutoSwap by c.setting("Auto Swap", bSettings.elytraAutoSwap.value).onValueChange { _, it -> bSettings.elytraAutoSwap.value = it }
        @Tab(ELYTRA_TAB) val elytraMinimumDurability by c.setting("Minimum Durability", bSettings.elytraMinimumDurability.value, 0..432).onValueChange { _, it -> bSettings.elytraMinimumDurability.value = it }
        @Tab(ELYTRA_TAB) val elytraMinFireworksBeforeLanding by c.setting("Min Fireworks Before Landing", bSettings.elytraMinFireworksBeforeLanding.value, 0..64).onValueChange { _, it -> bSettings.elytraMinFireworksBeforeLanding.value = it }
        @Tab(ELYTRA_TAB) val elytraAllowEmergencyLand by c.setting("Allow Emergency Land", bSettings.elytraAllowEmergencyLand.value).onValueChange { _, it -> bSettings.elytraAllowEmergencyLand.value = it }
        @Tab(ELYTRA_TAB) val elytraTimeBetweenCacheCullSecs by c.setting("Time Between Cache Cull Secs", bSettings.elytraTimeBetweenCacheCullSecs.value, 0L..86400L).onValueChange { _, it -> bSettings.elytraTimeBetweenCacheCullSecs.value = it }
        @Tab(ELYTRA_TAB) val elytraCacheCullDistance by c.setting("Cache Cull Distance", bSettings.elytraCacheCullDistance.value, 0..100000).onValueChange { _, it -> bSettings.elytraCacheCullDistance.value = it }
        @Tab(ELYTRA_TAB) val elytraAllowLandOnNetherFortress by c.setting("Allow Land On Nether Fortress", bSettings.elytraAllowLandOnNetherFortress.value).onValueChange { _, it -> bSettings.elytraAllowLandOnNetherFortress.value = it }
        @Tab(ELYTRA_TAB) val elytraTermsAccepted by c.setting("Terms Accepted", bSettings.elytraTermsAccepted.value).onValueChange { _, it -> bSettings.elytraTermsAccepted.value = it }
        @Tab(ELYTRA_TAB) val elytraChatSpam by c.setting("Chat Spam", bSettings.elytraChatSpam.value).onValueChange { _, it -> bSettings.elytraChatSpam.value = it }
    }
}