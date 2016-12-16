/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.appspot.apprtc.AppRTCClient.SignalingParameters;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;

/**
 * Peer connection client implementation.
 *
 * <p>All public methods are routed to local looper thread.
 * All PeerConnectionEvents callbacks are invoked from the same looper thread.
 * This class is a singleton.
 */
public class PeerConnectionClient {
  static final String VIDEO_TRACK_ID = "ARDAMSv0";
  static final String AUDIO_TRACK_ID = "ARDAMSa0";
  static final String TAG = "PCRTCClient";
  static final String VIDEO_CODEC_VP8 = "VP8";
  static final String VIDEO_CODEC_VP9 = "VP9";
  static final String VIDEO_CODEC_H264 = "H264";
  static final String AUDIO_CODEC_OPUS = "opus";
  static final String AUDIO_CODEC_ISAC = "ISAC";
  static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
  static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
  static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
  static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
  static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
  static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
  static final String AUDIO_LEVEL_CONTROL_CONSTRAINT = "levelControl";
  static final String MAX_VIDEO_WIDTH_CONSTRAINT = "maxWidth";
  static final String MIN_VIDEO_WIDTH_CONSTRAINT = "minWidth";
  static final String MAX_VIDEO_HEIGHT_CONSTRAINT = "maxHeight";
  static final String MIN_VIDEO_HEIGHT_CONSTRAINT = "minHeight";
  static final String MAX_VIDEO_FPS_CONSTRAINT = "maxFrameRate";
  static final String MIN_VIDEO_FPS_CONSTRAINT = "minFrameRate";
  static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";
  static final int HD_VIDEO_WIDTH = 1280;
  static final int HD_VIDEO_HEIGHT = 720;
  static final int MAX_VIDEO_WIDTH = 1280;
  static final int MAX_VIDEO_HEIGHT = 1280;
  static final int MAX_VIDEO_FPS = 30;

  private static final PeerConnectionClient instance = new PeerConnectionClient();
  private final PCObserver pcObserver = new PCObserver();
  private final SDPObserver sdpObserver = new SDPObserver();
  private final ScheduledExecutorService executor;

  private Context context;
  private PeerConnectionFactory factory;
  private PeerConnection peerConnection;
  PeerConnectionFactory.Options options = null;
  private AudioSource audioSource;
  private VideoSource videoSource;
  private boolean videoCallEnabled;
  private boolean preferIsac;
  private String preferredVideoCodec;
  private boolean videoCapturerStopped;
  private boolean isError;
  private Timer statsTimer;
  private VideoRenderer.Callbacks localRender;
  private VideoRenderer.Callbacks remoteRender;
  private SignalingParameters signalingParameters;
  private MediaConstraints pcConstraints;
  private int videoWidth;
  private int videoHeight;
  private int videoFps;
  private MediaConstraints audioConstraints;
  private ParcelFileDescriptor aecDumpFileDescriptor;
  private MediaConstraints sdpMediaConstraints;
  private PeerConnectionParameters peerConnectionParameters;
  // Queued remote ICE candidates are consumed only after both local and
  // remote descriptions are set. Similarly local ICE candidates are sent to
  // remote peer after both local and remote description are set.
  private LinkedList<IceCandidate> queuedRemoteCandidates;
  private PeerConnectionEvents events;
  private boolean isInitiator;
  private SessionDescription localSdp; // either offer or answer SDP
  private MediaStream mediaStream;
  private int numberOfCameras;
  private CameraVideoCapturer videoCapture;
  // enableVideo is set to true if video should be rendered and sent.
  private boolean renderVideo;
  private VideoTrack localVideoTrack;
  private VideoTrack remoteVideoTrack;
  // enableAudio is set to true if audio should be sent.
  private boolean enableAudio;
  private AudioTrack localAudioTrack;
  private DataChannel mDataChannel;

  /**
   * Peer connection parameters.
   * p2p建立连接的参数
   */
  static class PeerConnectionParameters {
    final boolean videoCallEnabled;
    final boolean loopback;
    final boolean tracing;
    final boolean useCamera2;
    final int videoWidth;
    final int videoHeight;
    final int videoFps;
    final int videoStartBitrate;
    final String videoCodec;
    final boolean videoCodecHwAcceleration;
    final boolean captureToTexture;
    final int audioStartBitrate;
    final String audioCodec;
    final boolean noAudioProcessing;
    final boolean aecDump;
    final boolean useOpenSLES;
    final boolean disableBuiltInAEC;
    final boolean disableBuiltInAGC;
    final boolean disableBuiltInNS;
    final boolean enableLevelControl;

