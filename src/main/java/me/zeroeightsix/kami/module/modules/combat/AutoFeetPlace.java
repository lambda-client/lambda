package me.zeroeightsix.kami.module.modules.combat;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.BlockInteractionHelper;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockObsidian;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import static me.zeroeightsix.kami.util.BlockInteractionHelper.*;

/**
 * Created 13 August 2019 by hub
 * Updated 21 November 2019 by hub
 */
@Module.Info(name = "AutoFeetPlace", category = Module.Category.COMBAT)
public class AutoFeetPlace extends Module {

    private final Vec3d[] surroundTargets = {
            new Vec3d(1, 0, 0),
            new Vec3d(0, 0, 1),
            new Vec3d(-1, 0, 0),
            new Vec3d(0, 0, -1),
            new Vec3d(1, -1, 0),
            new Vec3d(0, -1, 1),
            new Vec3d(-1, -1, 0),
            new Vec3d(0, -1, -1),
            new Vec3d(0, -1, 0)
    };

    private Setting<Boolean> triggerable = register(Settings.b("Triggerable", true));
    private Setting<Integer> triggerableTimeoutTicks = register(Settings.i("Triggerable Timeout (Ticks)", 20));
    private Setting<Integer> blockPerTick = register(Settings.i("Blocks per Tick", 4));
    private Setting<Boolean> rotate = register(Settings.b("Rotate", true));
    private Setting<Boolean> announceUsage = register(Settings.b("Announce Usage", false));
    private Setting<Boolean> debugMessages = register(Settings.b("Debug Messages", false));

    private int playerHotbarSlot = -1;
    private int lastHotbarSlot = -1;
    private int offsetStep = 0;

    private int totalTickRuns = 0;

    private boolean isSneaking = false;

    @Override
    protected void onEnable() {

        if (mc.player == null) {
            this.disable();
            return;
        }

        // save initial player hand
        playerHotbarSlot = Wrapper.getPlayer().inventory.currentItem;
        lastHotbarSlot = -1;

        if (announceUsage.getValue()) {
            Command.sendChatMessage("[AutoFeetPlace] Enabled!");
        }

    }

    @Override
    protected void onDisable() {

        if (mc.player == null) {
            return;
        }

        // load initial player hand
        if (lastHotbarSlot != playerHotbarSlot && playerHotbarSlot != -1) {
            Wrapper.getPlayer().inventory.currentItem = playerHotbarSlot;
        }

        if (isSneaking) {
            mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING));
            isSneaking = false;
        }

        playerHotbarSlot = -1;
        lastHotbarSlot = -1;

        if (announceUsage.getValue()) {
            Command.sendChatMessage("[AutoFeetPlace] Disabled!");
        }

    }

    @Override
    public void onUpdate() {

        if (mc.player == null || ModuleManager.isModuleEnabled("Freecam")) {
            return;
        }

        if (triggerable.getValue() && totalTickRuns >= triggerableTimeoutTicks.getValue()) {
            if (debugMessages.getValue()) {
                Command.sendChatMessage("[AutoFeetPlace] Timeout reached.");
            }
            totalTickRuns = 0;
            this.disable();
            return;
        }

        int blocksPlaced = 0;

        while (blocksPlaced < blockPerTick.getValue()) {

            if (offsetStep >= surroundTargets.length) {
                offsetStep = 0;
                break;
            }

            BlockPos offsetPos = new BlockPos(surroundTargets[offsetStep]);
            BlockPos targetPos = new BlockPos(mc.player.getPositionVector()).add(offsetPos.x, offsetPos.y, offsetPos.z);

            boolean shouldTryToPlace = true;

            // check if block is already placed
            if (!Wrapper.getWorld().getBlockState(targetPos).getMaterial().isReplaceable()) {
                shouldTryToPlace = false;
            }

            // check if entity blocks placing
            for (Entity entity : mc.world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(targetPos))) {
                if (!(entity instanceof EntityItem) && !(entity instanceof EntityXPOrb)) {
                    shouldTryToPlace = false;
                    break;
                }
            }

            if (shouldTryToPlace) {
                if (placeBlock(targetPos)) {
                    blocksPlaced++;
                }
            }

            offsetStep++;

        }

        if (blocksPlaced > 0) {

            if (lastHotbarSlot != playerHotbarSlot && playerHotbarSlot != -1) {
                Wrapper.getPlayer().inventory.currentItem = playerHotbarSlot;
                lastHotbarSlot = playerHotbarSlot;
            }

        }

        totalTickRuns++;

    }

    private boolean placeBlock(BlockPos pos) {

        // check if block at pos is replaceable
        if (!mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
            return false;
        }

        // check if we have a block adjacent to blockpos to click at
        if (!checkForNeighbours(pos)) {
            return false;
        }

        for (EnumFacing side : EnumFacing.values()) {

            BlockPos neighbor = pos.offset(side);
            EnumFacing side2 = side.getOpposite();

            // check if neighbor can be right clicked
            if (!canBeClicked(neighbor)) {
                continue;
            }

            int obiSlot = findObiInHotbar();

            // check if any blocks were found
            if (obiSlot == -1) {
                this.disable();
                return false;
            }

            if (lastHotbarSlot != obiSlot) {
                Wrapper.getPlayer().inventory.currentItem = obiSlot;
                lastHotbarSlot = obiSlot;
            }

            Block neighborPos = mc.world.getBlockState(neighbor).getBlock();
            if (BlockInteractionHelper.blackList.contains(neighborPos) || BlockInteractionHelper.shulkerList.contains(neighborPos)) {
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING));
                isSneaking = true;
            }

            Vec3d hitVec = new Vec3d(neighbor).add(0.5, 0.5, 0.5).add(new Vec3d(side2.getDirectionVec()).scale(0.5));

            // fake rotation
            if (rotate.getValue()) {
                faceVectorPacketInstant(hitVec);
            }

            // place block
            mc.playerController.processRightClickBlock(mc.player, mc.world, neighbor, side2, hitVec, EnumHand.MAIN_HAND);
            mc.player.swingArm(EnumHand.MAIN_HAND);

            return true;

        }

        return false;

    }

    private int findObiInHotbar() {

        // search blocks in hotbar
        int slot = -1;
        for (int i = 0; i < 9; i++) {

            // filter out non-block items
            ItemStack stack = Wrapper.getPlayer().inventory.getStackInSlot(i);

            if (stack == ItemStack.EMPTY || !(stack.getItem() instanceof ItemBlock)) {
                continue;
            }

            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (block instanceof BlockObsidian) {
                slot = i;
                break;
            }

        }

        return slot;

    }

}
