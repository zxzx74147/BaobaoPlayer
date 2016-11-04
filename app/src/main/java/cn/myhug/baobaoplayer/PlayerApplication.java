package cn.myhug.baobaoplayer;

import android.app.Application;

/**
 * Created by zhengxin on 2016/10/27.
 */

public class PlayerApplication extends Application {
    private static PlayerApplication mInstance = null;

    public static PlayerApplication sharedInstance(){
        return  mInstance;
    }
    public void onCreate() {
        super.onCreate();
        mInstance = this;
    }
}
