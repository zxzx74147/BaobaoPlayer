package cn.myhug.baobaoplayer.media;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaRecorder;

import java.nio.ByteBuffer;

/**
 * Created by zhengxin on 2016/11/8.
 */

public class AudioRecordSource {

    private int frequence = Mp4Config.OUTPUT_AUDIO_SAMPLE_RATE_HZ; //录制频率，单位hz.这里的值注意了，写的不好，可能实例化AudioRecord对象的时候，会出错。我开始写成11025就不行。这取决于硬件设备
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord mAudioRecord = null;
    private ByteBuffer mByteBuffer= null;
    private byte[] mBuffer = null;
    private Thread mRecordThread = null;
    private volatile boolean mIsRecording = false;
    private long mTimeStamp = 0;
    private MediaEncoder mAudioEncoder = null;


    public void prepare() {
        int bufferSize = AudioRecord.getMinBufferSize(frequence, channelConfig, audioEncoding);
        //实例化AudioRecord
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequence, channelConfig, audioEncoding, bufferSize);
        mBuffer = new byte[bufferSize * 2];
        mByteBuffer = ByteBuffer.allocate(bufferSize * 2);
        mAudioRecord.startRecording();

    }

    public void startRecord() {
        if (mRecordThread == null) {
            mRecordThread.interrupt();
            mRecordThread = null;
        }
        mRecordThread = new Thread(mRecordRunnable);
        mRecordThread.start();
    }

    public void setEndoer(MediaEncoder endoer) {
        mAudioEncoder = endoer;
    }

    public void stop() {
        if (mRecordThread == null) {
            mRecordThread.interrupt();
            mRecordThread = null;
        }
    }




    public void resumeRecord(){
        mIsRecording = true;
    }

    public void pauseRecord(){
        mIsRecording = false;
    }

    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private Runnable mRecordRunnable = new Runnable() {
        @Override
        public void run() {
            try {

                while (!Thread.interrupted()) {
                    //从bufferSize中读取字节，返回读取的short个数
                    int bufferReadResult = mAudioRecord.read(mBuffer, 0, mBuffer.length);
                    if (mIsRecording && mAudioEncoder != null) {

                        mByteBuffer.position(0);
                        mByteBuffer.put(mBuffer,0,bufferReadResult);
                        mByteBuffer.flip();
                        info.offset = 0;
                        info.size = bufferReadResult;
                        mAudioEncoder.drainAudioEncoder(false,mByteBuffer,info);

                    }
                }
            } catch (Exception e) {

            } finally {
                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
                mAudioEncoder.drainAudioEncoder(true,mByteBuffer,info);
            }

        }
    };
}
