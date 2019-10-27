package me.zeroeightsix.kami.module.modules.combat;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.*;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

import static me.zeroeightsix.kami.module.modules.combat.CrystalAura.getPlayerPos;

/**
 * Created by hub on 7 August 2019
 * Updated by hub on 27 October 2019
 */
@Module.Info(name = "Auto32k", category = Module.Category.COMBAT)
public class Auto32k extends Module {

    private static final List<Block> shulkerList = Arrays.asList(
            Blocks.WHITE_SHULKER_BOX,
            Blocks.ORANGE_SHULKER_BOX,
            Blocks.MAGENTA_SHULKER_BOX,
            Blocks.LIGHT_BLUE_SHULKER_BOX,
            Blocks.YELLOW_SHULKER_BOX,
            Blocks.LIME_SHULKER_BOX,
            Blocks.PINK_SHULKER_BOX,
            Blocks.GRAY_SHULKER_BOX,
            Blocks.SILVER_SHULKER_BOX,
            Blocks.CYAN_SHULKER_BOX,
            Blocks.PURPLE_SHULKER_BOX,
            Blocks.BLUE_SHULKER_BOX,
            Blocks.BROWN_SHULKER_BOX,
            Blocks.GREEN_SHULKER_BOX,
            Blocks.RED_SHULKER_BOX,
            Blocks.BLACK_SHULKER_BOX
    );

    private static final DecimalFormat df = new DecimalFormat("#.#");

    private Setting<Boolean> moveToHotbar = register(Settings.b("Move 32k to Hotbar", true));
    private Setting<Double> placeRange = register(Settings.d("Place Range", 4.0d));
    private Setting<Integer> yOffset = register(Settings.i("Y Offset", 2));
    private Setting<Boolean> placeBehind = register(Settings.b("Place behind", true));
    private Setting<Boolean> placeObi = register(Settings.b("Obi on Top", true));
    private Setting<Boolean> spoofRotation = register(Settings.b("Spoof Rotation", true));
    private Setting<Boolean> raytraceCheck = register(Settings.b("Raytrace Check", true));
    private Setting<Boolean> debugMessages = register(Settings.b("Debug Messages", false));

    private int swordSlot;

