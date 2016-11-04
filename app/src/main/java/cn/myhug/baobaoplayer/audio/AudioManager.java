package cn.myhug.baobaoplayer.audio;

import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.util.SparseArray;

import java.io.IOException;

/**
 * Created by zhengxin on 2016/11/2.
 */

public class AudioManager {

    private static AudioManager mInstance = null;
    private SparseArray<MediaPlayer> mPlayer = null;
    private AudioManager(){

    }

    public static AudioManager sharedInstance(){
        if(mInstance== null) {
            mInstance = new AudioManager();
        }
        return mInstance;
    }

    public MediaPlayer playMusic(String filePath) throws IOException {
        MediaPlayer mediaPlayer = new  MediaPlayer();
        mediaPlayer.setDataSource(filePath);
        mediaPlayer.prepare();
        mediaPlayer.start();
        return mediaPlayer;
    }

    public MediaPlayer playMusic(  AssetFileDescriptor descriptor) throws IOException {
        MediaPlayer mediaPlayer = new  MediaPlayer();
        mediaPlayer.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
        mediaPlayer.prepare();
        mediaPlayer.start();
        return mediaPlayer;
    }


}
