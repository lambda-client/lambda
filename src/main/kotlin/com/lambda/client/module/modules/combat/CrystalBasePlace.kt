package com.lambda.client.module.modules.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.WorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.CrystalManager
import com.lambda.client.manager.managers.HotbarManager.resetHotbar
import com.lambda.client.manager.managers.HotbarManager.serverSideItem
import com.lambda.client.manager.managers.HotbarManager.spoofHotbar
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.Bind
import com.lambda.client.util.TickTimer
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.combat.CrystalUtils.calcCrystalDamage
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.items.block
import com.lambda.client.util.items.firstBlock
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.PlaceInfo
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.hasNeighbour
import com.lambda.client.util.world.isPlaceable
import net.minecraft.entity.EntityLivingBase
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import java.util.*

@CombatManager.CombatModule
object CrystalBasePlace : Module(
    name = "CrystalBasePlace",
    description = "Places obsidian for placing crystal on",
    category = Category.COMBAT,
    modulePriority = 90
) {
    private val manualPlaceBind by setting("Bind Manual Place", Bind())
    private val minDamageInc by setting("Min Damage Inc", 2.0f, 0.0f..10.0f, 0.25f)
    private val range by setting("Range", 4.0f, 0.0f..8.0f, 0.5f)
    private val delay by setting("Delay", 20, 0..50, 5)

    private val timer = TickTimer()
    private val renderer = ESPRenderer().apply { aFilled = 33; aOutline = 233 }
    private var inactiveTicks = 0
    private var rotationTo: Vec3d? = null
    private var placePacket: CPacketPlayerTryUseItemOnBlock? = null

    override fun isActive(): Boolean {
        return isEnabled && inactiveTicks <= 3
    }

    init {
        onDisable {
            inactiveTicks = 0
            placePacket = null
            resetHotbar()
        }

        listener<WorldEvent.RenderTickEvent> {
            val clear = inactiveTicks >= 30
            renderer.render(clear)
        }

        safeListener<InputEvent.KeyInputEvent> {
            if (!CombatManager.isOnTopPriority(this@CrystalBasePlace) || CombatSetting.pause) return@safeListener
            val target = CombatManager.target ?: return@safeListener

            if (manualPlaceBind.isDown(Keyboard.getEventKey())) prePlace(target)
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener
            inactiveTicks++

            if (!CombatManager.isOnTopPriority(CrystalBasePlace) || CombatSetting.pause) return@safeListener

            val slot = player.hotbarSlots.firstBlock(Blocks.OBSIDIAN) ?: return@safeListener
            val target = CombatManager.target ?: return@safeListener

            placePacket?.let { packet ->
                if (inactiveTicks > 1) {
                    if (!isHoldingObby) spoofHotbar(slot.hotbarSlot)
                    player.swingArm(EnumHand.MAIN_HAND)
                    connection.sendPacket(packet)
                    resetHotbar()
                    placePacket = null
                }
            }

            if (placePacket == null && CrystalAura.isEnabled && CrystalAura.inactiveTicks > 15) prePlace(target)

            if (isActive()) {
                rotationTo?.let { hitVec ->
                    sendPlayerPacket {
                        rotate(getRotationTo(hitVec))
                    }
                }
            } else {
                rotationTo = null
            }
        }
    }

    private val SafeClientEvent.isHoldingObby
        get() = isObby(player.heldItemMainhand)
            || isObby(player.serverSideItem)

    private fun isObby(itemStack: ItemStack) = itemStack.item.block == Blocks.OBSIDIAN

    private fun SafeClientEvent.prePlace(entity: EntityLivingBase) {
        if (rotationTo != null || !timer.tick((delay * 50.0f).toLong(), false)) return
        val placeInfo = getPlaceInfo(entity)

        if (placeInfo != null) {
            rotationTo = placeInfo.hitVec
            placePacket = CPacketPlayerTryUseItemOnBlock(placeInfo.pos,
                placeInfo.side, EnumHand.MAIN_HAND, placeInfo.hitVecOffset.x.toFloat(),
                placeInfo.hitVecOffset.y.toFloat(), placeInfo.hitVecOffset.z.toFloat())

            renderer.clear()
            renderer.add(placeInfo.placedPos, ColorHolder(255, 255, 255))

            inactiveTicks = 0
            timer.reset()
        } else {
            timer.reset((delay * -25.0f).toLong())
        }
    }

    private fun SafeClientEvent.getPlaceInfo(entity: EntityLivingBase): PlaceInfo? {
        return VectorUtils.getBlockPosInSphere(player.getPositionEyes(1.0f), range)
            .filter { world.isPlaceable(it) && hasNeighbour(it) && it.y <= entity.posY }
            .maxByOrNull {
                val damage = calcPlaceDamage(it, entity)
                damage.targetDamage - damage.selfDamage
            }?.let {
                getNeighbour(it, 1)
            }
    }

    private fun SafeClientEvent.calcPlaceDamage(pos: BlockPos, entity: EntityLivingBase): CrystalManager.CrystalDamage {
        // Set up a fake obsidian here for proper damage calculation
        val prevState = world.getBlockState(pos)
        world.setBlockState(pos, Blocks.OBSIDIAN.defaultState)

        // Checks damage
        val damage = calcCrystalDamage(pos.toVec3dCenter(0.5, 0.0, 0.0), entity)

        // Revert the block state before return
        world.setBlockState(pos, prevState)

        return damage
    }
}