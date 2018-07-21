package me.zeroeightsix.kami.setting;

import com.google.gson.JsonElement;

/**
 * Created by 086 on 13/12/2017.
 */
public interface FieldConverter {

    JsonElement toJson(SettingsClass.StaticSetting setting);
    Object fromJson(SettingsClass.StaticSetting setting, JsonElement value);

}
