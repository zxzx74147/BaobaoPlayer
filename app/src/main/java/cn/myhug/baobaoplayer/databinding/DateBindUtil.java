package cn.myhug.baobaoplayer.databinding;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

/**
 * Created by zhengxin on 15/9/25.
 */
public class DateBindUtil {

    private static final DateFormat DATE_FORMAT = DateFormat.getDateInstance();

    public static String formatDate(final Date date) {
        return DATE_FORMAT.format(date);
    }

    public static Date parseDate(final String dateString) {
        try {
            return DATE_FORMAT.parse(dateString);
        } catch (ParseException e) {
            return null;
        }
    }
}
