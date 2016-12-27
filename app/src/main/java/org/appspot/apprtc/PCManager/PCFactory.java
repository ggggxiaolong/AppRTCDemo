package org.appspot.apprtc.PCManager;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.util.SparseArray;
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
  final SparseArray<PCManager> mPCManagerSparseArray;

  PCFactory() {
    mPCManagerSparseArray = new SparseArray<>();
  }

  boolean createFactory(Context context) {
    if (mFactory != null) return true;
    if (BuildConfig.DEBUG) {
      PeerConnectionFactory.initializeInternalTracer();//初始化p2p的连接工厂
      PeerConnectionFactory.initializeFieldTrials("");
    }

    Log.d(TAG, "Enable OpenSL ES audio even if device supports it");
    WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(false /* enable */);
    Log.d(TAG, "Enable built-in AEC even if device supports it");
    WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(false);
    Log.d(TAG, "Enable built-in AGC even if device supports it");
    WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(false);
    Log.d(TAG, "Enable built-in NS even if device supports it");
    WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(false);

    // Create peer connection factory.
    if (!PeerConnectionFactory.initializeAndroidGlobals(context, true, true, true)) {
      return false;
    }
    mFactory = new PeerConnectionFactory(new PeerConnectionFactory.Options());
    Log.d(TAG, "Peer connection factory created.");
    return true;
  }

  PCManager createPCManager(final List<PeerConnection.IceServer> iceServers,
      EglBase.Context renderEGLContext, PCManager.Observer observer, int label) {
    if (mFactory == null) {
      Log.e(TAG, "method createFactory should be call first");
      return null;
    }
    PCManager pcManager = mPCManagerSparseArray.get(label);
    if (pcManager != null) return pcManager;
    pcManager = new PCManager(observer, label);
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
    mPCManagerSparseArray.put(label, pcManager);
    return pcManager;
  }

  PCManager getPCManager(int label) {
    return mPCManagerSparseArray.get(label);
  }

  AudioSource createAudioSource() {
    return mFactory.createAudioSource(new MediaConstraints());
  }

  //创建AudioTrack
  AudioTrack createAudioTrack(AudioSource audioSource) {
    AudioTrack audioTrack = mFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
    return audioTrack;
  }

  MediaStream createLocalMediaStream() {
    return mFactory.createLocalMediaStream("ARDAMS");
  }

  VideoSource createVideoSource(VideoCapturer capturer) {
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
  public void close(int label) {
    if (mPCManagerSparseArray.get(label) != null) {
      mPCManagerSparseArray.remove(label);
    }
    if (mPCManagerSparseArray.size() > 0) return;
    Log.d(TAG, "Closing peer connection factory.");
    mPCManagerSparseArray.clear();
    if (mFactory != null) {
      mFactory.dispose();
      mFactory = null;
      if (BuildConfig.DEBUG) {
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
      }
    }
    Log.d(TAG, "Closing peer connection done.");
  }

  public void closeAll() {
    for (int i = 0; i < mPCManagerSparseArray.size(); i++) {
      int key = mPCManagerSparseArray.keyAt(i);
      PCManager pcManager = mPCManagerSparseArray.get(key);
      if (pcManager != null) pcManager.close();
    }
  }
}
