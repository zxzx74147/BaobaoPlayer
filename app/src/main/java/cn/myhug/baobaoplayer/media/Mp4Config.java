package cn.myhug.baobaoplayer.media;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

/**
 * Created by zhengxin on 2016/10/31.
 */

public class Mp4Config {
    public static final int VIDEO_WIDTH = 640;
    public static final int VIDEO_HEIGHT = 480;
    public static final int VIDEO_BITRATE = 1000000;
    public static final int VIDEO_I_FRAME_INTERVAL = 10;
    public static final int VIDEO_FRAME_RATE = 24;

    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }
}
