package me.zeroeightsix.kami.module.modules.sdashb.combat;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemShulkerBox;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import static me.zeroeightsix.kami.module.modules.player.Scaffold.faceVectorPacketInstant;

/**
 * Fluffy made this for waizy <3
 */

@Module.Info(name = "Auto32kBypass", category = Module.Category.COMBAT)
public class Auto32kBypass extends Module{

    private Setting<Integer> delay = register(Settings.i("Delay", 15));

    int hopperIndex, shulkerIndex, redstoneIndex, dispenserIndex, obiIndex;
    int placeTick = 1;
    BlockPos origin, hopperPos;
    EnumFacing horizontalFace;

    @Override
    public void onEnable() {
        if(mc == null && mc.player == null) {
            return;
        }
        hopperIndex = shulkerIndex = redstoneIndex = dispenserIndex = obiIndex = -1;
        placeTick = 1;
        if(mc != null && mc.player != null && mc.objectMouseOver != null) {
            origin = new BlockPos((double)mc.objectMouseOver.getBlockPos().getX(),(double)mc.objectMouseOver.getBlockPos().getY(),(double)mc.objectMouseOver.getBlockPos().getZ());
            horizontalFace = mc.player.getHorizontalFacing();
            hopperPos = origin.offset(horizontalFace.getOpposite()).up();
        } else {
            this.toggle();
        }
    }

    @Override
    public void onUpdate() {
        if(mc == null && mc.player == null) {
            return;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = Minecraft.getMinecraft().player.inventory.mainInventory.get(i);
            if (itemStack.getItem().equals(Item.getItemFromBlock(Blocks.HOPPER))) {hopperIndex = i;}
            if (itemStack.getItem().equals(Item.getItemFromBlock(Blocks.OBSIDIAN))) {obiIndex = i;}
            if (itemStack.getItem() instanceof ItemShulkerBox) {shulkerIndex = i;}
            if (itemStack.getItem().equals(Item.getItemFromBlock(Blocks.REDSTONE_BLOCK))) {redstoneIndex = i;}
            if (itemStack.getItem().equals(Item.getItemFromBlock(Blocks.DISPENSER))) {dispenserIndex = i;}}

        placeTick++;
        if(checkNulls()) {
            if(placeTick == 3) {
                Vec3d vec = new Vec3d(origin.getX(), origin.getY(), origin.getZ());
                changeItem(obiIndex);
                placeBlock(origin, EnumFacing.UP, vec);

                changeItem(dispenserIndex);
                placeBlock(origin.up(), EnumFacing.UP, vec);

                changeItem(hopperIndex);
                BlockPos obiBase = origin.up();
                placeBlock(obiBase, horizontalFace.getOpposite(), new Vec3d(obiBase.getX(), obiBase.getY(), obiBase.getZ()));

                BlockPos dispenserPos = origin.up().up();
                faceBlock(dispenserPos, EnumFacing.DOWN);
                mc.playerController.processRightClickBlock(mc.player, mc.world, dispenserPos, EnumFacing.UP, new Vec3d(dispenserPos.getX(), dispenserPos.getY(), dispenserPos.getZ()), EnumHand.MAIN_HAND);
                mc.player.swingArm(EnumHand.MAIN_HAND);

                changeItem(shulkerIndex);
                placeTick = 4;
            }
            if(placeTick == delay.getValue()+4) {
                mc.playerController.windowClick(mc.player.openContainer.windowId, 1, mc.player.inventory.currentItem, ClickType.SWAP, mc.player);
                mc.player.closeScreen();
                placeTick = delay.getValue()+4;
            }
            if(placeTick == delay.getValue()+10) {
                mc.player.connection.sendPacket(new CPacketEntityAction(Minecraft.getMinecraft().player, CPacketEntityAction.Action.START_SNEAKING));

                EnumFacing left = null, right = null;

                if(horizontalFace == EnumFacing.NORTH) {left = EnumFacing.WEST; right = EnumFacing.EAST;} else if(horizontalFace == EnumFacing.EAST) {left = EnumFacing.NORTH; right = EnumFacing.SOUTH;} else if(horizontalFace == EnumFacing.SOUTH) {left = EnumFacing.EAST; right = EnumFacing.WEST;} else if(horizontalFace == EnumFacing.WEST) {left = EnumFacing.SOUTH; right = EnumFacing.NORTH;}
                changeItem(redstoneIndex);

                if(left != null && right != null) {
                    BlockPos dispenserPos = origin.up().up();
                    if(isAir(dispenserPos.offset(left))) {
                        placeBlock(dispenserPos, left.getOpposite(), new Vec3d(dispenserPos.getX(), dispenserPos.getY(), dispenserPos.getZ()));
                    } else if(isAir(dispenserPos.offset(right))) {
                        placeBlock(dispenserPos, right.getOpposite(), new Vec3d(dispenserPos.getX(), dispenserPos.getY(), dispenserPos.getZ()));
                    }
                }
                mc.player.connection.sendPacket(new CPacketEntityAction(Minecraft.getMinecraft().player, CPacketEntityAction.Action.STOP_SNEAKING));
                faceBlock(hopperPos, EnumFacing.UP);
                mc.playerController.processRightClickBlock(mc.player, mc.world, hopperPos, EnumFacing.UP, new Vec3d(hopperPos.getX(), hopperPos.getY(), hopperPos.getZ()), EnumHand.MAIN_HAND);
                mc.player.swingArm(EnumHand.MAIN_HAND);
                this.toggle();
            }
        } else {
            this.toggle();
        }
    }

    public boolean checkNulls() {
        if(hopperIndex != -1 && shulkerIndex != -1 && redstoneIndex != -1 && dispenserIndex != -1 && obiIndex != -1 && origin != null && horizontalFace != null && hopperPos != null) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isAir(BlockPos pos) {
        if (getBlock(pos) instanceof BlockAir) {
            return true;
        } else {
            return false;
        }
    }

    public Block getBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock();
    }

    public void changeItem(int slot) {
        mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
        mc.player.inventory.currentItem = slot;
    }

    public void placeBlock(BlockPos pos, EnumFacing facing, Vec3d vec) {
        Vec3d hitVec = new Vec3d(pos.offset(facing)).add(0.5, 0.5, 0.5).add(new Vec3d(facing.getDirectionVec()).scale(0.5));
        faceVectorPacketInstant(hitVec);
        mc.playerController.processRightClickBlock(Minecraft.getMinecraft().player, Minecraft.getMinecraft().world, pos, facing, vec, EnumHand.MAIN_HAND);
        mc.player.swingArm(EnumHand.MAIN_HAND);
    }

    public void faceBlock(BlockPos pos, EnumFacing face) {
        Vec3d hitVec = new Vec3d(pos.offset(face)).add(0.5, 0.5, 0.5).add(new Vec3d(face.getDirectionVec()).scale(0.5));
        faceVectorPacketInstant(hitVec);
    }

}