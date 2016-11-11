/*
 * Copyright 2014 Google Inc. All rights reserved.
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

package cn.myhug.baobaoplayer.record;

import android.app.Activity;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;


/**
 * Camera-related utility functions.
 */
public class CameraUtils {
    private static final String TAG = "CameraUtils";


    /**
     * Iterate over supported camera preview sizes to see which one best fits the
     * dimensions of the given view while maintaining the aspect ratio. If none can,
     * be lenient with the aspect ratio.
     *
     * @param w The width of the view.
     * @param h The height of the view.
     * @return Best match camera preview size to fit in the view.
     */
    public static Camera.Size choosePreviewSize(Camera.Parameters parms, int w, int h) {
        // Use a very small tolerance because we want an exact match.
        List<Camera.Size> sizes = parms.getSupportedPreviewSizes();
        final double ASPECT_TOLERANCE = 0.1;
        final double ASPECT_TOLERANCE_SECONDARY = 0.6;
        double targetRatio = (double) w / h;
        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;

        // Start with max value and refine as we iterate over available preview sizes. This is the
        // minimum difference between view and camera height.
        double minDiff = Double.MAX_VALUE;
//        double minDiff = 0f;

        // Target view height
        int targetHeight = h;


        ArrayList<Camera.Size> selectedsize = new ArrayList<>();
        // Try to find a preview size that matches aspect ratio and the target view size.
        // Iterate over all available sizes and pick the largest size that can fit in the view and
        // still maintain the aspect ratio.
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            Log.d(TAG,"choosePreviewSize ="+size.width + " w:h " + size.height + " , ratio : " + ratio);
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
                continue;
            }
            selectedsize.add(size);
        }
        if(selectedsize.size() > 0 ) {
            for (Camera.Size size : selectedsize) {
                if (targetHeight > size.height) {
                    continue;
                }

                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }

            if (null == optimalSize) {
                minDiff = Double.MAX_VALUE;
                for (Camera.Size size : selectedsize) {
                    if (Math.abs(size.height - targetHeight) < minDiff) {
                        optimalSize = size;
                        minDiff = Math.abs(size.height - targetHeight);
                    }
                }
            }
        }
        // Cannot find preview size that matches the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                double ratio = (double) size.width / size.height;
                if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE_SECONDARY) {
                    continue;
                }
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        if (optimalSize != null) {
            parms.setPreviewSize(optimalSize.width, optimalSize.height);
            Log.d(TAG,"choosePreviewSize="+optimalSize.width + " w:h " + optimalSize.height);
        }
        return optimalSize;
    }
//    /**
//     * Attempts to find a preview size that matches the provided width and height (which
//     * specify the dimensions of the encoded video).  If it fails to find a match it just
//     * uses the default preview size for video.
//     * <p>
//     * TODO: should do a best-fit match, e.g.
//     * https://github.com/commonsguy/cwac-camera/blob/master/camera/src/com/commonsware/cwac/camera/CameraUtils.java
//     */
//    public static void choosePreviewSize(Camera.Parameters parms, int width, int height) {
//        // We should make sure that the requested MPEG size is less than the preferred
//        // size, and has the same aspect ratio.
//        Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
//        if (ppsfv != null) {
//            Log.d(TAG, "Camera preferred preview size for video is " +
//                    ppsfv.width + "x" + ppsfv.height);
//        }
//
//        //for (Camera.Size size : parms.getSupportedPreviewSizes()) {
//        //    Log.d(TAG, "supported: " + size.width + "x" + size.height);
//        //}
//
//        for (Camera.Size size : parms.getSupportedPreviewSizes()) {
//            if (size.width == width && size.height == height) {
//                parms.setPreviewSize(width, height);
//                return;
//            }
//        }
//
//        Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
//        if (ppsfv != null) {
//            parms.setPreviewSize(ppsfv.width, ppsfv.height);
//        }
//        // else use whatever the default size is
//    }

    /**
     * Attempts to find a fixed preview frame rate that matches the desired frame rate.
     * <p/>
     * It doesn't seem like there's a great deal of flexibility here.
     * <p/>
     * TODO: follow the recipe from http://stackoverflow.com/questions/22639336/#22645327
     *
     * @return The expected frame rate, in thousands of frames per second.
     */
    public static int chooseFixedPreviewFps(Camera.Parameters parms, int desiredThousandFps) {
        List<int[]> supported = parms.getSupportedPreviewFpsRange();

        for (int[] entry : supported) {
            //Log.d(TAG, "entry: " + entry[0] + " - " + entry[1]);
            if ((entry[0] == entry[1]) && (entry[0] == desiredThousandFps)) {
                parms.setPreviewFpsRange(entry[0], entry[1]);
                return entry[0];
            }
        }

        int[] tmp = new int[2];
        parms.getPreviewFpsRange(tmp);
        int guess;
        if (tmp[0] == tmp[1]) {
            guess = tmp[0];
        } else {
            guess = tmp[1] / 2;     // shrug
        }

        Log.d(TAG, "Couldn't find match for " + desiredThousandFps + ", using " + guess);
        return guess;
    }

    public static int getCameraDisplayOrientation(Activity activity,
                                                  int cameraId) {

        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        try {
            android.hardware.Camera.getCameraInfo(cameraId, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
        } catch (Exception e) {//Crash on OPPON5117  Fail to get camera info
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (90 + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (270 - degrees + 360) % 360;
            }
        }
        return result;

    }

    public static void releaseCamera(Camera camera) {
        if(camera==null){
            return;
        }
        try{
            camera.stopPreview();
            camera.release();
        }catch (Exception e){

        }
    }
}
