package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.util.EntityUtil;
import net.minecraft.entity.item.EntityBoat;

/**
 * Created by 086 on 15/12/2017.
 */
@Module.Info(name = "BoatSpeed", category = Module.Category.MOVEMENT)
public class BoatSpeed extends Module {

    @Setting(name = "Speed") public float speed = .5f;
    @Setting(name = "Opacity") private static float opacity = .5f;

    @Override
    public void onRender() {
        if (isDisabled()) return;
        EntityBoat boat = getBoat();
        if (boat == null) return;
        boat.rotationYaw = mc.player.rotationYaw;
        boat.updateInputs(false, false, false, false); // Make sure the boat doesn't turn etc (params: isLeftDown, isRightDown, isForwardDown, isBackDown)
    }

    @Override
    public void onUpdate() {
        EntityBoat boat = getBoat();
        if (boat == null) return;

        int angle;

        boolean forward = mc.gameSettings.keyBindForward.isKeyDown();
        boolean left = mc.gameSettings.keyBindLeft.isKeyDown();
        boolean right = mc.gameSettings.keyBindRight.isKeyDown();
        boolean back = mc.gameSettings.keyBindBack.isKeyDown();
        if (!(forward && back)) boat.motionY = 0;
        if (mc.gameSettings.keyBindJump.isKeyDown()) boat.motionY += speed/2f;

        if (!forward && !left && !right && !back) return;
        if (left && right) angle = forward ? 0 : back ? 180 : -1;
        else if (forward && back) angle = left ? -90 : (right ? 90 : -1);
        else {
            angle = left ? -90 : (right ? 90 : 0);
            if (forward) angle /= 2;
            else if (back) angle = 180-(angle/2);
        }

        if (angle == -1) return;
        float yaw = mc.player.rotationYaw+angle;
        boat.motionX = EntityUtil.getRelativeX(yaw)*speed;
        boat.motionZ = EntityUtil.getRelativeZ(yaw)*speed;
    }

    private EntityBoat getBoat() {
        if (mc.player.getRidingEntity() != null && mc.player.getRidingEntity() instanceof EntityBoat) return (EntityBoat) mc.player.getRidingEntity();
        return null;
    }

    public static float getOpacity() {
        return opacity;
    }
}
