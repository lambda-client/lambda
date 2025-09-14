/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.Settings
import baritone.api.pathing.goals.Goal
import com.lambda.config.Configurable
import com.lambda.config.configurations.LambdaConfig
import com.lambda.config.groups.RotationSettings
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.NamedEnum

object BaritoneManager : Configurable(LambdaConfig) {
    override val name = "baritone"

    private val baritone = BaritoneAPI.getProvider()
    val baritoneSettings: Settings = BaritoneAPI.getSettings()

    @JvmStatic
    val primary: IBaritone = baritone.primaryBaritone

    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Rotation("Rotation"),
        Pathing("Pathing"),
        Behavior("Behavior"),
        Building("Building"),
        Rendering("Rendering"),
        Elytra("Elytra")
    }

    private enum class SubGroup(override val displayName: String) : NamedEnum {
        ChatAndControl("Chat & Control"),
        Waypoints("Waypoints"),
        Assumptions("Assumptions"),
        Movement("Movement"),
        BlockRules("Block Rules"),
        MiningAndFarming("Mining & Farming"),
        Interaction("Interaction"),
        Penalties("Penalties"),
        Exploration("Exploration"),
        Misc("Misc"),
        Rendering("Rendering"),
        RenderingColors("Rendering Colors"),
        RenderingSelection("Rendering Selection"),
        PathingPerformance("Pathing Performance"),
        PathingCore("Pathing Core"),
        Follow("Follow"),
        Schematic("Schematic")
    }

    val rotation: RotationSettings

    init {
        // ToDo: Dont actually save the settings as its duplicate data
        with(baritoneSettings) {

            // GENERAL
            setting("Log As Toast", logAsToast.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> logAsToast.value = it }
            setting("Chat Debug", chatDebug.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> chatDebug.value = it }
            setting("Chat Control", chatControl.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> chatControl.value = it }
            setting("Chat Control Anyway", chatControlAnyway.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> chatControlAnyway.value = it }
            setting("Prefix Control", prefixControl.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> prefixControl.value = it }
            setting("Prefix", prefix.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> prefix.value = it }
            setting("Short Baritone Prefix", shortBaritonePrefix.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> shortBaritonePrefix.value = it }
            setting("Use Message Tag", useMessageTag.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> useMessageTag.value = it }
            setting("Echo Commands", echoCommands.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> echoCommands.value = it }
            setting("Censor Coordinates", censorCoordinates.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> censorCoordinates.value = it }
            setting("Censor Ran Commands", censorRanCommands.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> censorRanCommands.value = it }
            setting("Desktop Notifications", desktopNotifications.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> desktopNotifications.value = it }
            setting("Notification On Path Complete", notificationOnPathComplete.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> notificationOnPathComplete.value = it }
            setting("Notification On Farm Fail", notificationOnFarmFail.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> notificationOnFarmFail.value = it }
            setting("Notification On Build Finished", notificationOnBuildFinished.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> notificationOnBuildFinished.value = it }
            setting("Notification On Explore Finished", notificationOnExploreFinished.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> notificationOnExploreFinished.value = it }
            setting("Notification On Mine Fail", notificationOnMineFail.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> notificationOnMineFail.value = it }
            setting("Verbose Command Exceptions", verboseCommandExceptions.value).group(Group.General, SubGroup.ChatAndControl).onValueChange { _, it -> verboseCommandExceptions.value = it }

            setting("Do Bed Waypoints", doBedWaypoints.value).group(Group.General, SubGroup.Waypoints).onValueChange { _, it -> doBedWaypoints.value = it }
            setting("Do Death Waypoints", doDeathWaypoints.value).group(Group.General, SubGroup.Waypoints).onValueChange { _, it -> doDeathWaypoints.value = it }

            setting("Anti Cheat Compatibility", antiCheatCompatibility.value).group(Group.General, SubGroup.Misc).onValueChange { _, it -> antiCheatCompatibility.value = it }

            // ROTATION
            rotation = RotationSettings(this@BaritoneManager, Group.Rotation)

            // PATHING
            setting("Pathing Max Chunk Border Fetch", pathingMaxChunkBorderFetch.value, 0..64).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> pathingMaxChunkBorderFetch.value = it }
            setting("Pathing Map Default Size", pathingMapDefaultSize.value, 0..2048).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> pathingMapDefaultSize.value = it }
            setting("Pathing Map Load Factor", pathingMapLoadFactor.value, 0f..1f, 0.05f).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> pathingMapLoadFactor.value = it }
            setting("Distance Trim", distanceTrim.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> distanceTrim.value = it }
            setting("Simplify Unloaded Y Coord", simplifyUnloadedYCoord.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> simplifyUnloadedYCoord.value = it }
            setting("Repack On Any Block Change", repackOnAnyBlockChange.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> repackOnAnyBlockChange.value = it }
            setting("Avoidance", avoidance.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> avoidance.value = it }
            setting("Prune Regions From RAM", pruneRegionsFromRAM.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> pruneRegionsFromRAM.value = it }
            setting("Backfill", backfill.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> backfill.value = it }
            setting("Max Fall Height No Water", maxFallHeightNoWater.value, 0..256).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> maxFallHeightNoWater.value = it }
            setting("Max Fall Height Bucket", maxFallHeightBucket.value, 0..256).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> maxFallHeightBucket.value = it }
            setting("Axis Height", axisHeight.value, 0..256).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> axisHeight.value = it }
            setting("Disconnect On Arrival", disconnectOnArrival.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> disconnectOnArrival.value = it }
            setting("Splice Path", splicePath.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> splicePath.value = it }
            setting("Max Path History Length", maxPathHistoryLength.value, 0..10000).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> maxPathHistoryLength.value = it }
            setting("Path History Cutoff Amount", pathHistoryCutoffAmount.value, 0..10000).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> pathHistoryCutoffAmount.value = it }
            setting("Mine Goal Update Interval", mineGoalUpdateInterval.value, 0..10000).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> mineGoalUpdateInterval.value = it }
            setting("Max Cached World Scan Count", maxCachedWorldScanCount.value, 0..100000).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> maxCachedWorldScanCount.value = it }
            setting("Mine Max Ore Locations Count", mineMaxOreLocationsCount.value, 0..100000).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> mineMaxOreLocationsCount.value = it }
            setting("Cached Chunks Expiry Seconds", cachedChunksExpirySeconds.value, 0L..86400L).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> cachedChunksExpirySeconds.value = it }
            setting("Y Level Box Size", yLevelBoxSize.value, 0.0..256.0).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> yLevelBoxSize.value = it }
            setting("Extend Cache On Threshold", extendCacheOnThreshold.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> extendCacheOnThreshold.value = it }
            setting("Cancel On Goal Invalidation", cancelOnGoalInvalidation.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> cancelOnGoalInvalidation.value = it }
            setting("Chunk Caching", chunkCaching.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> chunkCaching.value = it }
            setting("Chunk Packer Queue Max Size", chunkPackerQueueMaxSize.value, 0..10000).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> chunkPackerQueueMaxSize.value = it }
            setting("Path Through Cached Only", pathThroughCachedOnly.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> pathThroughCachedOnly.value = it }
            setting("Blacklist Closest On Failure", blacklistClosestOnFailure.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> blacklistClosestOnFailure.value = it }
            setting("Path Cutoff Minimum Length", pathCutoffMinimumLength.value, 0..1000).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> pathCutoffMinimumLength.value = it }
            setting("Cutoff At Load Boundary", cutoffAtLoadBoundary.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> cutoffAtLoadBoundary.value = it }
            setting("Minimum Improvement Repropagation", minimumImprovementRepropagation.value).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> minimumImprovementRepropagation.value = it }
            setting("Cost Verification Lookahead", costVerificationLookahead.value, 0..1000).group(Group.Pathing, SubGroup.PathingCore).onValueChange { _, it -> costVerificationLookahead.value = it }

            setting("Primary Timeout", primaryTimeoutMS.value, 0L..600000L, unit = " ms").group(Group.Pathing, SubGroup.PathingPerformance).onValueChange { _, it -> primaryTimeoutMS.value = it }
            setting("Failure Timeout", failureTimeoutMS.value, 0L..600000L, unit = " ms").group(Group.Pathing, SubGroup.PathingPerformance).onValueChange { _, it -> failureTimeoutMS.value = it }
            setting("Plan Ahead Primary Timeout", planAheadPrimaryTimeoutMS.value, 0L..600000L, unit = " ms").group(Group.Pathing, SubGroup.PathingPerformance).onValueChange { _, it -> planAheadPrimaryTimeoutMS.value = it }
            setting("Plan Ahead Failure Timeout", planAheadFailureTimeoutMS.value, 0L..600000L, unit = " ms").group(Group.Pathing, SubGroup.PathingPerformance).onValueChange { _, it -> planAheadFailureTimeoutMS.value = it }
            setting("Slow Path", slowPath.value).group(Group.Pathing, SubGroup.PathingPerformance).onValueChange { _, it -> slowPath.value = it }
            setting("Slow Path Time Delay", slowPathTimeDelayMS.value, 0L..600000L, unit = " ms").group(Group.Pathing, SubGroup.PathingPerformance).onValueChange { _, it -> slowPathTimeDelayMS.value = it }
            setting("Slow Path Timeout", slowPathTimeoutMS.value, 0L..600000L, unit = " ms").group(Group.Pathing, SubGroup.PathingPerformance).onValueChange { _, it -> slowPathTimeoutMS.value = it }
            setting("Planning Tick Lookahead", planningTickLookahead.value, 0..200).group(Group.Pathing, SubGroup.PathingPerformance).onValueChange { _, it -> planningTickLookahead.value = it }
            setting("Movement Timeout Ticks", movementTimeoutTicks.value, 0..2000).group(Group.Pathing, SubGroup.PathingPerformance).onValueChange { _, it -> movementTimeoutTicks.value = it }

            setting("Follow Offset Distance", followOffsetDistance.value, 0.0..256.0).group(Group.Pathing, SubGroup.Follow).onValueChange { _, it -> followOffsetDistance.value = it }
            setting("Follow Offset Direction", followOffsetDirection.value, -180f..180f, 1f).group(Group.Pathing, SubGroup.Follow).onValueChange { _, it -> followOffsetDirection.value = it }
            setting("Follow Radius", followRadius.value, 0..1000).group(Group.Pathing, SubGroup.Follow).onValueChange { _, it -> followRadius.value = it }
            setting("Follow Target Max Distance", followTargetMaxDistance.value, 0..10000).group(Group.Pathing, SubGroup.Follow).onValueChange { _, it -> followTargetMaxDistance.value = it }
            setting("Disable Completion Check", disableCompletionCheck.value).group(Group.Pathing, SubGroup.Follow).onValueChange { _, it -> disableCompletionCheck.value = it }

            // BEHAVIOR
            setting("Strict Liquid Check", strictLiquidCheck.value).group(Group.Behavior, SubGroup.Assumptions).onValueChange { _, it -> strictLiquidCheck.value = it }
            setting("Assume Walk On Water", assumeWalkOnWater.value).group(Group.Behavior, SubGroup.Assumptions).onValueChange { _, it -> assumeWalkOnWater.value = it }
            setting("Assume Walk On Lava", assumeWalkOnLava.value).group(Group.Behavior, SubGroup.Assumptions).onValueChange { _, it -> assumeWalkOnLava.value = it }
            setting("Assume Step", assumeStep.value).group(Group.Behavior, SubGroup.Assumptions).onValueChange { _, it -> assumeStep.value = it }
            setting("Assume Safe Walk", assumeSafeWalk.value).group(Group.Behavior, SubGroup.Assumptions).onValueChange { _, it -> assumeSafeWalk.value = it }
            setting("Assume External Auto Tool", assumeExternalAutoTool.value).group(Group.Behavior, SubGroup.Assumptions).onValueChange { _, it -> assumeExternalAutoTool.value = it }

            setting("Allow Parkour Ascend", allowParkourAscend.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> allowParkourAscend.value = it }
            setting("Allow Diagonal Descend", allowDiagonalDescend.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> allowDiagonalDescend.value = it }
            setting("Allow Diagonal Ascend", allowDiagonalAscend.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> allowDiagonalAscend.value = it }
            setting("Allow Downward", allowDownward.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> allowDownward.value = it }
            setting("Allow Vines", allowVines.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> allowVines.value = it }
            setting("Allow Walk On Bottom Slab", allowWalkOnBottomSlab.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> allowWalkOnBottomSlab.value = it }
            setting("Allow Parkour", allowParkour.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> allowParkour.value = it }
            setting("Allow Parkour Place", allowParkourPlace.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> allowParkourPlace.value = it }
            setting("Sprint Ascends", sprintAscends.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> sprintAscends.value = it }
            setting("Overshoot Traverse", overshootTraverse.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> overshootTraverse.value = it }
            setting("Pause Mining For Falling Blocks", pauseMiningForFallingBlocks.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> pauseMiningForFallingBlocks.value = it }
            setting("Allow Overshoot Diagonal Descend", allowOvershootDiagonalDescend.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> allowOvershootDiagonalDescend.value = it }
            setting("Sprint In Water", sprintInWater.value).group(Group.Behavior, SubGroup.Movement).onValueChange { _, it -> sprintInWater.value = it }

            setting("Allow Break", allowBreak.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> allowBreak.value = it }
            setting("Allow Break Anyway", allowBreakAnyway.value.toSet()).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> allowBreakAnyway.value = it.toList() }
            setting("Allow Sprint", allowSprint.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> allowSprint.value = it }
            setting("Allow Place", allowPlace.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> allowPlace.value = it }
            setting("Allow Place In Fluids Source", allowPlaceInFluidsSource.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> allowPlaceInFluidsSource.value = it }
            setting("Allow Place In Fluids Flow", allowPlaceInFluidsFlow.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> allowPlaceInFluidsFlow.value = it }
            setting("Allow Inventory", allowInventory.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> allowInventory.value = it }
            setting("Ticks Between Inventory Moves", ticksBetweenInventoryMoves.value, 0..20).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> ticksBetweenInventoryMoves.value = it }
            setting("Inventory Move Only If Stationary", inventoryMoveOnlyIfStationary.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> inventoryMoveOnlyIfStationary.value = it }
            setting("Auto Tool", autoTool.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> autoTool.value = it }
            setting("Allow Water Bucket Fall", allowWaterBucketFall.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> allowWaterBucketFall.value = it }
            setting("Allow Jump At Build Limit", allowJumpAtBuildLimit.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> allowJumpAtBuildLimit.value = it }
            setting("Right Click Container On Arrival", rightClickContainerOnArrival.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> rightClickContainerOnArrival.value = it }
            setting("Enter Portal", enterPortal.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> enterPortal.value = it }
            setting("Walk While Breaking", walkWhileBreaking.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> walkWhileBreaking.value = it }
            setting("Use Sword To Mine", useSwordToMine.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> useSwordToMine.value = it }
            setting("Right Click Speed", rightClickSpeed.value, 0..10).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> rightClickSpeed.value = it }
            setting("Block Reach Distance", blockReachDistance.value, 0f..10f, 0.1f).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> blockReachDistance.value = it }
            setting("Block Break Speed", blockBreakSpeed.value, 0..10).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> blockBreakSpeed.value = it }
            setting("Random Looking 1.13", randomLooking113.value, 0.0..1.0, 0.01).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> randomLooking113.value = it }
            setting("Random Looking", randomLooking.value, 0.0..1.0, 0.01).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> randomLooking.value = it }
            setting("Free Look", freeLook.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> freeLook.value = it }
            setting("Block Free Look", blockFreeLook.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> blockFreeLook.value = it }
            setting("Elytra Free Look", elytraFreeLook.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> elytraFreeLook.value = it }
            setting("Smooth Look", smoothLook.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> smoothLook.value = it }
            setting("Elytra Smooth Look", elytraSmoothLook.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> elytraSmoothLook.value = it }
            setting("Smooth Look Ticks", smoothLookTicks.value, 0..200).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> smoothLookTicks.value = it }
            setting("Remain With Existing Look Direction", remainWithExistingLookDirection.value).group(Group.Behavior, SubGroup.Interaction).onValueChange { _, it -> remainWithExistingLookDirection.value = it }

            setting("Block Placement Penalty", blockPlacementPenalty.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> blockPlacementPenalty.value = it }
            setting("Block Break Additional Penalty", blockBreakAdditionalPenalty.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> blockBreakAdditionalPenalty.value = it }
            setting("Jump Penalty", jumpPenalty.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> jumpPenalty.value = it }
            setting("Walk On Water One Penalty", walkOnWaterOnePenalty.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> walkOnWaterOnePenalty.value = it }
            setting("Avoid Breaking Multiplier", avoidBreakingMultiplier.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> avoidBreakingMultiplier.value = it }
            setting("Max Cost Increase", maxCostIncrease.value, 0.0..100.0).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> maxCostIncrease.value = it }
            setting("Backtrack Cost Favoring Coefficient", backtrackCostFavoringCoefficient.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> backtrackCostFavoringCoefficient.value = it }
            setting("Mob Spawner Avoidance Coefficient", mobSpawnerAvoidanceCoefficient.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> mobSpawnerAvoidanceCoefficient.value = it }
            setting("Mob Spawner Avoidance Radius", mobSpawnerAvoidanceRadius.value, 0..1000).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> mobSpawnerAvoidanceRadius.value = it }
            setting("Mob Avoidance Coefficient", mobAvoidanceCoefficient.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> mobAvoidanceCoefficient.value = it }
            setting("Mob Avoidance Radius", mobAvoidanceRadius.value, 0..1000).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> mobAvoidanceRadius.value = it }
            setting("Path Cutoff Factor", pathCutoffFactor.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> pathCutoffFactor.value = it }
            setting("Break Correct Block Penalty Multiplier", breakCorrectBlockPenaltyMultiplier.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> breakCorrectBlockPenaltyMultiplier.value = it }
            setting("Place Incorrect Block Penalty Multiplier", placeIncorrectBlockPenaltyMultiplier.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> placeIncorrectBlockPenaltyMultiplier.value = it }
            setting("Cost Heuristic", costHeuristic.value, 0.0..100.0, 0.1).group(Group.Behavior, SubGroup.Penalties).onValueChange { _, it -> costHeuristic.value = it }

            setting("Item Saver", itemSaver.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> itemSaver.value = it }
            setting("Item Saver Threshold", itemSaverThreshold.value, 0..100).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> itemSaverThreshold.value = it }
            setting("Prefer Silk Touch", preferSilkTouch.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> preferSilkTouch.value = it }
            setting("Mine Scan Dropped Items", mineScanDroppedItems.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> mineScanDroppedItems.value = it }
            setting("Mine Drop Loiter Duration MS Thanks Louca", mineDropLoiterDurationMSThanksLouca.value, 0L..600000L).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> mineDropLoiterDurationMSThanksLouca.value = it }
            setting("Legit Mine", legitMine.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> legitMine.value = it }
            setting("Legit Mine Y Level", legitMineYLevel.value, 0..256).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> legitMineYLevel.value = it }
            setting("Legit Mine Include Diagonals", legitMineIncludeDiagonals.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> legitMineIncludeDiagonals.value = it }
            setting("Force Internal Mining", forceInternalMining.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> forceInternalMining.value = it }
            setting("Internal Mining Air Exception", internalMiningAirException.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> internalMiningAirException.value = it }
            setting("Min Y Level While Mining", minYLevelWhileMining.value, 0..256).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> minYLevelWhileMining.value = it }
            setting("Max Y Level While Mining", maxYLevelWhileMining.value, 0..256).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> maxYLevelWhileMining.value = it }
            setting("Allow Only Exposed Ores", allowOnlyExposedOres.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> allowOnlyExposedOres.value = it }
            setting("Allow Only Exposed Ores Distance", allowOnlyExposedOresDistance.value, 0..16).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> allowOnlyExposedOresDistance.value = it }
            setting("Replant Crops", replantCrops.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> replantCrops.value = it }
            setting("Replant Nether Wart", replantNetherWart.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> replantNetherWart.value = it }
            setting("Farm Max Scan Size", farmMaxScanSize.value, 0..1024).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> farmMaxScanSize.value = it }
            setting("Consider Potion Effects", considerPotionEffects.value).group(Group.Behavior, SubGroup.MiningAndFarming).onValueChange { _, it -> considerPotionEffects.value = it }

            setting("Explore For Blocks", exploreForBlocks.value).group(Group.Behavior, SubGroup.Exploration).onValueChange { _, it -> exploreForBlocks.value = it }
            setting("World Exploring Chunk Offset", worldExploringChunkOffset.value, 0..32).group(Group.Behavior, SubGroup.Exploration).onValueChange { _, it -> worldExploringChunkOffset.value = it }
            setting("Explore Chunk Set Minimum Size", exploreChunkSetMinimumSize.value, 0..10000).group(Group.Behavior, SubGroup.Exploration).onValueChange { _, it -> exploreChunkSetMinimumSize.value = it }
            setting("Explore Maintain Y", exploreMaintainY.value, 0..256).group(Group.Behavior, SubGroup.Exploration).onValueChange { _, it -> exploreMaintainY.value = it }

            // BUILDING
            setting("Build In Layers", buildInLayers.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> buildInLayers.value = it }
            setting("Layer Order", layerOrder.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> layerOrder.value = it }
            setting("Layer Height", layerHeight.value, 0..256).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> layerHeight.value = it }
            setting("Start At Layer", startAtLayer.value, 0..256).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> startAtLayer.value = it }
            setting("Skip Failed Layers", skipFailedLayers.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> skipFailedLayers.value = it }
            setting("Build Only Selection", buildOnlySelection.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> buildOnlySelection.value = it }
            setting("Build Repeat", buildRepeat.value.blockPos).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> buildRepeat.value = it }
            setting("Build Repeat Count", buildRepeatCount.value, 0..1000).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> buildRepeatCount.value = it }
            setting("Build Repeat Sneaky", buildRepeatSneaky.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> buildRepeatSneaky.value = it }
            setting("Break From Above", breakFromAbove.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> breakFromAbove.value = it }
            setting("Goal Break From Above", goalBreakFromAbove.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> goalBreakFromAbove.value = it }
            setting("Map Art Mode", mapArtMode.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> mapArtMode.value = it }
            setting("Ok If Water", okIfWater.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> okIfWater.value = it }
            setting("Incorrect Size", incorrectSize.value, 0..1000).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> incorrectSize.value = it }
            setting("Schematic Orientation X", schematicOrientationX.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> schematicOrientationX.value = it }
            setting("Schematic Orientation Y", schematicOrientationY.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> schematicOrientationY.value = it }
            setting("Schematic Orientation Z", schematicOrientationZ.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> schematicOrientationZ.value = it }
            setting("Build Schematic Rotation", buildSchematicRotation.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> buildSchematicRotation.value = it }
            setting("Build Schematic Mirror", buildSchematicMirror.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> buildSchematicMirror.value = it }
            setting("Schematic Fallback Extension", schematicFallbackExtension.value).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> schematicFallbackExtension.value = it }
            setting("Builder Tick Scan Radius", builderTickScanRadius.value, 0..64).group(Group.Building, SubGroup.Schematic).onValueChange { _, it -> builderTickScanRadius.value = it }

            setting("Acceptable Throwaway Items", acceptableThrowawayItems.value.toSet()).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> acceptableThrowawayItems.value = it.toList() }
            setting("Blocks To Avoid", blocksToAvoid.value.toSet()).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> blocksToAvoid.value = it.toList() }
            setting("Blocks To Disallow Breaking", blocksToDisallowBreaking.value.toSet()).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> blocksToDisallowBreaking.value = it.toList() }
            setting("Blocks To Avoid Breaking", blocksToAvoidBreaking.value.toSet()).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> blocksToAvoidBreaking.value = it.toList() }
            setting("Build Ignore Blocks", buildIgnoreBlocks.value.toSet()).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> buildIgnoreBlocks.value = it.toList() }
            setting("Build Skip Blocks", buildSkipBlocks.value.toSet()).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> buildSkipBlocks.value = it.toList() }
            setting("Build Valid Substitutes", buildValidSubstitutes.value.mapValues { (_, v) -> v.toSet() }).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> buildValidSubstitutes.value = it.mapValues { (_, v) -> v.toList() } }
            setting("Build Substitutes", buildSubstitutes.value.mapValues { (_, v) -> v.toSet() }).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> buildSubstitutes.value = it.mapValues { (_, v) -> v.toList() } }
            setting("Ok If Air", okIfAir.value.toSet()).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> okIfAir.value = it.toList() }
            setting("Build Ignore Existing", buildIgnoreExisting.value).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> buildIgnoreExisting.value = it }
            setting("Build Ignore Direction", buildIgnoreDirection.value).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> buildIgnoreDirection.value = it }
            setting("Build Ignore Properties", buildIgnoreProperties.value.toSet()).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> buildIgnoreProperties.value = it.toList() }
            setting("Avoid Updating Falling Blocks", avoidUpdatingFallingBlocks.value).group(Group.Building, SubGroup.BlockRules).onValueChange { _, it -> avoidUpdatingFallingBlocks.value = it }

            // RENDERING
            setting("Render Path", renderPath.value).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> renderPath.value = it }
            setting("Render Path As Line", renderPathAsLine.value).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> renderPathAsLine.value = it }
            setting("Render Goal", renderGoal.value).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> renderGoal.value = it }
            setting("Render Goal Animated", renderGoalAnimated.value).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> renderGoalAnimated.value = it }
            setting("Render Goal Ignore Depth", renderGoalIgnoreDepth.value).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> renderGoalIgnoreDepth.value = it }
            setting("Render Goal XZ Beacon", renderGoalXZBeacon.value).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> renderGoalXZBeacon.value = it }
            setting("Path Render Line Width Pixels", pathRenderLineWidthPixels.value, 0f..10f, 0.1f).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> pathRenderLineWidthPixels.value = it }
            setting("Goal Render Line Width Pixels", goalRenderLineWidthPixels.value, 0f..10f, 0.1f).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> goalRenderLineWidthPixels.value = it }
            setting("Fade Path", fadePath.value).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> fadePath.value = it }
            setting("Render Cached Chunks", renderCachedChunks.value).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> renderCachedChunks.value = it }
            setting("Cached Chunks Opacity", cachedChunksOpacity.value, 0f..1f, 0.05f).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> cachedChunksOpacity.value = it }
            setting("Render Path Ignore Depth", renderPathIgnoreDepth.value).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> renderPathIgnoreDepth.value = it }
            setting("Render Selection Boxes", renderSelectionBoxes.value).group(Group.Rendering, SubGroup.Rendering).onValueChange { _, it -> renderSelectionBoxes.value = it }

            setting("Color Current Path", colorCurrentPath.value).group(Group.Rendering, SubGroup.RenderingColors).onValueChange { _, it -> colorCurrentPath.value = it }
            setting("Color Next Path", colorNextPath.value).group(Group.Rendering, SubGroup.RenderingColors).onValueChange { _, it -> colorNextPath.value = it }
            setting("Color Blocks To Break", colorBlocksToBreak.value).group(Group.Rendering, SubGroup.RenderingColors).onValueChange { _, it -> colorBlocksToBreak.value = it }
            setting("Color Blocks To Place", colorBlocksToPlace.value).group(Group.Rendering, SubGroup.RenderingColors).onValueChange { _, it -> colorBlocksToPlace.value = it }
            setting("Color Blocks To Walk Into", colorBlocksToWalkInto.value).group(Group.Rendering, SubGroup.RenderingColors).onValueChange { _, it -> colorBlocksToWalkInto.value = it }
            setting("Color Best Path So Far", colorBestPathSoFar.value).group(Group.Rendering, SubGroup.RenderingColors).onValueChange { _, it -> colorBestPathSoFar.value = it }
            setting("Color Most Recent Considered", colorMostRecentConsidered.value).group(Group.Rendering, SubGroup.RenderingColors).onValueChange { _, it -> colorMostRecentConsidered.value = it }
            setting("Color Goal Box", colorGoalBox.value).group(Group.Rendering, SubGroup.RenderingColors).onValueChange { _, it -> colorGoalBox.value = it }
            setting("Color Inverted Goal Box", colorInvertedGoalBox.value).group(Group.Rendering, SubGroup.RenderingColors).onValueChange { _, it -> colorInvertedGoalBox.value = it }

            setting("Render Selection", renderSelection.value).group(Group.Rendering, SubGroup.RenderingSelection).onValueChange { _, it -> renderSelection.value = it }
            setting("Color Selection", colorSelection.value).group(Group.Rendering, SubGroup.RenderingSelection).onValueChange { _, it -> colorSelection.value = it }
            setting("Color Selection Pos1", colorSelectionPos1.value).group(Group.Rendering, SubGroup.RenderingSelection).onValueChange { _, it -> colorSelectionPos1.value = it }
            setting("Color Selection Pos2", colorSelectionPos2.value).group(Group.Rendering, SubGroup.RenderingSelection).onValueChange { _, it -> colorSelectionPos2.value = it }
            setting("Selection Opacity", selectionOpacity.value, 0f..1f, 0.05f).group(Group.Rendering, SubGroup.RenderingSelection).onValueChange { _, it -> selectionOpacity.value = it }
            setting("Selection Line Width", selectionLineWidth.value, 0f..10f, 0.1f).group(Group.Rendering, SubGroup.RenderingSelection).onValueChange { _, it -> selectionLineWidth.value = it }
            setting("Render Selection Ignore Depth", renderSelectionIgnoreDepth.value).group(Group.Rendering, SubGroup.RenderingSelection).onValueChange { _, it -> renderSelectionIgnoreDepth.value = it }
            setting("Render Selection Corners", renderSelectionCorners.value).group(Group.Rendering, SubGroup.RenderingSelection).onValueChange { _, it -> renderSelectionCorners.value = it }
            setting("Render Selection Boxes Ignore Depth", renderSelectionBoxesIgnoreDepth.value).group(Group.Rendering, SubGroup.RenderingSelection).onValueChange { _, it -> renderSelectionBoxesIgnoreDepth.value = it }

            // ELYTRA
            setting("Simulation Ticks", elytraSimulationTicks.value, 0..200).group(Group.Elytra).onValueChange { _, it -> elytraSimulationTicks.value = it }
            setting("Pitch Range", elytraPitchRange.value, 0..90).group(Group.Elytra).onValueChange { _, it -> elytraPitchRange.value = it }
            setting("Firework Speed", elytraFireworkSpeed.value, 0.0..5.0, 0.05).group(Group.Elytra).onValueChange { _, it -> elytraFireworkSpeed.value = it }
            setting("Firework Setback Use Delay", elytraFireworkSetbackUseDelay.value, 0..600).group(Group.Elytra).onValueChange { _, it -> elytraFireworkSetbackUseDelay.value = it }
            setting("Minimum Avoidance", elytraMinimumAvoidance.value, 0.0..10.0, 0.1).group(Group.Elytra).onValueChange { _, it -> elytraMinimumAvoidance.value = it }
            setting("Conserve Fireworks", elytraConserveFireworks.value).group(Group.Elytra).onValueChange { _, it -> elytraConserveFireworks.value = it }
            setting("Render Raytraces", elytraRenderRaytraces.value).group(Group.Elytra).onValueChange { _, it -> elytraRenderRaytraces.value = it }
            setting("Render Hitbox Raytraces", elytraRenderHitboxRaytraces.value).group(Group.Elytra).onValueChange { _, it -> elytraRenderHitboxRaytraces.value = it }
            setting("Render Simulation", elytraRenderSimulation.value).group(Group.Elytra).onValueChange { _, it -> elytraRenderSimulation.value = it }
            setting("Auto Jump", elytraAutoJump.value).group(Group.Elytra).onValueChange { _, it -> elytraAutoJump.value = it }
            setting("Nether Seed", elytraNetherSeed.value, -9_000_000_000_000_000L..9_000_000_000_000_000L).group(Group.Elytra).onValueChange { _, it -> elytraNetherSeed.value = it }
            setting("Predict Terrain", elytraPredictTerrain.value).group(Group.Elytra).onValueChange { _, it -> elytraPredictTerrain.value = it }
            setting("Auto Swap", elytraAutoSwap.value).group(Group.Elytra).onValueChange { _, it -> elytraAutoSwap.value = it }
            setting("Minimum Durability", elytraMinimumDurability.value, 0..432).group(Group.Elytra).onValueChange { _, it -> elytraMinimumDurability.value = it }
            setting("Min Fireworks Before Landing", elytraMinFireworksBeforeLanding.value, 0..64).group(Group.Elytra).onValueChange { _, it -> elytraMinFireworksBeforeLanding.value = it }
            setting("Allow Emergency Land", elytraAllowEmergencyLand.value).group(Group.Elytra).onValueChange { _, it -> elytraAllowEmergencyLand.value = it }
            setting("Time Between Cache Cull Secs", elytraTimeBetweenCacheCullSecs.value, 0L..86400L).group(Group.Elytra).onValueChange { _, it -> elytraTimeBetweenCacheCullSecs.value = it }
            setting("Cache Cull Distance", elytraCacheCullDistance.value, 0..100000).group(Group.Elytra).onValueChange { _, it -> elytraCacheCullDistance.value = it }
            setting("Allow Land On Nether Fortress", elytraAllowLandOnNetherFortress.value).group(Group.Elytra).onValueChange { _, it -> elytraAllowLandOnNetherFortress.value = it }
            setting("Terms Accepted", elytraTermsAccepted.value).group(Group.Elytra).onValueChange { _, it -> elytraTermsAccepted.value = it }
            setting("Chat Spam", elytraChatSpam.value).group(Group.Elytra).onValueChange { _, it -> elytraChatSpam.value = it }
        }
    }

    /**
     * Whether Baritone is currently pathing
     */
    val isPathing: Boolean
        get() = primary.pathingBehavior.isPathing

    /**
     * Whether Baritone is active (pathing, calculating goal, etc.)
     */
    val isActive: Boolean
        get() = primary.customGoalProcess.isActive || primary.pathingBehavior.isPathing || primary.pathingControlManager.mostRecentInControl()
            .orElse(null)?.isActive == true

    /**
     * Sets the current Baritone goal and starts pathing
     */
    fun setGoalAndPath(goal: Goal) = primary.customGoalProcess.setGoalAndPath(goal)

    /**
     * Force cancel Baritone
     */
    fun cancel() = primary.pathingBehavior.cancelEverything()
}
