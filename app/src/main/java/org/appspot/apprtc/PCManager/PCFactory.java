package org.appspot.apprtc.PCManager;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.util.List;
import org.appspot.apprtc.BuildConfig;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;

/**
 * @author leon.tan on 12/25/16.
 */

public class PCFactory {
  static final String VIDEO_TRACK_ID = "ARDAMSv0";
  static final String AUDIO_TRACK_ID = "ARDAMSa0";
  public static final String TAG = "PCFactory";
  private PeerConnectionFactory mFactory;

  boolean createFactory(Context context) {
    if (mFactory != null) return true;
    if (BuildConfig.DEBUG) PeerConnectionFactory.initializeInternalTracer();//初始化p2p的连接工厂
    // Initialize field trials.
    PeerConnectionFactory.initializeFieldTrials("");

    Log.d(TAG, "Disable OpenSL ES audio even if device supports it");
    WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true /* enable */);
    Log.d(TAG, "Disable built-in AEC even if device supports it");
    WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
    Log.d(TAG, "Disable built-in AGC even if device supports it");
    WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true);
    Log.d(TAG, "Disable built-in NS even if device supports it");
    WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true);

    // Create peer connection factory.
    if (!PeerConnectionFactory.initializeAndroidGlobals(context, true, true, true)) {
      return false;
    }
    mFactory = new PeerConnectionFactory(new PeerConnectionFactory.Options());
    Log.d(TAG, "Peer connection factory created.");
    return true;
  }

  PCManager createPCManager(final List<PeerConnection.IceServer> iceServers,
      EglBase.Context renderEGLContext, PCManager pcManager) {
    if (mFactory == null){
      throw new IllegalStateException("method createFactory should be call first");
    }
    mFactory.setVideoHwAccelerationOptions(renderEGLContext, renderEGLContext);

    PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
    rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
    rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
    rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
    rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
    rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

    MediaConstraints pcConstraints = new MediaConstraints();
    pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

    PeerConnection peerConnection =
        mFactory.createPeerConnection(rtcConfig, pcConstraints, pcManager);
    pcManager.setPeerConnection(peerConnection);
    return pcManager;
  }

  AudioSource createAudioSource(){
    return mFactory.createAudioSource(new MediaConstraints());
  }

  //创建AudioTrack
  AudioTrack createAudioTrack(AudioSource audioSource) {
    AudioTrack audioTrack = mFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
    return audioTrack;
  }

  MediaStream createLocalMediaStream(){
    return mFactory.createLocalMediaStream("ARDAMS");
  }

  VideoSource createVideoSource(VideoCapturer capturer){
    return mFactory.createVideoSource(capturer);
  }
  // 创建VideoTrack
  VideoTrack createVideoTrack(VideoSource source, VideoRenderer.Callbacks callbacks) {
    VideoTrack videoTrack = mFactory.createVideoTrack(VIDEO_TRACK_ID, source);
    videoTrack.addRenderer(new VideoRenderer(callbacks));
    return videoTrack;
  }

  /**
   * should be close at lost
   */
  public void close(){
    Log.d(TAG, "Closing peer connection factory.");
    if (mFactory != null) {
      mFactory.dispose();
      mFactory = null;
    }
    Log.d(TAG, "Closing peer connection done.");
    PeerConnectionFactory.stopInternalTracingCapture();
    PeerConnectionFactory.shutdownInternalTracer();
  }
}
