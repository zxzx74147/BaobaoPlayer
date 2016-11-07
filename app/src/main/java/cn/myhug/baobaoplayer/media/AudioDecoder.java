package cn.myhug.baobaoplayer.media;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

import cn.myhug.baobaoplayer.PlayerApplication;

/**
 * Created by guoheng on 2016/9/1.
 */
public class AudioDecoder {

    private static final String TAG = "MediaMixer";
    private static final boolean VERBOSE = false;           // lots of logging


    MediaCodec mAudioDecoder = null;

    MediaExtractor extractor = null;


    public int mDecodeTrackAudioIndex;

    private long mMeidaDuration = 0;

    // where to find files (note: requires WRITE_EXTERNAL_STORAGE permission)
    private Uri uri;


    void SurfaceDecoderPrePare(Surface encodersurface) throws IOException {
        extractor = new MediaExtractor();
        if (uri != null) {
            extractor.setDataSource(PlayerApplication.sharedInstance(), uri, null);
        } else {
            throw new RuntimeException("No video uri found ");
        }

        //Audio
        mDecodeTrackAudioIndex = selectAudioTrack(extractor);
        if (mDecodeTrackAudioIndex < 0) {
            throw new RuntimeException("No video track found in " + uri.getPath());
        }
        extractor.selectTrack(mDecodeTrackAudioIndex);

        MediaFormat audioFormat = extractor.getTrackFormat(mDecodeTrackAudioIndex);
        mMeidaDuration = audioFormat.getLong(MediaFormat.KEY_DURATION);
        String audioMime = audioFormat.getString(MediaFormat.KEY_MIME);
        mAudioDecoder = MediaCodec.createDecoderByType(audioMime);
        mAudioDecoder.configure(audioFormat, null, null, 0);
        mAudioDecoder.start();

    }

    public long getDuration() {
        return mMeidaDuration;
    }


    private int selectAudioTrack(MediaExtractor extractor) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                if (VERBOSE) {
                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                }
                return i;
            }
        }

        return -1;
    }


    void release() {
        if (mAudioDecoder != null) {
            mAudioDecoder.stop();
            mAudioDecoder.release();
            mAudioDecoder = null;
        }
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }

    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

}
