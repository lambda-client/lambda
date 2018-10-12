package me.zeroeightsix.kami.setting.converter;

import com.google.common.base.Converter;

/**
 * Created by 086 on 13/10/2018.
 */
public abstract class AbstractBoxedNumberConverter<T extends Number> extends Converter<T, String> {

    @Override
    protected String doForward(T t) {
        return t.toString();
    }

}