    PeerConnectionParameters(boolean videoCallEnabled, boolean loopback, boolean tracing,
        boolean useCamera2, int videoWidth, int videoHeight, int videoFps, int videoStartBitrate,
        String videoCodec, boolean videoCodecHwAcceleration, boolean captureToTexture,
        int audioStartBitrate, String audioCodec, boolean noAudioProcessing, boolean aecDump,
        boolean useOpenSLES, boolean disableBuiltInAEC, boolean disableBuiltInAGC,
        boolean disableBuiltInNS, boolean enableLevelControl) {
      this.videoCallEnabled = videoCallEnabled;
      this.useCamera2 = useCamera2;
      this.loopback = loopback;
      this.tracing = tracing;
      this.videoWidth = videoWidth;
      this.videoHeight = videoHeight;
      this.videoFps = videoFps;
      this.videoStartBitrate = videoStartBitrate;
      this.videoCodec = videoCodec;
      this.videoCodecHwAcceleration = videoCodecHwAcceleration;
      this.captureToTexture = captureToTexture;
      this.audioStartBitrate = audioStartBitrate;
      this.audioCodec = audioCodec;
      this.noAudioProcessing = noAudioProcessing;
      this.aecDump = aecDump;
      this.useOpenSLES = useOpenSLES;
      this.disableBuiltInAEC = disableBuiltInAEC;
      this.disableBuiltInAGC = disableBuiltInAGC;
      this.disableBuiltInNS = disableBuiltInNS;
      this.enableLevelControl = enableLevelControl;
    }
  }

  /**
   * Peer connection mEvents.
   * p2p连接回调
   */
  interface PeerConnectionEvents {
    /**
     * Callback fired once local SDP is created and set.
     */
    void onLocalDescription(final SessionDescription sdp);

    /**
     * Callback fired once local Ice candidate is generated.
     */
    void onIceCandidate(final IceCandidate candidate);

    /**
     * Callback fired once local ICE candidates are removed.
     */
    void onIceCandidatesRemoved(final IceCandidate[] candidates);

    /**
     * Callback fired once connection is established (IceConnectionState is
     * CONNECTED).
     */
    void onIceConnected();

    /**
     * Callback fired once connection is closed (IceConnectionState is
     * DISCONNECTED).
     */
    void onIceDisconnected();

    /**
     * Callback fired once peer connection is closed.
     */
    void onPeerConnectionClosed();

    /**
     * Callback fired once peer connection statistics is ready.
     */
    void onPeerConnectionStatsReady(final StatsReport[] reports);

    /**
     * Callback fired once peer connection error happened.
     */
    void onPeerConnectionError(final String description);
  }

  private PeerConnectionClient() {
    // Executor thread is started once in private ctor and is used for all
    // peer connection API calls to ensure new peer connection factory is
    // created on the same thread as previously destroyed factory.
    // 在私有构造函数中线程池实例化， 线程池被用于所有的p2p连接API调用，来保证
    // 连接工厂方法的建立和销毁在同一个线程
    executor = Executors.newSingleThreadScheduledExecutor();
  }

  static PeerConnectionClient getInstance() {
    return instance;
  }

  void setPeerConnectionFactoryOptions(PeerConnectionFactory.Options options) {
    this.options = options;
  }

  void createPeerConnectionFactory(final Context context,
      final PeerConnectionParameters peerConnectionParameters, final PeerConnectionEvents events) {
    this.peerConnectionParameters = peerConnectionParameters;
    this.events = events;
    videoCallEnabled = peerConnectionParameters.videoCallEnabled;
    // Reset variables to initial states.
    this.context = null;
    factory = null;
    peerConnection = null;
    preferIsac = false;
    videoCapturerStopped = false;
    isError = false;
    queuedRemoteCandidates = null;
    localSdp = null; // either offer or answer SDP
    mediaStream = null;
    videoCapture = null;
    renderVideo = true;
    localVideoTrack = null;
    remoteVideoTrack = null;
    enableAudio = true;
    localAudioTrack = null;
    statsTimer = new Timer();

    executor.execute(new Runnable() {
      @Override public void run() {
        createPeerConnectionFactoryInternal(context);
      }
    });
  }

  void createPeerConnection(final EglBase.Context renderEGLContext,
      final VideoRenderer.Callbacks localRender, final VideoRenderer.Callbacks remoteRender,
      final SignalingParameters signalingParameters) {
    if (peerConnectionParameters == null) {
      Log.e(TAG, "Creating peer connection without initializing factory.");
      return;
    }
    this.localRender = localRender;
    this.remoteRender = remoteRender;
    this.signalingParameters = signalingParameters;
    executor.execute(new Runnable() {
      @Override public void run() {
        try {
          createMediaConstraintsInternal();//这只连接信息
          createPeerConnectionInternal(renderEGLContext);
        } catch (Exception e) {
          reportError("Failed to create peer connection: " + e.getMessage());
          throw e;
        }
      }
    });
  }

  void close() {
    executor.execute(new Runnable() {
      @Override public void run() {
        closeInternal();
      }
    });
  }

  public boolean isVideoCallEnabled() {
    return videoCallEnabled;
  }

