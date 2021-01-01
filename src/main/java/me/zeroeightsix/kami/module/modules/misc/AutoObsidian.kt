package me.zeroeightsix.kami.module.modules.misc

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zeroeightsix.kami.event.KamiEvent
import me.zeroeightsix.kami.event.events.OnUpdateWalkingPlayerEvent
import me.zeroeightsix.kami.event.events.RenderWorldEvent
import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.manager.managers.PlayerPacketManager
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.module.modules.player.NoBreakAnimation
import me.zeroeightsix.kami.process.AutoObsidianProcess
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.*
import me.zeroeightsix.kami.util.BlockUtils.placeBlock
import me.zeroeightsix.kami.util.EntityUtils.getDroppedItem
import me.zeroeightsix.kami.util.color.ColorHolder
import me.zeroeightsix.kami.util.graphics.ESPRenderer
import me.zeroeightsix.kami.util.math.RotationUtils.getRotationTo
import me.zeroeightsix.kami.util.math.Vec2f
import me.zeroeightsix.kami.util.math.VectorUtils
import me.zeroeightsix.kami.util.math.VectorUtils.toVec3d
import me.zeroeightsix.kami.util.text.MessageSendHelper.sendChatMessage
import net.minecraft.block.BlockShulkerBox
import net.minecraft.block.state.IBlockState
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.gui.inventory.GuiShulkerBox
import net.minecraft.init.Blocks
import net.minecraft.init.SoundEvents
import net.minecraft.inventory.ClickType
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.commons.extension.ceilToInt
import org.kamiblue.commons.interfaces.DisplayEnum
import org.kamiblue.event.listener.listener

@Module.Info(
    name = "AutoObsidian",
    category = Module.Category.MISC,
    description = "Breaks down Ender Chests to restock obsidian"
)
object AutoObsidian : Module() {
    private val fillMode = register(Settings.e<FillMode>("FillMode", FillMode.TARGET_STACKS))
    private val searchShulker = register(Settings.b("SearchShulker", false))
    private val autoRefill = register(Settings.booleanBuilder("AutoRefill").withValue(false).withVisibility { fillMode.value != FillMode.INFINITE })
    private val threshold = register(Settings.integerBuilder("RefillThreshold").withValue(8).withRange(1, 56).withVisibility { autoRefill.value && fillMode.value != FillMode.INFINITE })
    private val targetStacks = register(Settings.integerBuilder("TargetStacks").withValue(1).withRange(1, 20).withVisibility { fillMode.value == FillMode.TARGET_STACKS })
    private val delayTicks = register(Settings.integerBuilder("DelayTicks").withValue(5).withRange(0, 10))
    private val interacting = register(Settings.enumBuilder(InteractMode::class.java).withName("InteractMode").withValue(InteractMode.SPOOF))
    private val maxReach = register(Settings.floatBuilder("MaxReach").withValue(4.5F).withRange(1.0f, 6.0f).withStep(0.1f))

    private enum class FillMode(override val displayName: String, val message: String) : DisplayEnum {
        TARGET_STACKS("Target stacks", "Target Stacks Reached"),
        FILL_INVENTORY("Fill inventory", "Inventory filled"),
        INFINITE("Infinite", "")
    }

    enum class State(override val displayName: String) : DisplayEnum {
        SEARCHING("Searching"),
        PLACING("Placing"),
        PRE_MINING("Pre mining"),
        MINING("Mining"),
        COLLECTING("Collecting"),
        DONE("Done")
    }

    private enum class SearchingState(override val displayName: String) : DisplayEnum {
        PLACING("SearchingState"),
        OPENING("Opening"),
        PRE_MINING("Pre mining"),
        MINING("Mining"),
        COLLECTING("Collecting"),
        DONE("Done")
    }

    @Suppress("UNUSED")
    private enum class InteractMode(override val displayName: String) : DisplayEnum {
        OFF("Off"),
        SPOOF("Spoof"),
        VIEW_LOCK("View Lock")
    }

    private enum class ItemID(val id: Int) {
        OBSIDIAN(49),
        ENDER_CHEST(130),
        DIAMOND_PICKAXE(278)
    }

    var pathing = false
    var goal: BlockPos? = null
    var state = State.SEARCHING

    private var active = false
    private var searchingState = SearchingState.PLACING
    private var placingPos = BlockPos(0, -1, 0)
    private var shulkerBoxId = 0
    private var tickCount = 0
    private var lastHitVec: Vec3d? = null

    private val rotateTimer = TimerUtils.TickTimer(TimerUtils.TimeUnit.TICKS)
    private val shulkerOpenTimer = TimerUtils.TickTimer(TimerUtils.TimeUnit.TICKS)
    private val renderer = ESPRenderer().apply { aFilled = 33; aOutline = 233 }

