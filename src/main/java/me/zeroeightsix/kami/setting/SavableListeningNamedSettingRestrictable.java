package me.zeroeightsix.kami.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import me.zeroeightsix.kami.setting.converter.Convertable;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Created by 086 on 12/10/2018.
 */
public abstract class SavableListeningNamedSettingRestrictable<T> extends ListeningNamedSettingRestrictable<T> implements Convertable<T> {

    public SavableListeningNamedSettingRestrictable(T value, Predicate<T> restriction, BiConsumer<T, T> consumer, String name) {
        super(value, restriction, consumer, name);
    }

    @Override
    public void setValueFromString(String value) {
        JsonParser jp = new JsonParser();
        setValue(this.converter().reverse().convert(jp.parse(value)));
    }

    @Override
    public String getValueAsString() {
        return this.converter().convert(getValue()).toString();
    }
}
