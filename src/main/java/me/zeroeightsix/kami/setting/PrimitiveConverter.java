package me.zeroeightsix.kami.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;

/**
 * Created by 086 on 13/12/2017.
 */
public class PrimitiveConverter implements FieldConverter {

    @Override
    public JsonElement toJson(SettingsClass.StaticSetting setting) {
        Class type = setting.getField().getType();
        if (type.isPrimitive()) {
            if (type != char.class && type != boolean.class)
                return new JsonPrimitive((Number) setting.getValue());
            else if (type == char.class)
                return new JsonPrimitive((Character) setting.getValue());
            else
                return new JsonPrimitive((Boolean) setting.getValue());
        }
        if (Number.class.isAssignableFrom(type))
            return new JsonPrimitive((Number) setting.getValue());
        if (Character.class.isAssignableFrom(type))
            return new JsonPrimitive((Character) setting.getValue());
        if (Boolean.class.isAssignableFrom(type))
            return new JsonPrimitive((Boolean) setting.getValue());
        if (CharSequence.class.isAssignableFrom(type))
            return new JsonPrimitive(String.valueOf(setting.getValue()));

        if (type.isEnum())
            return new JsonPrimitive(Arrays.asList(type.getEnumConstants()).indexOf(setting.getValue()));
        throw new IllegalArgumentException("unconvertable type " + type.getCanonicalName() + "!");
    }

    @Override
    public Object fromJson(SettingsClass.StaticSetting setting, JsonElement value) {
        if (setting.getField().getType().isEnum()) return setting.getField().getType().getEnumConstants()[value.getAsInt()];
        if (value.isJsonPrimitive()) {
            JsonPrimitive p = value.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) {
                if (setting.getField().getType() == int.class)
                    return p.getAsInt();
                if (setting.getField().getType() == long.class)
                    return p.getAsLong();
                if (setting.getField().getType() == short.class)
                    return p.getAsShort();
                if (setting.getField().getType() == double.class)
                    return p.getAsDouble();
                if (setting.getField().getType() == float.class)
                    return p.getAsFloat();
                if (setting.getField().getType() == byte.class)
                    return p.getAsByte();
            }
            if (p.isString()) return p.getAsString();
        }
        return null;
    }
}
