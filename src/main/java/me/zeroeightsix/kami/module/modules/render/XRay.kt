package me.zeroeightsix.kami.module.modules.render

import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Setting
import me.zeroeightsix.kami.setting.Settings
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.util.BlockRenderLayer
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.*

/**
 * Created by 20kdc on 15/02/2020.
 * Updated by dominikaaaa on 17/02/20
 * Note for anybody using this in a development environment: THIS DOES NOT WORK. It will lag and the texture will break
 */
@Module.Info(
        name = "XRay",
        category = Module.Category.RENDER,
        description = "See through common blocks!"
)
@EventBusSubscriber(modid = KamiMod.MODID)
class XRay : Module() {
    // Split by ',' & each element trimmed (this is a bit weird but it works for now?)
    private val hiddenBlockNames = register(Settings.stringBuilder("HiddenBlocks").withValue(DEFAULT_XRAY_CONFIG).withConsumer { old: String?, value: String ->
        refreshHiddenBlocksSet(value)
        if (isEnabled) mc.renderGlobal.loadRenderers()
    }.build())
    @JvmField
    var invert: Setting<Boolean> = register(Settings.booleanBuilder("Invert").withValue(false).withConsumer { old: Boolean?, value: Boolean ->
        invertStatic = value
        if (isEnabled) mc.renderGlobal.loadRenderers()
    }.build())
    private val outlines = register(Settings.booleanBuilder("Outlines").withValue(true).withConsumer { old: Boolean?, value: Boolean ->
        outlinesStatic = value
        if (isEnabled) mc.renderGlobal.loadRenderers()
    }.build())

    // Get hidden block list for command display
    fun extGet(): String {
        return extGetInternal(null)
    }

    // Add entry by arbitrary user-provided string
    fun extAdd(s: String) {
        hiddenBlockNames.value = extGetInternal(null) + ", " + s
    }

    // Remove entry by arbitrary user-provided string
    fun extRemove(s: String?) {
        hiddenBlockNames.value = extGetInternal(Block.getBlockFromName(s))
    }

    // Clears the list.
    fun extClear() {
        hiddenBlockNames.value = ""
    }

    // Resets the list to default
    fun extDefaults() {
        extClear()
        extAdd(DEFAULT_XRAY_CONFIG)
    }

    // Set the list to 1 value
    fun extSet(s: String) {
        extClear()
        extAdd(s)
    }

    private fun extGetInternal(filter: Block?): String {
        val sb = StringBuilder()
        var notFirst = false
        for (b in hiddenBlocks) {
            if (b === filter) continue
            if (notFirst) sb.append(", ")
            notFirst = true
            sb.append(Block.REGISTRY.getNameForObject(b))
        }
        return sb.toString()
    }

    private fun refreshHiddenBlocksSet(v: String) {
        hiddenBlocks.clear()
        for (s in v.split(",").toTypedArray()) {
            val s2 = s.trim { it <= ' ' }
            val block = Block.getBlockFromName(s2)
            if (block != null) hiddenBlocks.add(block)
        }
    }

    override fun onEnable() {
        // This is important because otherwise the changes in ChunkCache behavior won't propagate.
        // Also needs to be done if shouldHide effects change.
        mc.renderGlobal.loadRenderers()
    }

    override fun onDisable() {
        // This is important because otherwise the changes in ChunkCache behavior won't propagate.
        // Also needs to be done if shouldHide effects change.
        mc.renderGlobal.loadRenderers()
    }

    companion object {
        // A default reasonable configuration for the XRay. Most people will want to use it like this.
        private const val DEFAULT_XRAY_CONFIG = "minecraft:grass,minecraft:dirt,minecraft:netherrack,minecraft:gravel,minecraft:sand,minecraft:stone"

        // A static mirror of the state.
        private val hiddenBlocks = Collections.synchronizedSet(HashSet<Block>())
        private var invertStatic: Boolean = false
        private var outlinesStatic = true

        // This is the state used for hidden blocks.
        private var transparentState: IBlockState? = null

        // This is used as part of a mechanism to make the Minecraft renderer play along with the XRay.
        // Essentially, the XRay primitive is just a block state transformer.
        // Then this implements a custom block that the block state transformer can use for hidden blocks.
        var transparentBlock: Block? = null

        @SubscribeEvent
        fun registerBlocks(event: RegistryEvent.Register<Block?>) {
            transparentBlock = object : Block(Material.GLASS) {
                // did you know this name's new
                override fun getRenderLayer(): BlockRenderLayer {
                    return BlockRenderLayer.CUTOUT
                }

                // Not opaque so other materials (such as, of course, ores) will render
                override fun isOpaqueCube(blah: IBlockState): Boolean {
                    return false
                }

                // Essentially, the hidden-block world should be a projected grid-like thing...?
                override fun shouldSideBeRendered(blah: IBlockState, w: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean {
                    val adj = pos.offset(side)
                    val other = w.getBlockState(adj)
                    // this directly adj. to this must never be rendered
                    return if (other.block === this) false else !other.isOpaqueCube
                    // if it contacts something opaque, don't render as we'll probably accidentally make it harder to see
                }
            }
            (transparentBlock as Block).setRegistryName("kami_xray_transparent")
            transparentState = (transparentBlock as Block).defaultState
            event.registry.registerAll(transparentBlock)
        }

        @SubscribeEvent
        fun registerItems(event: RegistryEvent.Register<Item?>) {
            // this runs after transparentBlock is set, right?
            event.registry.registerAll(ItemBlock(transparentBlock).setRegistryName(transparentBlock!!.registryName))
        }

        @JvmStatic
        fun transform(input: IBlockState): IBlockState? {
            val b = input.block
            var hide = hiddenBlocks.contains(b)
            if (invertStatic) hide = !hide
            if (hide) {
                var target = Blocks.AIR.defaultState
                if (outlinesStatic && transparentState != null) target = transparentState
                return target
            }
            return input
        }
    }

    init {
        invertStatic = invert.value
        outlinesStatic = outlines.value
        refreshHiddenBlocksSet(hiddenBlockNames.value)
    }
}