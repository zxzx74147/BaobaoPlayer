package cn.myhug.baobaoplayer.record;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;

/**
 * Handler for RenderThread.  Used for messages sent from the UI thread to the render thread.
 * <p/>
 * The object is created on the render thread, and the various "send" methods are called
 * from the UI thread.
 */
public class RenderHandler extends Handler {
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
    private static final int MSG_ENCODER_AVAILABLE = 10;
    private static final int MSG_SWITCH_CAMERA = 11;
    private static final int MSG_ENCODER_AVAILABLE2 = 12;

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
     * <p/>
     * The flag tells the caller whether or not it can expect a surfaceChanged() to
     * arrive very soon.
     * <p/>
     * Call from UI thread.
     */
    public void sendSurfaceAvailable(SurfaceHolder holder, boolean newSurface) {
        sendMessage(obtainMessage(MSG_SURFACE_AVAILABLE,
                newSurface ? 1 : 0, 0, holder));
    }

    /**
     * Sends the "surface available" message.  If the surface was newly created (i.e.
     * this is called from surfaceCreated()), set newSurface to true.  If this is
     * being called during Activity startup for a previously-existing surface, set
     * newSurface to false.
     * <p/>
     * The flag tells the caller whether or not it can expect a surfaceChanged() to
     * arrive very soon.
     * <p/>
     * Call from UI thread.
     */
    public void sendEncoderAvailable(Surface surface) {
        sendMessage(obtainMessage(MSG_ENCODER_AVAILABLE,
                surface));
    }

    /**
     * Sends the "surface changed" message, forwarding what we got from the SurfaceHolder.
     * <p/>
     * Call from UI thread.
     */
    public void sendSurfaceChanged(@SuppressWarnings("unused") int format, int width,
                                   int height) {
        // ignore format
        sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height));
    }

    /**
     * Sends the "shutdown" message, which tells the render thread to halt.
     * <p/>
     * Call from UI thread.
     */
    public void sendSurfaceDestroyed() {
        sendMessage(obtainMessage(MSG_SURFACE_DESTROYED));
    }

    /**
     * Sends the "shutdown" message, which tells the render thread to halt.
     * <p/>
     * Call from UI thread.
     */
    public void sendShutdown() {
        sendMessage(obtainMessage(MSG_SHUTDOWN));
    }

    /**
     * Sends the "frame available" message.
     * <p/>
     * Call from UI thread.
     */
    public void sendFrameAvailable() {
        sendMessage(obtainMessage(MSG_FRAME_AVAILABLE));
    }

    /**
     * Sends the "zoom value" message.  "progress" should be 0-100.
     * <p/>
     * Call from UI thread.
     */
    public void sendZoomValue(int progress) {
        sendMessage(obtainMessage(MSG_ZOOM_VALUE, progress, 0));
    }

    /**
     * Sends the "size value" message.  "progress" should be 0-100.
     * <p/>
     * Call from UI thread.
     */
    public void sendSizeValue(int progress) {
        sendMessage(obtainMessage(MSG_SIZE_VALUE, progress, 0));
    }

    /**
     * Sends the "rotate value" message.  "progress" should be 0-100.
     * <p/>
     * Call from UI thread.
     */
    public void sendRotateValue(int progress) {
        sendMessage(obtainMessage(MSG_ROTATE_VALUE, progress, 0));
    }

    public void switchCamera() {
        sendMessage(obtainMessage(MSG_SWITCH_CAMERA, 0));
    }

    /**
     * Sends the "position" message.  Sets the position of the rect.
     * <p/>
     * Call from UI thread.
     */
    public void sendPosition(int x, int y) {
        sendMessage(obtainMessage(MSG_POSITION, x, y));
    }

    /**
     * Sends the "redraw" message.  Forces an immediate redraw.
     * <p/>
     * Call from UI thread.
     */
    public void sendRedraw() {
        sendMessage(obtainMessage(MSG_REDRAW));
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
                renderThread.surfaceAvailable((SurfaceHolder) msg.obj, msg.arg1 != 0);
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
            case MSG_ZOOM_VALUE:
                renderThread.setZoom(msg.arg1);
                break;
            case MSG_SIZE_VALUE:
                renderThread.setSize(msg.arg1);
                break;
            case MSG_ROTATE_VALUE:
                renderThread.setRotate(msg.arg1);
                break;
            case MSG_POSITION:
                renderThread.setPosition(msg.arg1, msg.arg2);
                break;
            case MSG_REDRAW:
                renderThread.draw();
                break;
            case MSG_ENCODER_AVAILABLE:
                renderThread.setEncoderSurface((Surface) msg.obj);

                break;
            case MSG_SWITCH_CAMERA:
                renderThread.switchCamera();
                break;
            default:
                throw new RuntimeException("unknown message " + what);
        }

    }
}
