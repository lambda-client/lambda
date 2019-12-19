package me.pork.vocoshulkerpeek;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.modules.bewwawho.render.ShulkerBypass;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemShulkerBox;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

public class ShulkerPreview {
    public static int metadataTicks = -1;
    public static int guiTicks = -1;
    public static EntityItem drop;
    public static InventoryBasic toOpen;

    @SubscribeEvent
    public void onEntitySpawn(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityItem) {
            drop = (EntityItem) entity;
            metadataTicks = 0;
        }

    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase == Phase.END && metadataTicks > -1) {
            ++metadataTicks;
        }

        if (event.phase == Phase.END && guiTicks > -1) {
            ++guiTicks;
        }

        if (metadataTicks == 20) {
            if (Minecraft.getMinecraft().player == null) return;
            metadataTicks = -1;
            if (drop.getItem().getItem() instanceof ItemShulkerBox && (KamiMod.MODULE_MANAGER.getModule(ShulkerBypass.class).isEnabled())) {
                Command.sendChatMessage("[ShulkerBypass] New shulker found! use /peek to view its content");
                VocoShulkerPeek.shulker = drop.getItem();
            }
        }

        if (guiTicks == 20) {
            guiTicks = -1;
            VocoShulkerPeek.mc.player.displayGUIChest(toOpen);
        }

    }
}
