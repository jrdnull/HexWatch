package uk.jordonsmith.hexwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face using the current hour, minute and seconds time as a hex triplet.
 * e.g 12:04:15 => #120415
 */
public class HexWatchFaceService extends CanvasWatchFaceService {
    private static final long UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final int MSG_UPDATE_TIME = 0;

        private Time mTime;

        private Paint mHexPaint;
        private Paint mDayPaint;
        private Paint mDatePaint;

        private float mYOffset;

        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();

                        if (shouldTimerBeRunning()) {
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME,
                                    UPDATE_RATE_MS - (System.currentTimeMillis() % UPDATE_RATE_MS));
                        }
                        break;
                }
            }
        };

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(HexWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = HexWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.y_offset);

            mHexPaint = createTextPaint();
            mHexPaint.setTextSize(resources.getDimension(R.dimen.text_size_time));

            mDayPaint = createTextPaint();
            mDayPaint.setFakeBoldText(true);
            mDayPaint.setTextSize(resources.getDimension(R.dimen.text_size_date));

            mDatePaint = createTextPaint();
            mDatePaint.setTextSize(resources.getDimension(R.dimen.text_size_date));

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mLowBitAmbient) {
                mDatePaint.setAntiAlias(!inAmbientMode);
            }

            invalidate();
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            float x = canvas.getWidth() / 2f;
            float y = mYOffset;

            mTime.setToNow();
            String timeHex = mTime.format("#%H%M%S");

            canvas.drawColor(isInAmbientMode() ? Color.BLACK : Color.parseColor(timeHex));
            canvas.drawText(timeHex, x, y, mHexPaint);

            if (!isInAmbientMode() && getPeekCardPosition().isEmpty()) {
                y += mDayPaint.getTextSize() * 1.2f;
                canvas.drawText(mTime.format("%A").toUpperCase(), x, y, mDayPaint);

                y += mDatePaint.getTextSize();
                canvas.drawText(mTime.format("%b %d, %Y"), x, y, mDatePaint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                registerTimeZoneReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterTimeZoneReceiver();
            }

            updateTimer();
        }

        /**
         * Registers the time zone broadcast receiver if not already registered.
         */
        private void registerTimeZoneReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }

            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            HexWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        /**
         * Unregisters the time zone broadcast receiver if registered.
         */
        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }

            mRegisteredTimeZoneReceiver = false;
            HexWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);

            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Returns a new Paint for text
         */
        private Paint createTextPaint() {
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setAntiAlias(true);

            return paint;
        }
    }
}
