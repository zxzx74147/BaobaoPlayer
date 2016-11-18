/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.myhug.baobaoplayer.widget;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.MediaController;
import android.widget.MediaController.MediaPlayerControl;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Map;

import cn.myhug.baobaoplayer.filter.IChangeFilter;
import cn.myhug.baobaoplayer.filter.base.MagicSurfaceInputFilter;
import cn.myhug.baobaoplayer.filter.base.gpuimage.GPUImageFilter;
import cn.myhug.baobaoplayer.filter.helper.MagicFilterFactory;
import cn.myhug.baobaoplayer.filter.helper.MagicFilterType;
import cn.myhug.baobaoplayer.gles.Drawable2d;
import cn.myhug.baobaoplayer.gles.EglCore;
import cn.myhug.baobaoplayer.gles.GlUtil;
import cn.myhug.baobaoplayer.gles.ScaledDrawable2d;
import cn.myhug.baobaoplayer.gles.Sprite2d;
import cn.myhug.baobaoplayer.gles.Texture2dProgram;
import cn.myhug.baobaoplayer.gles.WindowSurface;
import cn.myhug.baobaoplayer.utils.OpenGlUtils;
import cn.myhug.baobaoplayer.utils.Rotation;
import cn.myhug.baobaoplayer.utils.TextureRotationUtil;
import tv.danmaku.ijk.media.exo.IjkExoMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;

import static android.R.attr.format;

/**
 * Displays a video file.  The VideoView class
 * can load images from various sources (such as resources or content
 * providers), takes care of computing its measurement from the video so that
 * it can be used in any layout manager, and provides various display options
 * such as scaling and tinting.
 */
public class BBFilterVideoView extends SurfaceView implements MediaPlayerControl, IChangeFilter {

    private int id = (int) (System.currentTimeMillis() % 1000);
    private String TAG = "BBFilterVideoView:" + id;
    // settable by the client
    private Uri mUri;
    private Map<String, String> mHeaders;

    // all possible internal states
    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    // mCurrentState is a VideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    // All the stuff we need for playing and showing a video
//    private SurfaceHolder mSurfaceHolder = null;
    //    private MediaPlayer mMediaPlayer = null;
    private IMediaPlayer mMediaPlayer = null;

    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private MediaController mMediaController;
    private IMediaPlayer.OnCompletionListener mOnCompletionListener;
    private IMediaPlayer.OnPreparedListener mOnPreparedListener;
    private int mCurrentBufferPercentage;
    private IMediaPlayer.OnErrorListener mOnErrorListener;
    private IMediaPlayer.OnInfoListener mOnInfoListener;
    private int mSeekWhenPrepared;  // recording the seek position while preparing
    private boolean mCanPause;
    private boolean mCanSeekBack;
    private boolean mCanSeekForward;
    private Context mContext;
    private RenderThread mRenderThread = null;
    private int mVideoWidth, mVideoHeight;
    private SurfaceHolder sSurfaceHolder;
    private MagicFilterType mFilterType = null;
    private int mRotate = 0;

    public static class UnionData {
        public SurfaceHolder holder;
        public IMediaPlayer player;
    }


    public BBFilterVideoView(Context context) {
        super(context);
        initVideoView();
    }

