package org.carbridge.extension.splitscreen.payload;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

/** Turns Android Auto's surface gestures (tap, scroll deltas, fling velocity) into touch events. */
final class TouchForwarder {
    interface Sink {
        /** Delivers [event] in display coordinates; false when nothing can receive it. */
        boolean send(MotionEvent event);
    }

    private static final long TAP_MS = 50;
    // Android Auto sends no "finger up" for scrolls, so a pause ends the drag
    private static final long DRAG_END_MS = 150;
    private static final long FRAME_MS = 16;
    private static final int FLING_FRAMES = 3;

    private final Sink sink;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable endDrag = this::endDrag;
    private boolean dragging;
    private long downTime;
    private long lastTime;
    private float x;
    private float y;

    TouchForwarder(Sink sink) {
        this.sink = sink;
    }

    void tap(float tapX, float tapY) {
        endDrag();
        long down = SystemClock.uptimeMillis();
        if (!send(down, down, MotionEvent.ACTION_DOWN, tapX, tapY)) return;
        // Real input wants real times, so the finger lifts later rather than with a future time
        handler.postDelayed(() -> send(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, tapX, tapY), TAP_MS);
    }

    void scroll(float distanceX, float distanceY, float anchorX, float anchorY) {
        if (!ensureDrag(anchorX, anchorY)) return;
        // Distances follow GestureDetector: previous minus current position
        x -= distanceX;
        y -= distanceY;
        lastTime = Math.max(SystemClock.uptimeMillis(), lastTime + 1);
        send(downTime, lastTime, MotionEvent.ACTION_MOVE, x, y);
        handler.removeCallbacks(endDrag);
        handler.postDelayed(endDrag, DRAG_END_MS);
    }

    void fling(float velocityX, float velocityY, float anchorX, float anchorY) {
        if (!ensureDrag(anchorX, anchorY)) return;
        handler.removeCallbacks(endDrag);
        // A few fast moves with synthetic times, so the view's VelocityTracker sees the speed
        float stepX = velocityX * FRAME_MS / 1000f;
        float stepY = velocityY * FRAME_MS / 1000f;
        long time = Math.max(SystemClock.uptimeMillis(), lastTime + 1);
        for (int i = 0; i < FLING_FRAMES; i++) {
            x += stepX;
            y += stepY;
            time += FRAME_MS;
            send(downTime, time, MotionEvent.ACTION_MOVE, x, y);
        }
        send(downTime, time, MotionEvent.ACTION_UP, x, y);
        dragging = false;
    }

    void cancel() {
        handler.removeCallbacks(endDrag);
        if (dragging) {
            send(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, y);
            dragging = false;
        }
    }

    private boolean ensureDrag(float anchorX, float anchorY) {
        if (dragging) return true;
        x = anchorX;
        y = anchorY;
        downTime = SystemClock.uptimeMillis();
        lastTime = downTime;
        dragging = send(downTime, downTime, MotionEvent.ACTION_DOWN, x, y);
        return dragging;
    }

    private void endDrag() {
        handler.removeCallbacks(endDrag);
        if (!dragging) return;
        lastTime = Math.max(SystemClock.uptimeMillis(), lastTime + 1);
        send(downTime, lastTime, MotionEvent.ACTION_UP, x, y);
        dragging = false;
    }

    private boolean send(long down, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            return sink.send(event);
        } finally {
            event.recycle();
        }
    }
}
