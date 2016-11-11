package cn.myhug.baobaoplayer.record;

import android.os.Handler;
import android.os.Message;

/**
 * Custom message handler for main UI thread.
 * <p/>
 * Receives messages from the renderer thread with UI-related updates, like the camera
 * parameters (which we show in a text message on screen).
 */
public class MainHandler extends Handler {

    private static final String TAG = "MainHandler";
    private static final int MSG_SEND_CAMERA_PARAMS0 = 0;
    private static final int MSG_SEND_CAMERA_PARAMS1 = 1;
    private static final int MSG_SEND_RECT_SIZE = 2;
    private static final int MSG_SEND_ZOOM_AREA = 3;
    private static final int MSG_SEND_ROTATE_DEG = 4;
    public static final int MSG_SEND_PREVIEW_START = 5;
    public static final int MSG_SEND_OPEN_CAMERA_FAIL = 6;
    public static final int MSG_SEND_OPEN_INVALID_SURFACE_AVAILABLE = 7;
    public static final int MSG_SEND_SURFACE_INVALID_CREATE = 8;
    public static final int MSG_SEND_UNKNOWN_FAIL = 9;


    public MainHandler() {

    }

    /**
     * Sends the updated camera parameters to the main thread.
     * <p/>
     * Call from render thread.
     */
    public void sendCameraParams(int width, int height, float fps) {
        // The right way to do this is to bundle them up into an object.  The lazy
        // way is to send two messages.
        sendMessage(obtainMessage(MSG_SEND_CAMERA_PARAMS0, width, height));
        sendMessage(obtainMessage(MSG_SEND_CAMERA_PARAMS1, (int) (fps * 1000), 0));
    }

    /**
     * Sends the updated rect size to the main thread.
     * <p/>
     * Call from render thread.
     */
    public void sendRectSize(int width, int height) {
        sendMessage(obtainMessage(MSG_SEND_RECT_SIZE, width, height));
    }

    /**
     * Sends the updated zoom area to the main thread.
     * <p/>
     * Call from render thread.
     */
    public void sendZoomArea(int width, int height) {
        sendMessage(obtainMessage(MSG_SEND_ZOOM_AREA, width, height));
    }

    /**
     * Sends the updated zoom area to the main thread.
     * <p/>
     * Call from render thread.
     */
    public void sendRotateDeg(int rot) {
        sendMessage(obtainMessage(MSG_SEND_ROTATE_DEG, rot, 0));
    }

    @Override
    public void handleMessage(Message msg) {


        switch (msg.what) {
            case MSG_SEND_CAMERA_PARAMS0: {
//                activity.mCameraPreviewWidth = msg.arg1;
//                activity.mCameraPreviewHeight = msg.arg2;
                break;
            }
            case MSG_SEND_CAMERA_PARAMS1: {
//                activity.mCameraPreviewFps = msg.arg1 / 1000.0f;
                break;
            }
            case MSG_SEND_RECT_SIZE: {
//                activity.mRectWidth = msg.arg1;
//                activity.mRectHeight = msg.arg2;
                break;
            }
            case MSG_SEND_ZOOM_AREA: {
//                activity.mZoomWidth = msg.arg1;
//                activity.mZoomHeight = msg.arg2;
                break;
            }
            case MSG_SEND_ROTATE_DEG: {
//                activity.mRotateDeg = msg.arg1;
                break;
            }
            default:
                throw new RuntimeException("Unknown message " + msg.what);
        }
    }
}
