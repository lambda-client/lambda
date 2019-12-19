package me.pork.vocoshulkerpeek;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.IClientCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;

@Mod(
        modid = "vocoshulkerpeek",
        name = "Peek Bypass for KAMI Blue",
        version = "1.1",
        acceptedMinecraftVersions = "[1.12.2]"
)
public class VocoShulkerPeek {
    public static final String MOD_ID = "vocoshulkerpeek";
    public static final String MOD_NAME = "VocoShulkerPeek";
    public static final String VERSION = "1.0";
    public static ItemStack shulker;
    public static Minecraft mc;


    static {
        shulker = ItemStack.EMPTY;
        mc = Minecraft.getMinecraft();
    }

    @EventHandler
    public void postinit(FMLPostInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new PeekCommand());
        MinecraftForge.EVENT_BUS.register(new ShulkerPreview());
    }

    public static NBTTagCompound getShulkerNBT(ItemStack stack) {
        if (mc.player == null) return null;
        NBTTagCompound compound = stack.getTagCompound();
        if (compound != null && compound.hasKey("BlockEntityTag", 10)) {
            NBTTagCompound tags = compound.getCompoundTag("BlockEntityTag");
            if (ModuleManager.getModule("ShulkerBypass").isEnabled()) {
                if (tags.hasKey("Items", 9)) {
                    return tags;
                } else {
                    Command.sendWarningMessage("[ShulkerBypass] Shulker is empty!");
                }
            }
        }

        return null;
    }

    public static class PeekCommand extends CommandBase implements IClientCommand {
        public boolean allowUsageWithoutPrefix(ICommandSender sender, String message) {
            return false;
        }

        public String getName() {
            return "peek";
        }

        public String getUsage(ICommandSender sender) {
            return null;
        }

        public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            if (mc.player != null && ModuleManager.getModule("ShulkerBypass").isEnabled()) {
                if (!VocoShulkerPeek.shulker.isEmpty()) {
                    NBTTagCompound shulkerNBT = VocoShulkerPeek.getShulkerNBT(VocoShulkerPeek.shulker);
                    if (shulkerNBT != null) {
                        TileEntityShulkerBox fakeShulker = new TileEntityShulkerBox();
                        fakeShulker.loadFromNbt(shulkerNBT);
                        String customName = "container.shulkerBox";
                        boolean hasCustomName = false;
                        if (shulkerNBT.hasKey("CustomName", 8)) {
                            customName = shulkerNBT.getString("CustomName");
                            hasCustomName = true;
                        }

                        InventoryBasic inv = new InventoryBasic(customName, hasCustomName, 27);

                        for (int i = 0; i < 27; ++i) {
                            inv.setInventorySlotContents(i, fakeShulker.getStackInSlot(i));
                        }

                        ShulkerPreview.toOpen = inv;
                        ShulkerPreview.guiTicks = 0;
                    }
                } else {
                    Command.sendChatMessage("[ShulkerBypass] No shulker detected! please drop and pickup your shulker.");
                }
            }
        }

        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return true;
        }
    }
}
