package com.brucetoo.videoplayer.videomanage.player;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.view.Surface;

import com.brucetoo.videoplayer.Config;
import com.brucetoo.videoplayer.IViewTracker;
import com.brucetoo.videoplayer.utils.Logger;
import com.brucetoo.videoplayer.videomanage.interfaces.IMediaPlayer;
import com.brucetoo.videoplayer.videomanage.interfaces.VideoPlayerListener;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default Media Player implement by system {@link MediaPlayer}
 * All lifecycle method must be called in sub thread,and post callback
 * to main thread,handle them in {@link VideoPlayerView}.
 */
public class DefaultMediaPlayer implements IMediaPlayer,
    MediaPlayer.OnErrorListener,
    MediaPlayer.OnBufferingUpdateListener,
    MediaPlayer.OnInfoListener,
    MediaPlayer.OnCompletionListener,
    MediaPlayer.OnVideoSizeChangedListener,
    MediaPlayer.OnPreparedListener {

    private String TAG = "DefaultMediaPlayer";
    private static final boolean SHOW_LOGS = Config.SHOW_LOGS;
    private Surface mSurface;

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private final MediaPlayer mMediaPlayer;
    private final AtomicReference<State> mState = new AtomicReference<>();

    private VideoPlayerListener mListener;
    private Context mContext;
    private IViewTracker mViewTracker;

    public DefaultMediaPlayer(Context context, VideoPlayerListener listener) {
        this.mContext = context;
        this.mListener = listener;
        if (SHOW_LOGS) Logger.v(TAG, "constructor of MediaPlayerWrapper");
        if (SHOW_LOGS)
            Logger.v(TAG, "constructor of MediaPlayerWrapper, main Looper " + Looper.getMainLooper());
        if (SHOW_LOGS)
            Logger.v(TAG, "constructor of MediaPlayerWrapper, my Looper " + Looper.myLooper());

        if (Looper.myLooper() != null) {
            throw new RuntimeException("myLooper not null, a bug in some MediaPlayer implementation cause that listeners are not called at all. Please use a thread without Looper");
        }
        mMediaPlayer = new MediaPlayer();

        mState.set(State.IDLE);
        mMediaPlayer.setOnVideoSizeChangedListener(this);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnBufferingUpdateListener(this);
        mMediaPlayer.setOnInfoListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setScreenOnWhilePlaying(true);
    }

    /**
     * Main thread message!!!
     */
    private final Runnable mOnVideoPreparedMessage = new Runnable() {
        @Override
        public void run() {
            if (SHOW_LOGS) Logger.v(TAG, ">> run, onVideoPrepared");
            mListener.onVideoPrepared(mViewTracker);
            if (SHOW_LOGS) Logger.v(TAG, "<< run, onVideoPrepared");
        }
    };

    private final Runnable mOnVideoStartMessage = new Runnable() {
        @Override
        public void run() {
            if (SHOW_LOGS) Logger.v(TAG, ">> run, onVideoStarted");
            mListener.onVideoStarted(mViewTracker);
            if (SHOW_LOGS) Logger.v(TAG, "<< run, onVideoStarted");
        }
    };

    private final Runnable mOnVideoPauseMessage = new Runnable() {
        @Override
        public void run() {
            if (SHOW_LOGS) Logger.v(TAG, ">> run, mOnVideoPauseMessage");
            mListener.onVideoPaused(mViewTracker);
            if (SHOW_LOGS) Logger.v(TAG, "<< run, mOnVideoPauseMessage");
        }
    };

    private final Runnable mOnVideoStopMessage = new Runnable() {
        @Override
        public void run() {
            if (SHOW_LOGS) Logger.v(TAG, ">> run, onVideoStopped");
            mListener.onVideoStopped(mViewTracker);
            if (SHOW_LOGS) Logger.v(TAG, "<< run, onVideoStopped");
        }
    };

    private final Runnable mOnVideoResetMessage = new Runnable() {
        @Override
        public void run() {
            if (SHOW_LOGS) Logger.v(TAG, ">> run, onVideoReset");
            mListener.onVideoReset(mViewTracker);
            if (SHOW_LOGS) Logger.v(TAG, "<< run, onVideoReset");
        }
    };

    private final Runnable mOnVideoReleaseMessage = new Runnable() {
        @Override
        public void run() {
            if (SHOW_LOGS) Logger.v(TAG, ">> run, OnVideoRelease");
            mListener.onVideoReleased(mViewTracker);
            if (SHOW_LOGS) Logger.v(TAG, "<< run, OnVideoRelease");
        }
    };

    @Override
    public void prepare() {
        if (SHOW_LOGS) Logger.v(TAG, ">> execute prepare, mState " + mState);

        synchronized (mState) {
            try {
                mMediaPlayer.prepare();
            } catch (Exception e) {
                e.printStackTrace();
                prepareAsync();
            }
        }
    }

    @Override
    public void prepareAsync() {
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mMediaPlayer.prepareAsync();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }


    @Override
    public void onPrepared(MediaPlayer mp) {
        if (SHOW_LOGS) Logger.v(TAG, ">> onPrepared");
        mState.set(State.PREPARED);

        if (mListener != null) {
            mMainThreadHandler.post(mOnVideoPreparedMessage);
        }

        //when prepare complete start playing video
        start();
    }

    @Override
    public void setDataSource(@NonNull String url) throws IOException {
        synchronized (mState) {
            if (SHOW_LOGS)
                Logger.v(TAG, "setDataSource, filePath " + url + ", mState " + mState);

            //setOnBufferingUpdateListener only be called in internet streams
            mMediaPlayer.setDataSource(mContext, Uri.parse(url));
            mState.set(State.INITIALIZED);
        }
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        if (SHOW_LOGS) Logger.v(TAG, "onVideoSizeChanged, width " + width + ", height " + height);
        if (!inUiThread()) {
            throw new RuntimeException("this should be called in Main Thread");
        }
        if (mListener != null) {
            mListener.onVideoSizeChanged(mViewTracker,width, height);
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (SHOW_LOGS) Logger.v(TAG, "onVideoCompletion, mState " + mState);

        synchronized (mState) {
            mState.set(State.PLAYBACK_COMPLETED);
        }

        if (mListener != null) {
            mListener.onVideoCompletion(mViewTracker);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (SHOW_LOGS) Logger.v(TAG, "onError, what " + what + ", extra " + extra);

        synchronized (mState) {
            mState.set(State.ERROR);
        }

        //weird error code what = -38 ? What the hell?
        if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN || what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            if (mListener != null) {
                mListener.onError(mViewTracker,what, extra);
            }
        }
        // We always return true, because after Error player stays in this state.
        // See here http://developer.android.com/reference/android/media/MediaPlayer.html
        return true;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        if (SHOW_LOGS) Logger.v(TAG, "onBufferingUpdate percent : " + percent);
        if (mListener != null) {
            mListener.onBufferingUpdate(mViewTracker,percent);
        }
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (SHOW_LOGS) Logger.v(TAG, "onInfo");
        printInfo(what);
        if (mListener != null) {
            mListener.onInfo(mViewTracker,what);
        }
        return false;
    }

    private void printInfo(int what) {
        switch (what) {
            case MediaPlayer.MEDIA_INFO_UNKNOWN:
                if (SHOW_LOGS) Logger.inf(TAG, "onInfo, MEDIA_INFO_UNKNOWN");
                break;
            case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                if (SHOW_LOGS) Logger.inf(TAG, "onInfo, MEDIA_INFO_VIDEO_TRACK_LAGGING");
                break;
            case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                if (SHOW_LOGS)
                    Logger.inf(TAG, "onInfo, MEDIA_INFO_VIDEO_RENDERING_START");
                break;
            case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                if (SHOW_LOGS) Logger.inf(TAG, "onInfo, MEDIA_INFO_BUFFERING_START");
                break;
            case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                if (SHOW_LOGS) Logger.inf(TAG, "onInfo, MEDIA_INFO_BUFFERING_END");
                break;
            case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                if (SHOW_LOGS) Logger.inf(TAG, "onInfo, MEDIA_INFO_BAD_INTERLEAVING");
                break;
            case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                if (SHOW_LOGS) Logger.inf(TAG, "onInfo, MEDIA_INFO_NOT_SEEKABLE");
                break;
            case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                if (SHOW_LOGS) Logger.inf(TAG, "onInfo, MEDIA_INFO_METADATA_UPDATE");
                break;
            case MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                if (SHOW_LOGS) Logger.inf(TAG, "onInfo, MEDIA_INFO_UNSUPPORTED_SUBTITLE");
                break;
            case MediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                if (SHOW_LOGS) Logger.inf(TAG, "onInfo, MEDIA_INFO_SUBTITLE_TIMED_OUT");
                break;
        }
    }

    @Override
    public void start() {
        if (SHOW_LOGS) Logger.v(TAG, ">> start");

        synchronized (mState) {
            if (SHOW_LOGS) Logger.v(TAG, "start, mState " + mState);
            mMediaPlayer.start();
            mState.set(State.STARTED);

            if (mListener != null) {
                mMainThreadHandler.post(mOnVideoStartMessage);
            }
        }
        if (SHOW_LOGS) Logger.v(TAG, "<< start");
    }

    @Override
    public void pause() {
        if (SHOW_LOGS) Logger.v(TAG, ">> pause");

        synchronized (mState) {
            if (SHOW_LOGS)
                Logger.v(TAG, "pause, mState " + mState);
            mMediaPlayer.pause();
            mState.set(State.PAUSED);

            if (mListener != null) {
                mMainThreadHandler.post(mOnVideoPauseMessage);
            }
        }
        if (SHOW_LOGS) Logger.v(TAG, "<< pause");
    }

    @Override
    public void stop() {
        if (SHOW_LOGS) Logger.v(TAG, ">> stop");

        synchronized (mState) {
            if (SHOW_LOGS) Logger.v(TAG, "stop, mState " + mState);

            mMediaPlayer.stop();
            mState.set(State.STOPPED);

            if (mListener != null) {
                mMainThreadHandler.post(mOnVideoStopMessage);
            }
        }
        if (SHOW_LOGS) Logger.v(TAG, "<< stop");
    }

    @Override
    public void reset() {
        if (SHOW_LOGS) Logger.v(TAG, ">> reset , mState " + mState);

        synchronized (mState) {
            mMediaPlayer.reset();
            mState.set(State.IDLE);

            if (mListener != null) {
                mMainThreadHandler.post(mOnVideoResetMessage);
            }
        }
        if (SHOW_LOGS) Logger.v(TAG, "<< reset , mState " + mState);
    }

    @Override
    public void release() {
        if (SHOW_LOGS) Logger.v(TAG, ">> release, mState " + mState);
        synchronized (mState) {
            mMediaPlayer.release();
            mState.set(State.END);

            if (mListener != null) {
                mMainThreadHandler.post(mOnVideoReleaseMessage);
            }
        }
        if (SHOW_LOGS) Logger.v(TAG, "<< release, mState " + mState);
    }

    @Override
    public void clearAll() {
        if (SHOW_LOGS) Logger.v(TAG, ">> clearAll, mState " + mState);
        synchronized (mState) {
            mMediaPlayer.setOnVideoSizeChangedListener(null);
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnErrorListener(null);
            mMediaPlayer.setOnBufferingUpdateListener(null);
            mMediaPlayer.setOnInfoListener(null);
            mMediaPlayer.setOnPreparedListener(null);
        }
        if (SHOW_LOGS) Logger.v(TAG, "<< clearAll, mState " + mState);
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        mMediaPlayer.setVolume(leftVolume, rightVolume);
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        if (SHOW_LOGS) Logger.v(TAG, ">> setSurfaceTexture " + surfaceTexture);
        if (SHOW_LOGS) Logger.v(TAG, "setSurfaceTexture mSurface " + mSurface);

        if (surfaceTexture != null) {
            mSurface = new Surface(surfaceTexture);
            try {
                mMediaPlayer.setSurface(mSurface);
            }catch (IllegalStateException e){
                //TODO handle exception
            }
        } else {
            mMediaPlayer.setSurface(mSurface);
        }
        if (SHOW_LOGS) Logger.v(TAG, "<< setSurfaceTexture " + surfaceTexture);

    }

    @Override
    public int getVideoWidth() {
        return mMediaPlayer.getVideoWidth();
    }

    @Override
    public int getVideoHeight() {
        return mMediaPlayer.getVideoHeight();
    }

    @Override
    public int getCurrentPosition() throws IOException{
        int currentPos;
        try {
            currentPos = mMediaPlayer.getCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
        return currentPos;
    }

    @Override
    public boolean isPlaying() {
        boolean isPlaying;
        try {
            isPlaying = mMediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
        return isPlaying;
    }

    @Override
    public int getDuration() throws IOException {
        int duration = 0;
        synchronized (mState) {
            duration = mMediaPlayer.getDuration();
        }
        return duration;
    }

    @Override
    public void seekTo(int mis) throws IOException {
        synchronized (mState) {
            State state = mState.get();
            if (SHOW_LOGS)
                Logger.v(TAG, "seekToPosition, position " + mis + ", mState " + state);
            mMediaPlayer.seekTo(mis);
        }
    }

    @Override
    public void setViewTracker(IViewTracker viewTracker) {
       this.mViewTracker = viewTracker;
    }

    @Override
    public State getCurrentState() {
        synchronized (mState) {
            return mState.get();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + hashCode();
    }

    private boolean inUiThread() {
        return Thread.currentThread().getId() == 1;
    }
}
