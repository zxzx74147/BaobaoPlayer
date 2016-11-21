package cn.myhug.baobaoplayer.media;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

import cn.myhug.baobaoplayer.PlayerApplication;

/**
 * Created by guoheng on 2016/9/1.
 */
public class AudioDecoder {

    private static final String TAG = "AudioDecoder";
    private static final boolean VERBOSE = false;           // lots of logging
    public static final int BUFFER_LEN = 1024 * 256;
    public static final int TIMEOUT_USEC = 10000;

    MediaCodec mAudioDecoder = null;

    MediaExtractor extractor = null;
    MediaCodec.BufferInfo latest = new MediaCodec.BufferInfo();


    public int mDecodeTrackAudioIndex;

    private long mMeidaDuration = 0;

    // where to find files (note: requires WRITE_EXTERNAL_STORAGE permission)
    private Uri uri;

    private FileDescriptor fileDescriptor = null;

    private byte[] mMixAudioBuffer = new byte[BUFFER_LEN];
    private byte[] mResult = new byte[BUFFER_LEN];
    private int mLeftLen = 0;


    public void prepare() throws IOException {
        extractor = new MediaExtractor();
        try {
            if (fileDescriptor != null) {
                extractor.setDataSource(fileDescriptor);
            } else if (uri != null) {
                extractor.setDataSource(PlayerApplication.sharedInstance(), uri, null);
            } else {
                extractor = null;
                return;
            }
        }catch (Exception e){
            extractor = null;
            return;
        }

        //Audio
        mDecodeTrackAudioIndex = selectAudioTrack(extractor);
        if (mDecodeTrackAudioIndex < 0) {
            extractor = null;
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

    public boolean hasSource() {
        return extractor != null;
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

    public void setFileDescriptor(FileDescriptor descriptor) {
        this.fileDescriptor = descriptor;
    }


    public byte[] getResult() {
        return mResult;
    }

    public int pumpAudioBuffer(int len) {
        int sum = 0;
        int loopCount = 0;
        while (sum < len) {

            int dstLen = len - sum;
            if (mLeftLen >= dstLen) {
                System.arraycopy(mMixAudioBuffer, 0, mResult, sum, dstLen);
                mLeftLen -= dstLen;
                System.arraycopy(mMixAudioBuffer, dstLen, mMixAudioBuffer, 0, mLeftLen);
                sum += dstLen;
                continue;
            } else if (mLeftLen > 0) {
                System.arraycopy(mMixAudioBuffer, 0, mResult, sum, mLeftLen);
                sum += mLeftLen;
                mLeftLen = 0;

            }

            int inputIndex = mAudioDecoder.dequeueInputBuffer(TIMEOUT_USEC);
            int outputIndex = -1;
            if (inputIndex >= 0) {
                ByteBuffer buffer = mAudioDecoder.getInputBuffers()[inputIndex];
                int chunkSize = extractor.readSampleData(buffer, 0);
                if (chunkSize > 0) {
                    long presentationTimeUs = extractor.getSampleTime();
                    mAudioDecoder.queueInputBuffer(inputIndex, 0, chunkSize, presentationTimeUs, extractor.getSampleFlags() /*flags*/);
                    outputIndex = mAudioDecoder.dequeueOutputBuffer(latest, TIMEOUT_USEC);
                } else {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                }
                if (!extractor.advance()) {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                }
            } else {
                release();
                try {
                    prepare();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (VERBOSE) Log.d(TAG, "no output from mAudioDecoder available");
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not important for us, since we're using Surface
                if (VERBOSE) Log.d(TAG, "mAudioDecoder output buffers changed");
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mAudioDecoder.getOutputFormat();
                if (VERBOSE) Log.d(TAG, "mAudioDecoder output format changed: " + newFormat);
            } else if (outputIndex < 0) {

            } else {
                ByteBuffer decodeBuffer = mAudioDecoder.getOutputBuffers()[outputIndex];
                decodeBuffer.get(mMixAudioBuffer, mLeftLen, latest.size);
                mLeftLen += latest.size;
                mAudioDecoder.releaseOutputBuffer(outputIndex, false);
            }
            loopCount++;
            if (loopCount > 72) {
                if (VERBOSE) Log.d(TAG, "extractor fail");
                extractor = null;
                break;
            }
        }
        return sum;
    }

}
