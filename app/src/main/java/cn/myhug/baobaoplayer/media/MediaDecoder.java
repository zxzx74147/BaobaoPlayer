package cn.myhug.baobaoplayer.media;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

import cn.myhug.baobaoplayer.PlayerApplication;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageFilter;

/**
 * Created by guoheng on 2016/9/1.
 */
public class MediaDecoder {

    private static final String TAG = "MediaMixer";
    private static final boolean VERBOSE = false;           // lots of logging

    private int mDecodeWidth = Mp4Config.VIDEO_WIDTH;
    private int mDecodeHeight = Mp4Config.VIDEO_HEIGHT;

    MediaCodec mVideoDecoder = null;
    MediaCodec mAudioDecoder = null;

    CodecOutputSurface outputSurface = null;
    private GPUImageFilter mFilter = null;

    MediaExtractor extractor = null;


    public int mDecodeTrackVideoIndex;
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

        //Video
        mDecodeTrackVideoIndex = selectVideoTrack(extractor);
        if (mDecodeTrackVideoIndex < 0) {
            throw new RuntimeException("No video track found in " + uri.getPath());
        }
        extractor.selectTrack(mDecodeTrackVideoIndex);

        MediaFormat format = extractor.getTrackFormat(mDecodeTrackVideoIndex);
        if (VERBOSE) {
            Log.d(TAG, "Video size is " + format.getInteger(MediaFormat.KEY_WIDTH) + "x" +
                    format.getInteger(MediaFormat.KEY_HEIGHT));
        }
        mMeidaDuration = format.getLong(MediaFormat.KEY_DURATION);
        int videoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
        int videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        int rotation = 0;
        try {
            rotation = format.getInteger(MediaFormat.KEY_ROTATION);
        } catch (Exception e) {

        }
        if (rotation % 180 == 90) {
            videoHeight = videoHeight + videoWidth;
            videoWidth = videoHeight - videoWidth;
            videoHeight = videoHeight - videoWidth;
        }
        outputSurface = new CodecOutputSurface(mDecodeWidth, mDecodeHeight, encodersurface);
        outputSurface.setFilter(mFilter);
        outputSurface.setVideoWH(videoWidth, videoHeight);

        String mime = format.getString(MediaFormat.KEY_MIME);
        mVideoDecoder = MediaCodec.createDecoderByType(mime);
        mVideoDecoder.configure(format, outputSurface.getSurface(), null, 0);
        mVideoDecoder.start();

        //Audio
        mDecodeTrackAudioIndex = selectAudioTrack(extractor);
        if (mDecodeTrackAudioIndex < 0) {
            throw new RuntimeException("No video track found in " + uri.getPath());
        }
        extractor.selectTrack(mDecodeTrackAudioIndex);

        MediaFormat audioFormat = extractor.getTrackFormat(mDecodeTrackAudioIndex);
        String audioMime = audioFormat.getString(MediaFormat.KEY_MIME);
        mAudioDecoder = MediaCodec.createDecoderByType(audioMime);
        mAudioDecoder.configure(audioFormat,null,null,0);
        mAudioDecoder.start();

    }

    public long getDuration() {
        return mMeidaDuration;
    }


    private int selectVideoTrack(MediaExtractor extractor) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (VERBOSE) {
                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                }
                return i;
            }
        }

        return -1;
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
        if (mVideoDecoder != null) {
            mVideoDecoder.stop();
            mVideoDecoder.release();
            mVideoDecoder = null;
        }

        if (mAudioDecoder != null) {
            mAudioDecoder.stop();
            mAudioDecoder.release();
            mAudioDecoder = null;
        }

        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
        if (outputSurface != null) {
            outputSurface.release();
            outputSurface = null;
        }
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public void setFilter(GPUImageFilter gpuImageFilter) {
        mFilter = gpuImageFilter;

    }
}