    public BBFilterVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        initVideoView();
    }

    public BBFilterVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initVideoView();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.i("@@@@", "onMeasure");
        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        if (mVideoWidth > 0 && mVideoHeight > 0) {
            if (mVideoWidth * height > width * mVideoHeight) {
                //Log.i("@@@", "image too tall, correcting");
                height = width * mVideoHeight / mVideoWidth;
            } else if (mVideoWidth * height < width * mVideoHeight) {
                //Log.i("@@@", "image too wide, correcting");
                width = height * mVideoWidth / mVideoHeight;
            } else {
                //Log.i("@@@", "aspect ratio is correct: " +
                //width+"/"+height+"="+
                //mVideoWidth+"/"+mVideoHeight);
            }
        }
        //Log.i("@@@@@@@@@@", "setting size: " + width + 'x' + height);
        setMeasuredDimension(width, height);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(BBFilterVideoView.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(BBFilterVideoView.class.getName());
    }

    public int resolveAdjustedSize(int desiredSize, int measureSpec) {
        int result = desiredSize;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                /* Parent says we can be as big as we want. Just don't be larger
                 * than max size imposed on ourselves.
                 */
                result = desiredSize;
                break;

            case MeasureSpec.AT_MOST:
                /* Parent says we can be as big as we want, up to specSize.
                 * Don't be larger than specSize, and don't be larger than
                 * the max size imposed on ourselves.
                 */
                result = Math.min(desiredSize, specSize);
                break;

            case MeasureSpec.EXACTLY:
                // No choice. Do what we are told.
                result = specSize;
                break;
        }
        return result;
    }

    private void initVideoView() {
        mContext = getContext();
        mVideoWidth = 0;
        mVideoHeight = 0;
        getHolder().addCallback(mSHCallback);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;

        mOnInfoListener = new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer iMediaPlayer, int i, int i1) {
                switch (i) {
                    case IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
                        if (mRenderThread != null) {

                            mRotate = i1;
                            if (mVideoWidth != 0 && mVideoHeight != 0) {
                                if (mRotate % 180 == 90) {
                                    mVideoWidth = iMediaPlayer.getVideoHeight();
                                    mVideoHeight = iMediaPlayer.getVideoWidth();
                                } else {
                                    mVideoWidth = iMediaPlayer.getVideoWidth();
                                    mVideoHeight = iMediaPlayer.getVideoHeight();
                                }
                                requestLayout();
                            }
                            RenderHandler rh = mRenderThread.getHandler();
                            if (rh != null) {
                                rh.sendRotateChanged(mRotate);
                            }
                        }
                        break;
                }
                return false;
            }
        };


    }


    public void setVideoURI(Uri uri) {
        setVideoURI(uri, null);
    }

    /**
     * @hide
     */
    public void setVideoURI(Uri uri, Map<String, String> headers) {
        mUri = uri;
        mHeaders = headers;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }

    private void onPause() {
        if (mRenderThread == null) {
            return;
        }
        RenderHandler rh = mRenderThread.getHandler();
        rh.sendShutdown();
        try {
            mRenderThread.join();
        } catch (InterruptedException ie) {
            // not expected
            throw new RuntimeException("join was interrupted", ie);
        }
        sSurfaceHolder = null;
        mRenderThread = null;
        Log.d(TAG, "onPause END");
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.setSurface(null);
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
        }
    }

    private void onResume() {
        mRenderThread = new RenderThread(new MainHandler(this));
        mRenderThread.setName("TexFromCam Render");
        mRenderThread.start();
        mRenderThread.waitUntilReady();

        RenderHandler rh = mRenderThread.getHandler();

        if (sSurfaceHolder != null) {
            Log.d(TAG, "Sending previous surface");
            rh.sendSurfaceAvailable(sSurfaceHolder, false, mMediaPlayer);
        } else {
            Log.d(TAG, "No previous surface");
        }
        if (mSurfaceWidth > 0 && mSurfaceHeight > 0) {
            rh.sendSurfaceChanged(format, mSurfaceWidth, mSurfaceHeight);
        }
        if (mVideoWidth > 0 && mVideoHeight > 0) {
            rh.sendVideoChanged(mVideoWidth, mVideoHeight);
        }
        if (mFilterType != null) {
            rh.sendFilterChanged(mFilterType);
        }
        Log.d(TAG, "onResume END");


    }

    private void openVideo() {
        if (mUri == null || sSurfaceHolder == null) {
            // not ready for playback just yet, will try again later
            return;
        }
        // Tell the music playback service to pause
        // TODO: these constants need to be published somewhere in the framework.
//        Intent i = new Intent("com.android.music.musicservicecommand");
//        i.putExtra("command", "pause");
//        mContext.sendBroadcast(i);

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false);
        try {
//

//            mMediaPlayer = new IjkMediaPlayer();
//            IjkMediaPlayer mediaPlayer = (IjkMediaPlayer) mMediaPlayer;
//            mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
//            mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);

//            if(mMediaPlayer ==null) {
//                mMediaPlayer = new IjkExoMediaPlayer(mContext);
//            }
            Log.d(TAG, "create Player");
            mMediaPlayer = new IjkExoMediaPlayer(mContext);
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mOnInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(mContext, mUri, mHeaders);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            prepareDecodeSurface();

//            mMediaPlayer.prepareAsync();
            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
            attachMediaController();
        } catch (IOException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        }
    }


    public void prepareDecodeSurface() {
        if (mRenderThread == null) {
            return;
        }
        RenderHandler rh = mRenderThread.getHandler();

        if (sSurfaceHolder != null) {
            Log.d(TAG, "Sending previous surface");
            rh.sendSurfaceAvailable(sSurfaceHolder, false, mMediaPlayer);
        } else {
            Log.d(TAG, "No previous surface");
        }
    }

    public void setMediaController(MediaController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
        attachMediaController();
    }

    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            View anchorView = this.getParent() instanceof View ?
                    (View) this.getParent() : this;
            mMediaController.setAnchorView(anchorView);
            mMediaController.setEnabled(isInPlaybackState());
        }
    }

    IMediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new IMediaPlayer.OnVideoSizeChangedListener() {

                @Override
                public void onVideoSizeChanged(final IMediaPlayer mp, int width, int height, int sar_num, int sar_den) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (mVideoWidth != 0 && mVideoHeight != 0) {
                                if (mRotate % 180 == 90) {
                                    mVideoWidth = mp.getVideoHeight();
                                    mVideoHeight = mp.getVideoWidth();
                                } else {
                                    mVideoWidth = mp.getVideoWidth();
                                    mVideoHeight = mp.getVideoHeight();
                                }
                                requestLayout();
                            }
                            if (mRenderThread != null) {
                                RenderHandler rh = mRenderThread.getHandler();
                                rh.sendVideoChanged(mVideoWidth, mVideoHeight);
                            }
                        }
                    });

                }

