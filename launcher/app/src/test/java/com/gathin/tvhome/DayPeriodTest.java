package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DayPeriodTest {
    @Test public void allDayPeriodBoundaries() {
        String[] expected = {"午夜", "凌晨", "凌晨", "凌晨", "凌晨", "早上", "早上", "早上",
                "上午", "上午", "上午", "中午", "中午", "下午", "下午", "下午", "下午", "下午",
                "晚上", "晚上", "晚上", "晚上", "晚上", "午夜"};
        for (int hour = 0; hour < 24; hour++) assertEquals("hour=" + hour, expected[hour], DayPeriod.at(hour));
    }
}
