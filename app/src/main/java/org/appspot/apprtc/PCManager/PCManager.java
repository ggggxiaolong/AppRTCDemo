package org.appspot.apprtc.PCManager;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

/**
 * @author leon.tan on 12/25/16.
 */

public class PCManager implements PeerConnection.Observer, SdpObserver {
  public static final String TAG = "PCManager";
  private static final String VIDEO_CODEC_VP9 = "VP9";
  static final PCFactory pcFactory = new PCFactory();
  PeerConnection mPeerConnection;
  final Observer mObserver;
  final int mLabel;
  DCManager mDCManager;
  MediaManager mMediaManager;
  LinkedList<IceCandidate> mRemoteCandidates;
  boolean isError = false;
  SessionDescription mLocalSDP;
  boolean isInitiator = false;
  final ExecutorService mExecutor;
  Timer statsTimer;

  PCManager(Observer observer, int label) {
    mObserver = observer;
    mDCManager = new DCManager();
    mMediaManager = new MediaManager();
    mExecutor = Executors.newSingleThreadExecutor();
    statsTimer = new Timer();
    mLabel = label;
  }

  public static boolean createPCFactory(Context context) {
    return pcFactory.createFactory(context);
  }

  public static void createPCManager(final List<PeerConnection.IceServer> iceServers,
      EglBase.Context context, PCManager.Observer observer, int label) {
    pcFactory.createPCManager(iceServers, context, observer, label);
  }

  public static PCManager get(int label){
    return pcFactory.getPCManager(label);
  }

  void setPeerConnection(@NonNull PeerConnection peerConnection) {
    mPeerConnection = peerConnection;
    mRemoteCandidates = new LinkedList<IceCandidate>();
  }

  public DCManager getDCManager(boolean initiative, DCManager.Observer observer) {
    if (initiative) {
      DataChannel dc = mPeerConnection.createDataChannel("MobileDC", new DataChannel.Init());
      mDCManager.setDataChannel(dc);
    }
    mDCManager.setObserver(observer);
    return mDCManager;
  }

  public MediaManager getMediaManager(MediaManager.Parameter parameter) {
    if (mMediaManager.isInit) return mMediaManager;
    MediaStream mediaStream = pcFactory.createLocalMediaStream();
    AudioSource audioSource = pcFactory.createAudioSource();
    VideoSource videoSource = pcFactory.createVideoSource(parameter.videoCapturer);
    AudioTrack audioTrack = pcFactory.createAudioTrack(audioSource);
    parameter.videoCapturer.startCapture(parameter.videoWidth, parameter.videoHeight, parameter.videoFps);
    VideoTrack videoTrack = pcFactory.createVideoTrack(videoSource, parameter.localRender);
    videoTrack.setEnabled(true);
    mediaStream.addTrack(audioTrack);
    mediaStream.addTrack(videoTrack);
    mPeerConnection.addStream(mediaStream);

    mMediaManager.setParameter(parameter);
    mMediaManager.audioSource = audioSource;
    mMediaManager.videoSource = videoSource;
    mMediaManager.localAudioTrack = audioTrack;
    mMediaManager.localVideoTrack = videoTrack;
    mMediaManager.isInit = true;
    return mMediaManager;
  }

  //(start)-----------------------------PeerConnection.Observer-----------------------
  @Override public void onSignalingChange(PeerConnection.SignalingState newState) {
    Log.d(TAG, "SignalingState: " + newState);
  }

  @Override public void onIceConnectionReceivingChange(boolean receiving) {
    Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
  }

