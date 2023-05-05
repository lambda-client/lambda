package com.lambda.client.util.items

import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.item.Item

val shulkerList: Set<Block> = hashSetOf(
    Blocks.WHITE_SHULKER_BOX,
    Blocks.ORANGE_SHULKER_BOX,
    Blocks.MAGENTA_SHULKER_BOX,
    Blocks.LIGHT_BLUE_SHULKER_BOX,
    Blocks.YELLOW_SHULKER_BOX,
    Blocks.LIME_SHULKER_BOX,
    Blocks.PINK_SHULKER_BOX,
    Blocks.GRAY_SHULKER_BOX,
    Blocks.SILVER_SHULKER_BOX,
    Blocks.CYAN_SHULKER_BOX,
    Blocks.PURPLE_SHULKER_BOX,
    Blocks.BLUE_SHULKER_BOX,
    Blocks.BROWN_SHULKER_BOX,
    Blocks.GREEN_SHULKER_BOX,
    Blocks.RED_SHULKER_BOX,
    Blocks.BLACK_SHULKER_BOX
)

val signsList: Set<Block> = hashSetOf(
    Blocks.WALL_SIGN,
    Blocks.STANDING_SIGN
)

val blockBlacklist: Set<Block> = hashSetOf(
    Blocks.ANVIL,
    Blocks.BEACON,
    Blocks.BED,
    Blocks.BREWING_STAND,
    Blocks.STONE_BUTTON,
    Blocks.WOODEN_BUTTON,
    Blocks.CAKE,
    Blocks.CAULDRON,
    Blocks.CHEST,
    Blocks.TRAPPED_CHEST,
    Blocks.COMMAND_BLOCK,
    Blocks.CHAIN_COMMAND_BLOCK,
    Blocks.REPEATING_COMMAND_BLOCK,
    Blocks.DAYLIGHT_DETECTOR,
    Blocks.DAYLIGHT_DETECTOR_INVERTED,
    Blocks.DISPENSER,
    Blocks.DROPPER,
    Blocks.OAK_DOOR,
    Blocks.DARK_OAK_DOOR,
    Blocks.ACACIA_DOOR,
    Blocks.BIRCH_DOOR,
    Blocks.JUNGLE_DOOR,
    Blocks.SPRUCE_DOOR,
    Blocks.ENCHANTING_TABLE,
    Blocks.ENDER_CHEST,
    Blocks.OAK_FENCE_GATE,
    Blocks.ACACIA_FENCE_GATE,
    Blocks.BIRCH_FENCE_GATE,
    Blocks.DARK_OAK_FENCE_GATE,
    Blocks.JUNGLE_FENCE_GATE,
    Blocks.SPRUCE_FENCE_GATE,
    Blocks.FLOWER_POT,
    Blocks.FURNACE,
    Blocks.HOPPER,
    Blocks.JUKEBOX,
    Blocks.LEVER,
    Blocks.NOTEBLOCK,
    Blocks.POWERED_COMPARATOR,
    Blocks.UNPOWERED_COMPARATOR,
    Blocks.REDSTONE_ORE,
    Blocks.POWERED_REPEATER,
    Blocks.UNPOWERED_REPEATER,
    Blocks.STANDING_SIGN,
    Blocks.WALL_SIGN,
    Blocks.STRUCTURE_BLOCK,
    Blocks.TRAPDOOR,
    Blocks.CRAFTING_TABLE,
).apply {
    addAll(shulkerList)
}

val Block.item: Item get() = Item.getItemFromBlock(this)

val Block.id: Int get() = Block.getIdFromBlock(this)