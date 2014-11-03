package me.theegg.qingming;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Debug;
import android.util.Log;

/*
 * +-------------------------------------------------------------------+
 * |                                        |                          |
 * |  +------------------------+            |                          |
 * |  |                        |            |                          |
 * |  |                        |            |                          |
 * |  |                        |            |                          |
 * |  |           Viewport     |            |                          |
 * |  +------------------------+            |                          |
 * |                                        |                          |
 * |                                        |                          |
 * |                                        |                          |
 * |                          Cache         |                          |
 * |----------------------------------------+                          |
 * |                                                                   |
 * |                                                                   |
 * |                                                                   |
 * |                                                                   |
 * |                                                                   |
 * |                               Entire bitmap -- too big for memory |
 * +-------------------------------------------------------------------+
 */

/**
 * 记录整个场景，这个场景是一个无法直接放进内存的bitmap
 * 客户继承此类，并且实现相应的抽象方法来返回所需要的bitmap
 */
public abstract class Scene {
    private final String TAG = "Scene";

    private final static int MINIMUM_PIXELS_IN_VIEW = 50;

    /**
     * 场景的大小
     */
    private Point size = new Point();
    /**
     * 可视区
     */
    private final Viewport viewport = new Viewport();
    /**
     * 缓冲区
     */
    private final Cache cache = new Cache();

    /**
     * 设置场景的大小
     */
    public void setSceneSize(int width, int height) {
        size.set(width, height);
    }

    /**
     * 返回Point的对象表示场景大小，不要改变Point！
     */
    public Point getSceneSize() {
        return size;
    }

    /**
     * 使传进来的Point指针得到场景大小
     */
    public void getSceneSize(Point point) {
        point.set(size.x, size.y);
    }

    /**
     * 获取可视区
     */
    public Viewport getViewport() {
        return viewport;
    }

    /**
     * 初始化缓冲区
     */
    public void initialize() {
        if (cache.getState() == CacheState.UNINITIALIZED) {
            synchronized (cache) {
                cache.setState(CacheState.INITIALIZED);
            }
        }
    }

    /**
     * 开启缓冲区线程
     */
    public void start() {
        cache.start();
    }

    /**
     * 停止缓冲区线程
     */
    public void stop() {
        cache.stop();
    }

    /**
     * 暂停或继续缓冲区线程
     * 可以用在惯性移动时临时停止更新缓冲区
     *
     * @param suspend 真为暂停，假为继续。
     */
    public void setSuspend(boolean suspend) {
        if (suspend) {
            synchronized (cache) {
                cache.setState(CacheState.SUSPEND);
            }
        } else {
            if (cache.getState() == CacheState.SUSPEND) {
                synchronized (cache) {
                    cache.setState(CacheState.INITIALIZED);
                }
            }
        }
    }

    /**
     * 使缓冲区内容无效，这将导致缓冲区重新填充
     */
    @SuppressWarnings("unused")
    public void invalidate() {
        cache.invalidate();
    }

    /**
     * 将场景(scene)内容画到画布上(canvas)。
     * 这个操作将场景中可视区的bitmap填充到画布上。
     * 如果缓冲区已经有数据了，而且不是暂停状态，那么
     * 将使用缓冲区中高分辨率的bitmap。
     * 如果不可用，则从样本中取低分辨率的bitmap。
     */
    public void draw(Canvas c) {
        viewport.draw(c);
    }

    /**
     * This method must return a high resolution Bitmap that the Scene
     * will use to fill out the viewport bitmap upon request. This bitmap
     * is normally larger than the viewport so that the viewport can be
     * scrolled without having to refresh the cache. This method runs
     * on a thread other than the UI thread, and it is not under a lock, so
     * it is expected that this method can run for a long time (seconds?).
     *
     * @param rectOfCache The Rect representing the area of the Scene that
     *                    the Scene wants cached.
     * @return the Bitmap representing the requested area of the larger bitmap
     */
    protected abstract Bitmap fillCache(Rect rectOfCache);

    /**
     * The memory allocation you just did in fillCache caused an OutOfMemoryError.
     * You can attempt to recover. Experience shows that when we get an
     * OutOfMemoryError, we're pretty hosed and are going down. For instance, if
     * we're trying to decode a bitmap region with
     * {@link android.graphics.BitmapRegionDecoder} and we run out of memory,
     * we're going to die somewhere in the C code with a SIGSEGV.
     *
     * @param error The OutOfMemoryError exception data
     */
    protected abstract void fillCacheOutOfMemoryError(OutOfMemoryError error);

