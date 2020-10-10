package me.zeroeightsix.kami.module.modules.combat

import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.manager.mangers.CombatManager
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.InventoryUtils
import me.zeroeightsix.kami.util.TimerUtils
import me.zeroeightsix.kami.util.combat.CombatUtils
import me.zeroeightsix.kami.util.combat.CrystalUtils
import me.zeroeightsix.kami.util.event.listener
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.entity.monster.EntityMob
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemSword
import net.minecraft.network.play.server.SPacketConfirmTransaction
import kotlin.math.max

@Module.Info(
        name = "AutoOffhand",
        description = "Manages item in your offhand",
        category = Module.Category.COMBAT
)
object AutoOffhand : Module() {
    private val type = register(Settings.enumBuilder(Type::class.java, "Type").withValue(Type.TOTEM))

    // Totem
    private val hpThreshold = register(Settings.floatBuilder("HpThreshold").withValue(5f).withRange(1f, 20f).withVisibility { type.value == Type.TOTEM })
    private val checkDamage = register(Settings.booleanBuilder("CheckDamage").withValue(true).withVisibility { type.value == Type.TOTEM })
    private val mob = register(Settings.booleanBuilder("Mob").withValue(true).withVisibility { type.value == Type.TOTEM && checkDamage.value })
    private val player = register(Settings.booleanBuilder("Player").withValue(true).withVisibility { type.value == Type.TOTEM && checkDamage.value })
    private val crystal = register(Settings.booleanBuilder("Crystal").withValue(true).withVisibility { type.value == Type.TOTEM && checkDamage.value })

    // Gapple
    private val offhandGapple = register(Settings.booleanBuilder("OffhandGapple").withValue(false).withVisibility { type.value == Type.GAPPLE })
    private val checkAura = register(Settings.booleanBuilder("CheckAura").withValue(true).withVisibility { type.value == Type.GAPPLE && offhandGapple.value })
    private val checkWeapon = register(Settings.booleanBuilder("CheckWeapon").withValue(false).withVisibility { type.value == Type.GAPPLE && offhandGapple.value })
    private val checkCAGapple = register(Settings.booleanBuilder("CheckCrystalAura").withValue(true).withVisibility { type.value == Type.GAPPLE && offhandGapple.value && !offhandCrystal.value })

    // Crystal
    private val offhandCrystal = register(Settings.booleanBuilder("OffhandCrystal").withValue(false).withVisibility { type.value == Type.CRYSTAL })
    private val checkCACrystal = register(Settings.booleanBuilder("CheckCrystalAura").withValue(false).withVisibility { type.value == Type.CRYSTAL && offhandCrystal.value })

    private enum class Type(val itemId: Int) {
        TOTEM(449),
        GAPPLE(322),
        CRYSTAL(426)
    }

    private val transactionLog = HashMap<Short, Boolean>()
    private val movingTimer = TimerUtils.TickTimer()
    private var maxDamage = 0f

    init {
        listener<PacketEvent.Receive> {
            if (mc.player == null || it.packet !is SPacketConfirmTransaction || it.packet.windowId != 0 || !transactionLog.containsKey(it.packet.actionNumber)) return@listener
            transactionLog[it.packet.actionNumber] = it.packet.wasAccepted()
            if (!transactionLog.containsValue(false)) movingTimer.reset(-175L) // If all the click packets were accepted then we reset the timer for next moving
        }

        listener<SafeTickEvent> {
            if (mc.player.isDead || !movingTimer.tick(200L, false)) return@listener // Delays 4 ticks by default
            if (!mc.player.inventory.getItemStack().isEmpty()) { // If player is holding an in inventory
                if (mc.currentScreen is GuiContainer) {// If inventory is open (playing moving item)
                    movingTimer.reset() // delay for 5 ticks
                } else { // If inventory is not open (ex. inventory desync)
                    InventoryUtils.removeHoldingItem()
                }
            } else { // If player is not holding an item in inventory
                val type1 = getType()
                // First check for whether player is holding the right item already or not
                if (type1 != null && !checkOffhandItem(type1)) getItemSlot(type1)?.let { (slot, type2) ->
                    // Second check is for case of when player ran out of the original type of item
                    if (slot == 45 || checkOffhandItem(type2)) return@let
                    transactionLog.clear()
                    transactionLog.putAll(InventoryUtils.moveToSlot(0, slot, 45).associate { it to false })
                    mc.playerController.updateController()
                    movingTimer.reset()
                }
            }
            updateDamage()
        }
    }

    private fun getType() = when {
        checkTotem() -> Type.TOTEM
        checkGapple() -> Type.GAPPLE
        checkCrystal() -> Type.CRYSTAL
        mc.player.heldItemOffhand.isEmpty() -> Type.TOTEM
        else -> null
    }

    private fun checkTotem() = mc.player.health < hpThreshold.value
            || (checkDamage.value && mc.player.absorptionAmount + mc.player.health - maxDamage < hpThreshold.value)

    private fun checkGapple(): Boolean {
        val item = mc.player.heldItemMainhand.getItem()
        return offhandGapple.value
                && (checkAura.value && CombatManager.isActiveAndTopPriority(Aura)
                || checkWeapon.value && (item is ItemSword || item is ItemAxe)
                || (checkCAGapple.value && !offhandCrystal.value) && CombatManager.isActiveAndTopPriority(CrystalAura))
    }

    private fun checkCrystal() = offhandCrystal.value && checkCACrystal.value && CrystalAura.isEnabled && CombatManager.isOnTopPriority(CrystalAura)

    private fun checkOffhandItem(type: Type) = Item.getIdFromItem(mc.player.heldItemOffhand.getItem()) == type.itemId

    private fun getItemSlot(type: Type, loopTime: Int = 1): Pair<Int, Type>? = getSlot(type.itemId)?.to(type)
            ?: if (loopTime <= 3) getItemSlot(getNextType(type), loopTime + 1)
            else null

    private fun getSlot(itemId: Int): Int? {
        val slot = mc.player.inventoryContainer.inventory.subList(9, 46).indexOfFirst { Item.getIdFromItem(it.getItem()) == itemId }
        return if (slot != -1) slot + 9 else null
    }

    private fun getNextType(type: Type) = with(Type.values()) { this[(type.ordinal + 1) % this.size] }

    private fun updateDamage() {
        maxDamage = 0f
        if (!checkDamage.value) return
        for (entity in mc.world.loadedEntityList) {
            if (entity.name == mc.player.name) continue
            if (entity !is EntityMob && entity !is EntityPlayer && entity !is EntityEnderCrystal) continue
            if (mc.player.getDistance(entity) > 10f) continue
            if (mob.value && entity is EntityMob) {
                maxDamage = max(CombatUtils.calcDamageFromMob(entity), maxDamage)
            }
            if (player.value && entity is EntityPlayer) {
                maxDamage = max(CombatUtils.calcDamageFromPlayer(entity), maxDamage)
            }
            if (crystal.value && entity is EntityEnderCrystal) {
                maxDamage = max(CrystalUtils.calcDamage(entity, mc.player), maxDamage)
            }
        }
    }
}