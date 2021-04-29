package com.lambda.client.module.modules.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.AutoEat
import com.lambda.client.process.PauseProcess
import com.lambda.client.process.PauseProcess.pauseBaritone
import com.lambda.client.process.PauseProcess.unpauseBaritone
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.InfoCalculator
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.combat.CombatUtils
import com.lambda.client.util.combat.CrystalUtils.calcCrystalDamage
import com.lambda.client.util.combat.CrystalUtils.getPlacePos
import com.lambda.client.util.graphics.*
import com.lambda.client.util.math.RotationUtils.getRelativeRotation
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.isActiveOrFalse
import com.lambda.client.util.threads.runSafeR
import com.lambda.client.util.threads.safeListener
import com.lambda.commons.extension.ceilToInt
import com.lambda.event.listener.listener
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.entity.passive.AbstractHorse
import net.minecraft.entity.passive.EntityTameable
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPickaxe
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11.*
import java.util.*

internal object CombatSetting : Module(
    name = "CombatSetting",
    description = "Settings for combat module targeting",
    category = Category.COMBAT,
    showOnArray = false,
    alwaysEnabled = true
) {
    private val page = setting("Page", Page.TARGETING)

    /* Targeting */
    private val filter = setting("Filter", TargetFilter.ALL, { page.value == Page.TARGETING })
    private val fov = setting("FOV", 90.0f, 0.0f..180.0f, 5.0f, { page.value == Page.TARGETING && filter.value == TargetFilter.FOV })
    private val priority = setting("Priority", TargetPriority.DISTANCE, { page.value == Page.TARGETING })
    private val players = setting("Players", true, { page.value == Page.TARGETING })
    private val friends = setting("Friends", false, { page.value == Page.TARGETING && players.value })
    private val teammates = setting("Teammates", false, { page.value == Page.TARGETING && players.value })
    private val sleeping = setting("Sleeping", false, { page.value == Page.TARGETING && players.value })
    private val mobs = setting("Mobs", true, { page.value == Page.TARGETING })
    private val passive = setting("Passive Mobs", false, { page.value == Page.TARGETING && mobs.value })
    private val neutral = setting("Neutral Mobs", false, { page.value == Page.TARGETING && mobs.value })
    private val hostile = setting("Hostile Mobs", false, { page.value == Page.TARGETING && mobs.value })
    private val tamed = setting("Tamed Mobs", false, { page.value == Page.TARGETING && mobs.value })
    private val invisible = setting("Invisible", true, { page.value == Page.TARGETING })
    private val ignoreWalls = setting("Ignore Walls", false, { page.value == Page.TARGETING })
    private val range = setting("Target Range", 16.0f, 2.0f..64.0f, 2.0f, { page.value == Page.TARGETING })

    /* In Combat */
    private val pauseForDigging = setting("Pause For Digging", true, { page.value == Page.IN_COMBAT })
    private val pauseForEating = setting("Pause For Eating", true, { page.value == Page.IN_COMBAT })
    private val ignoreOffhandEating = setting("Ignore Offhand Eating", true, { page.value == Page.IN_COMBAT && pauseForEating.value })
    private val pauseBaritone = setting("Pause Baritone", true, { page.value == Page.IN_COMBAT })
    private val resumeDelay = setting("Resume Delay", 3, 1..10, 1, { page.value == Page.IN_COMBAT && pauseBaritone.value })
    private val motionPrediction = setting("Motion Prediction", true, { page.value == Page.IN_COMBAT })
    private val pingSync = setting("Ping Sync", true, { page.value == Page.IN_COMBAT && motionPrediction.value })
    private val ticksAhead = setting("Ticks Ahead", 5, 0..20, 1, { page.value == Page.IN_COMBAT && motionPrediction.value && !pingSync.value })

    /* Render */
    private val renderPredictedPos = setting("Render Predicted Position", false, { page.value == Page.RENDER })

    private enum class Page {
        TARGETING, IN_COMBAT, RENDER
    }

    private enum class TargetFilter {
        ALL, FOV, MANUAL
    }

    private enum class TargetPriority {
        DAMAGE, HEALTH, CROSS_HAIR, DISTANCE
    }

    private var overrideRange = range.value
    private var paused = false
    private val resumeTimer = TickTimer(TimeUnit.SECONDS)
    private val jobMap = hashMapOf<(SafeClientEvent) -> Unit, Job?>(
        { it: SafeClientEvent -> it.updateTarget() } to null,
        { it: SafeClientEvent -> it.updatePlacingList() } to null,
        { it: SafeClientEvent -> it.updateCrystalList() } to null
    )

    val pause
        get() = runSafeR {
            player.ticksExisted < 10
                || checkDigging()
                || checkEating()
        } ?: false

    private fun SafeClientEvent.checkDigging() =
        pauseForDigging.value
            && player.heldItemMainhand.item is ItemPickaxe
            && playerController.isHittingBlock

    private fun SafeClientEvent.checkEating() =
        pauseForEating.value
            && (PauseProcess.isPausing(AutoEat) || player.isHandActive && player.activeItemStack.item is ItemFood)
            && (!ignoreOffhandEating.value || player.activeHand != EnumHand.OFF_HAND)

    override fun isActive() = KillAura.isActive() || BedAura.isActive() || CrystalAura.isActive() || Surround.isActive()

    init {
        listener<RenderOverlayEvent> {
            if (!renderPredictedPos.value) return@listener
            CombatManager.target?.let {
                val ticks = if (pingSync.value) (InfoCalculator.ping() / 25f).ceilToInt() else ticksAhead.value
                val posCurrent = EntityUtils.getInterpolatedPos(it, KamiTessellator.pTicks())
                val posAhead = CombatManager.motionTracker.calcPositionAhead(ticks, true) ?: return@listener
                val posAheadEye = posAhead.add(0.0, it.eyeHeight.toDouble(), 0.0)
                val posCurrentScreen = Vec2d(ProjectionUtils.toScaledScreenPos(posCurrent))
                val posAheadScreen = Vec2d(ProjectionUtils.toScaledScreenPos(posAhead))
                val posAheadEyeScreen = Vec2d(ProjectionUtils.toScaledScreenPos(posAheadEye))
                val vertexHelper = VertexHelper(GlStateUtils.useVbo())
                val vertices = arrayOf(posCurrentScreen, posAheadScreen, posAheadEyeScreen)
                glDisable(GL_TEXTURE_2D)
                RenderUtils2D.drawLineStrip(vertexHelper, vertices, 2f, ColorHolder(80, 255, 80))
                glEnable(GL_TEXTURE_2D)
            }
        }

        safeListener<TickEvent.ClientTickEvent>(5000) {
            for ((function, future) in jobMap) {
                if (future.isActiveOrFalse) continue // Skip if the previous thread isn't done
                jobMap[function] = defaultScope.launch { function(this@safeListener) }
            }

            if (isActive() && pauseBaritone.value) {
                pauseBaritone()
                resumeTimer.reset()
                paused = true
            } else if (resumeTimer.tick(resumeDelay.value.toLong(), false)) {
                unpauseBaritone()
                paused = false
            }
        }
    }

    private fun SafeClientEvent.updateTarget() {
        CombatManager.getTopModule()?.let {
            overrideRange = if (it is KillAura) it.range else range.value
        }

        getTargetList().let {
            CombatManager.target = getTarget(it)
        }
    }

    private fun SafeClientEvent.updatePlacingList() {
        if (CrystalAura.isDisabled && CrystalBasePlace.isDisabled && CrystalESP.isDisabled && player.ticksExisted % 4 != 0) return

        val eyePos = player.getPositionEyes(1f) ?: Vec3d.ZERO
        val cacheList = ArrayList<Pair<BlockPos, CombatManager.CrystalDamage>>()
        val target = CombatManager.target
        val prediction = target?.let { getPrediction(it) }

        for (pos in getPlacePos(target, player, 8f)) {
            val dist = eyePos.distanceTo(pos.toVec3dCenter(0.0, 0.5, 0.0))
            val damage = target?.let { calcCrystalDamage(pos, it, prediction?.first, prediction?.second) } ?: 0.0f
            val selfDamage = calcCrystalDamage(pos, player)
            cacheList.add(Pair(pos, CombatManager.CrystalDamage(damage, selfDamage, dist)))
        }

        CombatManager.placeMap = LinkedHashMap<BlockPos, CombatManager.CrystalDamage>(cacheList.size).apply {
            putAll(cacheList.sortedByDescending { it.second.targetDamage })
        }
    }

    /* Crystal damage calculation */
    private fun SafeClientEvent.updateCrystalList() {
        if (CrystalAura.isDisabled && CrystalESP.isDisabled && (player.ticksExisted - 2) % 4 != 0) return

        val cacheList = ArrayList<Pair<EntityEnderCrystal, CombatManager.CrystalDamage>>()
        val eyePos = player.getPositionEyes(1f)
        val target = CombatManager.target
        val prediction = target?.let { getPrediction(it) }

        for (entity in world.loadedEntityList.toList()) {
            if (entity.isDead) continue
            if (entity !is EntityEnderCrystal) continue
            val dist = entity.distanceTo(eyePos)
            if (dist > 16.0f) continue
            val damage = if (target != null && prediction != null) calcCrystalDamage(entity, target, prediction.first, prediction.second) else 0.0f
            val selfDamage = calcCrystalDamage(entity, player)
            cacheList.add(entity to CombatManager.CrystalDamage(damage, selfDamage, dist))
        }

        CombatManager.crystalMap = LinkedHashMap<EntityEnderCrystal, CombatManager.CrystalDamage>(cacheList.size).apply {
            putAll(cacheList.sortedByDescending { it.second.targetDamage })
        }
    }

    fun getPrediction(entity: Entity) = CombatManager.target?.let {
        if (motionPrediction.value) {
            val ticks = if (pingSync.value) (InfoCalculator.ping() / 25f).ceilToInt() else ticksAhead.value
            CombatManager.motionTracker.getPositionAndBBAhead(ticks) ?: it.positionVector to it.entityBoundingBox
        } else {
            it.positionVector to it.entityBoundingBox
        }
    } ?: entity.positionVector to entity.entityBoundingBox
    /* End of crystal damage calculation */

    /* Targeting */
    private fun SafeClientEvent.getTargetList(): LinkedList<EntityLivingBase> {
        val targetList = LinkedList<EntityLivingBase>()
        for (entity in getCacheList()) {
            if (AntiBot.isBot(entity)) continue

            if (!tamed.value
                && (entity is EntityTameable && entity.isTamed
                    || entity is AbstractHorse && entity.isTame)) continue

            if (!teammates.value
                && player.isOnSameTeam(entity)) continue

            if (!shouldIgnoreWall()
                && player.canEntityBeSeen(entity)
                && !EntityUtils.canEntityFeetBeSeen(entity)
                && EntityUtils.canEntityHitboxBeSeen(entity) == null) continue

            targetList.add(entity)
        }

        return targetList
    }

    private fun SafeClientEvent.getCacheList(): LinkedList<EntityLivingBase> {
        val player = arrayOf(players.value, friends.value, sleeping.value)
        val mob = arrayOf(mobs.value, passive.value, neutral.value, hostile.value)
        val cacheList = LinkedList(EntityUtils.getTargetList(player, mob, invisible.value, overrideRange))
        if ((cacheList.isEmpty() || getTarget(cacheList) == null) && overrideRange != range.value) {
            cacheList.addAll(EntityUtils.getTargetList(player, mob, invisible.value, range.value))
        }
        return cacheList
    }

    private fun shouldIgnoreWall(): Boolean {
        val module = CombatManager.getTopModule()
        return if (module is KillAura || module is AimBot) ignoreWalls.value
        else true
    }

    private fun SafeClientEvent.getTarget(listIn: LinkedList<EntityLivingBase>): EntityLivingBase? {
        val copiedList = LinkedList(listIn)
        return filterTargetList(copiedList) ?: CombatManager.target?.let { entity ->
            if (!entity.isDead && listIn.contains(entity)) entity else null
        }
    }

    private fun SafeClientEvent.filterTargetList(listIn: LinkedList<EntityLivingBase>): EntityLivingBase? {
        if (listIn.isEmpty()) return null
        return filterByPriority(filterByFilter(listIn))
    }

    private fun SafeClientEvent.filterByFilter(listIn: LinkedList<EntityLivingBase>): LinkedList<EntityLivingBase> {
        when (filter.value) {
            TargetFilter.FOV -> {
                listIn.removeIf { getRelativeRotation(it) > fov.value }
            }

            TargetFilter.MANUAL -> {
                if (!mc.gameSettings.keyBindAttack.isKeyDown && !mc.gameSettings.keyBindUseItem.isKeyDown) {
                    return LinkedList()
                }
                val eyePos = player.getPositionEyes(KamiTessellator.pTicks())
                val lookVec = player.lookVec.scale(range.value.toDouble())
                val sightEndPos = eyePos.add(lookVec)
                listIn.removeIf { it.entityBoundingBox.calculateIntercept(eyePos, sightEndPos) == null }
            }

            else -> {
            }
        }
        return listIn
    }

    private fun SafeClientEvent.filterByPriority(listIn: LinkedList<EntityLivingBase>): EntityLivingBase? {
        if (listIn.isEmpty()) return null

        if (priority.value == TargetPriority.DAMAGE) filterByDamage(listIn)

        if (priority.value == TargetPriority.HEALTH) filterByHealth(listIn)

        return if (priority.value == TargetPriority.CROSS_HAIR) filterByCrossHair(listIn) else filterByDistance(listIn)
    }

    private fun filterByDamage(listIn: LinkedList<EntityLivingBase>) {
        if (listIn.isEmpty()) return
        var damage = Float.MIN_VALUE
        val toKeep = HashSet<Entity>()
        for (entity in listIn) {
            val currentDamage = CombatUtils.calcDamage(entity, roundDamage = true)
            if (currentDamage >= damage) {
                if (currentDamage > damage) {
                    damage = currentDamage
                    toKeep.clear()
                }
                toKeep.add(entity)
            }
        }
        listIn.removeIf { !toKeep.contains(it) }
    }

    private fun filterByHealth(listIn: LinkedList<EntityLivingBase>) {
        if (listIn.isEmpty()) return
        var health = Float.MAX_VALUE
        val toKeep = HashSet<Entity>()
        for (e in listIn) {
            val currentHealth = e.health
            if (currentHealth <= health) {
                if (currentHealth < health) {
                    health = currentHealth
                    toKeep.clear()
                }
                toKeep.add(e)
            }
        }
        listIn.removeIf { !toKeep.contains(it) }
    }

    private fun SafeClientEvent.filterByCrossHair(listIn: LinkedList<EntityLivingBase>): EntityLivingBase? {
        if (listIn.isEmpty()) return null
        return listIn.sortedBy { getRelativeRotation(it) }[0]
    }

    private fun SafeClientEvent.filterByDistance(listIn: LinkedList<EntityLivingBase>): EntityLivingBase? {
        if (listIn.isEmpty()) return null
        return listIn.sortedBy { it.getDistance(player) }[0]
    }
    /* End of targeting */
}