  private void createPeerConnectionFactoryInternal(Context context) {
    PeerConnectionFactory.initializeInternalTracer();//初始化p2p的连接工厂
    if (peerConnectionParameters.tracing) {
      PeerConnectionFactory.startInternalTracingCapture(
          Environment.getExternalStorageDirectory().getAbsolutePath()
              + File.separator
              + "webrtc-trace.txt");
    }
    Log.d(TAG,
        "Create peer connection factory. Use video: " + peerConnectionParameters.videoCallEnabled);
    isError = false;

    // Initialize field trials.
    PeerConnectionFactory.initializeFieldTrials("");

    // Check preferred video codec. 初始化视频编码格式
    preferredVideoCodec = VIDEO_CODEC_VP8;
    if (videoCallEnabled && peerConnectionParameters.videoCodec != null) {
      if (peerConnectionParameters.videoCodec.equals(VIDEO_CODEC_VP9)) {
        preferredVideoCodec = VIDEO_CODEC_VP9;
      } else if (peerConnectionParameters.videoCodec.equals(VIDEO_CODEC_H264)) {
        preferredVideoCodec = VIDEO_CODEC_H264;
      }
    }
    Log.d(TAG, "Pereferred video codec: " + preferredVideoCodec);

    // Check if ISAC is used by default. ISAC音频编码
    preferIsac =
        peerConnectionParameters.audioCodec != null && peerConnectionParameters.audioCodec.equals(
            AUDIO_CODEC_ISAC);

    // Enable/disable OpenSL ES playback. 是否开启OpenSL ES
    if (!peerConnectionParameters.useOpenSLES) {
      Log.d(TAG, "Disable OpenSL ES audio even if device supports it");
      WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true /* enable */);
    } else {
      Log.d(TAG, "Allow OpenSL ES audio if device supports it");
      WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(false);
    }

    if (peerConnectionParameters.disableBuiltInAEC) {//是否开启音频AEC
      Log.d(TAG, "Disable built-in AEC even if device supports it");
      WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
    } else {
      Log.d(TAG, "Enable built-in AEC if device supports it");
      WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(false);
    }

