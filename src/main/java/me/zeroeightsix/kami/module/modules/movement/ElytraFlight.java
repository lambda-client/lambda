package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.math.MathHelper;

import java.util.Objects;

import static me.zeroeightsix.kami.util.MessageSendHelper.sendChatMessage;

/**
 * Created by 086 on 11/04/2018.
 * Updated by Itistheend on 28/12/19.
 * Updated by S-B99 on 06/03/20
 */
@Module.Info(name = "ElytraFlight", description = "Modifies elytras to fly at custom velocities and fall speeds", category = Module.Category.MOVEMENT)
public class ElytraFlight extends Module {
    private Setting<ElytraFlightMode> mode = register(Settings.e("Mode", ElytraFlightMode.HIGHWAY));
    private Setting<Boolean> defaultSetting = register(Settings.b("Defaults", false));
    private Setting<Boolean> easyTakeOff = register(Settings.booleanBuilder("Easy Takeoff").withValue(true).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<TakeoffMode> takeOffMode = register(Settings.enumBuilder(TakeoffMode.class).withName("Takeoff Mode").withValue(TakeoffMode.PACKET).withVisibility(v -> easyTakeOff.getValue() && mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Boolean> overrideMaxSpeed = register(Settings.booleanBuilder("Over Max Speed").withValue(false).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> speedHighway = register(Settings.floatBuilder("Speed H").withValue(1.8f).withMaximum(1.8f).withVisibility(v -> !overrideMaxSpeed.getValue() && mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> speedHighwayOverride = register(Settings.floatBuilder("Speed H O").withValue(1.8f).withVisibility(v -> overrideMaxSpeed.getValue() && mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> fallSpeedHighway = register(Settings.floatBuilder("Fall Speed H").withValue(0.000050000002f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> fallSpeed = register(Settings.floatBuilder("Fall Speed").withValue(-.003f).withVisibility(v -> !mode.getValue().equals(ElytraFlightMode.HIGHWAY)).build());
    private Setting<Float> upSpeedBoost = register(Settings.floatBuilder("Up Speed B").withValue(0.08f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.BOOST)).build());
    private Setting<Float> downSpeedBoost = register(Settings.floatBuilder("Down Speed B").withValue(0.04f).withVisibility(v -> mode.getValue().equals(ElytraFlightMode.BOOST)).build());

    @Override
    public void onUpdate() {
        if (mc.player == null) return;
        if (defaultSetting.getValue()) defaults();

        takeOff();
        setFlySpeed();

//        sendChatMessage("Rotation: " + mc.player.rotationPitch + " Camera: " + mc.player.cameraPitch);

        /* required on some servers in order to land */
        if (mc.player.onGround) mc.player.capabilities.allowFlying = false;

        if (!mc.player.isElytraFlying()) return;

        if (mode.getValue() == ElytraFlightMode.BOOST) {
            if (mc.player.isInWater()) {
                Objects.requireNonNull(mc.getConnection()).sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_FALL_FLYING));
                return;
            }

            if (mc.gameSettings.keyBindJump.isKeyDown())
                mc.player.motionY += upSpeedBoost.getValue();
            else
                if (mc.gameSettings.keyBindSneak.isKeyDown())
                    mc.player.motionY -= downSpeedBoost.getValue();

            if (mc.gameSettings.keyBindForward.isKeyDown()) {
                float yaw = (float) Math.toRadians(mc.player.rotationYaw);
                mc.player.motionX -= MathHelper.sin(yaw) * 0.05F;
                mc.player.motionZ += MathHelper.cos(yaw) * 0.05F;
            } else
                if (mc.gameSettings.keyBindBack.isKeyDown()) {
                    float yaw = (float) Math.toRadians(mc.player.rotationYaw);
                    mc.player.motionX += MathHelper.sin(yaw) * 0.05F;
                    mc.player.motionZ -= MathHelper.cos(yaw) * 0.05F;
                }
        } else {
            mc.player.capabilities.setFlySpeed(.915f);
            mc.player.capabilities.isFlying = true;

            if (mc.player.capabilities.isCreativeMode)
                return;
            mc.player.capabilities.allowFlying = true;
        }
    }

    private void setFlySpeed() {
        if (mc.player.capabilities.isFlying) {
            if (mode.getValue().equals(ElytraFlightMode.HIGHWAY)) {
                mc.player.setSprinting(false);
                mc.player.setVelocity(0, 0, 0);
                mc.player.setPosition(mc.player.posX, mc.player.posY - fallSpeedHighway.getValue(), mc.player.posZ);
                mc.player.capabilities.setFlySpeed(getHighwaySpeed());
            } else {
                mc.player.setVelocity(0, 0, 0);
                mc.player.capabilities.setFlySpeed(.915f);
                mc.player.setPosition(mc.player.posX, mc.player.posY - fallSpeed.getValue(), mc.player.posZ);
            }
        }
    }

    private void takeOff() {
        if (!(mode.getValue().equals(ElytraFlightMode.HIGHWAY) && easyTakeOff.getValue())) return;
        if (!mc.player.isElytraFlying() && !mc.player.onGround) {
            switch (takeOffMode.getValue()) {
                case CLIENT:
                    mc.player.capabilities.isFlying = true;
                case PACKET:
                    Objects.requireNonNull(mc.getConnection()).sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_FALL_FLYING));
                default:
            }
        }

        if (mc.player.isElytraFlying()) {
            easyTakeOff.setValue(false);
            sendChatMessage(getChatName() + "Disabled takeoff!");
        }
    }

    @Override
    protected void onDisable() {
        mc.player.capabilities.isFlying = false;
        mc.player.capabilities.setFlySpeed(0.05f);
        if (mc.player.capabilities.isCreativeMode) return;
        mc.player.capabilities.allowFlying = false;
    }

    private void defaults() {
        easyTakeOff.setValue(true);
        takeOffMode.setValue(TakeoffMode.PACKET);
        overrideMaxSpeed.setValue(false);
        speedHighway.setValue(1.8f);
        speedHighwayOverride.setValue(1.8f);
        fallSpeedHighway.setValue(.000050000002f);
        fallSpeed.setValue(-.003f);
        upSpeedBoost.setValue(0.08f);
        downSpeedBoost.setValue(0.04f);
        defaultSetting.setValue(false);
        sendChatMessage(getChatName() + " Set to defaults!");
        sendChatMessage(getChatName() + " Close and reopen the " + getName() + " settings menu to see changes");
    }

    private float getHighwaySpeed() {
        if (overrideMaxSpeed.getValue()) {
            return speedHighwayOverride.getValue();
        } else {
            return speedHighway.getValue();
        }
    }

    private enum ElytraFlightMode { BOOST, FLY, HIGHWAY }
    private enum TakeoffMode { CLIENT, PACKET }
}
