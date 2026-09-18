package org.hcz.util;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Date;

public class DateUtil {


// 对日期的【分钟】进行加/减
//
// @param date    日期
// @param minutes 分钟数，负数为减
// @return 加/减几分钟后的日期

    public static Date addDateMinutes(Date date, int minutes) {
        return Date.from(date.toInstant().plus(Duration.ofMinutes(minutes)));
    }


// 对日期的【天】进行加/减，按系统时区的日历日推进。
//
// @param date 日期
// @param days 天数，负数为减
// @return 加/减几天后的日期

    public static Date addDateDays(Date date, int days) {
        return Date.from(date.toInstant().atZone(ZoneId.systemDefault()).plusDays(days).toInstant());
    }
}
