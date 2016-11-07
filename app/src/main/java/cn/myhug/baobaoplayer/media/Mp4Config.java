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

    public static final String MIME_TYPE_AUDIO = "audio/mp4a-latm";    // H.264 Advanced Video Coding
    public static final int OUTPUT_AUDIO_BIT_RATE = 32 * 1024;
    public static final int OUTPUT_AUDIO_AAC_PROFILE =
            MediaCodecInfo.CodecProfileLevel.AACObjectHE;
    public static final int OUTPUT_AUDIO_SAMPLE_RATE_HZ = 44100; // Must match the input stream.


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
