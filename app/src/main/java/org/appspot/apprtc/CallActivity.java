/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.multidex.MultiDex;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;
import org.appspot.apprtc.AppRTCClient.RoomConnectionParameters;
import org.appspot.apprtc.AppRTCClient.SignalingParameters;
import org.appspot.apprtc.PCManager.DCManager;
import org.appspot.apprtc.PCManager.MediaManager;
import org.appspot.apprtc.PCManager.PCManager;
import org.appspot.apprtc.PeerConnectionClient.PeerConnectionParameters;
import org.appspot.apprtc.bean.DCRequest;
import org.appspot.apprtc.bean.DCResponse;
import org.appspot.apprtc.util.DCPresenter;
import org.appspot.apprtc.util.DeviceState;
import org.appspot.apprtc.util.LooperExecutor;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import rx.schedulers.Schedulers;

/**
 * Activity for peer connection call setup, call waiting
 * and call view.
 * p2p建立开始建立，建立等待，建立成功的处理
 */
public class CallActivity extends Activity
    implements AppRTCClient.SignalingEvents, CallFragment.OnCallEvents, PCManager.Observer {

  public static final String EXTRA_ROOMID = "org.appspot.apprtc.ROOMID";
  public static final String EXTRA_LOOPBACK = "org.appspot.apprtc.LOOPBACK";
  public static final String EXTRA_VIDEO_CALL = "org.appspot.apprtc.VIDEO_CALL";
  public static final String EXTRA_CAMERA2 = "org.appspot.apprtc.CAMERA2";
  public static final String EXTRA_VIDEO_WIDTH = "org.appspot.apprtc.VIDEO_WIDTH";
  public static final String EXTRA_VIDEO_HEIGHT = "org.appspot.apprtc.VIDEO_HEIGHT";
  public static final String EXTRA_VIDEO_FPS = "org.appspot.apprtc.VIDEO_FPS";
  public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED =
      "org.appsopt.apprtc.VIDEO_CAPTUREQUALITYSLIDER";
  public static final String EXTRA_VIDEO_BITRATE = "org.appspot.apprtc.VIDEO_BITRATE";
  public static final String EXTRA_VIDEOCODEC = "org.appspot.apprtc.VIDEOCODEC";
  public static final String EXTRA_HWCODEC_ENABLED = "org.appspot.apprtc.HWCODEC";
  public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "org.appspot.apprtc.CAPTURETOTEXTURE";
  public static final String EXTRA_AUDIO_BITRATE = "org.appspot.apprtc.AUDIO_BITRATE";
  public static final String EXTRA_AUDIOCODEC = "org.appspot.apprtc.AUDIOCODEC";
  public static final String EXTRA_NOAUDIOPROCESSING_ENABLED =
      "org.appspot.apprtc.NOAUDIOPROCESSING";
  public static final String EXTRA_AECDUMP_ENABLED = "org.appspot.apprtc.AECDUMP";
  public static final String EXTRA_OPENSLES_ENABLED = "org.appspot.apprtc.OPENSLES";
  public static final String EXTRA_DISABLE_BUILT_IN_AEC = "org.appspot.apprtc.DISABLE_BUILT_IN_AEC";
  public static final String EXTRA_DISABLE_BUILT_IN_AGC = "org.appspot.apprtc.DISABLE_BUILT_IN_AGC";
  public static final String EXTRA_DISABLE_BUILT_IN_NS = "org.appspot.apprtc.DISABLE_BUILT_IN_NS";
  public static final String EXTRA_ENABLE_LEVEL_CONTROL = "org.appspot.apprtc.ENABLE_LEVEL_CONTROL";
  public static final String EXTRA_DISPLAY_HUD = "org.appspot.apprtc.DISPLAY_HUD";
  public static final String EXTRA_TRACING = "org.appspot.apprtc.TRACING";
  public static final String EXTRA_CMDLINE = "org.appspot.apprtc.CMDLINE";
  public static final String EXTRA_RUNTIME = "org.appspot.apprtc.RUNTIME";
  private static final String TAG = "CallRTCClient";

  // List of mandatory application permissions.
  // 需要的权限
  private static final String[] MANDATORY_PERMISSIONS = {
      "android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
      "android.permission.INTERNET"
  };

  // Peer connection statistics callback period in ms.
  private static final int STAT_CALLBACK_PERIOD = 1000;
  // Local preview screen position before call is connected.
  private static final int LOCAL_X_CONNECTING = 0;
  private static final int LOCAL_Y_CONNECTING = 0;
  private static final int LOCAL_WIDTH_CONNECTING = 100;
  private static final int LOCAL_HEIGHT_CONNECTING = 100;
  // Local preview screen position after call is connected.
  private static final int LOCAL_X_CONNECTED = 72;
  private static final int LOCAL_Y_CONNECTED = 72;
  private static final int LOCAL_WIDTH_CONNECTED = 25;
  private static final int LOCAL_HEIGHT_CONNECTED = 25;
  // Remote video screen position
  private static final int REMOTE_X = 0;
  private static final int REMOTE_Y = 0;
  private static final int REMOTE_WIDTH = 100;
  private static final int REMOTE_HEIGHT = 100;
  //private PeerConnectionClient peerConnectionClient = null;
  private AppRTCClient appRtcClient;
  private SignalingParameters signalingParameters;
  private AppRTCAudioManager audioManager = null;
  private EglBase rootEglBase;
  private SurfaceViewRenderer localRender;
  private SurfaceViewRenderer remoteRender;
  private PercentFrameLayout localRenderLayout;
  private PercentFrameLayout remoteRenderLayout;
  private ScalingType scalingType;
  private Toast logToast;
  private boolean commandLineRun;
  private int runTimeMs;
  private boolean activityRunning;
  private RoomConnectionParameters roomConnectionParameters;
  private PeerConnectionParameters peerConnectionParameters;
  private boolean iceConnected;
  private boolean isError;
  private boolean callControlFragmentVisible = true;
  private long callStartedTimeMs = 0;
  private boolean micEnabled = true;

  // Controls
  private CallFragment callFragment;
  private HudFragment hudFragment;
  private CpuMonitor cpuMonitor;
  DCPresenter mDCPresenter;
  DCManager mDCManager;
  MediaManager mMediaManager;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

    // Set window styles for fullscreen-window size. Needs to be done before
    // adding content.
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN
        | LayoutParams.FLAG_KEEP_SCREEN_ON
        | LayoutParams.FLAG_DISMISS_KEYGUARD
        | LayoutParams.FLAG_SHOW_WHEN_LOCKED
        | LayoutParams.FLAG_TURN_SCREEN_ON);
    getWindow().getDecorView()
        .setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    setContentView(R.layout.activity_call);

    iceConnected = false;
    signalingParameters = null;
    scalingType = ScalingType.SCALE_ASPECT_FILL;

    // Create UI controls.
    localRender = (SurfaceViewRenderer) findViewById(R.id.local_video_view);
    remoteRender = (SurfaceViewRenderer) findViewById(R.id.remote_video_view);
    localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);
    remoteRenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video_layout);
    callFragment = new CallFragment();
    hudFragment = new HudFragment();

    // Show/hide call control fragment on view click.
    View.OnClickListener listener = new View.OnClickListener() {
      @Override public void onClick(View view) {
        toggleCallControlFragmentVisibility();
      }
    };

    localRender.setOnClickListener(listener);
    remoteRender.setOnClickListener(listener);

    // Create video renderers.
    rootEglBase = EglBase.create();
    localRender.init(rootEglBase.getEglBaseContext(), null);
    remoteRender.init(rootEglBase.getEglBaseContext(), null);
    localRender.setZOrderMediaOverlay(true);
    updateVideoView();

    // Check for mandatory permissions.
    // 检查必须的权限
    for (String permission : MANDATORY_PERMISSIONS) {
      if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        logAndToast("Permission " + permission + " is not granted");
        setResult(RESULT_CANCELED);
        finish();
        return;
      }
    }

    // Get Intent parameters.
    // 取出预设选项
    final Intent intent = getIntent();
    Uri roomUri = intent.getData();
    if (roomUri == null) {
      logAndToast(getString(R.string.missing_url));
      Log.e(TAG, "Didn't get any URL in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }
    String roomId = intent.getStringExtra(EXTRA_ROOMID);
    if (roomId == null || roomId.length() == 0) {
      logAndToast(getString(R.string.missing_url));
      Log.e(TAG, "Incorrect room ID in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }

    commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false);//是否显示命令行
    runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0);//开始建立连接后多长时间结束

    appRtcClient = new WebSocket3Client(this, new LooperExecutor());
    // Create connection parameters. 创建连接参数
    roomConnectionParameters = new RoomConnectionParameters(roomUri.toString(), roomId, false);

    // Create CPU monitor
    cpuMonitor = new CpuMonitor(this);
    hudFragment.setCpuMonitor(cpuMonitor);

    // Send intent arguments to fragments.
    callFragment.setArguments(intent.getExtras());
    hudFragment.setArguments(intent.getExtras());
    // Activate call and HUD fragments and start the call.
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    ft.add(R.id.call_fragment_container, callFragment);
    ft.add(R.id.hud_fragment_container, hudFragment);
    ft.commit();
    //------------------------------------
    // TODO: 2016/12/12 Jump
    startCall();//注意这里，开始并行操作了，开启请求服务器
    //------------------------------------

    // For command line execution run connection for <runTimeMs> and exit.
    //对于通过隐式意图开启的连接运行<runTimeMs>长时间后断开连接
    if (commandLineRun && runTimeMs > 0) {
      (new Handler()).postDelayed(new Runnable() {
        @Override public void run() {
          disconnect();
        }
      }, runTimeMs);
    }

    //peerConnectionClient = PeerConnectionClient.getInstance();
    if (!PCManager.createPCFactory(this)) {
      reportError("create peer connection factory fail");
    }
    //if (loopback) {//如果是和自己建立连接
    //  PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
    //  options.networkIgnoreMask = 0;
    //  peerConnectionClient.setPeerConnectionFactoryOptions(options);
    //}
    //peerConnectionClient.createPeerConnectionFactory(CallActivity.this, peerConnectionParameters,
    //    CallActivity.this);
    mDCPresenter = new DCPresenter(this);
  }

  // Activity interfaces
  @Override public void onPause() {
    super.onPause();
    activityRunning = false;
    if (mMediaManager != null) {
      mMediaManager.stopVideoSource();
    }
    cpuMonitor.pause();
  }

  @Override public void onResume() {
    super.onResume();
    activityRunning = true;
    if (mMediaManager != null) {
      mMediaManager.startVideoSource();
    }
    cpuMonitor.resume();
  }

  @Override protected void onDestroy() {
    disconnect();
    if (logToast != null) {
      logToast.cancel();
    }
    activityRunning = false;
    rootEglBase.release();
    super.onDestroy();
  }

  // CallFragment.OnCallEvents interface implementation.
  @Override public void onCallHangUp() {
    disconnect();
  }

  @Override public void onCameraSwitch() {
    if (mMediaManager != null) {
      mMediaManager.switchCamera();
    }
  }

  @Override public void onVideoScalingSwitch(ScalingType scalingType) {
    this.scalingType = scalingType;
    updateVideoView();
  }

  @Override public void onCaptureFormatChange(int width, int height, int framerate) {
    if (mMediaManager != null) {
      mMediaManager.changeCaptureFormat(width, height, framerate);
    }
  }

  @Override public boolean onToggleMic() {
    if (mMediaManager != null) {
      micEnabled = !micEnabled;
      mMediaManager.setAudioEnabled(micEnabled);
    }
    return micEnabled;
  }

  // Helper functions.
  private void toggleCallControlFragmentVisibility() {
    if (!iceConnected || !callFragment.isAdded()) {
      return;
    }
    // Show/hide call control fragment
    callControlFragmentVisible = !callControlFragmentVisible;
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (callControlFragmentVisible) {
      ft.show(callFragment);
      ft.show(hudFragment);
    } else {
      ft.hide(callFragment);
      ft.hide(hudFragment);
    }
    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    ft.commit();
  }

  VideoCapturer createVideoCapturer() {
    VideoCapturer videoCapturer;
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
    } else {
      videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
    }
    return videoCapturer;
  }

  VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    final String[] deviceNames = enumerator.getDeviceNames();

    // First, try to find front facing camera
    Logging.d(TAG, "Looking for front facing cameras.");
    for (String deviceName : deviceNames) {
      if (enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating front facing camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    // Front facing camera not found, try something else
    Logging.d(TAG, "Looking for other cameras.");
    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating other camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    return null;
  }

  private void updateVideoView() {
    remoteRenderLayout.setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH, REMOTE_HEIGHT);
    remoteRender.setScalingType(scalingType);
    remoteRender.setMirror(false);

    if (iceConnected) {
      localRenderLayout.setPosition(LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED, LOCAL_WIDTH_CONNECTED,
          LOCAL_HEIGHT_CONNECTED);
      localRender.setScalingType(ScalingType.SCALE_ASPECT_FIT);
    } else {
      localRenderLayout.setPosition(LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING, LOCAL_WIDTH_CONNECTING,
          LOCAL_HEIGHT_CONNECTING);
      localRender.setScalingType(scalingType);
    }
    localRender.setMirror(true);

    localRender.requestLayout();
    remoteRender.requestLayout();
  }

  private void startCall() {
    if (appRtcClient == null) {
      Log.e(TAG, "AppRTC client is not allocated for a call.");
      return;
    }
    callStartedTimeMs = System.currentTimeMillis();

    // Start room connection. 开始与房间建立连接 对应日志3：9
    logAndToast(getString(R.string.connecting_to, roomConnectionParameters.roomUrl));
    //---------------------------------------------------
    // TODO: 2016/12/12 jump 2
    appRtcClient.connectToRoom(roomConnectionParameters);
    //---------------------------------------------------

    // Create and audio manager that will take care of audio routing,
    // audio modes, audio device enumeration etc.
    // 创建一个 音频管理器 它将管理音频的控制
    // 音频模式， 音频设备的列表等
    audioManager = AppRTCAudioManager.create(this, new Runnable() {
      // This method will be called each time the audio state (number and
      // type of devices) has been changed.
      @Override public void run() {
        onAudioManagerChangedState();
      }
    });
    // Store existing audio settings and change audio mode to
    // MODE_IN_COMMUNICATION for best possible VoIP performance.
    Log.d(TAG, "Initializing the audio manager...");
    audioManager.init();
  }

  // Should be called from UI thread
  private void callConnected(int label) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    Log.i(TAG, "Call connected: delay=" + delta + "ms");
    if (PCManager.get(label) == null || isError) {
      Log.w(TAG, "Call is connected in closed or error state");
      return;
    }
    // Update video view.刷新UI
    // TODO: 2016/12/12 jump 5
    updateVideoView();
    // Enable statistics callback. 开启统计
    PCManager.get(label).enableStatsEvents(true, STAT_CALLBACK_PERIOD);
  }

  private void onAudioManagerChangedState() {
    // TODO(henrika): disable video if AppRTCAudioManager.AudioDevice.EARPIECE
    // is active.
  }

  // Disconnect from remote resources, dispose of local resources, and exit.
  private void disconnect() {
    activityRunning = false;
    if (appRtcClient != null) {
      appRtcClient.disconnectFromRoom();
      appRtcClient = null;
    }
    PCManager.closeAll();
    if (localRender != null) {
      localRender.release();
      localRender = null;
    }
    if (remoteRender != null) {
      remoteRender.release();
      remoteRender = null;
    }
    if (audioManager != null) {
      audioManager.close();
      audioManager = null;
    }
    //if (iceConnected && !isError) {
    //  setResult(RESULT_OK);
    //} else {
    //  setResult(RESULT_CANCELED);
    //}
    mDCPresenter.close();
    finish();
  }

  private void disconnectWithErrorMessage(final String errorMessage) {
    if (commandLineRun || !activityRunning) {
      Log.e(TAG, "Critical error: " + errorMessage);
      disconnect();
    } else {
      new AlertDialog.Builder(this).setTitle(getText(R.string.channel_error_title))
          .setMessage(errorMessage)
          .setCancelable(false)
          .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
              disconnect();
            }
          })
          .create()
          .show();
    }
  }

  // Log |msg| and Toast about it.
  private void logAndToast(String msg) {
    Log.d(TAG, msg);
    if (logToast != null) {
      logToast.cancel();
    }
    logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
    logToast.show();
  }

  private void reportError(final String description) {
    runOnUiThread(new Runnable() {
      @Override public void run() {
        if (!isError) {
          isError = true;
          disconnectWithErrorMessage(description);
        }
      }
    });
  }

  // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
  // All callbacks are invoked from websocket signaling looper thread and
  // are routed to UI thread.
  // 信令服务器返回房间信息时的回调
  private void onConnectedToRoomInternal(final SignalingParameters params, int label) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;

    signalingParameters = params;
    logAndToast("Creating peer connection, delay=" + delta + "ms"); //日志331行
    PCManager.createPCManager(signalingParameters.iceServers, rootEglBase.getEglBaseContext(), this,
        label);
    PCManager pcManager = PCManager.get(label);
    if (pcManager == null) {
      disconnect();
      return;
    }
    if (label == TYPE_DC) {
      mDCManager = pcManager.getDCManager(false, mDcObserver);
      Log.i(TAG, "create data channel");
    }
    if (label == TYPE_MS) {
      MediaManager.Parameter parameter =
          new MediaManager.Parameter(rootEglBase.getEglBaseContext(), localRender, remoteRender,
              createVideoCapturer(), 1280, 720, 30);
      mMediaManager = pcManager.getMediaManager(parameter);
    }
    if (params.offerSdp != null) {
      pcManager.setRemoteDescription(params.offerSdp);
      pcManager.createAnswer(params.mediaConstraints);
    }
  }

  //服务器返回建立房间的参数
  @Override public void onConnectedToRoom(final SignalingParameters params, int label) {
    Log.i(TAG, "SignalingEvents --> onConnectedToRoom");
    runOnUiThread(new Runnable() {
      @Override public void run() {
        onConnectedToRoomInternal(params, label);
      }
    });
  }

  @Override
  public void onRemoteDescription(final SessionDescription sdp, MediaConstraints constraints,
      final int label) {
    Log.i(TAG, "SignalingEvents --> onRemoteDescription");
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override public void run() {
        if (PCManager.get(label) == null) {
          Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
          return;
        }
        logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
        PCManager.get(label).setRemoteDescription(sdp);
        if (!signalingParameters.initiator) {
          logAndToast("Creating ANSWER...");
          // Create answer. Answer SDP will be sent to offering client in
          // PeerConnectionEvents.onLocalDescription event.
          PCManager.get(label).createAnswer(constraints);
        }
      }
    });
  }

  @Override public void onRemoteIceCandidate(final IceCandidate candidate, int label) {
    Log.i(TAG, "SignalingEvents --> onRemoteIceCandidate");
    runOnUiThread(new Runnable() {
      @Override public void run() {
        if (PCManager.get(label) == null) {
          Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
          return;
        }
        PCManager.get(label).addRemoteIceCandidate(candidate);
      }
    });
  }

  @Override
  public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates, final int label) {
    Log.i(TAG, "SignalingEvents --> onRemoteIceCandidatesRemoved");
    runOnUiThread(new Runnable() {
      @Override public void run() {
        if (PCManager.get(label) == null) {
          Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
          return;
        }
        PCManager.get(label).removeRemoteIceCandidates(candidates);
      }
    });
  }

  @Override public void onChannelClose(int label) {
    Log.i(TAG, "SignalingEvents --> onChannelClose");
    runOnUiThread(new Runnable() {
      @Override public void run() {
        logAndToast("Remote end hung up; dropping PeerConnection");
        // TODO: 2016/12/26 检查关闭的类型
        PCManager.get(label).close();
      }
    });
  }

  //信令服务器建立连接失败的回调
  @Override public void onChannelError(final String description) {
    Log.i(TAG, "SignalingEvents --> onChannelError");
    reportError(description);
  }

  // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
  // Send local peer connection SDP and ICE candidates to remote party.
  // All callbacks are invoked from peer connection client looper thread and
  // are routed to UI thread.
  // 发送本地peer连接SDP到远程参与者
  @Override public void onLocalDescription(final SessionDescription sdp, int label) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    Log.i(TAG, "PeerConnectionEvents --> onLocalDescription");
    runOnUiThread(new Runnable() {
      @Override public void run() {
        if (appRtcClient != null) {
          logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
          if (signalingParameters.initiator) {
            appRtcClient.sendOfferSdp(sdp, label);
          } else {
            appRtcClient.sendAnswerSdp(sdp, label);
          }
        }
      }
    });
  }

  @Override public void onIceCandidate(final IceCandidate candidate, int label) {
    Log.i(TAG, "PeerConnectionEvents --> onIceCandidate");
    runOnUiThread(new Runnable() {
      @Override public void run() {
        if (appRtcClient != null) {
          appRtcClient.sendLocalIceCandidate(candidate, label);
        }
      }
    });
  }

  @Override public void onIceCandidatesRemoved(final IceCandidate[] candidates, int label) {
    Log.i(TAG, "PeerConnectionEvents --> onIceCandidatesRemoved");
    runOnUiThread(new Runnable() {
      @Override public void run() {
        if (appRtcClient != null) {
          appRtcClient.sendLocalIceCandidateRemovals(candidates, label);
        }
      }
    });
  }

  @Override public void onIceConnected(int label) {
    Log.i(TAG, "PeerConnectionEvents --> onIceConnected");
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override public void run() {
        logAndToast("ICE connected, delay=" + delta + "ms");
        iceConnected = true;
        callConnected(label);
      }
    });
  }

  @Override public void onIceDisconnected(int label) {
    Log.i(TAG, "PeerConnectionEvents --> onIceDisconnected");
    runOnUiThread(new Runnable() {
      @Override public void run() {
        logAndToast("ICE disconnected");
        iceConnected = false;
        PCManager.get(label).close();
      }
    });
  }

  @Override public void onPeerConnectionClosed(int label) {
    Log.i(TAG, "PeerConnectionEvents --> onPeerConnectionClosed");
  }

  @Override public void onPeerConnectionStatsReady(final StatsReport[] reports, int label) {
    Log.i(TAG, "PeerConnectionEvents --> onPeerConnectionStatsReady");
    runOnUiThread(new Runnable() {
      @Override public void run() {
        if (!isError && iceConnected) {
          hudFragment.updateEncoderStatistics(reports);
        }
      }
    });
  }

  @Override public void onPeerConnectionError(final String description, int label) {
    Log.i(TAG, "PeerConnectionEvents --> onPeerConnectionError");
    reportError(description);
  }

  final DCManager.Observer mDcObserver = new DCManager.Observer() {
    @Override public void onRequest(DCRequest request) {
      mDCPresenter.onRequest(request)
          .subscribeOn(Schedulers.io())
          .subscribe(response -> mDCManager.sendMessage(response));
    }

    @Override public void onResponse(DCResponse response) {
      // TODO: 2016/12/22
    }
  };
}
