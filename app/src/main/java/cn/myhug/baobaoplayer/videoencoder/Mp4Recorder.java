package cn.myhug.baobaoplayer.videoencoder;

import android.media.MediaRecorder;

/**
 * Created by zhengxin on 2016/10/31.
 */

public class Mp4Recorder  {
    private MediaRecorder mRecorder = null;

    public Mp4Recorder(){
        mRecorder = new MediaRecorder();
    }


    private boolean prepareVideoRecorder(){

        return true;
    }
}
