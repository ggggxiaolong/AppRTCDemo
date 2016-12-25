package org.appspot.apprtc.PCManager;

import android.util.Log;
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
  VideoRenderer.Callbacks localRender, remoteRender;
  VideoTrack localVideoTrack, remoteVideoTrack;
  AudioTrack localAudioTrack, remoteAudioTrack;
  AudioSource audioSource;
  VideoSource videoSource;
  Parameter parameter;
  boolean renderVideo;
  boolean isInit;

  MediaManager(){}

  public void stopVideoSource() {
  }

  public void startVideoSource() {
  }

  public void setAudioEnabled(final boolean enable) {
    localAudioTrack.setEnabled(enable);
  }

  public void setVideoEnabled(final boolean enable) {
    renderVideo = enable;
    if (localVideoTrack != null) {
      localVideoTrack.setEnabled(renderVideo);
    }
    if (remoteVideoTrack != null) {
      remoteVideoTrack.setEnabled(renderVideo);
    }
  }

  void addRemoteStream(final MediaStream stream) {
    // FIXME: 12/25/16 判断空
    if (stream.videoTracks.size() == 1) {
      remoteVideoTrack = stream.videoTracks.get(0);
      remoteVideoTrack.setEnabled(renderVideo);
      remoteVideoTrack.addRenderer(new VideoRenderer(remoteRender));
    }
  }

  void removeRemoteStream(final MediaStream stream) {
    remoteVideoTrack = null;
  }

  void findVideoSender() {
    for (RtpSender sender : peerConnection.getSenders()) {
      if (sender.track() != null) {
        String trackType = sender.track().kind();
        if (trackType.equals(VIDEO_TRACK_TYPE)) {
          Log.d(TAG, "Found video sender.");
          localVideoSender = sender;
        }
      }
    }
  }

  public void switchCamera() {
    //executor.execute(new Runnable() {
    //  @Override
    //  public void run() {
        switchCameraInternal();
    //  }
    //});
  }

  void switchCameraInternal() {
    // TODO: 12/25/16 判断空
    if (parameter.videoCapturer instanceof CameraVideoCapturer) {
      //if (!videoCallEnabled || isError || videoCapturer == null) {
      //  Log.e(TAG, "Failed to switch camera. Video: " + videoCallEnabled + ". Error : " + isError);
      //  return; // No video is sent or only one camera is available or error happened.
      //}
      Log.d(TAG, "Switch camera");
      CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) parameter.videoCapturer;
      cameraVideoCapturer.switchCamera(null);
    } else {
      Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
    }
  }

  public void changeCaptureFormat(final int width, final int height, final int framerate) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        changeCaptureFormatInternal(width, height, framerate);
      }
    });
  }

  void changeCaptureFormatInternal(int width, int height, int framerate) {
    if (!videoCallEnabled || isError || videoCapturer == null) {
      Log.e(TAG,
          "Failed to change capture format. Video: " + videoCallEnabled + ". Error : " + isError);
      return;
    }
    Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
    videoSource.adaptOutputFormat(width, height, framerate);
  }

  public void close(){
    Log.d(TAG, "Closing audio source.");
    if (audioSource != null) {
      audioSource.dispose();
      audioSource = null;
    }
    Log.d(TAG, "Stopping capture.");
    if (parameter.videoCapturer != null) {
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

    Parameter(EglBase.Context renderEGLContext, VideoRenderer.Callbacks localRender,
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
