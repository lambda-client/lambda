/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.config.SettingGroup
import com.lambda.config.serializer.ItemCodec
import com.lambda.util.NamedEnum
import net.minecraft.item.Item
import net.minecraft.item.Items

class EatSettings(
    c: Configurable,
    baseGroup: NamedEnum
) : SettingGroup(), EatConfig {
    val nutritiousFoodDefaults = listOf(Items.APPLE, Items.BAKED_POTATO, Items.BEEF, Items.BEETROOT, Items.BEETROOT_SOUP, Items.BREAD, Items.CARROT, Items.CHICKEN, Items.CHORUS_FRUIT, Items.COD, Items.COOKED_BEEF, Items.COOKED_CHICKEN, Items.COOKED_COD, Items.COOKED_MUTTON, Items.COOKED_PORKCHOP, Items.COOKED_RABBIT, Items.COOKED_SALMON, Items.COOKIE, Items.DRIED_KELP, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE, Items.GOLDEN_CARROT, Items.HONEY_BOTTLE, Items.MELON_SLICE, Items.MUSHROOM_STEW, Items.MUTTON, Items.POISONOUS_POTATO, Items.PORKCHOP, Items.POTATO, Items.PUFFERFISH, Items.PUMPKIN_PIE, Items.RABBIT, Items.RABBIT_STEW, Items.ROTTEN_FLESH, Items.SALMON, Items.SPIDER_EYE, Items.SUSPICIOUS_STEW, Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.TROPICAL_FISH)
    val resistanceFoodDefaults = listOf(Items.ENCHANTED_GOLDEN_APPLE)
    val regenerationFoodDefaults = listOf(Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE)
    val negativeFoodDefaults = listOf(Items.CHICKEN, Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.ROTTEN_FLESH, Items.SPIDER_EYE)

    override val eatOnHunger by c.setting("Eat On Hunger", true, "Whether to eat when hungry").group(baseGroup).index()
    override val minFoodLevel by c.setting("Minimum Food Level", 6, 0..20, 1, "The minimum food level to eat food", " food level") { eatOnHunger }.group(baseGroup).index()
    override val saturated by c.setting("Saturated", EatConfig.Saturation.EatSmart, "When to stop eating") { eatOnHunger }.group(baseGroup).index()
    override val nutritiousFood by c.setting<Item>("Nutritious Food", nutritiousFoodDefaults, nutritiousFoodDefaults, "Items that are be considered nutritious", ItemCodec) { eatOnHunger }.group(baseGroup).index()
    override val selectionPriority by c.setting("Selection Priority", EatConfig.SelectionPriority.MostNutritious, "The priority for selecting food items") { eatOnHunger }.group(baseGroup).index()
    override val eatOnFire by c.setting("Eat On Fire", true, "Whether to eat when on fire").group(baseGroup).index()
    override val resistanceFood by c.setting<Item>("Resistance Food", resistanceFoodDefaults, resistanceFoodDefaults, "Items that give Fire Resistance", ItemCodec) { eatOnFire }.group(baseGroup).index()
    override val eatOnDamage by c.setting("Eat On Damage", true, "Whether to eat when damaged").group(baseGroup).index()
    override val minDamage by c.setting("Minimum Damage", 10, 0..20, 1, "The minimum damage threshold to trigger eating") { eatOnDamage }.group(baseGroup).index()
    override val regenerationFood by c.setting<Item>("Regeneration Food", regenerationFoodDefaults, regenerationFoodDefaults, "Items that give Regeneration", ItemCodec) { eatOnDamage }.group(baseGroup).index()
    override val ignoreBadFood by c.setting("Ignore Bad Food", true, "Whether to eat when the food is bad").group(baseGroup).index()
    override val badFood by c.setting<Item>("Bad Food", negativeFoodDefaults, negativeFoodDefaults, "Items that are considered bad food", ItemCodec) { ignoreBadFood }.group(baseGroup).index()
}