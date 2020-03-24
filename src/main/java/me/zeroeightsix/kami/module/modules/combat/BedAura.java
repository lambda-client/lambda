package me.zeroeightsix.kami.module.modules.combat;

import java.util.Comparator;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;

@Module.Info(name = "BedAura", category = Module.Category.COMBAT, description = "Automatically right clicks beds in range")
public class BedAura extends Module {
    private Setting<Integer> waitTick = register(Settings.i("Tick Delay", 10));
    private Setting<Double> range = register(Settings.d("Hit Range", 4.0D));

    private int waitCounter;

    public void onUpdate() {
        if (mc.player == null) return;

        if (mc.player.dimension != 0) {
            if (waitTick.getValue() > 0) {
                if (waitCounter < waitTick.getValue()) {
                    ++waitCounter;
                    return;
                }
                waitCounter = 0;
            }
            mc.world.loadedTileEntityList.stream()
                    .filter((e) -> e instanceof TileEntityBed)
                    .filter((e) -> mc.player.getPosition().getDistance(e.getPos().x, e.getPos().y, e.getPos().z) <= range.getValue())
                    .map((entity) -> (TileEntityBed) entity)
                    .min(Comparator.comparing((e) -> mc.player.getPosition().getDistance(e.getPos().x, e.getPos().y, e.getPos().z)))
                    .ifPresent(bed -> mc.playerController.processRightClickBlock(mc.player, mc.world, bed.getPos(), EnumFacing.UP, new Vec3d(bed.getPos().getX(), bed.getPos().getY(), bed.getPos().getZ()), EnumHand.MAIN_HAND));
        } else {
            Command.sendErrorMessage(getChatName() + "Exploding beds only works in the nether and in the end, disabling!");
            disable();
        }
    }
}
