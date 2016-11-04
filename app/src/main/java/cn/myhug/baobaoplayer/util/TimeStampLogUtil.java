package cn.myhug.baobaoplayer.util;

import android.util.Log;
import android.util.SparseLongArray;

/**
 * Created by zhengxin on 2016/11/3.
 */

public class TimeStampLogUtil {
    private static final String TAG = "TIME_DURATION";
    private static long mTimeStamp = System.currentTimeMillis();
    private static SparseLongArray mTimeStamps = new SparseLongArray(10);
    public static void logTimeStamp(String log){
        logTimeStamp(0, log);
    }
    public static void logTimeStamp(int index,String log){
        long last = mTimeStamps.get(index);
        if(last == 0){
            last =System.currentTimeMillis();
        }
        long now =System.currentTimeMillis();
        Log.i(TAG+"index:"+index,log+":"+(now-last));
        mTimeStamps.put(index,now);
    }
}
