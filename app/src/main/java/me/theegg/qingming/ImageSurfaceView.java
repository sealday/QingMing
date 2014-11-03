package me.theegg.qingming;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Scroller;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

public class ImageSurfaceView extends SurfaceView implements SurfaceHolder.Callback, OnGestureListener, OnDoubleTapListener {
    private final static String TAG = ImageSurfaceView.class.getSimpleName();

    private InputStreamScene scene;
    private final Touch touch;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private long lastScaleTime = 0;
    private long SCALE_MOVE_GUARD = 500;
    private boolean pressMoveGuard = false;

    private DrawThread drawThread;

    public ImageSurfaceView(Context context) {
        this(context, null);
    }

    public ImageSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        touch = new Touch(context);
        init(context);
    }

    private void init(Context context) {
        gestureDetector = new GestureDetector(context, this);
        getHolder().addCallback(this);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, String.format("surfaceChanged: width -> %d , height -> %d", width, height));
        scene.getViewport().setSize(width, height);
        setViewportCenter();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, String.format("surfaceCreated:"));
        drawThread = new DrawThread(holder);
        drawThread.setName("drawThread");
        drawThread.setRunning(true);
        drawThread.start();
        scene.start();
        touch.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        touch.stop();
        scene.stop();
        drawThread.setRunning(false);
        boolean retry = true;
        while (retry) {
            try {
                drawThread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }
    }
    public void getViewport(Point p) {
        scene.getViewport().getOrigin(p);
    }

    public void setViewport(Point viewport) {
        scene.getViewport().setOrigin(viewport.x, viewport.y);
    }

    public void setViewportCenter() {
        Point sceneSize = scene.getSceneSize();

        Log.v("sceneSize", String.format(" width -> %d, height -> %d", sceneSize.x, sceneSize.y));

        Point viewportSize = new Point();
        scene.getViewport().getSize(viewportSize);

        Log.v("viewport", String.format(" width -> %d, height -> %d", viewportSize.x, viewportSize.y));

        int x = (sceneSize.x - viewportSize.x) / 2;
        int y = (sceneSize.y - viewportSize.y) / 2;

        scene.getViewport().setOrigin(x, y);
    }

    public void setInputStream(InputStream inputStream) throws IOException {
        scene = new InputStreamScene(inputStream);
    }

    float oldX = -1;
    float oldY = -1;

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        int action = me.getActionMasked();
        boolean consumed = gestureDetector.onTouchEvent(me);
        if (consumed)
            return true;

        int count = me.getPointerCount();
        if (count == 2) {
            Log.v("touch", "two finger");
            float x0 = me.getX(0);
            float y0 = me.getY(0);
            float x1 = me.getX(1);
            float y1 = me.getY(1);

            if (Math.abs(x0 - oldX) < 25 && Math.abs(y0 - oldY) < 25) {
                return touch.move(x0, y0, x1, y1);
            } else {
                scaleGestureDetector.onTouchEvent(me);
            }

            switch (action) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    Log.v("touch", "two finger down");
                    oldX = me.getX(0);
                    oldY = me.getY(0);
                    return touch.down(x1, y1);
                case MotionEvent.ACTION_MOVE:
                    if (scaleGestureDetector.isInProgress() || System.currentTimeMillis() - lastScaleTime < SCALE_MOVE_GUARD)
                        break;
                    return touch.move(me);
                case MotionEvent.ACTION_POINTER_UP:
                    oldX = -1;
                    oldY = -1;
                    return touch.up(x1, y1);
                case MotionEvent.ACTION_CANCEL:
                    return touch.cancel(me);
            }
        } else {
            scaleGestureDetector.onTouchEvent(me);
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    return touch.down(me);
                case MotionEvent.ACTION_MOVE:
                    if (scaleGestureDetector.isInProgress() || System.currentTimeMillis() - lastScaleTime < SCALE_MOVE_GUARD || pressMoveGuard)
                        break;
                    return touch.move(me);
                case MotionEvent.ACTION_UP:
                    return touch.up(me);
                case MotionEvent.ACTION_CANCEL:
                    return touch.cancel(me);
            }
        }
        return super.onTouchEvent(me);
    }


    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        Log.v("down", "single tap");
        PointF screenFocus = new PointF();
        float scaleFactor = 2;
        screenFocus.set(e.getX(), e.getY());
        scene.getViewport().zoom(
                scaleFactor,
                screenFocus);
        invalidate();

        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        Log.v("touch", "double tap");

        PointF screenFocus = new PointF();
        float scaleFactor = 0.5f;
        screenFocus.set(e.getX(), e.getY());
        scene.getViewport().zoom(
                scaleFactor,
                screenFocus);
        invalidate();

        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private PointF screenFocus = new PointF();

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            if (scaleFactor != 0f && scaleFactor != 1.0f) {
                scaleFactor = 1 / scaleFactor;
                screenFocus.set(detector.getFocusX(), detector.getFocusY());
                scene.getViewport().zoom(
                        scaleFactor,
                        screenFocus);
                invalidate();
            }
            lastScaleTime = System.currentTimeMillis();
            return true;
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return touch.fling(e1, e2, velocityX, velocityY);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    private Timer pressTimer = new Timer();

    @Override
    public void onLongPress(MotionEvent e) {
        Log.v("touch", "long press");
        setViewportCenter();
        pressMoveGuard = true;
        pressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                pressMoveGuard = false;
            }
        }, 0, 500);

    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    class DrawThread extends Thread {
        private SurfaceHolder surfaceHolder;

        private boolean running = false;

        public void setRunning(boolean value) {
            running = value;
        }

        public DrawThread(SurfaceHolder surfaceHolder) {
            this.surfaceHolder = surfaceHolder;
        }

        @Override
        public void run() {
            Canvas c;
            while (running) {
                try {
                    // Don't hog the entire CPU
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                }
                c = null;
                try {
                    c = surfaceHolder.lockCanvas();
                    if (c != null) {
                        synchronized (surfaceHolder) {
                            scene.draw(c);// draw it
                        }
                    }
                } finally {
                    if (c != null) {
                        surfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }
    }

    enum TouchState {UNTOUCHED, IN_TOUCH, START_FLING, IN_FLING}

    private class Touch {
        TouchState state = TouchState.UNTOUCHED;
        /**
         * Where on the view did we initially touch
         */
        final Point viewDown = new Point(0, 0);
        /**
         * What was the coordinates of the viewport origin?
         */
        final Point viewportOriginAtDown = new Point(0, 0);

        final Scroller scroller;

        TouchThread touchThread;

        Touch(Context context) {
            scroller = new Scroller(context);
        }

        void start() {
            touchThread = new TouchThread(this);
            touchThread.setName("touchThread");
            touchThread.start();
        }

        void stop() {
            touchThread.running = false;
            touchThread.interrupt();

            boolean retry = true;
            while (retry) {
                try {
                    touchThread.join();
                    retry = false;
                } catch (InterruptedException e) {
                    // we will try it again and again...
                }
            }
            touchThread = null;
        }

        Point fling_viewOrigin = new Point();
        Point fling_viewSize = new Point();
        Point fling_sceneSize = new Point();

        boolean fling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            scene.getViewport().getOrigin(fling_viewOrigin);
            scene.getViewport().getSize(fling_viewSize);
            scene.getSceneSize(fling_sceneSize);

            synchronized (this) {
                state = TouchState.START_FLING;
                scene.setSuspend(true);
                scroller.fling(
                        fling_viewOrigin.x,
                        fling_viewOrigin.y,
                        (int) -velocityX,
                        (int) -velocityY,
                        0,
                        fling_sceneSize.x - fling_viewSize.x,
                        0,
                        fling_sceneSize.y - fling_viewSize.y);
                touchThread.interrupt();
            }
            return true;
        }

        boolean down(MotionEvent event) {
            scene.setSuspend(false);    // If we were suspended because of a fling
            synchronized (this) {
                state = TouchState.IN_TOUCH;
                viewDown.x = (int) event.getX();
                viewDown.y = (int) event.getY();
                Point p = new Point();
                scene.getViewport().getOrigin(p);
                viewportOriginAtDown.set(p.x, p.y);
            }
            Log.v("touch", "down");
            return true;
        }

        boolean down(float x, float y) {
            scene.setSuspend(false);    // If we were suspended because of a fling
            synchronized (this) {
                state = TouchState.IN_TOUCH;
                viewDown.x = (int) x;
                viewDown.y = (int) y;
                Point p = new Point();
                scene.getViewport().getOrigin(p);
                viewportOriginAtDown.set(p.x, p.y);
            }
            return true;
        }

        boolean move(MotionEvent event) {
            if (state == TouchState.IN_TOUCH) {
                float zoom = scene.getViewport().getZoom();
                float deltaX = zoom * (event.getX() - viewDown.x);
                float deltaY = zoom * (event.getY() - viewDown.y);
                float newX = (viewportOriginAtDown.x - deltaX);
                float newY = (viewportOriginAtDown.y - deltaY);

                scene.getViewport().setOrigin((int) newX, (int) newY);
                invalidate();
            }
            return true;
        }

        boolean move(float x0, float y0, float x, float y) {
            if (state == TouchState.IN_TOUCH) {
                float zoom = scene.getViewport().getZoom();
                float deltaX = zoom * (x - viewDown.x);
                float deltaY = zoom * (y - viewDown.y);
                float newX = (viewportOriginAtDown.x - deltaX);
                float newY = (viewportOriginAtDown.y - deltaY);

                Point p = new Point();
                scene.getSceneSize(p);
                double deg = Math.atan2(Math.abs(y - y0), Math.abs(x - x0));
                if (deg < PI_6) {
                    scene.getViewport().setOrigin(viewportOriginAtDown.x, (int) newY);
                } else if (deg > PI_3) {
                    scene.getViewport().setOrigin((int) newX, viewportOriginAtDown.y);
                }

                Log.v("touch", String.format("x0 -> %d , y0 -> %d, x -> %.2f, y -> %.2f", viewportOriginAtDown.x, viewportOriginAtDown.y, newX, newY));

                invalidate();
            }

            return true;
        }

        boolean up(MotionEvent event) {
            if (state == TouchState.IN_TOUCH) {
                state = TouchState.UNTOUCHED;
            }
            return true;
        }

        boolean up(float x, float y) {
            if (state == TouchState.IN_TOUCH) {
                state = TouchState.UNTOUCHED;
            }
            return true;
        }

        boolean cancel(MotionEvent event) {
            if (state == TouchState.IN_TOUCH) {
                state = TouchState.UNTOUCHED;
            }

            return true;
        }

        private class TouchThread extends Thread {
            final Touch touch;
            boolean running = false;

            void setRunning(boolean value) {
                running = value;
            }

            TouchThread(Touch touch) {
                this.touch = touch;
            }

            @Override
            public void run() {
                running = true;
                while (running) {
                    while (touch.state != TouchState.START_FLING && touch.state != TouchState.IN_FLING) {
                        try {
                            Thread.sleep(Long.MAX_VALUE);
                        } catch (InterruptedException e) {
                        }
                        if (!running)
                            return;
                    }
                    synchronized (touch) {
                        if (touch.state == TouchState.START_FLING) {
                            touch.state = TouchState.IN_FLING;
                        }
                    }
                    if (touch.state == TouchState.IN_FLING) {
                        scroller.computeScrollOffset();
                        scene.getViewport().setOrigin(scroller.getCurrX(), scroller.getCurrY());
                        if (scroller.isFinished()) {
                            scene.setSuspend(false);
                            synchronized (touch) {
                                touch.state = TouchState.UNTOUCHED;
                                try {
                                    Thread.sleep(5);
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private final static double PI_6 = Math.PI / 6;
    private final static double PI_3 = Math.PI / 3;
}
