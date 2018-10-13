package me.zeroeightsix.kami.setting.converter;

import com.google.common.base.Converter;

/**
 * Created by 086 on 12/10/2018.
 */
public interface Convertable<T, JsonElement> {

    Converter<T, JsonElement> converter();

}
