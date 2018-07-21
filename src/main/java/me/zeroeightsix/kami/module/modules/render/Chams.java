package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.util.EntityUtil;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Created by 086 on 12/12/2017.
 */
@Module.Info(name = "Chams", category = Module.Category.RENDER)
public class Chams extends Module {

//    static Framebuffer entityFBO;


    public Chams() {
//        entityFBO = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
    }

    @Override
    public void onWorldRender(RenderEvent event) {
//        entityFBO.setFramebufferColor(0,0,0,0);
//        entityFBO.framebufferClear();

//        entityFBO.bindFramebuffer(false);
//        GlStateManager.enableBlend();
//        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.pushMatrix();
        mc.world.playerEntities.stream().filter(entityPlayer -> entityPlayer != mc.player).forEach(Chams::drawEntityOutline);
        mc.world.loadedEntityList.stream().filter(entity -> EntityUtil.isLiving(entity) && entity != mc.player).forEach(Chams::drawEntityOutline);
//        entityFBO.unbindFramebuffer();
        GlStateManager.popMatrix();
//        mc.getFramebuffer().bindFramebuffer(true);

//        entityFBO.framebufferRenderExt(mc.displayWidth, mc.displayHeight, false);

        GlStateManager.color(1,1,1);
        GlStateManager.enableBlend();
    }

    /*
    @EventHandler
    public Listener<DisplaySizeChangedEvent> listener = new Listener<>(event -> {
        try {
            if (GLContext.getCapabilities() == null) return;
        }catch (Exception e) {
            return;
        }
        entityFBO = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
    });*/

    static void drawEntityOutline(Entity e) {
        Render render = Wrapper.getMinecraft().getRenderManager().getEntityRenderObject(e);

        Vec3d interp = EntityUtil.getInterpolatedPos(e, Wrapper.getMinecraft().getRenderPartialTicks());
        interp = interp.subtract(mc.getRenderManager().renderPosX,mc.getRenderManager().renderPosY,mc.getRenderManager().renderPosZ);
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        render.doRender(e, interp.x, interp.y, interp.z, e.rotationYaw, Wrapper.getMinecraft().getRenderPartialTicks());
    }

}