    override fun isActive(): Boolean {
        return isEnabled && active
    }

    override fun onEnable() {
        state = State.SEARCHING
    }

    override fun onDisable() {
        reset()
    }

    init {
        listener<SafeTickEvent> {
            if (it.phase != TickEvent.Phase.END || mc.playerController == null) return@listener

            if (tickCount < delayTicks.value) {
                tickCount++
                return@listener
            } else {
                tickCount = 0
            }

            updateState()
            when (state) {
                State.SEARCHING -> {
                    searchingState()
                }
                State.PLACING -> {
                    placeEnderChest(placingPos)
                }
                State.PRE_MINING -> {
                    mineBlock(placingPos, true)
                }
                State.MINING -> {
                    mineBlock(placingPos, false)
                }
                State.COLLECTING -> {
                    collectDroppedItem(ItemID.OBSIDIAN.id)
                }
                State.DONE -> {
                    if (!autoRefill.value) {
                        sendChatMessage("$chatName ${fillMode.value.message}, disabling.")
                        this.disable()
                    } else {
                        if (active) sendChatMessage("$chatName ${fillMode.value.message}, stopping.")
                        reset()
                    }
                }
            }
        }

        listener<RenderWorldEvent> {
            if (state != State.DONE) renderer.render(clear = false, cull = true)
        }

        listener<OnUpdateWalkingPlayerEvent> {
            if (it.era != KamiEvent.Era.PRE || rotateTimer.tick(20L, false)) return@listener
            doRotation()
        }
    }

    private fun doRotation() {
        val rotation = lastHitVec?.let { Vec2f(getRotationTo(it, true)) } ?: return

        when (interacting.value) {
            InteractMode.SPOOF -> {
                val packet = PlayerPacketManager.PlayerPacket(rotating = true, rotation = rotation )
                PlayerPacketManager.addPacket(this, packet)
            }
            InteractMode.VIEW_LOCK -> {
                mc.player.rotationYaw = rotation.x
                mc.player.rotationPitch = rotation.y
            }
        }
    }

    private fun updateState() {
        if (state != State.DONE) {
            updatePlacingPos()
        }

        if (!active && state != State.DONE) {
            active = true
            BaritoneUtils.primary?.pathingControlManager?.registerProcess(AutoObsidianProcess)
        }

        updateMainState()
        updateSearchingState()
    }

    private fun updatePlacingPos() {
        val eyePos = mc.player.getPositionEyes(1f)
        if (isPositionValid(placingPos, mc.world.getBlockState(placingPos), eyePos)) return

        val posList = VectorUtils.getBlockPosInSphere(eyePos, maxReach.value)
            .sortedBy { it.distanceSqToCenter(eyePos.x, eyePos.y, eyePos.z) }
            .map { it to mc.world.getBlockState(it) }
            .toList()

        val pair = posList.find { it.second.block == Blocks.ENDER_CHEST || it.second.block is BlockShulkerBox }
            ?: posList.find { isPositionValid(it.first, it.second, eyePos) }

        if (pair != null) {
            placingPos = pair.first
            renderer.clear()
            renderer.add(pair.first, ColorHolder(64, 255, 64))
        } else {
            sendChatMessage("$chatName No valid position for placing shulker box / ender chest nearby, disabling.")
            mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            this.disable()
        }
    }

    private fun isPositionValid(pos: BlockPos, blockState: IBlockState, eyePos: Vec3d) =
        !mc.world.getBlockState(pos.down()).material.isReplaceable
            && (blockState.block.let { it == Blocks.ENDER_CHEST || it is BlockShulkerBox }
            || BlockUtils.isPlaceable(pos))
            && mc.world.isAirBlock(pos.up())
            && mc.world.rayTraceBlocks(eyePos, pos.toVec3d())?.let { it.typeOfHit == RayTraceResult.Type.MISS } ?: true

    private fun updateMainState() {
        val passCountCheck = checkObbyCount()

        state = when {
            state == State.DONE && autoRefill.value && InventoryUtils.countItemAll(ItemID.OBSIDIAN.id) <= threshold.value -> {
                State.SEARCHING
            }
            state == State.COLLECTING && (!canPickUpObby() || getDroppedItem(ItemID.OBSIDIAN.id, 16.0f) == null) -> {
                State.DONE
            }
            state != State.DONE && mc.world.isAirBlock(placingPos) && !passCountCheck -> {
                State.COLLECTING
            }
            state == State.MINING && mc.world.isAirBlock(placingPos) -> {
                State.PLACING
            }
            state == State.PLACING && !mc.world.isAirBlock(placingPos) -> {
                State.PRE_MINING
            }
            state == State.SEARCHING && searchingState == SearchingState.DONE && passCountCheck -> {
                State.PLACING
            }
            else -> {
                state
            }
        }
    }