    /**
     * Calculate the Rect of the cache's window based on the current viewportRect.
     * The returned Rect must at least contain the viewportRect, but it can be
     * larger if the system believes a bitmap of the returned size will fit into
     * memory. This function must be fast as it happens while the cache lock is held.
     *
     * @param viewportRect The returned must be able to contain this Rect
     * @return The Rect that will be used to fill the cache
     */
    protected abstract Rect calculateCacheWindow(Rect viewportRect);

    /**
     * This method fills the passed-in bitmap with sample data. This function must
     * return as fast as possible so it shouldn't have to do any IO at all -- the
     * quality of the user experience rests on the speed of this function.
     *
     * @param bitmap       The Bitmap to fill
     * @param rectOfSample Rectangle within the Scene that this bitmap represents.
     */
    protected abstract void drawSampleRectIntoBitmap(Bitmap bitmap, Rect rectOfSample);

    /**
     * The Cache is done drawing the bitmap -- time to add the finishing touches
     *
     * @param canvas a canvas on which to draw
     */
    protected abstract void drawComplete(Canvas canvas);
    //endregion

    /**
     * 可视区
     */
    public class Viewport {
        /**
         * 当前可视区对应的bitmap
         */
        Bitmap bitmap = null;
        /**
         * 定义可视区在场景中位置的矩形(Rect)对象
         */
        final Rect window = new Rect(0, 0, 0, 0);
        /**
         * 放大倍率
         */
        float zoom = 1.0f;

        /**
         * 设置原点
         */
        public void setOrigin(int x, int y) {
            synchronized (this) {
                int w = window.width();
                int h = window.height();

                // 检查边界
                if (x < 0) x = 0;
                if (y < 0) y = 0;
                if (x + w > size.x) x = size.x - w;
                if (y + h > size.y) y = size.y - h;

                window.set(x, y, x + w, y + h);
            }
        }

        /**
         * 获取原点
         */
        public void getOrigin(Point p) {
            synchronized (this) {
                p.set(window.left, window.top);
            }
        }

        /**
         * 设置可视区大小
         */
        public void setSize(int w, int h) {
            synchronized (this) {
                if (bitmap != null) {
                    bitmap.recycle();
                    bitmap = null;
                }
                bitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
                window.set(
                        window.left,
                        window.top,
                        window.left + w,
                        window.top + h);
            }
        }

        /**
         * 获取可视区大小
         */
        public void getSize(Point p) {
            synchronized (this) {
                p.x = window.width();
                p.y = window.height();
            }
        }

        /**
         * 获取与可视区相关联的bitmap的大小
         */
        public void getPhysicalSize(Point p) {
            synchronized (this) {
                p.x = getPhysicalWidth();
                p.y = getPhysicalHeight();
            }
        }

        /**
         * 获取与可视区相关联的bitmap的宽度
         */
        public int getPhysicalWidth() {
            return bitmap.getWidth();
        }

        /**
         * 获取与可视区相关联的bitmap的高度
         */
        public int getPhysicalHeight() {
            return bitmap.getHeight();
        }

        /**
         * 获取放大倍率
         */
        public float getZoom() {
            return zoom;
        }

