package me.zeroeightsix.kami.module.modules.bewwawho.gui;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/***
 * @author Waizy
 * Updated by S-B99 on 22/12/19
 */
@Module.Info(name = "InventoryViewer", category = Module.Category.GUI, description = "View your inventory on screen", showOnArray = Module.ShowOnArray.OFF)
public class InventoryViewer extends Module {

    private Setting<Integer> optionX;
    private Setting<Integer> optionY;
    private Setting<ViewMode> viewMode = register(Settings.e("Appearance", ViewMode.ICONLARGE));

    private enum ViewMode {
        ICONLARGEBG, ICONLARGE, MC, ICON, ICONBACK, CLEAR, SOLID, SOLIDCLEAR
    }

    private ResourceLocation getBox() {
        if (viewMode.getValue().equals(ViewMode.CLEAR)) {
            return new ResourceLocation("textures/gui/container/invpreview.png");
        }
        else if (viewMode.getValue().equals(ViewMode.ICONBACK)) {
            return new ResourceLocation("textures/gui/container/one.png");
        }
        else if (viewMode.getValue().equals(ViewMode.SOLID)) {
            return new ResourceLocation("textures/gui/container/two.png");
        }
        else if (viewMode.getValue().equals(ViewMode.SOLIDCLEAR)) {
            return new ResourceLocation("textures/gui/container/three.png");
        }
        else if (viewMode.getValue().equals(ViewMode.ICON)) {
            return new ResourceLocation("textures/gui/container/four.png");
        }
        else if (viewMode.getValue().equals(ViewMode.ICONLARGE)) {
            return new ResourceLocation("textures/gui/container/five.png");
        }
        else if (viewMode.getValue().equals(ViewMode.ICONLARGEBG)) {
            return new ResourceLocation("textures/gui/container/six.png");
        }
        else {
            return new ResourceLocation("textures/gui/container/generic_54.png");
        }
    }

    public InventoryViewer() {
        this.optionX = this.register(Settings.i("X", 574));
        this.optionY = this.register(Settings.i("Y", 469));
    }

    private static void preboxrender() {
        GL11.glPushMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.disableAlpha();
        GlStateManager.clear(256);
        GlStateManager.enableBlend();
    }

    private static void postboxrender() {
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.popMatrix();
        GL11.glPopMatrix();
    }

    private static void preitemrender() {
        GL11.glPushMatrix();
        GL11.glDepthMask(true);
        GlStateManager.clear(256);
        GlStateManager.disableDepth();
        GlStateManager.enableDepth();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.scale(1.0f, 1.0f, 0.01f);
    }

    private static void postitemrender() {
        GlStateManager.scale(1.0f, 1.0f, 1.0f);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        GlStateManager.scale(0.5, 0.5, 0.5);
        GlStateManager.disableDepth();
        GlStateManager.enableDepth();
        GlStateManager.scale(2.0f, 2.0f, 2.0f);
        GL11.glPopMatrix();
    }

    public void onEnable() {
        if (mc.player != null) {
            Command.sendChatMessage("[InventoryViewer] Right click the module to move it around");
        } else if (mc.player == null) {
            return;
        }
    }

    @Override
    public void onRender() {
        final NonNullList<ItemStack> items = (NonNullList<ItemStack>) InventoryViewer.mc.player.inventory.mainInventory;
        this.boxrender(this.optionX.getValue(), this.optionY.getValue());
        this.itemrender(items, this.optionX.getValue(), this.optionY.getValue());
    }

    private void boxrender(final int x, final int y) {
        preboxrender();
        ResourceLocation box = getBox();
        InventoryViewer.mc.renderEngine.bindTexture(box);
        InventoryViewer.mc.ingameGUI.drawTexturedModalRect(x, y, 7, 17, 162, 54); // 56 136 1296 432
        postboxrender();
    }

    private void itemrender(final NonNullList<ItemStack> items, final int x, final int y) {
        for (int size = items.size(), item = 9; item < size; ++item) {
            final int slotx = x + 1 + item % 9 * 18;
            final int sloty = y + 1 + (item / 9 - 1) * 18;
            preitemrender();
            InventoryViewer.mc.getRenderItem().renderItemAndEffectIntoGUI((ItemStack) items.get(item), slotx, sloty);
            InventoryViewer.mc.getRenderItem().renderItemOverlays(InventoryViewer.mc.fontRenderer, (ItemStack) items.get(item), slotx, sloty);
            postitemrender();
        }
    }
}