package me.zeroeightsix.kami.module.modules.player;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import net.minecraft.util.math.MathHelper;

/**
 * Created by 086 on 16/12/2017.
 */
@Module.Info(name = "PitchLock", category = Module.Category.PLAYER)
public class PitchLock extends Module {
    @Setting(name = "Auto") boolean auto = true;
    @Setting(name = "Pitch") float pitch = 180;
    @Setting(name = "Slice") int slice = 8;

    @Override
    public void onUpdate() {
        if (slice == 0) return;
        if (auto) {
            int angle = 360/slice;
            float yaw = mc.player.rotationPitch;
            yaw = Math.round(yaw/angle)*angle;
            mc.player.rotationPitch = yaw;
            if (mc.player.isRiding()) mc.player.getRidingEntity().rotationPitch = yaw;
        }else{
            mc.player.rotationPitch = MathHelper.clamp(pitch -180, -180, 180);
        }
    }
}
