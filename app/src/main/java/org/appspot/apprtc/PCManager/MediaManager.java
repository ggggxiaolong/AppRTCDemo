package org.appspot.apprtc.PCManager;

import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.RtpSender;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

/**
 * @author leon.tan on 2016/12/22.
 */

public class MediaManager {
  public static final String TAG = "MediaManager";
  VideoTrack localVideoTrack, remoteVideoTrack;
  AudioTrack localAudioTrack;
  AudioSource audioSource;
  VideoSource videoSource;
  Parameter parameter;
  boolean renderVideo;
  boolean isInit;
  boolean videoCapturerStopped;
  final ExecutorService mExecutorService;

  MediaManager() {
    mExecutorService = Executors.newSingleThreadScheduledExecutor();
  }

  public void setParameter(Parameter parameter) {
    this.parameter = parameter;
  }

  public void stopVideoSource() {
    if (parameter.videoCapturer != null && !videoCapturerStopped) {
      Log.d(TAG, "Stop video source.");
      mExecutorService.execute(() -> {
        try {
          parameter.videoCapturer.stopCapture();
        } catch (InterruptedException e) {
        }
      });
      videoCapturerStopped = true;
    }
  }

  public void startVideoSource() {
    if (parameter.videoCapturer != null && videoCapturerStopped) {
      Log.d(TAG, "Restart video source.");
      mExecutorService.execute(() -> {
        parameter.videoCapturer.startCapture(parameter.videoWidth, parameter.videoHeight,
            parameter.videoFps);
        videoCapturerStopped = false;
      });
    }
  }

  public void setAudioEnabled(final boolean enable) {
    localAudioTrack.setEnabled(enable);
  }

  public void setVideoEnabled(final boolean enable) {
    mExecutorService.execute(() -> {
      renderVideo = enable;
      if (localVideoTrack != null) {
        localVideoTrack.setEnabled(renderVideo);
      }
      if (remoteVideoTrack != null) {
        remoteVideoTrack.setEnabled(renderVideo);
      }
    });
  }

  void addRemoteStream(final MediaStream stream) {
    mExecutorService.execute(() -> {
      if (stream.videoTracks.size() == 1) {
        remoteVideoTrack = stream.videoTracks.get(0);
        remoteVideoTrack.setEnabled(renderVideo);
        if (parameter != null && parameter.remoteRender != null) {
          remoteVideoTrack.addRenderer(new VideoRenderer(parameter.remoteRender));
        }
      }
    });
  }

  void removeRemoteStream(final MediaStream stream) {
    remoteVideoTrack = null;
  }

  public void switchCamera() {
    mExecutorService.execute(() -> {
      switchCameraInternal();
    });
  }

  void switchCameraInternal() {
    if (parameter.videoCapturer != null && parameter.videoCapturer instanceof CameraVideoCapturer) {
      Log.d(TAG, "Switch camera");
      CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) parameter.videoCapturer;
      cameraVideoCapturer.switchCamera(null);
    } else {
      Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
    }
  }

  public void changeCaptureFormat(final int width, final int height, final int framerate) {
    mExecutorService.execute(() -> {
      changeCaptureFormatInternal(width, height, framerate);
    });
  }

  void changeCaptureFormatInternal(int width, int height, int framerate) {
    Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
    videoSource.adaptOutputFormat(width, height, framerate);
  }

  public void close() {
    Log.d(TAG, "Closing audio source.");
    if (audioSource != null) {
      audioSource.dispose();
      audioSource = null;
    }
    Log.d(TAG, "Stopping capture.");
    if (parameter != null && parameter.videoCapturer != null) {
      try {
        parameter.videoCapturer.stopCapture();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      parameter.videoCapturer.dispose();
      parameter = null;
    }
    Log.d(TAG, "Closing video source.");
    if (videoSource != null) {
      videoSource.dispose();
      videoSource = null;
    }
  }

  public static class Parameter {
    public final EglBase.Context renderEGLContext;
    public final VideoRenderer.Callbacks localRender;
    public final VideoRenderer.Callbacks remoteRender;
    public final VideoCapturer videoCapturer;
    public final int videoWidth;
    public final int videoHeight;
    public final int videoFps;

    public Parameter(EglBase.Context renderEGLContext, VideoRenderer.Callbacks localRender,
        VideoRenderer.Callbacks remoteRender, VideoCapturer videoCapturer, int videoWidth,
        int videoHeight, int videoFps) {
      this.renderEGLContext = renderEGLContext;
      this.localRender = localRender;
      this.remoteRender = remoteRender;
      this.videoCapturer = videoCapturer;
      this.videoWidth = videoWidth;
      this.videoHeight = videoHeight;
      this.videoFps = videoFps;
    }
  }
}
