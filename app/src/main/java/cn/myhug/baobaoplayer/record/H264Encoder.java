package cn.myhug.baobaoplayer.record;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.IOException;

//import cn.myhug.adk.TbadkApplication;
//import cn.myhug.adp.lib.util.BdUtilHelper;

/**
 * Created by zhengxin on 16/2/18.
 */
@SuppressLint({"InlinedApi", "NewApi"})
public class H264Encoder {
    private static final String MIME = "video/avc";
    private static final String TAG = "H264Encoder";
    private static final int STATUS_STOP = 0;
    private static final int STATUS_RUNNING = 1;

    private MediaCodec mMediaCodec = null;
    private MediaFormat mMediaFormat = null;
    private H264EncodeConfig mConfig = null;
    private Surface mSurface = null;
    private CommonEncoderPump mPump = null;


    public H264Encoder() {

    }


    public int configure(H264EncodeConfig config) {
        mConfig = config;

        try {

            mMediaCodec = MediaCodec.createByCodecName(mConfig.encoderName); // 11
            mMediaFormat = MediaFormat.createVideoFormat(MIME, mConfig.videoW, mConfig.videoH);
            mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mConfig.videoBitrate);
            mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mConfig.videoFramerate);
            mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
            mMediaCodec.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); // 12
            mSurface = mMediaCodec.createInputSurface();
            mPump = new CommonEncoderPump(mMediaCodec);


        } catch (IOException e) {
            e.printStackTrace();
            return 11;
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return 12;
        } catch (Exception e) {
//            BdUtilHelper.showToast(TbadkApplication.getInst(), "编码器初始化失败");
            e.printStackTrace();
            return 13;
        }
        return 0;
    }


    public Surface getEncoderSurface() {
        return mSurface;
    }


    public int start() {
        if (mMediaCodec != null) {
            mMediaCodec.start();
            if (mPump != null) {
                mPump.start();
            }
        }
        return 0;
    }

    public int stop() {
        if (mMediaCodec != null) {
            try {
                mMediaCodec.signalEndOfInputStream();
                mMediaCodec = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return 0;
    }


    public H264EncodeConfig getConfig() {
        return mConfig;
    }


    public void setMuxer(Mp4Muxer muxer) {
        // 使用 ENCODE_H264_OPENH264 直接在CDP进行调用发送接口
        if (mPump != null) {
            mPump.setMuxer(muxer);
        }
    }

    public void reset(){
        mMediaCodec.stop();
        mMediaCodec.release();
        configure(mConfig);
    }

    public MediaFormat getFormat() {
        return mMediaCodec.getOutputFormat();
    }
}
