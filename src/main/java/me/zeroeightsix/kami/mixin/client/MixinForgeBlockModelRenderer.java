package me.zeroeightsix.kami.mixin.client;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.event.events.RenderBlockModelEvent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.pipeline.ForgeBlockModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author 086
 */
@Mixin(ForgeBlockModelRenderer.class)
public class MixinForgeBlockModelRenderer {

    @Inject(method = "renderModelSmooth", at = @At("HEAD"), cancellable = true)
    public void renderModelSmooth(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos, BufferBuilder buffer, boolean checkSides, long rand, CallbackInfoReturnable info) {
        RenderBlockModelEvent event = new RenderBlockModelEvent(world, model, state, pos, buffer, checkSides, rand);
        KamiMod.EVENT_BUS.post(event);
        if (event.isCancelled()) info.setReturnValue(true);
    }

    @Inject(method = "renderModelFlat", at = @At("HEAD"), cancellable = true)
    public void renderModelFlat(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos, BufferBuilder buffer, boolean checkSides, long rand, CallbackInfoReturnable info) {
        RenderBlockModelEvent event = new RenderBlockModelEvent(world, model, state, pos, buffer, checkSides, rand);
        KamiMod.EVENT_BUS.post(event);
        if (event.isCancelled()) info.setReturnValue(true);
    }

}