    if (peerConnectionParameters.disableBuiltInAGC) {//是否开启音频AGC
      Log.d(TAG, "Disable built-in AGC even if device supports it");
      WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true);
    } else {
      Log.d(TAG, "Enable built-in AGC if device supports it");
      WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(false);
    }

    if (peerConnectionParameters.disableBuiltInNS) {//是否开启噪声抑制
      Log.d(TAG, "Disable built-in NS even if device supports it");
      WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true);
    } else {
      Log.d(TAG, "Enable built-in NS if device supports it");
      WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(false);
    }

    // Create peer connection factory.
    if (!PeerConnectionFactory.initializeAndroidGlobals(context, true, true,
        peerConnectionParameters.videoCodecHwAcceleration)) {
      events.onPeerConnectionError("Failed to initializeAndroidGlobals");
    }
    if (options != null) {//当和自己通讯的时候为非空
      Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
    }
    this.context = context;
    factory = new PeerConnectionFactory(options);
    Log.d(TAG, "Peer connection factory created.");
  }

  private void createMediaConstraintsInternal() {
    // Create peer connection constraints.
    // 创建p2p连接的约束
    pcConstraints = new MediaConstraints();
    // Enable DTLS for normal calls and disable for loopback calls. (DTLS 数据包传输层安全性协议)
    //if (peerConnectionParameters.loopback) {//如果是和自己通讯
    //  pcConstraints.optional.add(
    //      new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "false"));
    //} else {
    //  pcConstraints.optional.add(
    //      new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "true"));
    //}

    // Check if there is a camera on device and disable video call if not.
    numberOfCameras = CameraEnumerationAndroid.getDeviceCount();
    if (numberOfCameras == 0) {
      Log.w(TAG, "No camera on device. Switch to audio only call.");
      videoCallEnabled = false;//如果设备不存在摄像头
    }
    // Create video constraints if video call is enabled.
    if (videoCallEnabled) {
      videoWidth = peerConnectionParameters.videoWidth;
      videoHeight = peerConnectionParameters.videoHeight;
      videoFps = peerConnectionParameters.videoFps;

      // If video resolution is not specified, default to HD.
      if (videoWidth == 0 || videoHeight == 0) {
        videoWidth = HD_VIDEO_WIDTH;
        videoHeight = HD_VIDEO_HEIGHT;
      }

      // If fps is not specified, default to 30.
      if (videoFps == 0) {
        videoFps = 30;
      }

      videoWidth = Math.min(videoWidth, MAX_VIDEO_WIDTH);
      videoHeight = Math.min(videoHeight, MAX_VIDEO_HEIGHT);
      videoFps = Math.min(videoFps, MAX_VIDEO_FPS);
    }

    // Create audio constraints.
    audioConstraints = new MediaConstraints();
    // added for audio performance measurements
    if (peerConnectionParameters.noAudioProcessing) {//是否使用谷歌的技术对音频进行处理
      Log.d(TAG, "Disabling audio processing");
      audioConstraints.mandatory.add(
          new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
          new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
          new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
      audioConstraints.mandatory.add(
          new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
    }
    if (peerConnectionParameters.enableLevelControl) {//???音频平滑控制
      Log.d(TAG, "Enabling level control.");
      audioConstraints.mandatory.add(
          new MediaConstraints.KeyValuePair(AUDIO_LEVEL_CONTROL_CONSTRAINT, "true"));
    }
    // Create SDP constraints. 创建本地SDP信息
    sdpMediaConstraints = new MediaConstraints();
    //sdpMediaConstraints.mandatory.add(
    //    new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
    //if (videoCallEnabled || peerConnectionParameters.loopback) {//设备存在可以使用的摄像头，或者和自己通讯
    //  sdpMediaConstraints.mandatory.add(
    //      new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
    //} else {
    //  sdpMediaConstraints.mandatory.add(
    //      new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
    //}
  }

  private void createCapturer(CameraEnumerator enumerator) {
    final String[] deviceNames = enumerator.getDeviceNames();

    // First, try to find front facing camera
    Logging.d(TAG, "Looking for front facing cameras.");
    for (String deviceName : deviceNames) {//开启前置摄像头
      if (enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating front facing camera capturer.");
        videoCapture = enumerator.createCapturer(deviceName, null);//实例化videoCapture对象

        if (videoCapture != null) {
          return;
        }
      }
    }

    // Front facing camera not found, try something else
    Logging.d(TAG, "Looking for other cameras.");
    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating other camera capturer.");
        videoCapture = enumerator.createCapturer(deviceName, null);

        if (videoCapture != null) {
          return;
        }
      }
    }
  }

  private void createPeerConnectionInternal(EglBase.Context renderEGLContext) {
    if (factory == null || isError) {
      Log.e(TAG, "Peerconnection factory is not created");
      return;
    }
    Log.d(TAG, "Create peer connection.");

    Log.d(TAG, "PCConstraints: "
        + pcConstraints.toString());//mandatory: [], optional: [DtlsSrtpKeyAgreement: true]
    queuedRemoteCandidates = new LinkedList<IceCandidate>();//初始化消息队列

    if (videoCallEnabled) {//当设备存在可用的摄像头的情况下
      Log.d(TAG, "EGLContext: " + renderEGLContext);
      factory.setVideoHwAccelerationOptions(renderEGLContext, renderEGLContext);
    }

    PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(
        signalingParameters.iceServers);//设置turn服务器地址，可能会包含stun服务器
    // TCP candidates are only useful when connecting to a server that supports
    // ICE-TCP.
    rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
    rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
    rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
    rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
    // Use ECDSA encryption. （一种数字签名加密算法）
    rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

    // 信令服务器的交流回调 pcObserver
    //--------------there is a callback-------------------
    // TODO: 2016/12/9 peerConnection callback
    peerConnection = factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);
    //--------------there is a callback-------------------
    isInitiator = false;//将创建者标志改为false

    // Set default WebRTC tracing and INFO libjingle logging.
    // NOTE: this _must_ happen while |factory| is alive!
    // 设置libjingle的日志模式和输出路径
    // TODO: 2016/12/9 change libjingel log tag and level
    //Logging.enableTracing("logcat:", EnumSet.of(Logging.TraceLevel.TRACE_DEFAULT));
    //Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);
    Logging.enableTracing("logcat:", EnumSet.of(Logging.TraceLevel.TRACE_NONE));
    Logging.enableLogToDebugOutput(Logging.Severity.LS_ERROR);

    mediaStream = factory.createLocalMediaStream("ARDAMS");//实例化媒体流
    if (videoCallEnabled) { //设置开启摄像头，并且设备存在摄像头 实例化videoCapture对象
      if (peerConnectionParameters.useCamera2) {
        if (!peerConnectionParameters.captureToTexture) {
          reportError(context.getString(R.string.camera2_texture_only_error));
          return;
        }

        Logging.d(TAG, "Creating capture using camera2 API.");
        createCapturer(new Camera2Enumerator(context));
      } else {
        Logging.d(TAG, "Creating capture using camera1 API.");
        createCapturer(new Camera1Enumerator(peerConnectionParameters.captureToTexture));
      }

      if (videoCapture == null) {
        reportError("Failed to open camera");
        return;
      }
      mediaStream.addTrack(createVideoTrack(videoCapture));//添加视频流
    }

    //mediaStream.addTrack(createAudioTrack());//添加音频流
    peerConnection.addStream(mediaStream);//将流信息添加到p2p连接

    //peerConnection.createDataChannel("sdwqecsadqewa", null).registerObserver(mDataChannelObserver);
    if (peerConnectionParameters.aecDump) {
      try {
        aecDumpFileDescriptor = ParcelFileDescriptor.open(new File(
            Environment.getExternalStorageDirectory().getPath()
                + File.separator
                + "Download/audio.aecdump"), ParcelFileDescriptor.MODE_READ_WRITE
            | ParcelFileDescriptor.MODE_CREATE
            | ParcelFileDescriptor.MODE_TRUNCATE);
        factory.startAecDump(aecDumpFileDescriptor.getFd(), -1);
      } catch (IOException e) {
        Log.e(TAG, "Can not open aecdump file", e);
      }
    }

    Log.d(TAG, "Peer connection created.");
  }

  private void closeInternal() {
    if (factory != null && peerConnectionParameters.aecDump) {
      factory.stopAecDump();
    }
    Log.d(TAG, "Closing peer connection.");
    statsTimer.cancel();
    if (peerConnection != null) {
      peerConnection.dispose();
      peerConnection = null;
    }
    Log.d(TAG, "Closing audio source.");
    if (audioSource != null) {
      audioSource.dispose();
      audioSource = null;
    }
    Log.d(TAG, "Stopping capture.");
    if (videoCapture != null) {
      try {
        videoCapture.stopCapture();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      videoCapture.dispose();
      videoCapture = null;
    }
    Log.d(TAG, "Closing video source.");
    if (videoSource != null) {
      videoSource.dispose();
      videoSource = null;
    }
    Log.d(TAG, "Closing peer connection factory.");
    if (factory != null) {
      factory.dispose();
      factory = null;
    }
    options = null;
    Log.d(TAG, "Closing peer connection done.");
    events.onPeerConnectionClosed();
    PeerConnectionFactory.stopInternalTracingCapture();
    PeerConnectionFactory.shutdownInternalTracer();
  }

  public boolean isHDVideo() {
    return videoCallEnabled && videoWidth * videoHeight >= 1280 * 720;
  }

  private void getStats() {
    if (peerConnection == null || isError) {
      return;
    }
    boolean success = peerConnection.getStats(new StatsObserver() {
      @Override public void onComplete(final StatsReport[] reports) {
        events.onPeerConnectionStatsReady(reports);
      }
    }, null);
    if (!success) {
      Log.e(TAG, "getStats() returns false!");
    }
  }

  void enableStatsEvents(boolean enable, int periodMs) {
    if (enable) {
      try {
        statsTimer.schedule(new TimerTask() {
          @Override public void run() {
            executor.execute(new Runnable() {
              @Override public void run() {
                getStats();
              }
            });
          }
        }, 0, periodMs);
      } catch (Exception e) {
        Log.e(TAG, "Can not schedule statistics timer", e);
      }
    } else {
      statsTimer.cancel();
    }
  }

  void setAudioEnabled(final boolean enable) {
    executor.execute(new Runnable() {
      @Override public void run() {
        enableAudio = enable;
        if (localAudioTrack != null) {
          localAudioTrack.setEnabled(enableAudio);
        }
      }
    });
  }

  public void setVideoEnabled(final boolean enable) {
    executor.execute(new Runnable() {
      @Override public void run() {
        renderVideo = enable;
        if (localVideoTrack != null) {
          localVideoTrack.setEnabled(renderVideo);
        }
        if (remoteVideoTrack != null) {
          remoteVideoTrack.setEnabled(renderVideo);
        }
      }
    });
  }

  void createOffer() {
    executor.execute(new Runnable() {
      @Override public void run() {
        if (peerConnection != null && !isError) {
          Log.d(TAG, "PC Create OFFER");
          isInitiator = true;
          peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
        }
      }
    });
  }

  void createAnswer(final MediaConstraints constraints) {
    executor.execute(new Runnable() {
      @Override public void run() {
        if (peerConnection != null && !isError) {
          Log.d(TAG, "PC create ANSWER");
          isInitiator = false;
          peerConnection.createAnswer(sdpObserver,
              constraints == null ? sdpMediaConstraints : constraints);
        }
      }
    });
  }

  void addRemoteIceCandidate(final IceCandidate candidate) {
    executor.execute(new Runnable() {
      @Override public void run() {
        if (peerConnection != null && !isError) {
          if (queuedRemoteCandidates != null) {
            queuedRemoteCandidates.add(candidate);
          } else {
            peerConnection.addIceCandidate(candidate);
          }
        }
      }
    });
  }

  void removeRemoteIceCandidates(final IceCandidate[] candidates) {
    executor.execute(new Runnable() {
      @Override public void run() {
        if (peerConnection == null || isError) {
          return;
        }
        // Drain the queued remote candidates if there is any so that
        // they are processed in the proper order.
        drainCandidates();
        peerConnection.removeIceCandidates(candidates);
      }
    });
  }

  // 将信令服务器返回的sdp消息
  void setRemoteDescription(final SessionDescription sdp) {
    executor.execute(new Runnable() {
      @Override public void run() {
        if (peerConnection == null || isError) {
          return;
        }
        String sdpDescription = sdp.description;
        if (preferIsac) {//修正音频编码格式
          sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
        }
        if (videoCallEnabled) {//设置是否开启视频编码
          sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
        }
        if (videoCallEnabled && peerConnectionParameters.videoStartBitrate > 0) {//设置视频的编码率
          sdpDescription = setStartBitrate(VIDEO_CODEC_VP8, true, sdpDescription,
              peerConnectionParameters.videoStartBitrate);
          sdpDescription = setStartBitrate(VIDEO_CODEC_VP9, true, sdpDescription,
              peerConnectionParameters.videoStartBitrate);
          sdpDescription = setStartBitrate(VIDEO_CODEC_H264, true, sdpDescription,
              peerConnectionParameters.videoStartBitrate);
        }
        if (peerConnectionParameters.audioStartBitrate > 0) {//设置音频的编码率
          sdpDescription = setStartBitrate(AUDIO_CODEC_OPUS, false, sdpDescription,
              peerConnectionParameters.audioStartBitrate);
        }
        Log.d(TAG, "Set remote SDP.");
        SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
        peerConnection.setRemoteDescription(sdpObserver, sdpRemote);//设置远程的SDP信息
      }
    });
  }

  void stopVideoSource() {
    executor.execute(new Runnable() {
      @Override public void run() {
        if (videoCapture != null && !videoCapturerStopped) {
          Log.d(TAG, "Stop video source.");
          try {
            videoCapture.stopCapture();
          } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
          }
          videoCapturerStopped = true;
        }
      }
    });
  }

  void startVideoSource() {
    executor.execute(new Runnable() {
      @Override public void run() {
        if (videoCapture != null && videoCapturerStopped) {
          Log.d(TAG, "Restart video source.");
          videoCapture.startCapture(videoWidth, videoHeight, videoFps);
          videoCapturerStopped = false;
        }
      }
    });
  }

  private void reportError(final String errorMessage) {
    Log.e(TAG, "Peerconnection error: " + errorMessage);
    executor.execute(new Runnable() {
      @Override public void run() {
        if (!isError) {
          events.onPeerConnectionError(errorMessage);
          isError = true;
        }
      }
    });
  }

  //创建AudioTrack
  private AudioTrack createAudioTrack() {
    audioSource = factory.createAudioSource(audioConstraints);
    localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
    localAudioTrack.setEnabled(enableAudio);
    return localAudioTrack;
  }

  // 创建VideoTrack
  private VideoTrack createVideoTrack(VideoCapturer capturer) {
    videoSource = factory.createVideoSource(capturer);
    capturer.startCapture(videoWidth, videoHeight, videoFps);

    localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
    localVideoTrack.setEnabled(renderVideo);
    localVideoTrack.addRenderer(new VideoRenderer(localRender));
    return localVideoTrack;
  }

  private static String setStartBitrate(String codec, boolean isVideoCodec, String sdpDescription,
      int bitrateKbps) {
    String[] lines = sdpDescription.split("\r\n");
    int rtpmapLineIndex = -1;
    boolean sdpFormatUpdated = false;
    String codecRtpMap = null;
    // Search for codec rtpmap in format
    // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
    String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
    Pattern codecPattern = Pattern.compile(regex);
    for (int i = 0; i < lines.length; i++) {
      Matcher codecMatcher = codecPattern.matcher(lines[i]);
      if (codecMatcher.matches()) {
        codecRtpMap = codecMatcher.group(1);
        rtpmapLineIndex = i;
        break;
      }
    }
    if (codecRtpMap == null) {
      Log.w(TAG, "No rtpmap for " + codec + " codec");
      return sdpDescription;
    }
    Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);

    // Check if a=fmtp string already exist in remote SDP for this codec and
    // update it with new bitrate parameter.
    regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
    codecPattern = Pattern.compile(regex);
    for (int i = 0; i < lines.length; i++) {
      Matcher codecMatcher = codecPattern.matcher(lines[i]);
      if (codecMatcher.matches()) {
        Log.d(TAG, "Found " + codec + " " + lines[i]);
        if (isVideoCodec) {
          lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
        } else {
          lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
        }
        Log.d(TAG, "Update remote SDP line: " + lines[i]);
        sdpFormatUpdated = true;
        break;
      }
    }

    StringBuilder newSdpDescription = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      newSdpDescription.append(lines[i]).append("\r\n");
      // Append new a=fmtp line if no such line exist for a codec.
      if (!sdpFormatUpdated && i == rtpmapLineIndex) {
        String bitrateSet;
        if (isVideoCodec) {
          bitrateSet =
              "a=fmtp:" + codecRtpMap + " " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
        } else {
          bitrateSet = "a=fmtp:" + codecRtpMap + " " + AUDIO_CODEC_PARAM_BITRATE + "=" + (
              bitrateKbps
                  * 1000);
        }
        Log.d(TAG, "Add remote SDP line: " + bitrateSet);
        newSdpDescription.append(bitrateSet).append("\r\n");
      }
    }
    return newSdpDescription.toString();
  }

  private static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
    String[] lines = sdpDescription.split("\r\n");
    int mLineIndex = -1;
    String codecRtpMap = null;
    // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
    String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
    Pattern codecPattern = Pattern.compile(regex);
    String mediaDescription = "m=video ";
    if (isAudio) {
      mediaDescription = "m=audio ";
    }
    for (int i = 0; (i < lines.length) && (mLineIndex == -1 || codecRtpMap == null); i++) {
      if (lines[i].startsWith(mediaDescription)) {
        mLineIndex = i;
        continue;
      }
      Matcher codecMatcher = codecPattern.matcher(lines[i]);
      if (codecMatcher.matches()) {
        codecRtpMap = codecMatcher.group(1);
      }
    }
    if (mLineIndex == -1) {
      Log.w(TAG, "No " + mediaDescription + " line, so can't prefer " + codec);
      return sdpDescription;
    }
    if (codecRtpMap == null) {
      Log.w(TAG, "No rtpmap for " + codec);
      return sdpDescription;
    }
    Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + ", prefer at " + lines[mLineIndex]);
    String[] origMLineParts = lines[mLineIndex].split(" ");
    if (origMLineParts.length > 3) {
      StringBuilder newMLine = new StringBuilder();
      int origPartIndex = 0;
      // Format is: m=<media> <port> <proto> <fmt> ...
      newMLine.append(origMLineParts[origPartIndex++]).append(" ");
      newMLine.append(origMLineParts[origPartIndex++]).append(" ");
      newMLine.append(origMLineParts[origPartIndex++]).append(" ");
      newMLine.append(codecRtpMap);
      for (; origPartIndex < origMLineParts.length; origPartIndex++) {
        if (!origMLineParts[origPartIndex].equals(codecRtpMap)) {
          newMLine.append(" ").append(origMLineParts[origPartIndex]);
        }
      }
      lines[mLineIndex] = newMLine.toString();
      Log.d(TAG, "Change media description: " + lines[mLineIndex]);
    } else {
      Log.e(TAG, "Wrong SDP media description format: " + lines[mLineIndex]);
    }
    StringBuilder newSdpDescription = new StringBuilder();
    for (String line : lines) {
      newSdpDescription.append(line).append("\r\n");
    }
    return newSdpDescription.toString();
  }

  private void drainCandidates() {
    if (queuedRemoteCandidates != null) {
      Log.d(TAG, "Add " + queuedRemoteCandidates.size() + " remote candidates");
      for (IceCandidate candidate : queuedRemoteCandidates) {
        peerConnection.addIceCandidate(candidate);
      }
      queuedRemoteCandidates = null;
    }
  }

  private void switchCameraInternal() {
    if (!videoCallEnabled || numberOfCameras < 2 || isError || videoCapture == null) {
      Log.e(TAG, "Failed to switch camera. Video: "
          + videoCallEnabled
          + ". Error : "
          + isError
          + ". Number of cameras: "
          + numberOfCameras);
      return;  // No video is sent or only one camera is available or error happened.
    }
    Log.d(TAG, "Switch camera");
    videoCapture.switchCamera(null);//切换摄像头
  }

  void switchCamera() {
    executor.execute(new Runnable() {
      @Override public void run() {
        switchCameraInternal();
      }
    });
  }

  void changeCaptureFormat(final int width, final int height, final int framerate) {
    executor.execute(new Runnable() {
      @Override public void run() {
        changeCaptureFormatInternal(width, height, framerate);
      }
    });
  }

  private void changeCaptureFormatInternal(int width, int height, int framerate) {
    if (!videoCallEnabled || isError || videoCapture == null) {
      Log.e(TAG,
          "Failed to change capture format. Video: " + videoCallEnabled + ". Error : " + isError);
      return;
    }
    Log.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
    videoCapture.onOutputFormatRequest(width, height, framerate);
  }

  // Implementation detail: observe ICE & stream changes and react accordingly.
  // 实现细节： 根据观察ICE和流的改变而改变
  private class PCObserver implements PeerConnection.Observer {

    @Override public void onIceCandidate(final IceCandidate candidate) {
      Log.i(TAG, "PeerConnection.Observer --> onIceCandidate");
      executor.execute(new Runnable() {
        @Override public void run() {
          events.onIceCandidate(candidate);
        }
      });
    }

    @Override public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
      Log.i(TAG, "PeerConnection.Observer --> onIceCandidatesRemoved");
      executor.execute(new Runnable() {
        @Override public void run() {
          events.onIceCandidatesRemoved(candidates);
        }
      });
    }

    @Override public void onSignalingChange(PeerConnection.SignalingState newState) {
      Log.d(TAG, "SignalingState: " + newState);
    }

    @Override public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
      Log.i(TAG, "PeerConnection.Observer --> onIceConnectionChange");
      executor.execute(new Runnable() {
        @Override public void run() {
          Log.d(TAG, "IceConnectionState: " + newState);
          if (newState == IceConnectionState.CONNECTED) {
            events.onIceConnected();
          } else if (newState == IceConnectionState.DISCONNECTED) {
            events.onIceDisconnected();
          } else if (newState == IceConnectionState.FAILED) {
            reportError("ICE connection failed.");
          }
        }
      });
    }

    @Override public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
      Log.d(TAG, "IceGatheringState: " + newState);
    }

    @Override public void onIceConnectionReceivingChange(boolean receiving) {
      Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
    }

    @Override public void onAddStream(final MediaStream stream) {
      executor.execute(new Runnable() {
        @Override public void run() {
          if (peerConnection == null || isError) {
            return;
          }
          if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
            reportError("Weird-looking stream: " + stream);
            return;
          }
          if (stream.videoTracks.size() == 1) {
            remoteVideoTrack = stream.videoTracks.get(0);
            remoteVideoTrack.setEnabled(renderVideo);
            remoteVideoTrack.addRenderer(new VideoRenderer(remoteRender));
          }
        }
      });
    }

    @Override public void onRemoveStream(final MediaStream stream) {
      executor.execute(new Runnable() {
        @Override public void run() {
          remoteVideoTrack = null;
        }
      });
    }

    @Override public void onDataChannel(final DataChannel dc) {
      Log.i(TAG, "onDataChannel: datachannel create");
      mDataChannel = dc;
      mDataChannel.registerObserver(mDataChannelObserver);
      //reportError("AppRTC doesn't use data channels, but got: " + dc.label() + " anyway!");
    }

    @Override public void onRenegotiationNeeded() {
      // No need to do anything; AppRTC follows a pre-agreed-upon
      // signaling/negotiation protocol.
    }
  }

  // Implementation detail: handle offer creation/signaling and answer setting,
  // as well as adding remote ICE candidates once the answer SDP is set.
  // 处理offer 候选者／信令 和 应答 一旦应答SDP设置就会添加远程ICE候选者
  private class SDPObserver implements SdpObserver {
    @Override public void onCreateSuccess(final SessionDescription origSdp) {
      Log.d(TAG, "SDPObserver --> onCreateSuccess");
      if (localSdp != null) {
        reportError("Multiple SDP create.");
        return;
      }
      String sdpDescription = origSdp.description;
      if (preferIsac) {//设置音频解码
        sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
      }
      if (videoCallEnabled) {//是否开启视频解码
        sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
      }
      final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);
      localSdp = sdp;
      executor.execute(new Runnable() {
        @Override public void run() {
          if (peerConnection != null && !isError) {
            Log.d(TAG, "Set local SDP from " + sdp.type);
            peerConnection.setLocalDescription(sdpObserver, sdp);
          }
        }
      });
    }

    @Override public void onSetSuccess() {
      Log.d(TAG, "SDPObserver --> onSetSuccess");
      executor.execute(new Runnable() {
        @Override public void run() {
          if (peerConnection == null || isError) {
            return;
          }
          if (isInitiator) {//房间的创建者（不含参与者的SDP信息和ICE信息）
            // For offering peer connection we first create offer and set
            // local SDP, then after receiving answer set remote SDP.
            // 对于请求peer连接 我们首先创建请求并设置本地SDP信息，然后当接受应答后设置远端的SDP
            if (peerConnection.getRemoteDescription() == null) {
              // We've just set our local SDP so time to send it.
              Log.d(TAG, "Local SDP set successfully");
              events.onLocalDescription(localSdp);
            } else {
              // We've just set remote description, so drain remote
              // and send local ICE candidates.
              Log.d(TAG, "Remote SDP set successfully");
              drainCandidates();//清空队列
            }
          } else {
            // For answering peer connection we set remote SDP and then
            // create answer and set local SDP.
            // 对于应答peer连接，我们设置远程SDP然后创建应答并设置本地SDP
            if (peerConnection.getLocalDescription() != null) {
              // We've just set our local SDP so time to send it, drain
              // remote and send local ICE candidates.
              Log.d(TAG, "Local SDP set successfully");
              events.onLocalDescription(localSdp);
              drainCandidates();
            } else {
              // We've just set remote SDP - do nothing for now -
              // answer will be created soon.
              Log.d(TAG, "Remote SDP set successfully");
            }
          }
        }
      });
    }

    @Override public void onCreateFailure(final String error) {
      Log.i(TAG, "SDPObserver --> onCreateFailure");
      reportError("createSDP error: " + error);
    }

    @Override public void onSetFailure(final String error) {
      Log.i(TAG, "SDPObserver --> onSetFailure");
      reportError("setSDP error: " + error);
    }
  }

  DataChannel.Observer mDataChannelObserver = new DataChannel.Observer() {
    @Override public void onBufferedAmountChange(long l) {
      Log.i(TAG, "onBufferedAmountChange: " + l);
    }

    @Override public void onStateChange() {
      Log.i(TAG, "onStateChange " + mDataChannel.state());
    }

    @Override public void onMessage(DataChannel.Buffer buffer) {
      Log.i(TAG, "onMessage: " + byteBufferToString(buffer.data));
    }
  };

  public static String byteBufferToString(ByteBuffer buffer) {
    CharBuffer charBuffer = null;
    try {
      Charset charset = Charset.forName("UTF-8");
      CharsetDecoder decoder = charset.newDecoder();
      charBuffer = decoder.decode(buffer);
      buffer.flip();
      return charBuffer.toString();
    } catch (Exception ex) {
      ex.printStackTrace();
      return "";
    }
  }
}
