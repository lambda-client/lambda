package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.module.Module;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;

/**
 * Created by 086 on 12/12/2017.
 */
@Module.Info(name = "Brightness", description = "Makes everything brighter!", category = Module.Category.RENDER)
public class Brightness extends Module {

    @Override
    public void onUpdate() {
        mc.player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 1, 1));
    }
}