  @Override public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
    Log.d(TAG, "IceGatheringState: " + newState);
  }

  @Override public void onIceConnectionChange(IceConnectionState newState) {
    if (newState == IceConnectionState.CONNECTED) {
      mObserver.onIceConnected(mLabel);
    } else if (newState == IceConnectionState.DISCONNECTED) {
      mObserver.onIceDisconnected(mLabel);
    } else if (newState == IceConnectionState.FAILED) {
      mObserver.onIceDisconnected(mLabel);
    }
  }

  @Override public void onIceCandidate(IceCandidate iceCandidate) {
    mObserver.onIceCandidate(iceCandidate, mLabel);
  }

  @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
    mObserver.onIceCandidatesRemoved(iceCandidates, mLabel);
  }

  @Override public void onAddStream(MediaStream mediaStream) {
    mMediaManager.addRemoteStream(mediaStream);
  }

  @Override public void onRemoveStream(MediaStream mediaStream) {
    mMediaManager.removeRemoteStream(mediaStream);
  }

  @Override public void onDataChannel(DataChannel dataChannel) {
    Log.i(TAG, "receive a new data chanel:" + dataChannel.label());
    mDCManager.setDataChannel(dataChannel);
  }

  @Override public void onRenegotiationNeeded() {
    //do nothing now
  }
  //(end)-----------------------------PeerConnection.Observer-----------------------

  //(start)------------------------------SdpObserver-------------------------------

  @Override public void onCreateSuccess(SessionDescription origSdp) {
    if (mLocalSDP != null) {
      mObserver.onPeerConnectionError("Multiple SDP create.", mLabel);
      return;
    }
    String sdpDescription = origSdp.description;
    sdpDescription = preferCodec(sdpDescription, VIDEO_CODEC_VP9, false);
    final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);
    mLocalSDP = sdp;
    if (!isError) {
      Log.d(TAG, "Set local SDP from " + sdp.type);
      mPeerConnection.setLocalDescription(this, sdp);
    }
  }

  void getStats() {
    if (mPeerConnection == null || isError) {
      return;
    }
    boolean success = mPeerConnection.getStats(new StatsObserver() {
      @Override public void onComplete(final StatsReport[] reports) {
        mObserver.onPeerConnectionStatsReady(reports, mLabel);
      }
    }, null);
    if (!success) {
      Log.e(TAG, "getStats() returns false!");
    }
  }

  public void enableStatsEvents(boolean enable, int periodMs) {
    if (enable) {
      try {
        statsTimer.schedule(new TimerTask() {
          @Override public void run() {
            mExecutor.execute(new Runnable() {
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

  @Override public void onSetSuccess() {
    if (isError) {
      return;
    }
    if (isInitiator) {
      // For offering peer connection we first create offer and set
      // local SDP, then after receiving answer set remote SDP.
      if (mPeerConnection.getRemoteDescription() == null) {
        // We've just set our local SDP so time to send it.
        Log.d(TAG, "Local SDP set succesfully");
        mObserver.onLocalDescription(mLocalSDP, mLabel);
      } else {
        // We've just set remote description, so drain remote
        // and send local ICE candidates.
        Log.d(TAG, "Remote SDP set succesfully");
        drainCandidates();
      }
    } else {
      // For answering peer connection we set remote SDP and then
      // create answer and set local SDP.
      if (mPeerConnection.getLocalDescription() != null) {
        // We've just set our local SDP so time to send it, drain
        // remote and send local ICE candidates.
        Log.d(TAG, "Local SDP set succesfully");
        mObserver.onLocalDescription(mLocalSDP, mLabel);
        drainCandidates();
      } else {
        // We've just set remote SDP - do nothing for now -
        // answer will be created soon.
        Log.d(TAG, "Remote SDP set succesfully");
      }
    }
  }

  @Override public void onCreateFailure(String s) {
    Log.e(TAG, "sdp CreateFailure" + s);
    mObserver.onPeerConnectionError(s, mLabel);
  }

  @Override public void onSetFailure(String s) {
    Log.e(TAG, "sdp SetFailure:" + s);
    mObserver.onPeerConnectionError(s, mLabel);
  }
  //(end)------------------------------SdpObserver-------------------------------

  public MediaConstraints createSDPConstrains(boolean useAudio, boolean useVideo) {
    MediaConstraints constraints = new MediaConstraints();
    constraints.mandatory.add(
        new MediaConstraints.KeyValuePair("OfferToReceiveAudio", useAudio ? "true" : "false"));
    constraints.mandatory.add(
        new MediaConstraints.KeyValuePair("OfferToReceiveVideo", useVideo ? "true" : "false"));
    return constraints;
  }

  public void createOffer(MediaConstraints constraints) {
    if (!isError) {
      mExecutor.execute(() -> {
        Log.d(TAG, "PC Create OFFER");
        mPeerConnection.createOffer(this, constraints);
      });
    }
  }

  public void createAnswer(MediaConstraints constraints) {
    mExecutor.execute(() -> mPeerConnection.createAnswer(this, constraints));
  }

  public void addRemoteIceCandidate(final IceCandidate candidate) {
    if (isError) {
      if (mRemoteCandidates != null) {
        mRemoteCandidates.add(candidate);
      } else {
        mPeerConnection.addIceCandidate(candidate);
      }
    }
  }

  public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
    if (isError) {
      return;
    }
    // Drain the queued remote candidates if there is any so that
    // they are processed in the proper order.
    drainCandidates();
    mPeerConnection.removeIceCandidates(candidates);
  }

  public void setRemoteDescription(final SessionDescription sdp) {
    if (isError) {
      return;
    }
    String sdpDescription = sdp.description;
    sdpDescription = preferCodec(sdpDescription, VIDEO_CODEC_VP9, false);
    Log.d(TAG, "Set remote SDP.");
    SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
    mPeerConnection.setRemoteDescription(this, sdpRemote);
  }

  public void close() {
    statsTimer.cancel();
    mDCManager.close();
    if (mPeerConnection != null) mPeerConnection.dispose();
    mMediaManager.close();
    pcFactory.close(mLabel);
  }

  public static void closeAll(){
    pcFactory.closeAll();
  }

  void drainCandidates() {
    if (mRemoteCandidates != null) {
      Log.d(TAG, "Add " + mRemoteCandidates.size() + " remote candidates");
      for (IceCandidate candidate : mRemoteCandidates) {
        mPeerConnection.addIceCandidate(candidate);
      }
      mRemoteCandidates = null;
    }
  }

  static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
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

  public interface Observer {
    /**
     * Callback fired once local SDP is created and set.
     */
    void onLocalDescription(final SessionDescription sdp, int label);

    /**
     * Callback fired once local Ice candidate is generated.
     */
    void onIceCandidate(final IceCandidate candidate, int label);

    /**
     * Callback fired once local ICE candidates are removed.
     */
    void onIceCandidatesRemoved(final IceCandidate[] candidates, int label);

    /**
     * Callback fired once connection is established (IceConnectionState is
     * CONNECTED).
     */
    void onIceConnected(int label);

    /**
     * Callback fired once connection is closed (IceConnectionState is
     * DISCONNECTED).
     */
    void onIceDisconnected(int label);

    /**
     * Callback fired once peer connection is closed.
     */
    void onPeerConnectionClosed(int label);

    /**
     * Callback fired once peer connection statistics is ready.
     */
    void onPeerConnectionStatsReady(final StatsReport[] reports, int label);

    /**
     * Callback fired once peer connection error happened.
     */
    void onPeerConnectionError(final String description, int label);
  }
}
