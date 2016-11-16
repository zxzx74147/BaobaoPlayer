package cn.myhug.baobaoplayer.record;

import cn.myhug.baobaoplayer.media.Mp4Config;

//import com.umeng.analytics.MobclickAgent;

//import cn.myhug.adk.TbadkApplication;
//import cn.myhug.adk.core.util.StringHelper;

/**
 * Created by zhengxin on 16/2/18.
 */
public class H264EncodeConfig {

    public static String encoderNameError = "ENCODE_NAME_ERROR";
    public static String encoderName = "OMX.google.h264.encoder";


    public static int DEFAULT_WIDTH = Mp4Config.VIDEO_WIDTH;
    public static int DEFAULT_HEIGHT = Mp4Config.VIDEO_HEIGHT;
    public static int DEFAULT_FRAMERATE = 25;
    public static int DEFAULT_VIDEO_BITRATE = 800 * 1024;


    public static int ENCODE_H264_MEDIACODEC = 0;

    public int videoW = DEFAULT_WIDTH;
    public int videoH = DEFAULT_HEIGHT;
    public int videoFramerate = DEFAULT_FRAMERATE;
    public int videoBitrate = DEFAULT_VIDEO_BITRATE;
    public int IFI = 5;
    public static int encodeMethod = ENCODE_H264_MEDIACODEC;
    // public int encodeMethod = ENCODE_H264_MEDIACODEC; // 使用MediaCodec 接口进行编码H264


    public H264EncodeConfig() {

    }




}
