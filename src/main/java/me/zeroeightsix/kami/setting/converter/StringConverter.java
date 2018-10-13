package me.zeroeightsix.kami.setting.converter;

import com.google.common.base.Converter;

/**
 * Created by 086 on 13/10/2018.
 */
public class StringConverter extends Converter<String, String> {
    @Override
    protected String doForward(String s) {
        return s;
    }

    @Override
    protected String doBackward(String s) {
        return s;
    }
}