//            public void onVideoSizeChanged(IMediaPlayer mp, int width, int height) {
//                mVideoWidth = mp.getVideoWidth();
//                mVideoHeight = mp.getVideoHeight();
//                if (mVideoWidth != 0 && mVideoHeight != 0) {
//                    getHolder().setFixedSize(mVideoWidth, mVideoHeight);
//                    requestLayout();
//                }
//            }
            };

    IMediaPlayer.OnPreparedListener mPreparedListener = new IMediaPlayer.OnPreparedListener() {
        public void onPrepared(final IMediaPlayer mp) {
            post(new Runnable() {
                @Override
                public void run() {
                    mCurrentState = STATE_PREPARED;

                    // Get the capabilities of the player for this stream
//            Metadata data = mp.getMetadata(MediaPlayer.METADATA_ALL,
//                                      MediaPlayer.BYPASS_METADATA_FILTER);
//
//            if (data != null) {
//                mCanPause = !data.has(Metadata.PAUSE_AVAILABLE)
//                        || data.getBoolean(Metadata.PAUSE_AVAILABLE);
//                mCanSeekBack = !data.has(Metadata.SEEK_BACKWARD_AVAILABLE)
//                        || data.getBoolean(Metadata.SEEK_BACKWARD_AVAILABLE);
//                mCanSeekForward = !data.has(Metadata.SEEK_FORWARD_AVAILABLE)
//                        || data.getBoolean(Metadata.SEEK_FORWARD_AVAILABLE);
//            } else {
                    mCanPause = mCanSeekBack = mCanSeekForward = true;
//            }

                    if (mOnPreparedListener != null) {
                        mOnPreparedListener.onPrepared(mMediaPlayer);
                    }
                    if (mMediaController != null) {
                        mMediaController.setEnabled(true);
                    }
                    if (mRotate % 180 == 90) {
                        mVideoWidth = mp.getVideoHeight();
                        mVideoHeight = mp.getVideoWidth();
                    } else {
                        mVideoWidth = mp.getVideoWidth();
                        mVideoHeight = mp.getVideoHeight();
                    }
//                    mVideoWidth = mp.getVideoWidth();
//                    mVideoHeight = mp.getVideoHeight();

                    int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
                    if (seekToPosition != 0) {
                        seekTo(seekToPosition);
                    }
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        //Log.i("@@@@", "video size: " + mVideoWidth +"/"+ mVideoHeight);
                        getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                        if (mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                            // We didn't actually change the size (it was already at the size
                            // we need), so we won't get a "surface changed" callback, so
                            // start the video here instead of in the callback.
                            if (mTargetState == STATE_PLAYING) {
                                start();
                                if (mMediaController != null) {
                                    mMediaController.show();
                                }
                            } else if (!isPlaying() &&
                                    (seekToPosition != 0 || getCurrentPosition() > 0)) {
                                if (mMediaController != null) {
                                    // Show the media controls when we're paused into a video and make 'em stick.
                                    mMediaController.show(0);
                                }
                            }
                        }
                    } else {
                        // We don't know the video size yet, but should start anyway.
                        // The video size might be reported to us later.
                        if (mTargetState == STATE_PLAYING) {
                            start();
                        }
                    }
                }
            });

        }
    };

    private IMediaPlayer.OnCompletionListener mCompletionListener =
            new IMediaPlayer.OnCompletionListener() {
                public void onCompletion(IMediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                    if (mMediaController != null) {
                        mMediaController.hide();
                    }
                    if (mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(mp);
                    }
                }
            };

    private IMediaPlayer.OnErrorListener mErrorListener =
            new IMediaPlayer.OnErrorListener() {
                public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
                    Log.d(TAG, "Error: " + framework_err + "," + impl_err);
                    mCurrentState = STATE_ERROR;
                    mTargetState = STATE_ERROR;
                    if (mMediaController != null) {
                        mMediaController.hide();
                    }

            /* If an error handler has been supplied, use it and finish. */
                    if (mOnErrorListener != null) {
                        if (mOnErrorListener.onError(mp, framework_err, impl_err)) {
                            return true;
                        }
                    }

            /* Otherwise, pop up an error dialog so the user knows that
             * something bad has happened. Only try and pop up the dialog
             * if we're attached to a window. When we're going away and no
             * longer have a window, don't bother showing the user an error.
             */
                    if (getWindowToken() != null) {
//                Resources r = mContext.getResources();
//                int messageId;
//
//                if (framework_err == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
//                    messageId = com.android.internal.R.string.VideoView_error_text_invalid_progressive_playback;
//                } else {
//                    messageId = com.android.internal.R.string.VideoView_error_text_unknown;
//                }
//
//                new AlertDialog.Builder(mContext)
//                        .setMessage(messageId)
//                        .setPositiveButton(com.android.internal.R.string.VideoView_error_button,
//                                new DialogInterface.OnClickListener() {
//                                    public void onClick(DialogInterface dialog, int whichButton) {
//                                        /* If we get here, there is no onError listener, so
//                                         * at least inform them that the video is over.
//                                         */
//                                        if (mOnCompletionListener != null) {
//                                            mOnCompletionListener.onCompletion(mMediaPlayer);
//                                        }
//                                    }
//                                })
//                        .setCancelable(false)
//                        .show();
                    }
                    return true;
                }
            };

    private IMediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new IMediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(IMediaPlayer mp, int percent) {
                    mCurrentBufferPercentage = percent;
                }
            };

    /**
     * Register a callback to be invoked when the media file
     * is loaded and ready to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(IMediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file
     * has been reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(IMediaPlayer.OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs
     * during playback or setup.  If no listener is specified,
     * or if the listener returned false, VideoView will inform
     * the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(IMediaPlayer.OnErrorListener l) {
        mOnErrorListener = l;
    }

    /**
     * Register a callback to be invoked when an informational event
     * occurs during playback or setup.
     *
     * @param l The callback that will be run
     */
    public void setOnInfoListener(IMediaPlayer.OnInfoListener l) {
        mOnInfoListener = l;
    }

    SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
        public void surfaceChanged(SurfaceHolder holder, int format,
                                   int w, int h) {
            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState = (mTargetState == STATE_PLAYING);
            boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
            Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + w + "x" + h +
                    " holder=" + holder);

            if (mRenderThread != null) {
                RenderHandler rh = mRenderThread.getHandler();
                if (rh != null) {
                    rh.sendSurfaceChanged(format, mSurfaceWidth, mSurfaceHeight);
                    rh.sendVideoChanged(mVideoWidth, mVideoHeight);
                }
            } else {
                Log.d(TAG, "Ignoring surfaceChanged");
                return;
            }
        }

        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated holder=" + holder + " (static=" + sSurfaceHolder + ")");
            if (sSurfaceHolder != null) {
                throw new RuntimeException("sSurfaceHolder is already set");
            }

            sSurfaceHolder = holder;

            onResume();

            if (mRenderThread != null) {
                // Normal case -- render thread is running, tell it about the new surface.
                RenderHandler rh = mRenderThread.getHandler();
                rh.sendSurfaceAvailable(holder, true, mMediaPlayer);
            } else {
                // Sometimes see this on 4.4.x N5: power off, power on, unlock, with device in
                // landscape and a lock screen that requires portrait.  The surface-created
                // message is showing up after onPause().
                //
                // Chances are good that the surface will be destroyed before the activity is
                // unpaused, but we track it anyway.  If the activity is un-paused and we start
                // the RenderThread, the SurfaceHolder will be passed in right after the thread
                // is created.
                Log.d(TAG, "render thread not running");
            }
            openVideo();
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // after we return from this we can't use the surface any more
            Log.d(TAG, "surfaceDestroyed holder=" + holder);

            sSurfaceHolder = null;
            if (mRenderThread != null) {
                RenderHandler rh = mRenderThread.getHandler();
                rh.sendSurfaceDestroyed();
            }

            if (mMediaController != null) mMediaController.hide();
            release(true);
            onPause();
        }
    };

    /*
     * release the media player in any state
     */
    private void release(boolean cleartargetstate) {

        if (mMediaPlayer != null) {
            mMediaPlayer.setOnInfoListener(null);
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnPreparedListener(null);
            mMediaPlayer.setOnErrorListener(null);
            mMediaPlayer.setOnVideoSizeChangedListener(null);
            mMediaPlayer.stop();
//            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            if (cleartargetstate) {
                mTargetState = STATE_IDLE;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK &&
                keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                keyCode != KeyEvent.KEYCODE_MENU &&
                keyCode != KeyEvent.KEYCODE_CALL &&
                keyCode != KeyEvent.KEYCODE_ENDCALL;
        if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                } else {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!mMediaPlayer.isPlaying()) {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                }
                return true;
            } else {
                toggleMediaControlsVisiblity();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    public void start() {
        if (isInPlaybackState()) {
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }

    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
    }

    public void suspend() {
        release(false);
    }

    public void resume() {
        openVideo();
    }

    public void resume2() {
        if (!isInPlaybackState()) {
            if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
                mMediaPlayer.start();
                mCurrentState = STATE_PLAYING;
            }
        }
    }

    public int getDuration() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getDuration();
        }

        return -1;
    }

    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

    public boolean canPause() {
        return mCanPause;
    }

    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public void setFilter(MagicFilterType type) {
        mFilterType = type;
        if (mRenderThread == null) {
            return;
        }
        RenderHandler rh = mRenderThread.getHandler();
        if (rh != null) {
            rh.sendFilterChanged(mFilterType);
        }
    }

    private void onPlayerReady(IMediaPlayer player) {
        player.prepareAsync();
    }


    private static class RenderThread extends Thread implements
            SurfaceTexture.OnFrameAvailableListener {
        private static final String TAG = "RenderThread";

        // Orthographic projection matrix.
        private float[] mDisplayProjectionMatrix = new float[16];

        private Object mStartLock = new Object();
        private boolean mReady = false;
        private EglCore mEglCore;
        private Texture2dProgram mTexProgram;
        private WindowSurface mWindowSurface;
        private final ScaledDrawable2d mRectDrawable =
                new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect = new Sprite2d(mRectDrawable);

        private int mWindowSurfaceWidth;
        private int mWindowSurfaceHeight;

        private GPUImageFilter mImageFilter;
        private MagicFilterType mDstType;
        private FloatBuffer mGLCubeBuffer;
        private FloatBuffer mGLTextureBuffer;
        private MagicSurfaceInputFilter mSurfaceFilter = null;

        //        private int mVideoWidth;
//        private int mVideoHeight;
        private int textureId = OpenGlUtils.NO_TEXTURE;
        private SurfaceTexture mDecodeSurfaceTexture;
        private RenderHandler mHandler = null;
        private int mVideoWidth = 0, mVideoHeight = 0;
        private int mRotate = 0;
        private Surface mSuraface = null;
        private IMediaPlayer mPlayer = null;
        private MainHandler mMainHandler = null;

        public RenderThread(MainHandler mainHandler) {
            mMainHandler = mainHandler;
            mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            mGLCubeBuffer.clear();
            mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);
            mGLTextureBuffer.clear();
            float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(0), false, true);
            mGLTextureBuffer.put(textureCords).position(0);
        }

        /**
         * Thread entry point.
         */
        @Override
        public void run() {
            Looper.prepare();
            Log.d(TAG, "looper start");
            // We need to create the Handler before reporting ready.
            mHandler = new RenderHandler(this);
            synchronized (mStartLock) {
                mReady = true;
                mStartLock.notify();    // signal waitUntilReady()
            }
            Log.d(TAG, "looper create eglcore");
            // Prepare EGL and open the camera before we start handling messages.
            mEglCore = new EglCore(null, 0);

            Looper.loop();

            Log.d(TAG, "looper quit");
            releaseSurface();

            synchronized (mStartLock) {
                mReady = false;
            }
        }

        public void waitUntilReady() {
            synchronized (mStartLock) {
                while (!mReady) {
                    try {
                        mStartLock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        public void releaseSurface() {

            if (mSurfaceFilter != null) {
                mSurfaceFilter.destroy();
                mSurfaceFilter = null;
            }
            if (mImageFilter != null) {
                mImageFilter.destroy();
                mImageFilter = null;
            }
            if (mEglCore != null) {
                mEglCore.release();
                mEglCore = null;
            }
            if (mWindowSurface != null) {
                mWindowSurface.release();
                mWindowSurface = null;
            }
            if (mDecodeSurfaceTexture != null) {
                mDecodeSurfaceTexture.release();
                mDecodeSurfaceTexture = null;
            }
            if (textureId != OpenGlUtils.NO_TEXTURE) {
                GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
                textureId = OpenGlUtils.NO_TEXTURE;
            }

        }

        public void prepareSurface(SurfaceHolder holder, boolean newSurface, IMediaPlayer player) {

            if (mPlayer == player) {
                return;
            }
            mPlayer = player;
            if (player == null) {
                return;
            }
            if (mWindowSurface == null) {
                mWindowSurface = new WindowSurface(mEglCore, holder.getSurface(), false);
                mWindowSurface.makeCurrent();
                mImageFilter = MagicFilterFactory.initFilters(MagicFilterType.NONE);
                mImageFilter.init();
                mSurfaceFilter = new MagicSurfaceInputFilter();
                mSurfaceFilter.init();
                mWindowSurfaceWidth = mWindowSurface.getWidth();
                mWindowSurfaceHeight = mWindowSurface.getHeight();

                textureId = OpenGlUtils.getExternalOESTextureID();
                mDecodeSurfaceTexture = new SurfaceTexture(textureId);
                mSuraface = new Surface(mDecodeSurfaceTexture);
                mDecodeSurfaceTexture.setOnFrameAvailableListener(this);
            }
            Log.i(TAG, "prepare====" + player.toString());
            player.setSurface(mSuraface);
            mMainHandler.sendPlayerReady(mPlayer);
            updateGeometry();
        }

        private void updateGeometry() {
            if (mVideoHeight == 0 || mWindowSurfaceWidth == 0) {
                return;
            }
            int width = mWindowSurfaceWidth;
            int height = mWindowSurfaceHeight;
            if (mVideoWidth > 0) {
                height = mVideoHeight * width / mVideoWidth;
            }
            mWindowSurfaceWidth = width;
            mWindowSurfaceHeight = height;

            if (mImageFilter == null) {
                return;
            }

            mImageFilter.onDisplaySizeChanged(width, height);
            mImageFilter.onInputSizeChanged(mVideoWidth, mVideoHeight);

            if (mSurfaceFilter == null) {
                return;
            }
            mSurfaceFilter.onDisplaySizeChanged(mVideoWidth, mVideoHeight);
            mSurfaceFilter.onInputSizeChanged(mVideoWidth, mVideoHeight);
            mSurfaceFilter.initSurfaceFrameBuffer(mVideoWidth, mVideoHeight);
        }


        @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//            Log.i(TAG,"onFrameAvailable");
            mHandler.sendFrameAvailable();
        }


        float[] mtx = new float[16];


        private void draw() {
            mWindowSurface.makeCurrent();

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            mDecodeSurfaceTexture.getTransformMatrix(mtx);

            mSurfaceFilter.setTextureTransformMatrix(mtx);
            if (mDstType != null) {
                mImageFilter.destroy();
                mImageFilter = MagicFilterFactory.initFilters(mDstType);
                mImageFilter.init();
                mImageFilter.onDisplaySizeChanged(mWindowSurfaceWidth, mWindowSurfaceHeight);
                mImageFilter.onInputSizeChanged(mVideoWidth, mVideoHeight);
                mDstType = null;
            }
            if (mImageFilter == null) {
                mSurfaceFilter.onDrawFrame(textureId, mGLCubeBuffer, mGLTextureBuffer);
            } else {
                int id = mSurfaceFilter.onDrawToTexture(textureId);
                GlUtil.checkGlError("onDrawToTexture");
                mImageFilter.onDrawFrame(id, mGLCubeBuffer, mGLTextureBuffer);
                GlUtil.checkGlError("onDrawFrame");
            }
            mWindowSurface.swapBuffers();
            GlUtil.checkGlError("draw done");

        }


        public void setFilter(MagicFilterType type) {
            if (type == null) {
                return;
            }
            mDstType = type;
        }




        public void surfaceAvailable(SurfaceHolder obj, boolean b, IMediaPlayer player) {
            prepareSurface(obj, b, player);
        }

        /**
         * Handles the surfaceChanged message.
         * <p>
         * We always receive surfaceChanged() after surfaceCreated(), but surfaceAvailable()
         * could also be called with a Surface created on a previous run.  So this may not
         * be called.
         */
        private void surfaceChanged(int width, int height) {
            Log.d(TAG, "RenderThread surfaceChanged " + width + "x" + height);

            mWindowSurfaceWidth = width;
            mWindowSurfaceHeight = height;
            finishSurfaceSetup();
        }

        private void finishSurfaceSetup() {
            updateGeometry();
        }

        public void surfaceDestroyed() {
            releaseSurface();
        }

        /**
         * Shuts everything down.
         */
        private void shutdown() {
            Log.d(TAG, "shutdown");
            Looper.myLooper().quit();
        }

        public void frameAvailable() {
            if (mDecodeSurfaceTexture == null) {
                return;
            }
            mDecodeSurfaceTexture.updateTexImage();
            draw();
        }

        public RenderHandler getHandler() {
            return mHandler;
        }

        public void videoSizeChanged(int arg1, int arg2) {
            mVideoWidth = arg1;
            mVideoHeight = arg2;
            updateGeometry();
        }

        public void rotateChange(int arg1) {
            mRotate = arg1;
            float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(arg1), false, true);
            mGLTextureBuffer.put(textureCords).position(0);
            updateGeometry();
        }
    }

    private static class MainHandler extends Handler {
        private static final String TAG = "MainHandler";
        private static final int MSG_PLAYER_AVALIABLE = 0;

        private WeakReference<BBFilterVideoView> mViewRef = null;

        public MainHandler(BBFilterVideoView rt) {
            mViewRef = new WeakReference<>(rt);
        }

        public void sendPlayerReady(IMediaPlayer player) {
            sendMessage(obtainMessage(MSG_PLAYER_AVALIABLE, 0, 0, player));
        }

        @Override  // runs on RenderThread
        public void handleMessage(Message msg) {
            int what = msg.what;
            //Log.d(TAG, "RenderHandler [" + this + "]: what=" + what);

            BBFilterVideoView mVideoView = mViewRef.get();
            if (mVideoView == null) {
                Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_PLAYER_AVALIABLE:
                    mVideoView.onPlayerReady((IMediaPlayer) msg.obj);
                    break;
            }
        }

    }


    private static class RenderHandler extends Handler {
        private static final String TAG = "RenderHandler";
        private static final int MSG_SURFACE_AVAILABLE = 0;
        private static final int MSG_SURFACE_CHANGED = 1;

        private static final int MSG_SURFACE_DESTROYED = 2;
        private static final int MSG_SHUTDOWN = 3;
        private static final int MSG_FRAME_AVAILABLE = 4;
        private static final int MSG_ZOOM_VALUE = 5;
        private static final int MSG_SIZE_VALUE = 6;
        private static final int MSG_ROTATE_VALUE = 7;
        private static final int MSG_POSITION = 8;
        private static final int MSG_REDRAW = 9;
        private static final int MSG_VIDEO_SIZE_CHANGED = 10;
        private static final int MSG_FILTER_CHANGED = 11;
        private static final int MSG_ROTATE_CHANGED = 12;

        // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
        // but no real harm in it.
        private WeakReference<RenderThread> mWeakRenderThread;


        /**
         * Call from render thread.
         */
        public RenderHandler(RenderThread rt) {
            mWeakRenderThread = new WeakReference<RenderThread>(rt);
        }

        /**
         * Sends the "surface available" message.  If the surface was newly created (i.e.
         * this is called from surfaceCreated()), set newSurface to true.  If this is
         * being called during Activity startup for a previously-existing surface, set
         * newSurface to false.
         * <p>
         * The flag tells the caller whether or not it can expect a surfaceChanged() to
         * arrive very soon.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceAvailable(SurfaceHolder holder, boolean newSurface, IMediaPlayer player) {
            UnionData union = new UnionData();
            union.holder = holder;
            union.player = player;
            sendMessage(obtainMessage(MSG_SURFACE_AVAILABLE,
                    newSurface ? 1 : 0, 0, union));
        }

        /**
         * Sends the "surface changed" message, forwarding what we got from the SurfaceHolder.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceChanged(@SuppressWarnings("unused") int format, int width,
                                       int height) {
            // ignore format
            sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height));
        }

        public void sendVideoChanged(int width,
                                     int height) {
            // ignore format
            sendMessage(obtainMessage(MSG_VIDEO_SIZE_CHANGED, width, height));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p>
         * Call from UI thread.
         */
        public void sendSurfaceDestroyed() {
            sendMessage(obtainMessage(MSG_SURFACE_DESTROYED));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p>
         * Call from UI thread.
         */
        public void sendShutdown() {
            sendMessage(obtainMessage(MSG_SHUTDOWN));
        }

        /**
         * Sends the "frame available" message.
         * <p>
         * Call from UI thread.
         */
        public void sendFrameAvailable() {
            sendMessage(obtainMessage(MSG_FRAME_AVAILABLE));
        }

        public void sendFilterChanged(MagicFilterType type) {
            sendMessage(obtainMessage(MSG_FILTER_CHANGED, 0, 0, type));
        }

        /**
         * Sends the "zoom value" message.  "progress" should be 0-100.
         * <p>
         * Call from UI thread.
         */
        public void sendZoomValue(int progress) {
            sendMessage(obtainMessage(MSG_ZOOM_VALUE, progress, 0));
        }

        /**
         * Sends the "size value" message.  "progress" should be 0-100.
         * <p>
         * Call from UI thread.
         */
        public void sendSizeValue(int progress) {
            sendMessage(obtainMessage(MSG_SIZE_VALUE, progress, 0));
        }

        /**
         * Sends the "rotate value" message.  "progress" should be 0-100.
         * <p>
         * Call from UI thread.
         */
        public void sendRotateValue(int progress) {
            sendMessage(obtainMessage(MSG_ROTATE_VALUE, progress, 0));
        }

        /**
         * Sends the "position" message.  Sets the position of the rect.
         * <p>
         * Call from UI thread.
         */
        public void sendPosition(int x, int y) {
            sendMessage(obtainMessage(MSG_POSITION, x, y));
        }

        /**
         * Sends the "redraw" message.  Forces an immediate redraw.
         * <p>
         * Call from UI thread.
         */
        public void sendRedraw() {
            sendMessage(obtainMessage(MSG_REDRAW));
        }

        public void sendRotateChanged(int i1) {
            sendMessage(obtainMessage(MSG_ROTATE_CHANGED, i1, 0, null));
        }

        @Override  // runs on RenderThread
        public void handleMessage(Message msg) {
            int what = msg.what;
            //Log.d(TAG, "RenderHandler [" + this + "]: what=" + what);

            RenderThread renderThread = mWeakRenderThread.get();
            if (renderThread == null) {
                Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_SURFACE_AVAILABLE:
                    renderThread.surfaceAvailable(((UnionData) msg.obj).holder, msg.arg1 != 0, ((UnionData) msg.obj).player);
                    break;
                case MSG_SURFACE_CHANGED:
                    renderThread.surfaceChanged(msg.arg1, msg.arg2);
                    break;
                case MSG_SURFACE_DESTROYED:
                    renderThread.surfaceDestroyed();
                    break;
                case MSG_SHUTDOWN:
                    renderThread.shutdown();
                    break;
                case MSG_FRAME_AVAILABLE:
                    renderThread.frameAvailable();
                    break;
                case MSG_REDRAW:
                    renderThread.draw();
                    break;
                case MSG_VIDEO_SIZE_CHANGED:
                    renderThread.videoSizeChanged(msg.arg1, msg.arg2);
                    break;
                case MSG_FILTER_CHANGED:
                    renderThread.setFilter((MagicFilterType) msg.obj);
                    break;
                case MSG_ROTATE_CHANGED:
                    renderThread.rotateChange(msg.arg1);
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }
    }

}
