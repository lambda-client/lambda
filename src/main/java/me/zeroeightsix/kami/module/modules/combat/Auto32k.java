package me.zeroeightsix.kami.module.modules.combat;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.Wrapper;
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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Created by hub on 7 August 2019
 * Updated by hub on 25 October 2019
 */
@Module.Info(name = "Auto32k", category = Module.Category.COMBAT)
public class Auto32k extends Module {

    private static final DecimalFormat df = new DecimalFormat("#.#");
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

    private Setting<Boolean> moveToHotbar = register(Settings.b("Move 32k to Hotbar", true));
    private Setting<Double> placeRange = register(Settings.d("Place Range", 4.0d));
    private Setting<Integer> yOffset = register(Settings.i("Y Offset (both directions)", 2));
    private Setting<Boolean> placeBehind = register(Settings.b("Place behind", true));
    private Setting<Boolean> placeObi = register(Settings.b("Obi on Top", true));
    private Setting<Boolean> spoofRotation = register(Settings.b("Spoof Rotation", true));
    private Setting<Boolean> debugMessages = register(Settings.b("Debug Messages", false));

    private int swordSlot;

    private static void placeBlock(BlockPos pos, boolean spoofRotation) {

        if (!Wrapper.getWorld().getBlockState(pos).getMaterial().isReplaceable()) {
            return;
        }

        for (EnumFacing side : EnumFacing.values()) {

            BlockPos neighbor = pos.offset(side);
            EnumFacing side2 = side.getOpposite();

            if (!Wrapper.getWorld().getBlockState(neighbor).getBlock().canCollideCheck(Wrapper.getWorld().getBlockState(neighbor), false)) {
                continue;
            }

            Vec3d hitVec = new Vec3d(neighbor).add(0.5, 0.5, 0.5).add(new Vec3d(side2.getDirectionVec()).scale(0.5));

            if (spoofRotation) {
                Vec3d eyesPos = new Vec3d(Wrapper.getPlayer().posX, Wrapper.getPlayer().posY + Wrapper.getPlayer().getEyeHeight(), Wrapper.getPlayer().posZ);
                double diffX = hitVec.x - eyesPos.x;
                double diffY = hitVec.y - eyesPos.y;
                double diffZ = hitVec.z - eyesPos.z;
                double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
                float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
                float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
                Wrapper.getPlayer().connection.sendPacket(new CPacketPlayer.Rotation(Wrapper.getPlayer().rotationYaw + MathHelper.wrapDegrees(yaw - Wrapper.getPlayer().rotationYaw), Wrapper.getPlayer().rotationPitch + MathHelper.wrapDegrees(pitch - Wrapper.getPlayer().rotationPitch), Wrapper.getPlayer().onGround));
            }

            mc.playerController.processRightClickBlock(Wrapper.getPlayer(), mc.world, neighbor, side2, hitVec, EnumHand.MAIN_HAND);
            Wrapper.getPlayer().swingArm(EnumHand.MAIN_HAND);
            mc.rightClickDelayTimer = 4;

            return;

        }

    }

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

            ItemStack stack = Wrapper.getPlayer().inventory.getStackInSlot(i);

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
        BlockPos placeTestNextPos = basePos;
        BlockPos placeTestSuccessfull = null;

        if (placeBehind.getValue()) {
            facingDirection = EnumFacing.fromAngle(mc.player.rotationYaw).getOpposite();
        } else {
            facingDirection = EnumFacing.fromAngle(mc.player.rotationYaw);
        }

        int range = (int) Math.ceil(placeRange.getValue());
        //int yOffsetSanitized = yOffset.getValue() < 0 ? -1 * yOffset.getValue() : yOffset.getValue();
        int yOffsetSanitized = yOffset.getValue();

        // searching placeable area in front / behind the player in a straight line
        // we could test interations on both sides of the player while range - n (where n is iterations)
        // but i think this would not work in most cases cause of raytrace checks on click
        for (int i = range; i > 0; i--) {

            placeTestNextPos = placeTestNextPos.add(facingDirection.getXOffset(), 0, facingDirection.getZOffset());

            // max y Offset is 2 Blocks (3 iterations)
            for (int j = -1 * yOffsetSanitized; j < (1 + yOffsetSanitized); j++) {

                BlockPos placeTestNextPosOffsetY = placeTestNextPos.add(0, j, 0);

                boolean EntityLivingBaseOnTargetBlockPos = false;
                for (Entity entity : mc.world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(placeTestNextPosOffsetY))) {
                    if (entity instanceof EntityLivingBase) {
                        EntityLivingBaseOnTargetBlockPos = true;
                        break;
                    }
                }

                if (EntityLivingBaseOnTargetBlockPos) {
                    continue; // entity on block
                }

                if (!Wrapper.getWorld().getBlockState(placeTestNextPosOffsetY).getMaterial().isReplaceable()) {
                    continue; // no space for hopper
                }

                if (!Wrapper.getWorld().getBlockState(placeTestNextPosOffsetY.add(0, 1, 0)).getMaterial().isReplaceable()) {
                    continue; // no space for shulker
                }

                if (Wrapper.getWorld().getBlockState(placeTestNextPosOffsetY.add(0, -1, 0)).getBlock() instanceof BlockAir) {
                    continue; // air below hopper
                }

                if (Wrapper.getWorld().getBlockState(placeTestNextPosOffsetY.add(0, -1, 0)).getBlock() instanceof BlockLiquid) {
                    continue; // liquid below hopper
                }

                if (mc.player.getPositionVector().distanceTo(new Vec3d(placeTestNextPosOffsetY)) > placeRange.getValue()) {
                    continue; // out of range
                }

                placeTestSuccessfull = placeTestNextPosOffsetY;
                break;

            }

        }

        if (placeTestSuccessfull == null) {
            if (debugMessages.getValue()) {
                Command.sendChatMessage("Not enough space, disabling.");
            }
            this.disable();
            return;
        }

        BlockPos placeTargetPos = placeTestSuccessfull;

        if (debugMessages.getValue()) {
            Command.sendChatMessage("Place Target: " + placeTargetPos.toString() + " Distance: " + df.format(mc.player.getPositionVector().distanceTo(new Vec3d(placeTargetPos))));
        }

        mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING));

        Wrapper.getPlayer().inventory.currentItem = hopperSlot;
        placeBlock(new BlockPos(placeTargetPos), spoofRotation.getValue());

        Wrapper.getPlayer().inventory.currentItem = shulkerSlot;
        placeBlock(new BlockPos(placeTargetPos.add(0, 1, 0)), spoofRotation.getValue());

        if (placeObi.getValue() && obiSlot != -1) {
            Wrapper.getPlayer().inventory.currentItem = obiSlot;
            placeBlock(new BlockPos(placeTargetPos.add(0, 2, 0)), spoofRotation.getValue());
        }

        mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING));

        Wrapper.getPlayer().inventory.currentItem = shulkerSlot;
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

}
