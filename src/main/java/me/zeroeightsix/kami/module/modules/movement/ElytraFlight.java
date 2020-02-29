package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.math.MathHelper;

import java.util.Objects;

/**
 * Created by 086 on 11/04/2018.
 * Updated by Itistheend on 28/12/19.
 * Updated by S-B99 on 29/02/20
 */
@Module.Info(name = "ElytraFlight", description = "Modifies elytras to fly at custom velocities and fall speeds", category = Module.Category.MOVEMENT)
public class ElytraFlight extends Module {
    public Setting<ElytraFlightMode> mode = register(Settings.e("Mode", ElytraFlightMode.HIGHWAY));
    private Setting<Boolean> defaultSetting = register(Settings.b("Defaults", false));
    private Setting<Float> speed = register(Settings.floatBuilder("Speed H").withValue(1.8f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> fallSpeed = register(Settings.floatBuilder("Fall Speed").withValue(-.003f).withVisibility(v -> !mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> fallSpeedHighway = register(Settings.floatBuilder("Fall Speed H").withValue(0.000050000002f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> upSpeed = register(Settings.floatBuilder("Up Speed B").withValue(0.08f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.BOOST)).build());
    private Setting<Float> downSpeed = register(Settings.floatBuilder("Down Speed B").withValue(0.04f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.BOOST)).build());

    @Override
    public void onUpdate() {
        if (ModuleManager.getModuleByName("ElytraFlightCycle").isEnabled()) {
            cycle();
            Command.sendChatMessage("[ElytraFlightCycle] Cycled!");
            ModuleManager.getModuleByName("ElytraFlightCycle").disable();
        }
        if (defaultSetting.getValue()) {
            speed.setValue(1.8f);
            fallSpeed.setValue(-.003f);
            fallSpeedHighway.setValue(.000050000002f);
            upSpeed.setValue(0.08f);
            downSpeed.setValue(0.04f);
            defaultSetting.setValue(false);
            Command.sendChatMessage("[ElytraFlight] Set to defaults!");
            Command.sendChatMessage("[ElytraFlight] Close and reopen the ElytraFlight settings menu to see changes");
        }

        if (mc.player.capabilities.isFlying) {
            if (mode.getValue().equals(ElytraFlightMode.HIGHWAY)) {
                mc.player.setVelocity(0, 0, 0);
                mc.player.setPosition(mc.player.posX, mc.player.posY - fallSpeedHighway.getValue(), mc.player.posZ);
                mc.player.capabilities.setFlySpeed(speed.getValue());
                mc.player.setSprinting(false);
            }
            else {
                mc.player.setVelocity(0, 0, 0);
                mc.player.capabilities.setFlySpeed(.915f);
                mc.player.setPosition(mc.player.posX, mc.player.posY - fallSpeed.getValue(), mc.player.posZ);
            }
        }

        if (mc.player.onGround) {
            mc.player.capabilities.allowFlying = false;
        }

        if (!mc.player.isElytraFlying()) return;
        switch (mode.getValue()) {
            case BOOST:
                if (mc.player.isInWater()) {
                    Objects.requireNonNull(mc.getConnection()).sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_FALL_FLYING));
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
            default:
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

    public enum ElytraFlightMode {
        BOOST, FLY, HIGHWAY
    }

    public void cycle() { /* This fixes a bug with your wings not being opened sometimes */
        switch (mode.getValue()) {
            case HIGHWAY:
                mode.setValue(ElytraFlightMode.BOOST);
                mode.setValue(ElytraFlightMode.FLY);
                mode.setValue(ElytraFlightMode.HIGHWAY);
            case FLY:
                mode.setValue(ElytraFlightMode.HIGHWAY);
                mode.setValue(ElytraFlightMode.BOOST);
                mode.setValue(ElytraFlightMode.FLY);
            case BOOST:
                mode.setValue(ElytraFlightMode.FLY);
                mode.setValue(ElytraFlightMode.HIGHWAY);
                mode.setValue(ElytraFlightMode.BOOST);
        }
    }

}
