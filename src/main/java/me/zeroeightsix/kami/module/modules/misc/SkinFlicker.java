package me.zeroeightsix.kami.module.modules.misc;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import net.minecraft.entity.player.EnumPlayerModelParts;

import java.util.Random;

/**
 * Created by 086 on 30/01/2018.
 */
@Module.Info(name = "SkinFlicker", description = "Toggle the jacket layer rapidly for a cool skin effect", category = Module.Category.MISC)
public class SkinFlicker extends Module {

    @Setting(name = "Mode") private static FlickerMode mode = FlickerMode.HORIZONTAL;
    @Setting(name = "Slowness", min = 1) private int slowness = 2;

    private final static EnumPlayerModelParts[] PARTS_HORIZONTAL = new EnumPlayerModelParts[]{
            EnumPlayerModelParts.LEFT_SLEEVE,
            EnumPlayerModelParts.JACKET,
            EnumPlayerModelParts.HAT,
            EnumPlayerModelParts.LEFT_PANTS_LEG,
            EnumPlayerModelParts.RIGHT_PANTS_LEG,
            EnumPlayerModelParts.RIGHT_SLEEVE
    };

    private final static EnumPlayerModelParts[] PARTS_VERTICAL = new EnumPlayerModelParts[]{
            EnumPlayerModelParts.HAT,
            EnumPlayerModelParts.JACKET,
            EnumPlayerModelParts.LEFT_SLEEVE,
            EnumPlayerModelParts.RIGHT_SLEEVE,
            EnumPlayerModelParts.LEFT_PANTS_LEG,
            EnumPlayerModelParts.RIGHT_PANTS_LEG,
    };

    private Random r = new Random();
    private int len = EnumPlayerModelParts.values().length;

    @Override
    public void onUpdate() {
        switch (mode) {
            case RANDOM:
                if (mc.player.ticksExisted%slowness!=0) return;
                mc.gameSettings.switchModelPartEnabled(EnumPlayerModelParts.values()[r.nextInt(len)]);
                break;
            case VERTICAL:
            case HORIZONTAL:
                int i = (mc.player.ticksExisted/slowness)%(PARTS_HORIZONTAL.length*2); // *2 for on/off
                boolean on = false;
                if (i >= PARTS_HORIZONTAL.length) {
                    on = true;
                    i -= PARTS_HORIZONTAL.length;
                }
                mc.gameSettings.setModelPartEnabled(mode==FlickerMode.VERTICAL ? PARTS_VERTICAL[i] : PARTS_HORIZONTAL[i], on);
        }
    }

    public static enum FlickerMode {
        HORIZONTAL, VERTICAL, RANDOM
    }

}
