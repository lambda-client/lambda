package me.zeroeightsix.kami.module.modules.gui;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.gui.kami.KamiGUI;
import me.zeroeightsix.kami.gui.rgui.component.container.use.Frame;
import me.zeroeightsix.kami.gui.rgui.util.ContainerHelper;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.List;

/***
 * @author Unknown // LGPL Licensed
 * Updated by S-B99 on 18/01/20
 * GUI method written by S-B99
 */
@Module.Info(name = "InventoryViewer", category = Module.Category.GUI, description = "View your inventory on screen", showOnArray = Module.ShowOnArray.OFF)
public class InventoryViewer extends Module {
    public Setting<Boolean> startupGlobal = register(Settings.b("Enable Automatically", true));
    private Setting<ViewMode> viewMode = register(Settings.e("Appearance", ViewMode.ICONLARGE));

    KamiGUI kamiGUI = KamiMod.getInstance().getGuiManager();
    private int invPos(int i) {
        kamiGUI = KamiMod.getInstance().getGuiManager();
        if (kamiGUI != null) {
            List<Frame> frames = ContainerHelper.getAllChildren(Frame.class, kamiGUI);
            for (Frame frame : frames) {
                if (!frame.getTitle().equalsIgnoreCase("inventory viewer")) continue;
                switch (i) {
                    case 0:
                        return frame.getX();
                    case 1:
                        return frame.getY();
                    default:
                        return 0;
                }
            }
        }
        return 0;
    }
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

    private static void preBoxRender() {
        GL11.glPushMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.disableAlpha();
        GlStateManager.clear(256);
        GlStateManager.enableBlend();
    }

    private static void postBoxRender() {
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.popMatrix();
        GL11.glPopMatrix();
    }

    private static void preItemRender() {
        GL11.glPushMatrix();
        GL11.glDepthMask(true);
        GlStateManager.clear(256);
        GlStateManager.disableDepth();
        GlStateManager.enableDepth();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.scale(1.0f, 1.0f, 0.01f);
    }

    private static void postItemRender() {
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

    @Override
    public void onRender() {
        final NonNullList<ItemStack> items = InventoryViewer.mc.player.inventory.mainInventory;
        boxRender(invPos(0), invPos(1));
        itemRender(items, invPos(0), invPos(1));
    }

    private void boxRender(final int x, final int y) {
        preBoxRender();
        ResourceLocation box = getBox();
        mc.renderEngine.bindTexture(box);
        mc.ingameGUI.drawTexturedModalRect(x, y, 7, 17, 162, 54); // 168 56 // width and height of inventory
        postBoxRender();
    }

    private void itemRender(final NonNullList<ItemStack> items, final int x, final int y) {
        for (int size = items.size(), item = 9; item < size; ++item) {
            final int slotX = x + 1 + item % 9 * 18;
            final int slotY = y + 1 + (item / 9 - 1) * 18;
            preItemRender();
            mc.getRenderItem().renderItemAndEffectIntoGUI(items.get(item), slotX, slotY);
            mc.getRenderItem().renderItemOverlays(mc.fontRenderer, items.get(item), slotX, slotY);
            postItemRender();
        }
    }
}