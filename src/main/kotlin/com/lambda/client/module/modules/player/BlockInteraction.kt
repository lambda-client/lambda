package com.lambda.client.module.modules.player

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.mixin.MixinMinecraft
import com.lambda.mixin.render.MixinEntityRenderer
import com.lambda.mixin.world.MixinBlockLiquid
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemPickaxe
import net.minecraft.util.math.RayTraceResult

/**
 * @see MixinBlockLiquid Liquid Interact
 * @see MixinMinecraft Multi Task
 * @see MixinEntityRenderer No Entity Trace
 */
object BlockInteraction : Module(
    name = "BlockInteraction",
    description = "Modifies block interaction",
    category = Category.PLAYER,
    alias = arrayOf("LiquidInteract", "MultiTask", "NoEntityTrace", "NoMiningTrace")
) {
    private val liquidInteract by setting("Liquid Interact", false, description = "Place block on liquid")
    private val multiTask by setting("Multi Task", true, description = "Breaks block and uses item at the same time")
    private val noEntityTrace by setting("No Entity Trace", true, description = "Interact with blocks through entity")
    private val blockOnly by setting("Interact Block Only", true, { liquidInteract }, description = "Only activate liquid interact when holding a block")
    private val checkBlocks by setting("Check Blocks", true, { noEntityTrace }, description = "Only ignores entity when there is block behind")
    private val checkPickaxe by setting("Check Pickaxe", true, { noEntityTrace }, description = "Only ignores entity when holding pickaxe")
    private val sneakOverrides by setting("Sneak Override", true, { noEntityTrace && checkPickaxe }, description = "Overrides pickaxe check if sneaking")

    @JvmStatic
    val isLiquidInteractEnabled
        get() = isEnabled && liquidInteract && (!blockOnly || mc.player?.heldItemMainhand?.item is ItemBlock)

    @JvmStatic
    val isMultiTaskEnabled
        get() = isEnabled && multiTask

    @JvmStatic
    fun isNoEntityTraceEnabled(): Boolean {
        if (isDisabled || !noEntityTrace) return false

        val objectMouseOver = mc.objectMouseOver
        val holdingPickAxe = mc.player?.heldItemMainhand?.item is ItemPickaxe
        val sneaking = mc.gameSettings.keyBindSneak.isKeyDown

        return (!checkBlocks || objectMouseOver != null && objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) // Blocks
            && (!checkPickaxe || holdingPickAxe // Pickaxe
            || sneakOverrides && sneaking) // Override
    }
}
