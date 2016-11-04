package cn.myhug.baobaoplayer.videoencoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by zhengxin on 2016/10/31.
 */

public class VideoCompressor implements Runnable{

    private static int BUFFER_LENGTH = 1080*1920*4;
    private Handler mHandler = null;


    private MediaExtractor mExtractor = null;
    private  ByteBuffer inputBuffer = ByteBuffer.allocate(BUFFER_LENGTH);

    private File mDstFile = null;
    private MediaCodec mMediaCodec = null;



    public VideoCompressor(){
        mExtractor = new MediaExtractor();
        mHandler = new Handler();


    }


    public boolean setDataSource(String path){
        try {
            mExtractor.setDataSource(path);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean setDataSource(FileDescriptor file){
        try {
            mExtractor.setDataSource(file);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean setDstFile(File file){
        if(file == null){
            return false;
        }
        if(file.exists()){
            file.delete();
        }
        if(!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
        mDstFile = file;
        return true;
    }

    public void startCompress(){

    }


    @Override
    public void run() {
        int numTracks = mExtractor.getTrackCount();
        for (int i = 0; i < numTracks; ++i) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            mExtractor.selectTrack(i);
        }
        inputBuffer.reset();
        while (mExtractor.readSampleData(inputBuffer, 0) >= 0) {
            int trackIndex = mExtractor.getSampleTrackIndex();
            long presentationTimeUs = mExtractor.getSampleTime();
            mExtractor.advance();
        }
    }
}