    @Override
    protected void onEnable() {

        if (isDisabled() || mc.player == null || ModuleManager.isModuleEnabled("Freecam")) {
            this.disable();
            return;
        }

        df.setRoundingMode(RoundingMode.CEILING);

        int hopperSlot = -1;
        int shulkerSlot = -1;
        int obiSlot = -1;
        swordSlot = -1;

        for (int i = 0; i < 9; i++) {

            if (hopperSlot != -1 && shulkerSlot != -1 && obiSlot != -1) {
                break;
            }

            ItemStack stack = mc.player.inventory.getStackInSlot(i);

            if (stack == ItemStack.EMPTY || !(stack.getItem() instanceof ItemBlock)) {
                continue;
            }

            Block block = ((ItemBlock) stack.getItem()).getBlock();

            if (block == Blocks.HOPPER) {
                hopperSlot = i;
            } else if (shulkerList.contains(block)) {
                shulkerSlot = i;
            } else if (block == Blocks.OBSIDIAN) {
                obiSlot = i;
            }

        }

        BlockPos basePos = new BlockPos(mc.player.getPositionVector());

        if (hopperSlot == -1) {
            if (debugMessages.getValue()) {
                Command.sendChatMessage("Hopper missing, disabling.");
            }
            this.disable();
            return;
        }

        if (shulkerSlot == -1) {
            if (debugMessages.getValue()) {
                Command.sendChatMessage("Shulker missing, disabling.");
            }
            this.disable();
            return;
        }

        EnumFacing facingDirection;

        if (placeBehind.getValue()) {
            facingDirection = EnumFacing.fromAngle(mc.player.rotationYaw).getOpposite();
        } else {
            facingDirection = EnumFacing.fromAngle(mc.player.rotationYaw);
        }

        int range = (int) Math.ceil(placeRange.getValue());
        int yOffsetSanitized = yOffset.getValue() < 0 ? -1 * yOffset.getValue() : yOffset.getValue();

        BlockPos placeTargetPos = findPlaceAreaInRow(basePos, yOffsetSanitized, range, facingDirection);

        if (placeTargetPos == null) {
            if (debugMessages.getValue()) {
                Command.sendChatMessage("Not enough space to place optimal Hopper, searching for Blocks in a sphere.");
            }
            CrystalAura ca = (CrystalAura) ModuleManager.getModuleByName("CrystalAura");
            // TODO - sorting for optimal hopper placement (good kill possibility > bad kill possibility) goes here
            for (BlockPos pos : ca.getSphere(getPlayerPos(), range, range, false, true, 0)) {
                if (findPlaceArea(pos) != null) {
                    placeTargetPos = pos;
                    break;
                }
            }
        }

        if (placeTargetPos == null) {
            if (debugMessages.getValue()) {
                Command.sendChatMessage("Not enough space, disabling.");
            }
            this.disable();
            return;
        }

        if (debugMessages.getValue()) {
            Command.sendChatMessage("Place Target: " + placeTargetPos.toString() + " Distance: " + df.format(mc.player.getPositionVector().distanceTo(new Vec3d(placeTargetPos))));
        }

        mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING));

        mc.player.inventory.currentItem = hopperSlot;
        placeBlock(new BlockPos(placeTargetPos), spoofRotation.getValue());

        mc.player.inventory.currentItem = shulkerSlot;
        placeBlock(new BlockPos(placeTargetPos.add(0, 1, 0)), spoofRotation.getValue());

        if (placeObi.getValue() && obiSlot != -1) {
            mc.player.inventory.currentItem = obiSlot;
            placeBlock(new BlockPos(placeTargetPos.add(0, 2, 0)), spoofRotation.getValue());
        }

        mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING));

        mc.player.inventory.currentItem = shulkerSlot;
        BlockPos hopperPos = new BlockPos(placeTargetPos);
        mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(hopperPos, EnumFacing.DOWN, EnumHand.MAIN_HAND, 0, 0, 0));
        swordSlot = shulkerSlot + 32;

    }

    @Override
    public void onUpdate() {

        if (isDisabled() || mc.player == null || ModuleManager.isModuleEnabled("Freecam")) {
            return;
        }

        if (!(mc.currentScreen instanceof GuiContainer)) {
            return;
        }

        if (!moveToHotbar.getValue()) {
            this.disable();
            return;
        }

        if (swordSlot == -1) {
            return;
        }

        boolean swapReady = true;

        if (((GuiContainer) mc.currentScreen).inventorySlots.getSlot(0).getStack().isEmpty) {
            swapReady = false;
        }

        if (!((GuiContainer) mc.currentScreen).inventorySlots.getSlot(swordSlot).getStack().isEmpty) {
            swapReady = false;
        }

        if (swapReady) {
            mc.playerController.windowClick(((GuiContainer) mc.currentScreen).inventorySlots.windowId, 0, swordSlot - 32, ClickType.SWAP, mc.player);
            this.disable();
        }

    }

    private BlockPos findPlaceAreaInRow(BlockPos placeTestNextPos, int yOffsetSanitized, int range, EnumFacing facingDirection) {

        for (int i = range; i > 0; i--) {

            placeTestNextPos = placeTestNextPos.add(facingDirection.getXOffset(), 0, facingDirection.getZOffset());

            for (int j = (-1 * yOffsetSanitized); j < (1 + yOffsetSanitized); j++) {

                BlockPos testPos = findPlaceArea(placeTestNextPos.add(0, j, 0));
                if (testPos != null) {
                    return testPos;
                }

            }

        }

        return null;

    }

    private BlockPos findPlaceArea(BlockPos blockPos) {

        for (Entity entity : mc.world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(blockPos))) {
            if (entity instanceof EntityLivingBase) {
                return null; // entity on block
            }
        }

        if (!mc.world.getBlockState(blockPos).getMaterial().isReplaceable()) {
            return null; // no space for hopper
        }

        if (!mc.world.getBlockState(blockPos.add(0, 1, 0)).getMaterial().isReplaceable()) {
            return null; // no space for shulker
        }

        if (mc.world.getBlockState(blockPos.add(0, -1, 0)).getBlock() instanceof BlockAir) {
            return null; // air below hopper
        }

        if (mc.world.getBlockState(blockPos.add(0, -1, 0)).getBlock() instanceof BlockLiquid) {
            return null; // liquid below hopper
        }

        if (mc.player.getPositionVector().distanceTo(new Vec3d(blockPos)) > placeRange.getValue()) {
            return null; // out of range
        }

        if (raytraceCheck.getValue()) {
            RayTraceResult result = mc.world.rayTraceBlocks(new Vec3d(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ), new Vec3d(blockPos), false, true, false);
            if (!(result == null || result.getBlockPos().equals(blockPos))) {
                return null;
            }
        }

        return blockPos;

    }

    private static void placeBlock(BlockPos pos, boolean spoofRotation) {

        if (!mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
            return;
        }

        for (EnumFacing side : EnumFacing.values()) {

            BlockPos neighbor = pos.offset(side);
            EnumFacing side2 = side.getOpposite();

            if (!mc.world.getBlockState(neighbor).getBlock().canCollideCheck(mc.world.getBlockState(neighbor), false)) {
                continue;
            }

            Vec3d hitVec = new Vec3d(neighbor).add(0.5, 0.5, 0.5).add(new Vec3d(side2.getDirectionVec()).scale(0.5));

            if (spoofRotation) {
                Vec3d eyesPos = new Vec3d(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ);
                double diffX = hitVec.x - eyesPos.x;
                double diffY = hitVec.y - eyesPos.y;
                double diffZ = hitVec.z - eyesPos.z;
                double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
                float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
                float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
                mc.player.connection.sendPacket(new CPacketPlayer.Rotation(mc.player.rotationYaw + MathHelper.wrapDegrees(yaw - mc.player.rotationYaw), mc.player.rotationPitch + MathHelper.wrapDegrees(pitch - mc.player.rotationPitch), mc.player.onGround));
            }

            mc.playerController.processRightClickBlock(mc.player, mc.world, neighbor, side2, hitVec, EnumHand.MAIN_HAND);
            mc.player.swingArm(EnumHand.MAIN_HAND);
            mc.rightClickDelayTimer = 4;

            return;

        }

    }

}
