package com.sweetz.camerapreview;

/**
 * Created by Owner on 2016-10-26.
 */

public class NativeFilter {
    /** for JNI */
    static {
        System.loadLibrary("effect");
    }

    /**
     * convert YUV420SP to ARGB<br>
     *
     * @param rgb ARGB int(32bit) array.
     * @param data YUV420SP array from Camera Preview
     * @param width
     * @param height
     */
    public static native void decodeYUV420SP(int[] rgb, byte[] data, int width, int height);

}
