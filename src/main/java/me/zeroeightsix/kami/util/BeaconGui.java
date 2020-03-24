package me.zeroeightsix.kami.util;

import me.zeroeightsix.kami.module.modules.misc.BeaconSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiBeacon;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.IInventory;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

/**
 * @author TBM
 */
public class BeaconGui extends GuiBeacon {
    private static final ResourceLocation BEACON_GUI_TEXTURES = new ResourceLocation("textures/gui/container/beacon.png");
    public static final Potion[][] EFFECTS_LIST = new Potion[][] {{MobEffects.SPEED, MobEffects.HASTE}, {MobEffects.RESISTANCE, MobEffects.JUMP_BOOST}, {MobEffects.STRENGTH}};

    public BeaconGui(InventoryPlayer playerInventory, IInventory tileBeaconIn) {
        super(playerInventory, tileBeaconIn);
    }

    boolean doRenderButtons;

    @Override
    public void initGui() {
        super.initGui();
        doRenderButtons = true;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (doRenderButtons) {
            int id = 20;
            int newY = this.guiTop;
            for (Potion[] pos1 : EFFECTS_LIST) {
                for (Potion potion : pos1) {
                    PowerButtonCustom customPotion =
                            new PowerButtonCustom(id, guiLeft - 27, newY, potion, 0);
                    this.buttonList.add(customPotion);
                    if (potion == Potion.getPotionById(BeaconSelector.effect)) {
                        customPotion.setSelected(true);
                    }
                    newY += 27;
                    id++;
                }
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (button instanceof BeaconGui.PowerButtonCustom) {
            BeaconGui.PowerButtonCustom guibeacon$powerbutton = (BeaconGui.PowerButtonCustom)button;

            if (guibeacon$powerbutton.isSelected()) return;

            int i = Potion.getIdFromPotion(guibeacon$powerbutton.effect);

            if (guibeacon$powerbutton.tier < 3) {
                BeaconSelector.effect = i;
            }

            this.buttonList.clear();
            this.initGui();
            this.updateScreen();
        }

    }

    class PowerButtonCustom extends BeaconGui.Button {
        private final Potion effect;
        private final int tier;

        public PowerButtonCustom(int buttonId, int x, int y, Potion effectIn, int tierIn) {
            super(buttonId, x, y, GuiContainer.INVENTORY_BACKGROUND, effectIn.getStatusIconIndex() % 8 * 18, 198 + effectIn.getStatusIconIndex() / 8 * 18);
            this.effect = effectIn;
            this.tier = tierIn;
        }

        public void drawButtonForegroundLayer(int mouseX, int mouseY) {
            String s = I18n.format(this.effect.getName());

            if (this.tier >= 3 && this.effect != MobEffects.REGENERATION) {
                s = s + " II";
            }

            BeaconGui.this.drawHoveringText(s, mouseX, mouseY);
        }
    }

    @SideOnly(Side.CLIENT)
    static class Button extends GuiButton {
        private final ResourceLocation iconTexture;
        private final int iconX;
        private final int iconY;
        private boolean selected;

        protected Button(int buttonId, int x, int y, ResourceLocation iconTextureIn, int iconXIn, int iconYIn) {
            super(buttonId, x, y, 22, 22, "");
            this.iconTexture = iconTextureIn;
            this.iconX = iconXIn;
            this.iconY = iconYIn;
        }

        /**
         * Draws this button to the screen.
         */
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (this.visible) {
                mc.getTextureManager().bindTexture(BEACON_GUI_TEXTURES);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
                int j = 0;

                if (!this.enabled) {
                    j += this.width * 2;
                }
                else if (this.selected) {
                    j += this.width * 1;
                }
                else if (this.hovered) {
                    j += this.width * 3;
                }

                this.drawTexturedModalRect(this.x, this.y, j, 219, this.width, this.height);

                if (!BEACON_GUI_TEXTURES.equals(this.iconTexture)) {
                    mc.getTextureManager().bindTexture(this.iconTexture);
                }

                this.drawTexturedModalRect(this.x + 2, this.y + 2, this.iconX, this.iconY, 18, 18);
            }
        }

        public boolean isSelected()
        {
            return this.selected;
        }

        public void setSelected(boolean selectedIn)
        {
            this.selected = selectedIn;
        }
    }
}
