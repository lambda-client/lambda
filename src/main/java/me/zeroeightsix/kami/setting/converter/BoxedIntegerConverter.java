package me.zeroeightsix.kami.setting.converter;

/**
 * Created by 086 on 13/10/2018.
 */
public class BoxedIntegerConverter extends AbstractBoxedNumberConverter<Integer> {
    @Override
    protected Integer doBackward(String s) {
        return Integer.parseInt(s);
    }
}
