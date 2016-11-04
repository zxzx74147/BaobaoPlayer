package cn.myhug.baobaoplayer.data;

import android.net.Uri;

import java.io.Serializable;

/**
 * Created by zhengxin on 16/8/21.
 */

public class IntentData<T extends Serializable> implements Serializable {
    public int type = 0;
    public T data = null;
    public transient Uri uri = null;
}
