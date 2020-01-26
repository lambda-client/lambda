package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.math.MathHelper;

/**
 * Created by 086 on 11/04/2018.
 * Updated by Itistheend on 28/12/19.
 * Updated by S-B99 on 11/01/20
 */

@Module.Info(name = "ElytraFlight", description = "Allows infinite elytra flying", category = Module.Category.MOVEMENT)
public class ElytraFlight extends Module {

    private Setting<ElytraFlightMode> mode = register(Settings.e("Mode", ElytraFlightMode.FLY));
    private Setting<Boolean> highway = register(Settings.b("Highway Mode", false));
    private Setting<Boolean> defaultSetting = register(Settings.b("Defaults", false));
    private Setting<Float> speed = register(Settings.f("Speed Highway", 1.8f));
    private Setting<Float> upSpeed = register(Settings.f("Up Speed", 0.08f));
    private Setting<Float> downSpeed = register(Settings.f("Down Speed", 0.04f));
    private Setting<Float> fallSpeedHighway = register(Settings.f("Fall Speed Highway", 0.000050000002f));
    private Setting<Float> fallspeed = register(Settings.f("Fall Speed", -.003f));

    @Override
    public void onUpdate() {

        if (defaultSetting.getValue()) {
            speed.setValue(1.8f);
            fallSpeedHighway.setValue(.000050000002f);
            defaultSetting.setValue(false);
            Command.sendChatMessage("[ElytraFlight] Set to defaults!");
        }

        if (highway.getValue()) {
            mode.setValue(ElytraFlightMode.FLY);
        }

        if (mc.player.capabilities.isFlying) {
            if (highway.getValue()) {
                mc.player.setVelocity(0, 0, 0);
                mc.player.setPosition(mc.player.posX, mc.player.posY - fallSpeedHighway.getValue(), mc.player.posZ);
                mc.player.capabilities.setFlySpeed(speed.getValue());
                mc.player.setSprinting(false);
            }
            else {
                mc.player.setVelocity(0, 0, 0);
                mc.player.capabilities.setFlySpeed(.915f);
                mc.player.setPosition(mc.player.posX, mc.player.posY - fallspeed.getValue(), mc.player.posZ);
            }
        }

        if (mc.player.onGround) {
            mc.player.capabilities.allowFlying = false;
        }

        if (!mc.player.isElytraFlying()) return;
        switch (mode.getValue()) {
            case BOOST:
                if (mc.player.isInWater()) {
                    mc.getConnection()
                            .sendPacket(new CPacketEntityAction(mc.player,
                                    CPacketEntityAction.Action.START_FALL_FLYING));
                    return;
                }

                if (mc.gameSettings.keyBindJump.isKeyDown())
                    mc.player.motionY += upSpeed.getValue();
                else if (mc.gameSettings.keyBindSneak.isKeyDown())
                    mc.player.motionY -= downSpeed.getValue();

                if (mc.gameSettings.keyBindForward.isKeyDown()) {
                    float yaw = (float) Math
                            .toRadians(mc.player.rotationYaw);
                    mc.player.motionX -= MathHelper.sin(yaw) * 0.05F;
                    mc.player.motionZ += MathHelper.cos(yaw) * 0.05F;
                } else if (mc.gameSettings.keyBindBack.isKeyDown()) {
                    float yaw = (float) Math
                            .toRadians(mc.player.rotationYaw);
                    mc.player.motionX += MathHelper.sin(yaw) * 0.05F;
                    mc.player.motionZ -= MathHelper.cos(yaw) * 0.05F;
                }
                break;
            case FLY:
                mc.player.capabilities.setFlySpeed(.915f);
                mc.player.capabilities.isFlying = true;

                if (mc.player.capabilities.isCreativeMode) return;
                mc.player.capabilities.allowFlying = true;
                break;
        }
    }

    @Override
    protected void onDisable() {
        mc.player.capabilities.isFlying = false;
        mc.player.capabilities.setFlySpeed(0.05f);
        if (mc.player.capabilities.isCreativeMode) return;
        mc.player.capabilities.allowFlying = false;
    }

    private enum ElytraFlightMode {
        BOOST, FLY
    }

}
