package cn.myhug.baobaoplayer.utils;

import android.content.Context;
import android.os.Environment;

import cn.myhug.baobaoplayer.PlayerApplication;
import cn.myhug.baobaoplayer.widget.base.MagicBaseView;

/**
 * Created by why8222 on 2016/2/26.
 */
public class MagicParams {
    public static Context context;
    public static MagicBaseView magicBaseView;

    public static String videoPath = Environment.getExternalStorageDirectory().getPath();
    public static String videoName = "MagicCamera_test.mp4";

    public static int beautyLevel = 5;

    static  {
        context = PlayerApplication.sharedInstance();
    }
}
