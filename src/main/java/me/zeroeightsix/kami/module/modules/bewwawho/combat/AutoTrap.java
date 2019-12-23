package me.zeroeightsix.kami.module.modules.bewwawho.combat;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.zeroeightysix.BlockInteractionHelper;
import me.zeroeightsix.kami.util.zeroeightysix.EntityUtil;
import me.zeroeightsix.kami.util.zeroeightysix.Friends;
import me.zeroeightsix.kami.util.zeroeightysix.Wrapper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockObsidian;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/***
 * @author Elementars
 */
@Module.Info(name = "AutoTrap", category = Module.Category.COMBAT, description = "Traps players near you with obby")
public class AutoTrap extends Module {
    private final Vec3d[] offsetsDefault;
    private Setting<Double> range;
    private Setting<Integer> blockPerTick;
    private Setting<Boolean> rotate;
    private Setting<Boolean> announceUsage;
    private EntityPlayer closestTarget;
    private String lastTickTargetName;
    private int playerHotbarSlot;
    private int lastHotbarSlot;
    private boolean isSneaking;
    private int offsetStep;
    private boolean firstRun;

    public AutoTrap() {
        this.offsetsDefault = new Vec3d[]{new Vec3d(0.0, 0.0, -1.0), new Vec3d(1.0, 0.0, 0.0), new Vec3d(0.0, 0.0, 1.0), new Vec3d(-1.0, 0.0, 0.0), new Vec3d(0.0, 1.0, -1.0), new Vec3d(1.0, 1.0, 0.0), new Vec3d(0.0, 1.0, 1.0), new Vec3d(-1.0, 1.0, 0.0), new Vec3d(0.0, 2.0, -1.0), new Vec3d(1.0, 2.0, 0.0), new Vec3d(0.0, 2.0, 1.0), new Vec3d(-1.0, 2.0, 0.0), new Vec3d(0.0, 3.0, -1.0), new Vec3d(0.0, 3.0, 0.0)};
        this.range = this.register(Settings.d("Range", 5.5));
        this.blockPerTick = this.register(Settings.i("Blocks per Tick", 4));
        this.rotate = this.register(Settings.b("Rotate", true));
        this.announceUsage = this.register(Settings.b("Announce Usage", true));
        this.playerHotbarSlot = -1;
        this.lastHotbarSlot = -1;
        this.isSneaking = false;
        this.offsetStep = 0;
    }

    @Override
    protected void onEnable() {
        if (AutoTrap.mc.player == null) {
            this.disable();
            return;
        }
        this.firstRun = true;
        this.playerHotbarSlot = Wrapper.getPlayer().inventory.currentItem;
        this.lastHotbarSlot = -1;
    }

    @Override
    protected void onDisable() {
        if (AutoTrap.mc.player == null) {
            return;
        }
        if (this.lastHotbarSlot != this.playerHotbarSlot && this.playerHotbarSlot != -1) {
            Wrapper.getPlayer().inventory.currentItem = this.playerHotbarSlot;
        }
        if (this.isSneaking) {
            AutoTrap.mc.player.connection.sendPacket((Packet) new CPacketEntityAction((Entity) AutoTrap.mc.player, CPacketEntityAction.Action.STOP_SNEAKING));
            this.isSneaking = false;
        }
        this.playerHotbarSlot = -1;
        this.lastHotbarSlot = -1;
        if (this.announceUsage.getValue()) {
            Command.sendChatMessage("[AutoTrap] Disabled!");
        }
    }

    @Override
    public void onUpdate() {
        if (AutoTrap.mc.player == null || ModuleManager.isModuleEnabled("Freecam")) {
            return;
        }
        this.findClosestTarget();
        if (this.closestTarget == null) {
            if (this.firstRun) {
                this.firstRun = false;
                if (this.announceUsage.getValue()) {
                    Command.sendChatMessage("[AutoTrap] Enabled, waiting for target.");
                }
            }
            return;
        }
        if (this.firstRun) {
            this.firstRun = false;
            this.lastTickTargetName = this.closestTarget.getName();
            if (this.announceUsage.getValue()) {
                Command.sendChatMessage("[AutoTrap] Enabled, target: " + this.lastTickTargetName);
            }
        } else if (!this.lastTickTargetName.equals(this.closestTarget.getName())) {
            this.lastTickTargetName = this.closestTarget.getName();
            this.offsetStep = 0;
            if (this.announceUsage.getValue()) {
                Command.sendChatMessage("[AutoTrap] New target: " + this.lastTickTargetName);
            }
        }
        final List<Vec3d> placeTargets = new ArrayList<Vec3d>();
        Collections.addAll(placeTargets, this.offsetsDefault);
        int blocksPlaced = 0;
        while (blocksPlaced < this.blockPerTick.getValue()) {
            if (this.offsetStep >= placeTargets.size()) {
                this.offsetStep = 0;
                break;
            }
            final BlockPos offsetPos = new BlockPos((Vec3d) placeTargets.get(this.offsetStep));
            final BlockPos targetPos = new BlockPos(this.closestTarget.getPositionVector()).down().add(offsetPos.x, offsetPos.y, offsetPos.z);
            boolean shouldTryToPlace = true;
            if (!Wrapper.getWorld().getBlockState(targetPos).getMaterial().isReplaceable()) {
                shouldTryToPlace = false;
            }
            for (final Entity entity : AutoTrap.mc.world.getEntitiesWithinAABBExcludingEntity((Entity) null, new AxisAlignedBB(targetPos))) {
                if (!(entity instanceof EntityItem) && !(entity instanceof EntityXPOrb)) {
                    shouldTryToPlace = false;
                    break;
                }
            }
            if (shouldTryToPlace && this.placeBlock(targetPos)) {
                ++blocksPlaced;
            }
            ++this.offsetStep;
        }
        if (blocksPlaced > 0) {
            if (this.lastHotbarSlot != this.playerHotbarSlot && this.playerHotbarSlot != -1) {
                Wrapper.getPlayer().inventory.currentItem = this.playerHotbarSlot;
                this.lastHotbarSlot = this.playerHotbarSlot;
            }
            if (this.isSneaking) {
                AutoTrap.mc.player.connection.sendPacket((Packet) new CPacketEntityAction((Entity) AutoTrap.mc.player, CPacketEntityAction.Action.STOP_SNEAKING));
                this.isSneaking = false;
            }
        }
    }

