package me.theegg.qingming;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamScene extends Scene {

    private static final String TAG = "InputStreamScene";
    private static final boolean DEBUG = false;
    private static final BitmapFactory.Options options = new BitmapFactory.Options();

    /**
     * 降低采样率的值
     */
    private static final int DOWN_SAMPLE_SHIFT = 2;

    /**
     * 每个像素占多少字节
     */
    private final int BYTES_PER_PIXEL = 4;

    /**
     * 我们该用总内存的多少作为缓存？缓存越大，读取的时间越久。25%需要1.2秒，10%需要600毫秒，5%
     * 需要500毫秒。用户体验在越少的缓存情况下似乎越好。
     */
    private int percent = 5; // 超过25将会发生内存溢出

    private BitmapRegionDecoder decoder;
    private Bitmap sampleBitmap;

    static {
        options.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    public InputStreamScene(InputStream inputStream) throws IOException {
        BitmapFactory.Options tmpOptions = new BitmapFactory.Options();

        this.decoder = BitmapRegionDecoder.newInstance(inputStream, false);

        // 只计算大小
        tmpOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, tmpOptions);
        setSceneSize(tmpOptions.outWidth, tmpOptions.outHeight);

        // 创建图像样本
        tmpOptions.inJustDecodeBounds = false;
        tmpOptions.inSampleSize = (1 << DOWN_SAMPLE_SHIFT);
        sampleBitmap = BitmapFactory.decodeStream(inputStream, null, tmpOptions);

        // 表示初始化完毕
        initialize();
    }

    @Override
    protected Bitmap fillCache(Rect origin) {
        Bitmap bitmap = null;
        if (decoder != null)
            bitmap = decoder.decodeRegion(origin, options);
        return bitmap;
    }


    @Override
    protected void drawSampleRectIntoBitmap(Bitmap bitmap, Rect rectOfSample) {
        if (bitmap != null) {
            Canvas c = new Canvas(bitmap);
            int left = (rectOfSample.left >> DOWN_SAMPLE_SHIFT);
            int top = (rectOfSample.top >> DOWN_SAMPLE_SHIFT);
            int right = left + (rectOfSample.width() >> DOWN_SAMPLE_SHIFT);
            int bottom = top + (rectOfSample.height() >> DOWN_SAMPLE_SHIFT);
            Rect srcRect = new Rect(left, top, right, bottom);
            Rect identity = new Rect(0, 0, c.getWidth(), c.getHeight());
            c.drawBitmap(
                    sampleBitmap,
                    srcRect,
                    identity,
                    null
            );
        }
    }

    private Rect calculatedCacheWindowRect = new Rect();

    @Override
    protected Rect calculateCacheWindow(Rect viewportRect) {
        long bytesToUse = Runtime.getRuntime().maxMemory() * percent / 100;
        Point size = getSceneSize();

        int vw = viewportRect.width();
        int vh = viewportRect.height();

        // 计算最大值
        int tw = 0;
        int th = 0;
        int mw = tw;
        int mh = th;
        while ((vw + tw) * (vh + th) * BYTES_PER_PIXEL < bytesToUse) {
            mw = tw++;
            mh = th++;
        }

        // 不需要超过总的大小
        if (vw + mw > size.x) // 可视区宽度 + 外围宽度 > 图片的宽度
            mw = Math.max(0, size.x - vw);
        if (vh + mh > size.y) // 可视区高度 + 外围高度 > 图片的高度
            mh = Math.max(0, size.y - vh);

        int left = viewportRect.left - (mw >> 1);
        int right = viewportRect.right + (mw >> 1);
        if (left < 0) {
            right = right - left;
            left = 0;
        }
        if (right > size.x) {
            left = left - (right - size.x);
            right = size.x;
        }

        int top = viewportRect.top - (mh >> 1);
        int bottom = viewportRect.bottom + (mh >> 1);
        if (top < 0) {
            bottom = bottom - top;
            top = 0;
        }
        if (bottom > size.y) {
            top = top - (bottom - size.y);
            bottom = size.y;
        }

        calculatedCacheWindowRect.set(left, top, right, bottom);
        Log.d(TAG, "new cache.originRect = " + calculatedCacheWindowRect.toShortString() + " size=" + size.toString());
        return calculatedCacheWindowRect;
    }

    @Override
    protected void fillCacheOutOfMemoryError(OutOfMemoryError error) {
        if (percent > 0)
            percent -= 1;
        Log.e(TAG, String.format("caught oom -- cache now at %d percent.", percent));
    }

    @Override
    protected void drawComplete(Canvas canvas) {
    }
}
