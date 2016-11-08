package cn.myhug.baobaoplayer.media;

/**
 * Created by zhengxin on 2016/11/7.
 * 为减少new 对象，临时变量均为静态，非线程安全
 */

public class AudioUtil {
    private static final int MAXLEN = 1024*32;
    private static final short[] SRC1 = new short[MAXLEN];
    private static final short[] SRC2 = new short[MAXLEN];
    /**
     * byte数组转换成short数组
     *
     * @param data
     * @param items
     * @return
     */
    private static short[] byteToShortArray(byte[] data, int items) {
        short[] retVal = new short[items];
        for (int i = 0; i < retVal.length; i++) {
            SRC1[i] = (short) ((data[i * 2] & 0xff) | (data[i * 2 + 1] & 0xff) << 8);
        }
        return SRC1;
    }

    private static void byteToShortArray(byte[] data,short[] dst, int items) {
        for (int i = 0; i < items; i++) {
            dst[i] = (short) ((data[i * 2] & 0xff) | (data[i * 2 + 1] & 0xff) << 8);
        }
    }

    /**
     * short转byte数组
     *
     * @param s
     * @return
     */
    private static byte[] shortToByteArray(short s) {
        byte[] shortBuf = new byte[2];
        for (int i = 0; i < 2; i++) {
            int offset = (shortBuf.length - 2 + i) * 8;
            shortBuf[i] = (byte) ((s >>> offset) & 0xff);
        }
        return shortBuf;
    }
    private static void shortToByteArray(short s,byte[] dst,int dst_offset) {

        for (int i = 0; i < 2; i++) {
            int offset = i * 8;
            dst[dst_offset+i] = (byte) ((s >>> offset) & 0xff);
        }
    }

    public static short[] mixVoice(short[] source, short[] audio, int items) {
        for (int i = 0; i < items; i++) {
            source[i] = (short) ((source[i] + audio[i ]) / 2);
        }
        return source;
    }

    public static byte[] mixVoice(byte[] source, byte[] audio, int items) {
        int count = items/2;
        byteToShortArray(source,SRC1,count);
        byteToShortArray(audio,SRC2,count);
        short[] result = mixVoice(SRC1,SRC2,count);
        for(int i=0;i<count;i++){
            shortToByteArray(result[i],source,i*2);
        }
        return  source;

    }

}