    private boolean placeBlock(final BlockPos pos) {
        if (!AutoTrap.mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
            return false;
        }
        if (!BlockInteractionHelper.checkForNeighbours(pos)) {
            return false;
        }
        final Vec3d eyesPos = new Vec3d(Wrapper.getPlayer().posX, Wrapper.getPlayer().posY + Wrapper.getPlayer().getEyeHeight(), Wrapper.getPlayer().posZ);
        for (final EnumFacing side : EnumFacing.values()) {
            final BlockPos neighbor = pos.offset(side);
            final EnumFacing side2 = side.getOpposite();
            if (AutoTrap.mc.world.getBlockState(neighbor).getBlock().canCollideCheck(AutoTrap.mc.world.getBlockState(neighbor), false)) {
                final Vec3d hitVec = new Vec3d((Vec3i) neighbor).add(0.5, 0.5, 0.5).add(new Vec3d(side2.getDirectionVec()).scale(0.5));
                if (eyesPos.distanceTo(hitVec) <= this.range.getValue()) {
                    final int obiSlot = this.findObiInHotbar();
                    if (obiSlot == -1) {
                        this.disable();
                        return false;
                    }
                    if (this.lastHotbarSlot != obiSlot) {
                        Wrapper.getPlayer().inventory.currentItem = obiSlot;
                        this.lastHotbarSlot = obiSlot;
                    }
                    final Block neighborPos = AutoTrap.mc.world.getBlockState(neighbor).getBlock();
                    if (BlockInteractionHelper.blackList.contains(neighborPos) || BlockInteractionHelper.shulkerList.contains(neighborPos)) {
                        AutoTrap.mc.player.connection.sendPacket((Packet) new CPacketEntityAction((Entity) AutoTrap.mc.player, CPacketEntityAction.Action.START_SNEAKING));
                        this.isSneaking = true;
                    }
                    if (this.rotate.getValue()) {
                        BlockInteractionHelper.faceVectorPacketInstant(hitVec);
                    }
                    AutoTrap.mc.playerController.processRightClickBlock(AutoTrap.mc.player, AutoTrap.mc.world, neighbor, side2, hitVec, EnumHand.MAIN_HAND);
                    AutoTrap.mc.player.swingArm(EnumHand.MAIN_HAND);
                    return true;
                }
            }
        }
        return false;
    }

    private int findObiInHotbar() {
        int slot = -1;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = Wrapper.getPlayer().inventory.getStackInSlot(i);
            if (stack != ItemStack.EMPTY && stack.getItem() instanceof ItemBlock) {
                final Block block = ((ItemBlock) stack.getItem()).getBlock();
                if (block instanceof BlockObsidian) {
                    slot = i;
                    break;
                }
            }
        }
        return slot;
    }

    private void findClosestTarget() {
        final List<EntityPlayer> playerList = (List<EntityPlayer>) Wrapper.getWorld().playerEntities;
        this.closestTarget = null;
        for (final EntityPlayer target : playerList) {
            if (target == AutoTrap.mc.player) {
                continue;
            }
            if (Friends.isFriend(target.getName())) {
                continue;
            }
            if (!EntityUtil.isLiving((Entity) target)) {
                continue;
            }
            if (target.getHealth() <= 0.0f) {
                continue;
            }
            if (this.closestTarget == null) {
                this.closestTarget = target;
            } else {
                if (Wrapper.getPlayer().getDistance((Entity) target) >= Wrapper.getPlayer().getDistance((Entity) this.closestTarget)) {
                    continue;
                }
                this.closestTarget = target;
            }
        }
    }
}
