package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.init.Blocks;

/**
 * Created on 26 October 2019 by hub
 * Updated 21 November 2019 by hub
 */
@Module.Info(name = "IceSpeed", description = "Ice Speed", category = Module.Category.MOVEMENT)
public class IceSpeed extends Module {

    // Using double here cause float isnt cut off in the gui (example: 0.40000089485)
    private Setting<Double> slipperiness = register(Settings.doubleBuilder("Slipperiness").withMinimum(0.2).withValue(0.4).withMaximum(1.0).build());

    @Override
    public void onUpdate() {
        Blocks.ICE.slipperiness = slipperiness.getValue().floatValue();
        Blocks.PACKED_ICE.slipperiness = slipperiness.getValue().floatValue();
        Blocks.FROSTED_ICE.slipperiness = slipperiness.getValue().floatValue();
    }

    @Override
    public void onDisable() {
        Blocks.ICE.slipperiness = 0.98f;
        Blocks.PACKED_ICE.slipperiness = 0.98f;
        Blocks.FROSTED_ICE.slipperiness = 0.98f;
    }

}
