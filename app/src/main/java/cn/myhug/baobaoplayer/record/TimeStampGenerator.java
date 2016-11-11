package cn.myhug.baobaoplayer.record;

/**
 * Created by zhengxin on 2016/11/10.
 * 时间戳生成器
 */

public class TimeStampGenerator {

    private static TimeStampGenerator mInstance = new TimeStampGenerator();

    public static TimeStampGenerator sharedInstance() {
        return mInstance;
    }

    private volatile long mStartTimeStamp = 0;
    private volatile long mOffsetTimeStamp = 0;
    private volatile long mResult = 0;

    private TimeStampGenerator() {

    }
    public void stop() {

    }

    public void reset() {
        mStartTimeStamp = 0;
        mOffsetTimeStamp = 0;
        mResult = 0;
    }

    public void start() {
        mStartTimeStamp = System.nanoTime();
        mOffsetTimeStamp = mResult;
    }

    public void pause() {

    }

    //异步，pause之后也会调用getAudioStamp
    public synchronized long getAudioStamp() {
        long result = System.nanoTime() - mStartTimeStamp + mOffsetTimeStamp;
        mResult = Math.max(mResult, result);
        return result / 1000;
    }

    public boolean mIsFirstVideo = true;

    public long getDuration() {
        return mResult;
    }

    public synchronized long getVideoStamp() {

        long result = System.nanoTime() - mStartTimeStamp + mOffsetTimeStamp;
        mResult = Math.max(mResult, result);
        if (mIsFirstVideo) {
            mIsFirstVideo = false;
            return 0;
        }
        return result;
    }


}
