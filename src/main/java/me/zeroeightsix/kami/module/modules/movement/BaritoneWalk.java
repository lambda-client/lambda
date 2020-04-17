package me.zeroeightsix.kami.module.modules.movement;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.util.math.MathHelper;

@Module.Info(name = "BaritoneWalk", description = "AutoWalk with Baritone pathfinding.", category = Module.Category.MOVEMENT)
public class BaritoneWalk extends Module {

    private boolean walking = false;

    @Override
    public void onUpdate() {
//        int facing = MathHelper.floor((double)(mc.player.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;

        if (!walking) {
            switch (mc.player.getHorizontalFacing()) {
                case NORTH:
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ((int) mc.player.posX, (int) mc.player.posZ - 1068));
                    walking = true;

                    break;
                case SOUTH:
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ((int) mc.player.posX, (int) mc.player.posZ + 1068));
                    walking = true;

                    break;
                case EAST:
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ((int) mc.player.posX + 1068, (int) mc.player.posZ));
                    walking = true;

                    break;
                case WEST:
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ((int) mc.player.posX - 1068, (int) mc.player.posZ));
                    walking = true;

                    break;
            }
        }
    }

    @Override
    protected void onDisable() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);

        walking = false;
    }
}
