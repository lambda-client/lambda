package me.zeroeightsix.kami.module.modules.misc;

import me.zeroeightsix.kami.module.Module;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.Vec3d;

/**
 * Created by 086 on 11/12/2017.
 *
 * @see me.zeroeightsix.kami.mixin.client.MixinEntityRenderer#rayTraceBlocks(WorldClient, Vec3d, Vec3d)
 */
@Module.Info(name = "CameraClip", category = Module.Category.MISC, description = "Allows your camera to pass through blocks")
public class CameraClip extends Module {
}
