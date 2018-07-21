package me.zeroeightsix.kami.module;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.SettingsClass;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by 086 on 23/08/2017.
 */
public class Module extends SettingsClass {

    private final String name = ((Info)getClass().getAnnotation(Info.class)).name();
    private final String description = ((Info)getClass().getAnnotation(Info.class)).description();
    private final Category category = ((Info)getClass().getAnnotation(Info.class)).category();
    @Setting(name = "Bind", hidden = true)
    private int bind = ((Info)getClass().getAnnotation(Info.class)).bind();
    @Setting(name = "Enabled", hidden = true)
    private boolean enabled;
    public boolean alwaysListening = false;
    protected static final Minecraft mc = Minecraft.getMinecraft();

    public Module() {
        alwaysListening = (getClass().getAnnotation(Info.class)).alwaysListening();

        enabled = false;
//        FMLCommonHandler.instance().bus().register(this);
        initSettings();
    }

    public void onUpdate() {}
    public void onRender() {}
    public void onWorldRender(RenderEvent event) {}

    public int getBind() {
        return bind;
    }

    public String getBindName() {
        return bind == -1 ? "NONE" : Keyboard.getKeyName(bind);
    }

    public void setKey(int key) {
        this.bind = key;
    }

    public static enum Category
    {
        COMBAT("Combat", false),
        EXPLOITS("Exploits", false),
        RENDER("Render", false),
        MISC("Misc", false),
        PLAYER("Player", false),
        MOVEMENT("Movement", false),
        HIDDEN("Hidden", true);

        boolean hidden;
        String name;
        private int bind;

        Category(String name, boolean hidden) {
            this.name = name;
            this.hidden = hidden;
        }

        public boolean isHidden() {
            return hidden;
        }

        public String getName() {
            return name;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Info
    {
        String name();
        String description() default "Descriptionless";
        int bind() default 0;
        Module.Category category();
        boolean alwaysListening() default false;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    protected void onEnable() {}
    protected void onDisable() {}

    public void toggle() {
        setEnabled(!isEnabled());
    }

    public void enable() {
        enabled = true;
        onEnable();
        if (!alwaysListening)
            KamiMod.EVENT_BUS.subscribe(this);
    }

    public void disable() {
        enabled = false;
        onDisable();
        if (!alwaysListening)
            KamiMod.EVENT_BUS.unsubscribe(this);
    }

    public boolean isDisabled() {
        return !isEnabled();
    }

    public void setEnabled(boolean enabled) {
        boolean prev = this.enabled;
        if (prev != enabled)
            if (enabled)
                enable();
            else
                disable();
    }

    public String getHudInfo() {
        return null;
    }

    protected final void setAlwaysListening(boolean alwaysListening) {
        this.alwaysListening = alwaysListening;
        if (alwaysListening) KamiMod.EVENT_BUS.subscribe(this);
        if (!alwaysListening && isDisabled()) KamiMod.EVENT_BUS.unsubscribe(this);
    }
}
