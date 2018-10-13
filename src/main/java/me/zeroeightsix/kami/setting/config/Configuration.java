package me.zeroeightsix.kami.setting.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.SettingsRegister;
import me.zeroeightsix.kami.setting.converter.Convertable;

import java.util.Map;

/**
 * Created by 086 on 13/10/2018.
 */
public class Configuration {

    public JsonObject produceConfig() {
        return produceConfig(SettingsRegister.ROOT);
    }

    private JsonObject produceConfig(SettingsRegister register) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, SettingsRegister> entry : register.registerHashMap.entrySet()) {
            object.add(entry.getKey(), produceConfig(entry.getValue()));
        }
        for (Map.Entry<String, Setting> entry : register.settingHashMap.entrySet()) {
            Setting setting = entry.getValue();
            if (!(setting instanceof Convertable)) continue;
            object.add(entry.getKey(), ((Convertable<Object, JsonElement>) setting).converter().convert(setting.getValue()));
        }
        return object;
    }

    public void loadConfiguration(SettingsRegister register, JsonObject input) {
        for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
            String key = entry.getKey();
            JsonElement element = entry.getValue();
            if (register.registerHashMap.containsKey(key)) {
                loadConfiguration(register.subregister(key), element.getAsJsonObject());
            } else {
                Setting setting = register.getSetting(key);
                setting.setValue(((Convertable<Object, JsonElement>) setting).converter().reverse().convert(element));
            }
        }
    }


}
