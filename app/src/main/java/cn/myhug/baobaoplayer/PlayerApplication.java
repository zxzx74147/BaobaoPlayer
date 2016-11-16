package cn.myhug.baobaoplayer;

import android.app.Application;
import android.content.Context;

import com.danikula.videocache.HttpProxyCacheServer;

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

    private HttpProxyCacheServer proxy;

    public static HttpProxyCacheServer getProxy(Context context) {
        PlayerApplication app = (PlayerApplication) context.getApplicationContext();
        return app.proxy == null ? (app.proxy = app.newProxy()) : app.proxy;
    }

    private HttpProxyCacheServer newProxy() {
        return new HttpProxyCacheServer(this);
    }
}
