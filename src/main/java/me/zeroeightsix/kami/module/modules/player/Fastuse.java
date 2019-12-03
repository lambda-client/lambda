package me.zeroeightsix.kami.module.modules.player;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEndCrystal;
import net.minecraft.item.ItemExpBottle;

/**
 * Created by S-B99 on 23/10/2019
 * @author S-B99
 * Updated by d1gress/Qther on 2/12/2019
 * Updated by S-B99 on 03/12/19
 */
@Module.Info(category = Module.Category.PLAYER, description = "Changes delay when holding right click", name = "FastUse")
public class Fastuse extends Module {


//	private Setting<Integer> delay = register(Settings.i("Delay", 0));
	private Setting<Double> delay = register(Settings.doubleBuilder("Delay").withMinimum(0.0).withValue(0.0).withMaximum(10.0).build());
	private Setting<Mode> mode = register(Settings.e("Mode", Mode.BOTH));
	private static long time = 0;
//	private int modeCase;
	private enum Mode {
		ALL, BOTH, EXP, CRYSTAL
	}
	@Override
	public void onUpdate() {
		Item itemMain = mc.player.getHeldItemMainhand().getItem();
		Item itemOff = mc.player.getHeldItemOffhand().getItem();
		boolean doExpMain = itemMain instanceof ItemExpBottle;
		boolean doExpOff = itemOff instanceof ItemExpBottle;
		boolean doCrystalMain = itemMain instanceof ItemEndCrystal;
		boolean doCrystalOff = itemOff instanceof ItemEndCrystal;

		if (mc.player != null && delay.getValue() <= 0) {
			switch(mode.getValue()){
				case ALL:
					mc.rightClickDelayTimer = 0;
				case BOTH:
					if (doExpMain || doExpOff || doCrystalMain || doCrystalOff) {
						mc.rightClickDelayTimer = 0;
					}
				case EXP:
					if (doExpMain || doExpOff) {
						mc.rightClickDelayTimer = 0;
					}
				case CRYSTAL:
					if (doCrystalMain || doCrystalOff) {
						mc.rightClickDelayTimer = 0;
					}
			}
		}
		else if (mc.player != null && time == 0 || time + delay.getValue() * 50 <= System.currentTimeMillis()) { //scales weird
			switch(mode.getValue()){
				case ALL:
					mc.rightClickDelayTimer = 0;
				case BOTH:
					if (doExpMain || doExpOff || doCrystalMain || doCrystalOff) {
						mc.rightClickDelayTimer = 0;
					}
				case EXP:
					if (doExpMain || doExpOff) {
						mc.rightClickDelayTimer = 0;
					}
				case CRYSTAL:
					if (doCrystalMain || doCrystalOff) {
						mc.rightClickDelayTimer = 0;
					}
			}
			time = System.currentTimeMillis();
		}
	}
}
