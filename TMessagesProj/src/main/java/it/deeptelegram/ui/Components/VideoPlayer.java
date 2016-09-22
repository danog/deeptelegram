/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import it.deeptelegram.messenger.exoplayer.DummyTrackRenderer;
import it.deeptelegram.messenger.exoplayer.ExoPlaybackException;
import it.deeptelegram.messenger.exoplayer.ExoPlayer;
import it.deeptelegram.messenger.exoplayer.MediaCodecAudioTrackRenderer;
import it.deeptelegram.messenger.exoplayer.MediaCodecSelector;
import it.deeptelegram.messenger.exoplayer.MediaCodecTrackRenderer;
import it.deeptelegram.messenger.exoplayer.MediaCodecVideoTrackRenderer;
import it.deeptelegram.messenger.exoplayer.MediaFormat;
import it.deeptelegram.messenger.exoplayer.TrackRenderer;
import it.deeptelegram.messenger.exoplayer.audio.AudioCapabilities;
import it.deeptelegram.messenger.exoplayer.extractor.ExtractorSampleSource;
import it.deeptelegram.messenger.exoplayer.upstream.Allocator;
import it.deeptelegram.messenger.exoplayer.upstream.DataSource;
import it.deeptelegram.messenger.exoplayer.upstream.DefaultAllocator;
import it.deeptelegram.messenger.exoplayer.upstream.DefaultUriDataSource;
import it.deeptelegram.messenger.exoplayer.util.PlayerControl;

import java.util.concurrent.CopyOnWriteArrayList;

@SuppressLint("NewApi")
public class VideoPlayer implements ExoPlayer.Listener, MediaCodecVideoTrackRenderer.EventListener {

    public interface RendererBuilder {
        void buildRenderers(VideoPlayer player);
        void cancel();
    }

