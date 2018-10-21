package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.EntityUtil;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.util.MovementInput;
import net.minecraft.world.chunk.EmptyChunk;

/**
 * Created by 086 on 16/12/2017.
 */
@Module.Info(name = "EntitySpeed", category = Module.Category.MOVEMENT, description = "Abuse client-sided movement to shape sound barrier breaking rideables")
public class EntitySpeed extends Module {

    private Setting<Float> speed = register(Settings.f("Speed", 1));
    private Setting<Boolean> antiStuck = register(Settings.b("AntiStuck"));
    private Setting<Boolean> flight = register(Settings.b("Flight", false));
    private Setting<Boolean> wobble = register(Settings.booleanBuilder("Wobble").withValue(true).withVisibility(b -> flight.getValue()).build());
    private static Setting<Float> opacity = Settings.f("Boat opacity", .5f);

    public EntitySpeed() {
        register(opacity);
    }

    @Override
    public void onUpdate() {
        if ((mc.world != null) && (mc.player.getRidingEntity() != null)) {
            if (mc.player.getRidingEntity() instanceof EntityPig || mc.player.getRidingEntity() instanceof AbstractHorse) {
                if (!flight.getValue()) {
                    mc.player.getRidingEntity().motionY = -0.4D;
                }

                if (flight.getValue()) {
                    if (mc.gameSettings.keyBindJump.isKeyDown())
                        mc.player.getRidingEntity().motionY = speed.getValue();
                    else if (mc.gameSettings.keyBindForward.isKeyDown() || mc.gameSettings.keyBindBack.isKeyDown())
                        mc.player.getRidingEntity().motionY = wobble.getValue() ? Math.sin(mc.player.ticksExisted) : 0;
                }

                setMoveSpeedEntity(speed.getValue() * 3.8D);

                if (mc.player.getRidingEntity() instanceof EntityHorse){
                    mc.player.getRidingEntity().rotationYaw = mc.player.rotationYaw;
                }
            } else if (mc.player.getRidingEntity() instanceof EntityBoat) {
                EntityBoat boat = getBoat();
                if (boat == null) return;

                int angle;

                boolean forward = mc.gameSettings.keyBindForward.isKeyDown();
                boolean left = mc.gameSettings.keyBindLeft.isKeyDown();
                boolean right = mc.gameSettings.keyBindRight.isKeyDown();
                boolean back = mc.gameSettings.keyBindBack.isKeyDown();
                if (!(forward && back)) boat.motionY = 0;
                if (mc.gameSettings.keyBindJump.isKeyDown()) boat.motionY += speed.getValue() / 2f;

                if (!forward && !left && !right && !back) return;
                if (left && right) angle = forward ? 0 : back ? 180 : -1;
                else if (forward && back) angle = left ? -90 : (right ? 90 : -1);
                else {
                    angle = left ? -90 : (right ? 90 : 0);
                    if (forward) angle /= 2;
                    else if (back) angle = 180 - (angle / 2);
                }

                if (angle == -1) return;
                float yaw = mc.player.rotationYaw + angle;
                boat.motionX = EntityUtil.getRelativeX(yaw) * speed.getValue();
                boat.motionZ = EntityUtil.getRelativeZ(yaw) * speed.getValue();
            }
        }
    }

    @Override
    public void onRender() {
        EntityBoat boat = getBoat();
        if (boat == null) return;
        boat.rotationYaw = mc.player.rotationYaw;
        boat.updateInputs(false, false, false, false); // Make sure the boat doesn't turn etc (params: isLeftDown, isRightDown, isForwardDown, isBackDown)
    }

    private EntityBoat getBoat() {
        if (mc.player.getRidingEntity() != null && mc.player.getRidingEntity() instanceof EntityBoat)
            return (EntityBoat) mc.player.getRidingEntity();
        return null;
    }

    private void setMoveSpeedEntity(double speed) {
        if (mc.player.getRidingEntity() != null)
        {
            MovementInput movementInput = mc.player.movementInput;

            double forward = movementInput.moveForward;
            double strafe = movementInput.moveStrafe;
            float yaw = mc.player.rotationYaw;
            if ((forward == 0.0D) && (strafe == 0.0D))
            {
                mc.player.getRidingEntity().motionX = 0.0D;
                mc.player.getRidingEntity().motionZ = 0.0D;
            }
            else
            {
                if (forward != 0.0D)
                {
                    if (strafe > 0.0D) {
                        yaw += (forward > 0.0D ? -45 : 45);
                    } else if (strafe < 0.0D) {
                        yaw += (forward > 0.0D ? 45 : -45);
                    }
                    strafe = 0.0D;
                    if (forward > 0.0D) {
                        forward = 1.0D;
                    } else {
                        forward = -1.0D;
                    }
                }

                double motX = (forward * speed * Math.cos(Math.toRadians(yaw + 90.0F)) + strafe * speed * Math.sin(Math.toRadians(yaw + 90.0F)));
                double motZ = (forward * speed * Math.sin(Math.toRadians(yaw + 90.0F)) - strafe * speed * Math.cos(Math.toRadians(yaw + 90.0F)));

                if (antiStuck.getValue() && mc.world.getChunkFromChunkCoords((int) (mc.player.getRidingEntity().posX + motX) >> 4, (int) (mc.player.getRidingEntity().posZ + motZ) >> 4) instanceof EmptyChunk)
                    motX = motZ = 0;

                mc.player.getRidingEntity().motionX = motX;
                mc.player.getRidingEntity().motionZ = motZ;
            }
        }
    }

    public static float getOpacity() {
        return opacity.getValue();
    }

}
