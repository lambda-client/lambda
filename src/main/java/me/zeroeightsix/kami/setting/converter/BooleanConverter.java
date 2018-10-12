package me.zeroeightsix.kami.setting.converter;

import com.google.common.base.Converter;

/**
 * Created by 086 on 13/10/2018.
 */
public class BooleanConverter extends Converter<Boolean, String> {

    @Override
    protected String doForward(Boolean aBoolean) {
        return aBoolean.toString();
    }

    @Override
    protected Boolean doBackward(String s) {
        return Boolean.parseBoolean(s);
    }

}