    public interface Listener {
        void onStateChanged(boolean playWhenReady, int playbackState);
        void onError(Exception e);
        void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio);
    }

    public static class ExtractorRendererBuilder implements RendererBuilder {

        private static final int BUFFER_SEGMENT_SIZE = 256 * 1024;
        private static final int BUFFER_SEGMENT_COUNT = 256;

        private final Context context;
        private final String userAgent;
        private final Uri uri;

        public ExtractorRendererBuilder(Context context, String userAgent, Uri uri) {
            this.context = context;
            this.userAgent = userAgent;
            this.uri = uri;
        }

        @Override
        public void buildRenderers(VideoPlayer player) {
            Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);
            Handler mainHandler = player.getMainHandler();

            TrackRenderer[] renderers = new TrackRenderer[RENDERER_COUNT];
            DataSource dataSource = new DefaultUriDataSource(context, userAgent);
            ExtractorSampleSource sampleSource = new ExtractorSampleSource(uri, dataSource, allocator, BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE, mainHandler, null, 0);
            renderers[TYPE_VIDEO] = new MediaCodecVideoTrackRenderer(context, sampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, mainHandler, player, 50) {
                @Override
                protected void doSomeWork(long positionUs, long elapsedRealtimeUs, boolean sourceIsReady) throws ExoPlaybackException {
                    super.doSomeWork(positionUs, elapsedRealtimeUs, sourceIsReady);
                }
            };
            renderers[TYPE_AUDIO] = new MediaCodecAudioTrackRenderer(sampleSource, MediaCodecSelector.DEFAULT, null, true, mainHandler, null, AudioCapabilities.getCapabilities(context), AudioManager.STREAM_MUSIC);
            player.onRenderers(renderers);
        }

        @Override
        public void cancel() {

        }
    }

    public static final int STATE_IDLE = ExoPlayer.STATE_IDLE;
    public static final int STATE_PREPARING = ExoPlayer.STATE_PREPARING;
    public static final int STATE_BUFFERING = ExoPlayer.STATE_BUFFERING;
    public static final int STATE_READY = ExoPlayer.STATE_READY;
    public static final int STATE_ENDED = ExoPlayer.STATE_ENDED;
    public static final int TRACK_DISABLED = ExoPlayer.TRACK_DISABLED;
    public static final int TRACK_DEFAULT = ExoPlayer.TRACK_DEFAULT;

    public static final int RENDERER_COUNT = 2;
    public static final int TYPE_VIDEO = 0;
    public static final int TYPE_AUDIO = 1;

    private static final int RENDERER_BUILDING_STATE_IDLE = 1;
    private static final int RENDERER_BUILDING_STATE_BUILDING = 2;
    private static final int RENDERER_BUILDING_STATE_BUILT = 3;

    private final RendererBuilder rendererBuilder;
    private final ExoPlayer player;
    private final Handler mainHandler;
    private final CopyOnWriteArrayList<Listener> listeners;
    private final PlayerControl playerControl;

    private int rendererBuildingState;
    private int lastReportedPlaybackState;
    private boolean lastReportedPlayWhenReady;

    private Surface surface;
    private TrackRenderer videoRenderer;
    private int videoTrackToRestore;

    private boolean backgrounded;

    public VideoPlayer(RendererBuilder rendererBuilder) {
        this.rendererBuilder = rendererBuilder;
        player = ExoPlayer.Factory.newInstance(RENDERER_COUNT, 1000, 5000);
        player.addListener(this);
        playerControl = new PlayerControl(player);
        mainHandler = new Handler();
        listeners = new CopyOnWriteArrayList<>();
        lastReportedPlaybackState = STATE_IDLE;
        rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
    }

    public PlayerControl getPlayerControl() {
        return playerControl;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void setSurface(Surface surface) {
        this.surface = surface;
        pushSurface(false);
    }

    public Surface getSurface() {
        return surface;
    }

    public void blockingClearSurface() {
        surface = null;
        pushSurface(true);
    }

    public int getTrackCount(int type) {
        return player.getTrackCount(type);
    }

    public MediaFormat getTrackFormat(int type, int index) {
        return player.getTrackFormat(type, index);
    }

    public int getSelectedTrack(int type) {
        return player.getSelectedTrack(type);
    }

    public void setSelectedTrack(int type, int index) {
        player.setSelectedTrack(type, index);
    }

    public boolean getBackgrounded() {
        return backgrounded;
    }

    public void setBackgrounded(boolean backgrounded) {
        if (this.backgrounded == backgrounded) {
            return;
        }
        this.backgrounded = backgrounded;
        if (backgrounded) {
            videoTrackToRestore = getSelectedTrack(TYPE_VIDEO);
            setSelectedTrack(TYPE_VIDEO, TRACK_DISABLED);
            blockingClearSurface();
        } else {
            setSelectedTrack(TYPE_VIDEO, videoTrackToRestore);
        }
    }

    public void prepare() {
        if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILT) {
            player.stop();
        }
        rendererBuilder.cancel();
        videoRenderer = null;
        rendererBuildingState = RENDERER_BUILDING_STATE_BUILDING;
        maybeReportPlayerState();
        rendererBuilder.buildRenderers(this);
    }

    void onRenderers(TrackRenderer[] renderers) {
        for (int i = 0; i < RENDERER_COUNT; i++) {
            if (renderers[i] == null) {
                renderers[i] = new DummyTrackRenderer();
            }
        }
        videoRenderer = renderers[TYPE_VIDEO];
        pushSurface(false);
        player.prepare(renderers);
        rendererBuildingState = RENDERER_BUILDING_STATE_BUILT;
    }

    void onRenderersError(Exception e) {
        for (Listener listener : listeners) {
            listener.onError(e);
        }
        rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
        maybeReportPlayerState();
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        player.setPlayWhenReady(playWhenReady);
    }

    public void seekTo(long positionMs) {
        player.seekTo(positionMs);
    }

    public void release() {
        rendererBuilder.cancel();
        rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
        surface = null;
        player.release();
    }

    public int getPlaybackState() {
        if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILDING) {
            return STATE_PREPARING;
        }
        int playerState = player.getPlaybackState();
        if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILT && playerState == STATE_IDLE) {
            return STATE_PREPARING;
        }
        return playerState;
    }

    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    public long getDuration() {
        return player.getDuration();
    }

    public int getBufferedPercentage() {
        return player.getBufferedPercentage();
    }

    public boolean getPlayWhenReady() {
        return player.getPlayWhenReady();
    }

    Looper getPlaybackLooper() {
        return player.getPlaybackLooper();
    }

    Handler getMainHandler() {
        return mainHandler;
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int state) {
        maybeReportPlayerState();
    }

    @Override
    public void onPlayerError(ExoPlaybackException exception) {
        rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
        for (Listener listener : listeners) {
            listener.onError(exception);
        }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        for (Listener listener : listeners) {
            listener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
        }
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {

    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
        for (Listener listener : listeners) {
            listener.onError(e);
        }
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {

    }

    @Override
    public void onPlayWhenReadyCommitted() {
        // Do nothing.
    }

    @Override
    public void onDrawnToSurface(Surface surface) {
        // Do nothing.
    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
        for (Listener listener : listeners) {
            listener.onError(e);
        }
    }

    private void maybeReportPlayerState() {
        boolean playWhenReady = player.getPlayWhenReady();
        int playbackState = getPlaybackState();
        if (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState) {
            for (Listener listener : listeners) {
                listener.onStateChanged(playWhenReady, playbackState);
            }
            lastReportedPlayWhenReady = playWhenReady;
            lastReportedPlaybackState = playbackState;
        }
    }

    private void pushSurface(boolean blockForSurfacePush) {
        if (videoRenderer == null) {
            return;
        }

        if (blockForSurfacePush) {
            player.blockingSendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
        } else {
            player.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
        }
    }
}
