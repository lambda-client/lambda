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
 * Updated by S-B99 on 06/03/20
 */
@Module.Info(name = "ElytraFlight", description = "Modifies elytras to fly at custom velocities and fall speeds", category = Module.Category.MOVEMENT)
public class ElytraFlight extends Module {
    private Setting<ElytraFlightMode> mode = register(Settings.e("Mode", ElytraFlightMode.HIGHWAY));
    private Setting<Boolean> defaultSetting = register(Settings.b("Defaults", false));
    private Setting<Float> speedHighway = register(Settings.floatBuilder("Speed H").withValue(1.8f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> fallSpeed = register(Settings.floatBuilder("Fall Speed").withValue(-.003f).withVisibility(v -> !mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> fallSpeedHighway = register(Settings.floatBuilder("Fall Speed H").withValue(0.000050000002f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> upSpeedBoost = register(Settings.floatBuilder("Up Speed B").withValue(0.08f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.BOOST)).build());
    private Setting<Float> downSpeedBoost = register(Settings.floatBuilder("Down Speed B").withValue(0.04f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.BOOST)).build());

    @Override
    public void onUpdate() {
        if (mc.player == null) return;
        if (defaultSetting.getValue()) {
            speedHighway.setValue(1.8f);
            fallSpeed.setValue(-.003f);
            fallSpeedHighway.setValue(.000050000002f);
            upSpeedBoost.setValue(0.08f);
            downSpeedBoost.setValue(0.04f);
            defaultSetting.setValue(false);
            Command.sendChatMessage(this.getChatName() + " Set to defaults!");
            Command.sendChatMessage(this.getChatName() + " Close and reopen the ElytraFlight settings menu to see changes");
        }

        if (mc.player.capabilities.isFlying) {
            if (mode.getValue().equals(ElytraFlightMode.HIGHWAY)) {
                mc.player.setSprinting(false);
                mc.player.setVelocity(0, 0, 0);
                mc.player.setPosition(mc.player.posX, mc.player.posY - fallSpeedHighway.getValue(), mc.player.posZ);
                mc.player.capabilities.setFlySpeed(speedHighway.getValue());
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
                    mc.player.motionY += upSpeedBoost.getValue();
                else if (mc.gameSettings.keyBindSneak.isKeyDown())
                    mc.player.motionY -= downSpeedBoost.getValue();

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
}
