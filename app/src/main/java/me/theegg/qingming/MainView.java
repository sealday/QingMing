package me.theegg.qingming;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by seal on 14-11-10.
 */
public class MainView extends SurfaceView implements SurfaceHolder.Callback{
    private static final String TAG = "MainView";

    private DrawThread drawThread;
    private InputStreamScene scene;
    private InputStream inputStream;

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    private int offset = 20;

    public void goUp() {
        Scene.Viewport p = scene.getViewport();
        Point point = new Point();
        p.getOrigin(point);
        p.setOrigin(point.x, point.y - offset);
    }

    public void goDown() {
        Scene.Viewport p = scene.getViewport();
        Point point = new Point();
        p.getOrigin(point);
        p.setOrigin(point.x, point.y + offset);
    }

    public void goLeft() {
        Scene.Viewport p = scene.getViewport();
        Point point = new Point();
        p.getOrigin(point);
        p.setOrigin(point.x - offset, point.y);
    }

    public void goRight() {
        Scene.Viewport p = scene.getViewport();
        Point point = new Point();
        p.getOrigin(point);
        p.setOrigin(point.x + offset, point.y);
    }

    public void zoomOut() {
        Scene.Viewport p = scene.getViewport();
        p.zoom(1.2f);
    }

    public void zoomIn() {
        Scene.Viewport p = scene.getViewport();
        p.zoom(0.8f);
    }

    public MainView(Context context) {
        this(context, null);
    }

    public MainView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        getHolder().addCallback(this);
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            scene = new InputStreamScene(inputStream);
            drawThread = new DrawThread(holder);
            drawThread.start();
            scene.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        scene.getViewport().setSize(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }


    private class DrawThread extends Thread {
        private final SurfaceHolder holder;
        private boolean running;

        public DrawThread(SurfaceHolder holder) {
            this.holder = holder;
        }

        @Override
        public synchronized void start() {
            super.start();
            running = true;
        }

        @Override
        public void run() {
            while(running){
                Canvas canvas;
                canvas = holder.lockCanvas();
                try {
                    if (canvas != null) {
                        synchronized (holder) {
                                scene.draw(canvas);
                        }
                    }
                } finally {
                    if (canvas != null) {
                        holder.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }
    }
}