    /**
     * Check if we can pick up more obsidian:
     * There must be at least one slot which is either empty, or contains a stack of obsidian less than 64
     */
    private fun canPickUpObby(): Boolean {
        return fillMode.value == FillMode.INFINITE || mc.player?.inventory?.mainInventory?.any {
            it.isEmpty || it.item.id == ItemID.OBSIDIAN.id && it.count < 64
        } ?: false
    }

    /**
     * @return True if can still place more ender chest
     */
    private fun checkObbyCount(): Boolean {
        val inventory = InventoryUtils.countItemAll(ItemID.OBSIDIAN.id)
        val dropped = EntityUtils.getDroppedItems(ItemID.OBSIDIAN.id, 16.0f).sumBy { it.item.count }

        return when (fillMode.value!!) {
            FillMode.TARGET_STACKS -> {
                ((inventory + dropped) / 8.0f).ceilToInt() / 8 < targetStacks.value
            }
            FillMode.FILL_INVENTORY -> {
                countEmptySlots() - dropped >= 8
            }
            FillMode.INFINITE -> {
                true
            }
        }
    }

    private fun countEmptySlots(): Int {
        return mc.player?.inventory?.mainInventory?.sumBy {
            when {
                it.isEmpty -> 64
                it.item.id == ItemID.OBSIDIAN.id -> 64 - it.count
                else -> 0
            }
        } ?: 0
    }

    private fun updateSearchingState() {
        if (state == State.SEARCHING) {
            if (searchingState != SearchingState.DONE) {
                searchingState = when {
                    searchingState == SearchingState.PLACING && InventoryUtils.countItemAll(ItemID.ENDER_CHEST.id) > 0 -> {
                        SearchingState.DONE
                    }
                    searchingState == SearchingState.COLLECTING && getDroppedItem(shulkerBoxId, 16.0f) == null -> {
                        SearchingState.DONE
                    }
                    searchingState == SearchingState.MINING && mc.world.isAirBlock(placingPos) -> {
                        if (InventoryUtils.countItemAll(ItemID.ENDER_CHEST.id) > 0) {
                            SearchingState.COLLECTING
                        } else {
                            // In case if the shulker wasn't placed due to server lag
                            SearchingState.PLACING
                        }
                    }
                    searchingState == SearchingState.OPENING && (InventoryUtils.countItemAll(ItemID.ENDER_CHEST.id) >= 64
                        || InventoryUtils.getSlots(0, 35, 0) == null) -> {
                        SearchingState.PRE_MINING
                    }
                    searchingState == SearchingState.PLACING && !mc.world.isAirBlock(placingPos) -> {
                        if (mc.world.getBlockState(placingPos).block is BlockShulkerBox) {
                            SearchingState.OPENING
                        } else {
                            // In case if the shulker wasn't placed due to server lag
                            SearchingState.PRE_MINING
                        }
                    }
                    else -> {
                        searchingState
                    }
                }
            }
        } else {
            searchingState = SearchingState.PLACING
        }
    }

    private fun searchingState() {
        if (searchShulker.value) {
            when (searchingState) {
                SearchingState.PLACING -> placeShulker(placingPos)
                SearchingState.OPENING -> openShulker(placingPos)
                SearchingState.PRE_MINING -> mineBlock(placingPos, true)
                SearchingState.MINING -> mineBlock(placingPos, false)
                SearchingState.COLLECTING -> collectDroppedItem(shulkerBoxId)
                SearchingState.DONE -> {
                    updatePlacingPos()
                }
            }
        } else {
            searchingState = SearchingState.DONE
        }
    }

    private fun placeShulker(pos: BlockPos) {
        for (i in 219..234) {
            if (InventoryUtils.getSlotsHotbar(i) == null) {
                if (i != 234) continue else {
                    sendChatMessage("$chatName No shulker box was found in hotbar, disabling.")
                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                    this.disable()
                    return
                }
            }
            shulkerBoxId = i
            InventoryUtils.swapSlotToItem(i)
            break
        }

        if (mc.world.getBlockState(pos).block !is BlockShulkerBox) {
            placeBlock(pos)
        }
    }

