package me.zeroeightsix.kami.module.modules.misc;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemNameTag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/**
 * @author S-B99
 * Created by S-B99 on this commit: 83387d6c2243c2a70dc864c9fbef96a77b9a9735
 */
@Module.Info(name = "AutoNametag", description = "Automatically nametags entities", category = Module.Category.MISC)
public class AutoNametag extends Module {
    private Setting<Float> range = register(Settings.floatBuilder("Range").withMinimum(2.0f).withValue(3.5f).withMaximum(10.0f).build());
    private Setting<Boolean> debug = register(Settings.b("Debug", false));

    public void onUpdate() {
        useNameTag();
    }

    private void useNameTag() {
        int originalSlot = mc.player.inventory.currentItem;
        for (Entity w : mc.world.getLoadedEntityList()) {
            if (w instanceof EntityWither && w.getDisplayName().getUnformattedText().equalsIgnoreCase("Wither")) {
                final EntityWither wither = (EntityWither) w;
                if (mc.player.getDistance(wither) <= range.getValue()) {
                    if (debug.getValue()) Command.sendChatMessage("Found Unnamed Wither");
                    selectNameTags();
                    mc.playerController.interactWithEntity(mc.player, wither, EnumHand.MAIN_HAND);
                }
            }
        }
        mc.player.inventory.currentItem = originalSlot;
    }

    private void selectNameTags() {
        int tagSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack == ItemStack.EMPTY || stack.getItem() instanceof ItemBlock) continue;
            Item tag = stack.getItem();
            if (tag instanceof ItemNameTag) tagSlot = i;
        }

        if (tagSlot == -1) {
            if (debug.getValue()) Command.sendErrorMessage(getChatName() + "Error: No nametags in hotbar");
            return;
        }

        mc.player.inventory.currentItem = tagSlot;
    }
}
