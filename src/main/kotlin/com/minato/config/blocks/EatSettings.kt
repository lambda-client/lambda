
package com.minato.config.blocks

import com.minato.config.Config
import com.minato.config.ConfigBlock
import net.minecraft.item.Items

class EatSettings(override val c: Config) : EatConfig, ConfigBlock {
    val nutritiousFoodDefaults = listOf(Items.APPLE, Items.BAKED_POTATO, Items.BEEF, Items.BEETROOT, Items.BEETROOT_SOUP, Items.BREAD, Items.CARROT, Items.CHICKEN, Items.CHORUS_FRUIT, Items.COD, Items.COOKED_BEEF, Items.COOKED_CHICKEN, Items.COOKED_COD, Items.COOKED_MUTTON, Items.COOKED_PORKCHOP, Items.COOKED_RABBIT, Items.COOKED_SALMON, Items.COOKIE, Items.DRIED_KELP, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE, Items.GOLDEN_CARROT, Items.HONEY_BOTTLE, Items.MELON_SLICE, Items.MUSHROOM_STEW, Items.MUTTON, Items.POISONOUS_POTATO, Items.PORKCHOP, Items.POTATO, Items.PUFFERFISH, Items.PUMPKIN_PIE, Items.RABBIT, Items.RABBIT_STEW, Items.ROTTEN_FLESH, Items.SALMON, Items.SPIDER_EYE, Items.SUSPICIOUS_STEW, Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.TROPICAL_FISH)
    val resistanceFoodDefaults = listOf(Items.ENCHANTED_GOLDEN_APPLE)
    val regenerationFoodDefaults = listOf(Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE)
    val negativeFoodDefaults = listOf(Items.CHICKEN, Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.ROTTEN_FLESH, Items.SPIDER_EYE)

    override val eatOnHunger by c.setting("Eat On Hunger", true, "Whether to eat when hungry")
    override val minFoodLevel by c.setting("Minimum Food Level", 6, 0..20, 1, "The minimum food level to eat food", " food level") { eatOnHunger }
    override val saturated by c.setting("Saturated", EatConfig.Saturation.EatSmart, "When to stop eating") { eatOnHunger }
    override val nutritiousFood by c.setting("Nutritious Food", nutritiousFoodDefaults, nutritiousFoodDefaults, "Items that are be considered nutritious") { eatOnHunger }
    override val selectionPriority by c.setting("Selection Priority", EatConfig.SelectionPriority.MostNutritious, "The priority for selecting food items") { eatOnHunger }
    override val eatOnFire by c.setting("Eat On Fire", true, "Whether to eat when on fire")
    override val resistanceFood by c.setting("Resistance Food", resistanceFoodDefaults, resistanceFoodDefaults, "Items that give Fire Resistance") { eatOnFire }
    override val eatOnDamage by c.setting("Eat On Damage", true, "Whether to eat when damaged")
    override val minDamage by c.setting("Minimum Damage", 10, 0..20, 1, "The minimum damage threshold to trigger eating") { eatOnDamage }
    override val regenerationFood by c.setting("Regeneration Food", regenerationFoodDefaults, regenerationFoodDefaults, "Items that give Regeneration") { eatOnDamage }
    override val ignoreBadFood by c.setting("Ignore Bad Food", true, "Whether to eat when the food is bad")
    override val badFood by c.setting("Bad Food", negativeFoodDefaults, negativeFoodDefaults, "Items that are considered bad food") { ignoreBadFood }
}
