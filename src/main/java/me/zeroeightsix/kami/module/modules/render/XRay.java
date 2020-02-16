package me.zeroeightsix.kami.module.modules.render;

import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.EnumFacing;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.BlockStateBase;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.event.events.PlayerMoveEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.KamiMod;

/**
 * It'd be nice if this was customizable, but it'd be hundreds of settings.
 * For now this does the sensible thing but leaves an obvious extension point.
 * Created by 20kdc on 15/02/2020.
 */
@Module.Info(name = "XRay", category = Module.Category.RENDER, description = "Filters away the common blocks.")
@EventBusSubscriber(modid = KamiMod.MODID)
public class XRay extends Module {

    // This is the state used for hidden blocks.
    private static IBlockState transparentState;
    public static Block transparentBlock;

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        transparentBlock = new Block(Material.GLASS) {
            // did you know this name's new
            @Override
            public BlockRenderLayer getRenderLayer() {
                return BlockRenderLayer.CUTOUT;
            }
            // Not opaque so other materials (such as, of course, ores) will render
            @Override
            public boolean isOpaqueCube(IBlockState blah) {
                return false;
            }
            // Essentially, the hidden-block world should be a projected grid-like thing...?
            @Override
            public boolean shouldSideBeRendered(IBlockState blah, IBlockAccess w, BlockPos pos, EnumFacing side) {
                BlockPos adj = pos.offset(side);
                IBlockState other = w.getBlockState(adj);
                // this directly adj. to this must never be rendered
                if (other.getBlock() == this)
                    return false;
                // if it contacts something opaque, don't render as we'll probably accidentally make it harder to see
                return !other.isOpaqueCube();
            }
        };
        transparentBlock.setRegistryName("kami_xray_transparent");
        transparentState = transparentBlock.getDefaultState();
        event.getRegistry().registerAll(transparentBlock);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // this runs after transparentBlock is set, right?
        event.getRegistry().registerAll(new ItemBlock(transparentBlock).setRegistryName(transparentBlock.getRegistryName()));
    }

    // Determines if the XRay should hide a given block.
    private static boolean shouldHide(Block block) {
        boolean air = 
            (block == Blocks.GRASS) ||
            (block == Blocks.DIRT) ||
            (block == Blocks.NETHERRACK) ||
            (block == Blocks.GRAVEL) ||
            (block == Blocks.SAND) ||
            (block == Blocks.STONE);
        return air;
    }

    public static IBlockState transform(IBlockState input) {
        if (shouldHide(input.getBlock()))
            return transparentState != null ? transparentState : Blocks.AIR.getDefaultState();
        return input;
    }

    @Override
    protected void onEnable() {
        // This is important because otherwise the changes in ChunkCache behavior won't propagate.
        // Also needs to be done if shouldHide effects change.
        mc.renderGlobal.loadRenderers();
    }

    @Override
    protected void onDisable() {
        // This is important because otherwise the changes in ChunkCache behavior won't propagate.
        // Also needs to be done if shouldHide effects change.
        mc.renderGlobal.loadRenderers();
    }

}