    private fun placeEnderChest(pos: BlockPos) {
        /* Case where we need to move ender chests into the hotbar */
        if (InventoryUtils.getSlotsHotbar(ItemID.ENDER_CHEST.id) == null && InventoryUtils.getSlotsNoHotbar(ItemID.ENDER_CHEST.id) != null) {
            InventoryUtils.moveToHotbar(ItemID.ENDER_CHEST.id, ItemID.DIAMOND_PICKAXE.id)
            return
        } else if (InventoryUtils.getSlots(0, 35, ItemID.ENDER_CHEST.id) == null) {
            /* Case where we are out of ender chests */
            if (searchShulker.value) {
                state = State.SEARCHING
            } else {
                sendChatMessage("$chatName No ender chest was found in inventory, disabling.")
                mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                this.disable()
                return
            }
        }

        /* Else, we already have ender chests in the hotbar */
        InventoryUtils.swapSlotToItem(ItemID.ENDER_CHEST.id)

        placeBlock(pos)
    }

    private fun openShulker(pos: BlockPos) {
        if (mc.currentScreen is GuiShulkerBox) {
            val container = mc.player.openContainer
            val slot = container.inventory.subList(0, 27).indexOfFirst { it.item.id == ItemID.ENDER_CHEST.id }

            if (slot != -1) {
                InventoryUtils.inventoryClick(container.windowId, slot, 0, ClickType.QUICK_MOVE)
                mc.player.closeScreen()
            } else if (shulkerOpenTimer.tick(100, false)) { // Wait for maximum of 5 seconds
                sendChatMessage("$chatName No ender chest was found in shulker, disabling.")
                mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                disable()
            }
        } else {
            val side = EnumFacing.getDirectionFromEntityLiving(pos, mc.player)
            val hitVecOffset = BlockUtils.getHitVecOffset(side)

            lastHitVec = BlockUtils.getHitVec(pos, side)
            rotateTimer.reset()

            if (shulkerOpenTimer.tick(50)) {
                // TODO: Replace with defaultScope later
                moduleScope.launch {
                    delay(10L)
                    onMainThreadSafe {
                        connection.sendPacket(CPacketPlayerTryUseItemOnBlock(pos, side, EnumHand.MAIN_HAND, hitVecOffset.x.toFloat(), hitVecOffset.y.toFloat(), hitVecOffset.z.toFloat()))
                        player.swingArm(EnumHand.MAIN_HAND)
                    }
                }
            }
        }
    }

    private fun placeBlock(pos: BlockPos) {
        val pair = BlockUtils.getNeighbour(pos, 1, 6.5f)
            ?: run {
                sendChatMessage("Can't find neighbour block")
                return
            }

        lastHitVec = BlockUtils.getHitVec(pair.second, pair.first)
        rotateTimer.reset()

        mc.connection?.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING))

        // TODO: Replace with defaultScope later
        moduleScope.launch {
            delay(10L)
            onMainThreadSafe {
                placeBlock(pair.second, pair.first)
                if (NoBreakAnimation.isEnabled) NoBreakAnimation.resetMining()
            }

            delay(10L)
            onMainThreadSafe {
                connection.sendPacket(CPacketEntityAction(Companion.mc.player, CPacketEntityAction.Action.STOP_SNEAKING))
            }
        }
    }

    private fun mineBlock(pos: BlockPos, pre: Boolean): Boolean {
        if (pre) {
            if (InventoryUtils.getSlotsHotbar(ItemID.DIAMOND_PICKAXE.id) == null && InventoryUtils.getSlotsNoHotbar(ItemID.DIAMOND_PICKAXE.id) != null) {
                InventoryUtils.moveToHotbar(ItemID.DIAMOND_PICKAXE.id, ItemID.ENDER_CHEST.id)
                return false
            } else if (InventoryUtils.getSlots(0, 35, ItemID.DIAMOND_PICKAXE.id) == null) {
                sendChatMessage("No pickaxe was found in inventory.")
                mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                return false
            }
            InventoryUtils.swapSlotToItem(ItemID.DIAMOND_PICKAXE.id)
        }

        val side = EnumFacing.getDirectionFromEntityLiving(pos, mc.player)
        lastHitVec = BlockUtils.getHitVec(pos, side)
        rotateTimer.reset()

        // TODO: Replace with defaultScope later
        moduleScope.launch {
            delay(5L)
            onMainThreadSafe {
                if (pre) {
                    connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, side))
                    if (state != State.SEARCHING) state = State.MINING else searchingState = SearchingState.MINING
                } else {
                    connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, side))
                }
                player.swingArm(EnumHand.MAIN_HAND)
            }
        }
        return true
    }

    private fun collectDroppedItem(itemId: Int) {
        pathing = if (getDroppedItem(itemId, 16.0f) != null) {
            goal = getDroppedItem(itemId, 16.0f)
            true
        } else false
    }

    private fun reset() {
        active = false
        pathing = false
        searchingState = SearchingState.PLACING
        placingPos = BlockPos(0, -1, 0)
        tickCount = 0
        lastHitVec = null
    }
}