        /**
         * 调整放大倍率
         * @param factor 倍率
         * @param screenFocus 焦点
         */
        public void zoom(float factor, PointF screenFocus) {
            if (factor != 1.0) {

                PointF screenSize = new PointF(bitmap.getWidth(), bitmap.getHeight());
                PointF sceneSize = new PointF(getSceneSize());
                float screenWidthToHeight = screenSize.x / screenSize.y;
                float screenHeightToWidth = screenSize.y / screenSize.x;
                synchronized (this) {
                    float newZoom = zoom * factor;
                    RectF w1 = new RectF(window);
                    RectF w2 = new RectF();
                    PointF sceneFocus = new PointF(
                            w1.left + (screenFocus.x / screenSize.x) * w1.width(),
                            w1.top + (screenFocus.y / screenSize.y) * w1.height()
                    );
                    float w2Width = getPhysicalWidth() * newZoom;
                    if (w2Width > sceneSize.x) {
                        w2Width = sceneSize.x;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    if (w2Width < MINIMUM_PIXELS_IN_VIEW) {
                        w2Width = MINIMUM_PIXELS_IN_VIEW;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    float w2Height = w2Width * screenHeightToWidth;
                    if (w2Height > sceneSize.y) {
                        w2Height = sceneSize.y;
                        w2Width = w2Height * screenWidthToHeight;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    if (w2Height < MINIMUM_PIXELS_IN_VIEW) {
                        w2Height = MINIMUM_PIXELS_IN_VIEW;
                        w2Width = w2Height * screenWidthToHeight;
                        newZoom = w2Width / getPhysicalWidth();
                    }
                    w2.left = sceneFocus.x - ((screenFocus.x / screenSize.x) * w2Width);
                    w2.top = sceneFocus.y - ((screenFocus.y / screenSize.y) * w2Height);
                    if (w2.left < 0)
                        w2.left = 0;
                    if (w2.top < 0)
                        w2.top = 0;
                    w2.right = w2.left + w2Width;
                    w2.bottom = w2.top + w2Height;
                    if (w2.right > sceneSize.x) {
                        w2.right = sceneSize.x;
                        w2.left = w2.right - w2Width;
                    }
                    if (w2.bottom > sceneSize.y) {
                        w2.bottom = sceneSize.y;
                        w2.top = w2.bottom - w2Height;
                    }
                    window.set((int) w2.left, (int) w2.top, (int) w2.right, (int) w2.bottom);
                    zoom = newZoom;
                }
            }
        }

        /** 可视区绘制 */
        void draw(Canvas c) {
            cache.update(this);
            synchronized (this) {
                if (c != null && bitmap != null) {
                    c.drawBitmap(bitmap, 0F, 0F, null);
                    drawComplete(c);
                }
            }
        }
    }

    private enum CacheState {UNINITIALIZED, INITIALIZED, START_UPDATE, IN_UPDATE, READY, SUSPEND}

    /**
     * 记录缓冲的bitmap
     */
    private class Cache {

        /** 用一个矩形（Rect）来标识缓冲区的位置、大小 */
        final Rect window = new Rect(0, 0, 0, 0);

        /** 当前缓冲区的bitmap */
        Bitmap bitmapRef = null;

        /** 当前缓冲区的状态 */
        CacheState state = CacheState.UNINITIALIZED;

        /** 设置缓冲区状态 */
        private void setState(CacheState newState) {
            Log.i(TAG, String.format("cacheState old=%s new=%s", state.toString(), newState.toString()));
            state = newState;
        }

        /** 获取缓冲区状态 */
        private CacheState getState() {
            return state;
        }

        /** 缓冲区载入线程 */
        private CacheThread cacheThread;

        /** 开始载入 */
        private void start() {
            if (cacheThread != null) {
                cacheThread.setRunning(false);
                cacheThread.interrupt();
                cacheThread = null;
            }
            cacheThread = new CacheThread(this);
            cacheThread.setName("cacheThread");
            cacheThread.start();
        }

        /** 停止载入 */
        private void stop() {
            cacheThread.running = false;
            cacheThread.interrupt();

            boolean retry = true;
            while (retry) {
                try {
                    cacheThread.join();
                    retry = false;
                } catch (InterruptedException e) {
                    // 不断尝试停止
                }
            }
            cacheThread = null;
        }

        /** 使缓冲区无效 */
        private void invalidate() {
            synchronized (this) {
                setState(CacheState.INITIALIZED);
                cacheThread.interrupt();
            }
        }

        /** 填充可视区 */
        private void update(Viewport viewport) {
            Bitmap bitmap = null;
            synchronized (this) {
                switch (getState()) {
                    case UNINITIALIZED:
                        // 永远不能执行到这里
                        return;
                    case INITIALIZED:
                        // 开始缓冲数据
                        setState(CacheState.START_UPDATE);
                        cacheThread.interrupt();
                        break;
                    case START_UPDATE:
                        // 已经开始缓冲数据
                        break;
                    case IN_UPDATE:
                        // 正在缓冲数据
                        break;
                    case SUSPEND:
                        // 暂停缓冲数据
                        break;
                    case READY:
                        // 已经准备好了数据
                        if (bitmapRef == null) {
                            Log.d(TAG, "bitmapRef is null");
                            setState(CacheState.START_UPDATE);
                            cacheThread.interrupt();
                        } else if (!window.contains(viewport.window)) {
                            Log.d(TAG, "viewport not in cache");
                            setState(CacheState.START_UPDATE);
                            cacheThread.interrupt();
                        } else {
                            bitmap = bitmapRef;
                        }
                        break;
                }
            }
            if (bitmap == null)
                loadSampleIntoViewport();
            else
                loadBitmapIntoViewport(bitmap);
        }

        /** 从缓冲区中载入位图到可视区 */
        private void loadBitmapIntoViewport(Bitmap bitmap) {
            if (bitmap != null) {
                synchronized (viewport) {
                    int left = viewport.window.left - window.left;
                    int top = viewport.window.top - window.top;
                    int right = left + viewport.window.width();
                    int bottom = top + viewport.window.height();
                    viewport.getPhysicalSize(dstSize);
                    srcRect.set(left, top, right, bottom);
                    dstRect.set(0, 0, dstSize.x, dstSize.y);
                    Canvas c = new Canvas(viewport.bitmap);
                    c.drawColor(Color.BLACK);
                    c.drawBitmap(
                            bitmap,
                            srcRect,
                            dstRect,
                            null);
                }
            }
        }

        private final Rect srcRect = new Rect(0, 0, 0, 0);
        private final Rect dstRect = new Rect(0, 0, 0, 0);
        private final Point dstSize = new Point();

        /** 直接从源文件中读取可视区数据 */
        private void loadSampleIntoViewport() {
            if (getState() != CacheState.UNINITIALIZED) {
                synchronized (viewport) {
                    drawSampleRectIntoBitmap(
                            viewport.bitmap,
                            viewport.window
                    );
                }
            }
        }
    }

    /**
     * <p>The CacheThread's job is to wait until the {@link Cache#state} is
     * {@link CacheState#START_UPDATE} and then update the {@link Cache} given
     * the current {@link Viewport#window}. It does not want to hold the cache
     * lock during the call to {@link Scene#fillCache(android.graphics.Rect)} because the call
     * can take a long time. If we hold the lock, the user experience is very
     * jumpy.</p>
     * <p>The CacheThread and the {@link Cache} work hand in hand, both using the
     * cache itself to synchronize on and using the {@link Cache#state}.
     * The {@link Cache} is free to update any part of the cache object as long
     * as it holds the lock. The CacheThread is careful to make sure that it is
     * the {@link Cache#state} is {@link CacheState#IN_UPDATE} as it updates
     * the {@link Cache}. It locks and unlocks the cache all along the way, but
     * makes sure that the cache is not locked when it calls
     * {@link Scene#fillCache(android.graphics.Rect)}.
     */
    class CacheThread extends Thread {
        final Cache cache;
        boolean running = false;

        void setRunning(boolean value) {
            running = value;
        }

        CacheThread(Cache cache) {
            this.cache = cache;
        }

        @Override
        public void run() {
            running = true;
            Rect viewportRect = new Rect(0, 0, 0, 0);
            while (running) {
                while (running && cache.getState() != CacheState.START_UPDATE)
                    try {
                        // Sleep until we have something to do
                        Thread.sleep(Integer.MAX_VALUE);
                    } catch (InterruptedException ignored) {
                    }
                if (!running)
                    return;
                long start = System.currentTimeMillis();
                boolean cont = false;
                synchronized (cache) {
                    if (cache.getState() == CacheState.START_UPDATE) {
                        cache.setState(CacheState.IN_UPDATE);
                        cache.bitmapRef = null;
                        cont = true;
                    }
                }
                if (cont) {
                    synchronized (viewport) {
                        viewportRect.set(viewport.window);
                    }
                    synchronized (cache) {
                        if (cache.getState() == CacheState.IN_UPDATE)
                            //cache.setWindowRect(viewportRect);
                            cache.window.set(calculateCacheWindow(viewportRect));
                        else
                            cont = false;
                    }
                    if (cont) {
                        try {
                            Bitmap bitmap = fillCache(cache.window);
                            if (bitmap != null) {
                                synchronized (cache) {
                                    if (cache.getState() == CacheState.IN_UPDATE) {
                                        cache.bitmapRef = bitmap;
                                        cache.setState(CacheState.READY);
                                    } else {
                                        Log.w(TAG, "fillCache operation aborted");
                                    }
                                }
                            }
                            long done = System.currentTimeMillis();
                            if (Debug.isDebuggerConnected())
                                Log.d(TAG, String.format("fillCache in %dms", done - start));
                        } catch (OutOfMemoryError e) {
                            Log.d(TAG, "CacheThread out of memory");
                            /*
                             *  Attempt to recover. Experience shows that if we
                             *  do get an OutOfMemoryError, we're pretty hosed and are going down.
                             */
                            synchronized (cache) {
                                fillCacheOutOfMemoryError(e);
                                if (cache.getState() == CacheState.IN_UPDATE) {
                                    cache.setState(CacheState.START_UPDATE);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    //endregion
}
