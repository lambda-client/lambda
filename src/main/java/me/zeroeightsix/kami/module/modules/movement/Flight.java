package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;

/**
 * Created by 086 on 25/08/2017.
 */
@Module.Info(category = Module.Category.MOVEMENT, description = "Makes the player fly", name = "Flight")
public class Flight extends Module {

    @Setting(name = "Speed")
    public float speed = 10;
    @Setting(name = "Mode")
    public FlightMode mode = FlightMode.VANILLA;

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        switch (mode) {
            case VANILLA:
                mc.player.capabilities.isFlying = true;
                if (mc.player.capabilities.isCreativeMode) return;
                mc.player.capabilities.allowFlying = true;
                break;
        }
    }

    @Override
    public void onUpdate() {
        switch (mode) {
            case STATIC:
                mc.player.capabilities.isFlying = false;
                mc.player.motionX = 0;
                mc.player.motionY = 0;
                mc.player.motionZ = 0;
                mc.player.jumpMovementFactor = speed;

                if (mc.gameSettings.keyBindJump.isKeyDown())
                    mc.player.motionY += speed;
                if (mc.gameSettings.keyBindSneak.isKeyDown())
                    mc.player.motionY -= speed;
                break;
            case VANILLA:
                mc.player.capabilities.setFlySpeed(speed/100f);
                mc.player.capabilities.isFlying = true;
                if (mc.player.capabilities.isCreativeMode) return;
                mc.player.capabilities.allowFlying = true;
                break;
        }
    }

    @Override
    protected void onDisable() {
        switch (mode) {
            case VANILLA:
                mc.player.capabilities.isFlying = false;
                mc.player.capabilities.setFlySpeed(0.05f);
                if (mc.player.capabilities.isCreativeMode) return;
                mc.player.capabilities.allowFlying = false;
                break;
        }
    }

    public enum FlightMode {
        VANILLA, STATIC
    }

}
