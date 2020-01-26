package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.EntityUtil;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockFalling;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketEntityAction.Action;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.InputUpdateEvent;

import static me.zeroeightsix.kami.util.BlockInteractionHelper.*;

/**
 * Created by 086 on 20/01/19
 * Updated by Polymer on 16/01/20
 */
@Module.Info(name = "Scaffold", category = Module.Category.PLAYER, description = "Places blocks under you")
public class Scaffold extends Module {

    private Setting<Integer> future = register(Settings.integerBuilder("Ticks").withMinimum(0).withMaximum(60).withValue(2));
    private Setting legitBridge = register(Settings.b("Legit Bridge", false));
    private Setting autoPlace = register(Settings.b("AutoPlace", false));
    boolean shouldSlow = false;

    private static Scaffold INSTANCE;

    public Scaffold() {
        INSTANCE = this;
    }

    public static boolean shouldScaffold() {
        return INSTANCE.isEnabled();
    }
    @EventHandler
    private Listener<InputUpdateEvent> eventListener = new Listener<>(event -> {
        if ((boolean)legitBridge.getValue() && shouldSlow == true) {
            event.getMovementInput().moveStrafe *= 0.2f;
            event.getMovementInput().moveForward *= 0.2f;
        }
    });

    @Override
    public void onUpdate() {
        shouldSlow = false;
        if (isDisabled() || mc.player == null || ModuleManager.isModuleEnabled("Freecam")) return;
        Vec3d vec3d = EntityUtil.getInterpolatedPos(mc.player, future.getValue());
        if ((boolean) legitBridge.getValue()) vec3d = EntityUtil.getInterpolatedPos(mc.player, 0);
        BlockPos blockPos = new BlockPos(vec3d).down();
        BlockPos belowBlockPos = blockPos.down();
        BlockPos legitPos = new BlockPos(EntityUtil.getInterpolatedPos(mc.player, 2));
        //check if block behind player is air or other replaceable block and if it is, make the player crouch when legitBridge is enabled
        if (Wrapper.getWorld().getBlockState(legitPos.down()).getMaterial().isReplaceable() && (boolean)legitBridge.getValue()&& mc.player.onGround == true) {
            shouldSlow = true;
            mc.player.movementInput.sneak = true;
            mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, Action.START_SNEAKING));
        }
        // check if block is already placed
        if (!Wrapper.getWorld().getBlockState(blockPos).getMaterial().isReplaceable() ) {
            return;
        }
        // search blocks in hotbar
        int newSlot = -1;
        for (int i = 0; i < 9; i++) {
            // filter out non-block items
            ItemStack stack =
                    Wrapper.getPlayer().inventory.getStackInSlot(i);

            if (stack == ItemStack.EMPTY || !(stack.getItem() instanceof ItemBlock)) {
                continue;
            }
            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (blackList.contains(block) || block instanceof BlockContainer) {
                continue;
            }

            // filter out non-solid blocks
            if (!Block.getBlockFromItem(stack.getItem()).getDefaultState()
                    .isFullBlock())
                continue;

            // don't use falling blocks if it'd fall
            if (((ItemBlock) stack.getItem()).getBlock() instanceof BlockFalling) {
                if (Wrapper.getWorld().getBlockState(belowBlockPos).getMaterial().isReplaceable()) continue;
            }

            newSlot = i;
            break;
        }

        // check if any blocks were found
        if (newSlot == -1)
            return;

        // set slot
        int oldSlot = Wrapper.getPlayer().inventory.currentItem;
        Wrapper.getPlayer().inventory.currentItem = newSlot;

        // check if we don't have a block adjacent to blockpos
        if (!checkForNeighbours(blockPos)) {
            return;
        }
        // place block
        if ((boolean)autoPlace.getValue()) placeBlockScaffold(blockPos);
        mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, Action.STOP_SNEAKING));
        shouldSlow = false;

        // reset slot
        Wrapper.getPlayer().inventory.currentItem = oldSlot;
    }

}

