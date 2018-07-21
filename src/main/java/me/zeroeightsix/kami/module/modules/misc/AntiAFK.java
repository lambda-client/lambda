package me.zeroeightsix.kami.module.modules.misc;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.util.EnumHand;

import java.util.Random;

/**
 * Created by 086 on 16/12/2017.
 */
@Module.Info(name = "AntiAFK", category = Module.Category.MISC, description = "Moves in order not to get kicked. (May be invisible client-sided)")
public class AntiAFK extends Module {

    @Setting(name = "Swing") private boolean swing = true;
    @Setting(name = "Turn") private boolean turn = true;

    private Random random = new Random();

    @Override
    public void onUpdate() {
        if (mc.playerController.getIsHittingBlock()) return;

        if (mc.player.ticksExisted%40==0 && swing)
            mc.getConnection().sendPacket(new CPacketAnimation(EnumHand.MAIN_HAND));
        if (mc.player.ticksExisted%15==0 && turn)
            mc.player.rotationYaw = random.nextInt(360)-180;

        if (!(swing || turn) && mc.player.ticksExisted%80==0) {
            mc.player.jump();
        }
    }
}
