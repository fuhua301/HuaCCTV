package com.gathin.tvhome;

final class DayPeriod {
    private DayPeriod() { }
    static String at(int hour) {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour");
        if (hour == 0 || hour == 23) return "午夜";
        if (hour < 5) return "凌晨";
        if (hour < 8) return "早上";
        if (hour < 11) return "上午";
        if (hour < 13) return "中午";
        if (hour < 18) return "下午";
        return "晚上";
    }
}
