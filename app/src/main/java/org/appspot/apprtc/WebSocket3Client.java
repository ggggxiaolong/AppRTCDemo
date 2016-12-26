package org.appspot.apprtc;

import android.text.TextUtils;
import android.util.Log;
import io.socket.client.IO;
import io.socket.client.Socket;
import java.net.URISyntaxException;
import java.util.LinkedList;
import org.appspot.apprtc.util.LooperExecutor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import static org.appspot.apprtc.AppRTCClient.SignalingEvents.TYPE_DC;
import static org.appspot.apprtc.AppRTCClient.SignalingEvents.TYPE_MS;

/**
 * @author leon.tan on 2016/12/13.
 */

public final class WebSocket3Client implements AppRTCClient {

  ConnectionState mConnectionState;
  final SignalingEvents mEvents;
  final LooperExecutor mExecutor;
  final String TAG = "WebSocketClient";
  private Socket mSocket;
  private boolean mIsVideo;
  private boolean misAudio;
  PCInfo mDcPcInfo, mMediaPcInfo;

  public WebSocket3Client(SignalingEvents events, LooperExecutor executor) {
    this.mEvents = events;
    this.mExecutor = executor;
    mConnectionState = ConnectionState.NEW;
    executor.requestStart();
  }

  //------------------------AppRTCClient implement(start)---------------------------------

  /**
   * 与webSocket建立连接
   *
   * @param connectionParameters 连接参数
   */
  @Override public void connectToRoom(RoomConnectionParameters connectionParameters) {
    //add check
    String roomUrl = connectionParameters.roomId;
    try {
      mSocket = IO.socket(roomUrl);
      mSocket.on(Socket.EVENT_CONNECT, args -> {
        Log.i(TAG, "WebSocketListener --> onOpen: ");
        mConnectionState = ConnectionState.CONNECTED;
        mSocket.send("{\"roomID\":10010, \"type\":\"CREATE_OR_JOIN\"}");
      }).on(Socket.EVENT_MESSAGE, args -> {
        String text = (String) args[0];
        Log.i(TAG, "WebSocketListener --> onMessage: " + text);
        dealWebSocketMessage(text);
      }).on(Socket.EVENT_DISCONNECT, args -> {
        Log.i(TAG, "disconnectFromRoom");
        mConnectionState = ConnectionState.CLOSED;
      }).on(Socket.EVENT_CONNECT_ERROR, args -> {
        Log.i(TAG, "WebSocketListener --> onFailure, reason: ");
        mConnectionState = ConnectionState.ERROR;
        reportError(args[0].toString());
      });
      mSocket.connect();
    } catch (URISyntaxException e) {
      Log.i(TAG, "WebSocketListener --> URISyntaxException");
      mConnectionState = ConnectionState.ERROR;
      reportError(e.toString());
    }
  }

  @Override public void sendOfferSdp(SessionDescription sdp, int label) {
    Log.i(TAG, "sendOfferSdp: " + sdp.type);
    if (mConnectionState != ConnectionState.CONNECTED) {
      reportError("sending sdp offer in non connected");
      return;
    }
    sendSdp(sdp, label);
  }

  @Override public void sendAnswerSdp(SessionDescription sdp, int label) {
    Log.i(TAG, "sendAnswerSdp: " + sdp.type);
    if (mConnectionState != ConnectionState.CONNECTED) {
      reportError("sending sdp answer in non connected");
      return;
    }
    sendSdp(sdp, label);
  }

  private void sendSdp(SessionDescription sdp, int label) {
    JSONObject top = new JSONObject();
    jsonPut(top, "type", sdp.type == SessionDescription.Type.OFFER ? SDP_OFFER : SDP_ANSWER);
    final JSONObject sdpJson = new JSONObject();
    jsonPut(sdpJson, "sdp", sdp.description);
    jsonPut(sdpJson, "type", sdp.type.name().toLowerCase());

    JSONObject payload = new JSONObject();
    jsonPut(payload, "sdp", sdpJson);
    get(label).restore(payload);
    jsonPut(top, "payload", payload);
    String jsonString = top.toString();
    sendMessage(jsonString);
  }

  @Override public void sendLocalIceCandidate(IceCandidate candidate, int label) {
    Log.i(TAG, "sendLocalIceCandidate: ");
    if (mConnectionState != ConnectionState.CONNECTED) {
      reportError("sending local ice in non connected");
      return;
    }
    final JSONObject top = new JSONObject();
    jsonPut(top, "type", ICE_CANDIDATE);

    final JSONObject ice = new JSONObject();
    jsonPut(ice, "sdpMLineIndex", candidate.sdpMLineIndex);
    jsonPut(ice, "sdpMid", candidate.sdpMid);
    jsonPut(ice, "candidate", candidate.sdp);

    final JSONObject payload = new JSONObject();
    jsonPut(payload, "candidate", ice);
    get(label).restore(payload);

    jsonPut(top, "payload", payload);
    String jsonString = top.toString();
    sendMessage(jsonString);
  }

  @Override public void sendLocalIceCandidateRemovals(IceCandidate[] candidates, int label) {
    /*Log.i(TAG, "sendLocalIceCandidateRemovals: " + candidates.length);
    if (mConnectionState != ConnectionState.CONNECTED) {
      reportError("sending remove local ices in non connected");
      return;
    }
    final JSONObject json = new JSONObject();
    jsonPut(json, "type", "remove-candidates");
    JSONArray jsonArray = new JSONArray();
    for (IceCandidate candidate : candidates) {
      jsonArray.put(toJsonCandidate(candidate));
    }
    jsonPut(json, "candidates", jsonArray);
    String jsonString = json.toString();
    sendMessage(jsonString);*/
    //do nothing now
  }

  void sendMessage(String jsonString) {
    Log.d(TAG, "C -> WS: " + jsonString);
    mExecutor.execute(() -> mSocket.send(jsonString));
  }

  @Override public void disconnectFromRoom() {
    Log.i(TAG, "disconnectFromRoom");
    mConnectionState = ConnectionState.CLOSED;
    mSocket.close();
  }

  //------------------------AppRTCClient implement(end)---------------------------------

  private enum ConnectionState {
    NEW, CONNECTED, CLOSED, ERROR
  }

  void dealWebSocketMessage(String text) {
    Log.i(TAG, "WS -> C: " + text);
    try {
      JSONObject json = new JSONObject(text);
      String msg = json.getString("type");
      //String error = json.optString("error");
      JSONObject payload = json.optJSONObject("payload");
      switch (msg) {
        case ICE_CANDIDATE: {
          Log.d(TAG, "webSocket return :candidate");
          if (payload == null) return;
          JSONObject candidate = payload.getJSONObject("candidate");
          mEvents.onRemoteIceCandidate(toJavaCandidate(candidate), storePcInfo(new PCInfo(payload)));
          break;
        }
        case "remove-candidate": {
          Log.w(TAG, "webSocket return :remove-candidate:\n" + text);
          //if (payload == null) return;
          //JSONArray array = payload.getJSONArray("remove-candidates");
          //IceCandidate[] candidates = new IceCandidate[array.length()];
          //for (int i = 0; i < array.length(); i++) {
          //  candidates[i] = toJavaCandidate(array.getJSONObject(i));
          //}
          //mEvents.onRemoteIceCandidatesRemoved(candidates);
          break;
        }
        case SDP_ANSWER: {
          Log.d(TAG, "webSocket return :answer");
          //SessionDescription sdp =
          //    new SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"));
          //mEvents.onRemoteDescription(sdp ,mRemoteMediaConstrains);
          //reportError("received answer for call initiator " + msg);
          break;
        }
        case SDP_OFFER: {
          Log.d(TAG, "webSocket return :offer\n" + text);
          if (payload == null) return;
          String string = payload.getJSONObject("sdp").getString("sdp");
          SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, string);
          mEvents.onConnectedToRoom(createSignalingParameters(sdp, createSDPConstrains(misAudio, mIsVideo)), storePcInfo(new PCInfo(payload)));
          break;
        }
        case ROOM_CREATE: {
          Log.d(TAG, "webSocket return :CREATED \n" + text);
          break;
        }
        case ROOM_JOIN: {
          //exchange media info
          Log.i(TAG, "dealWebSocketMessage: room join");
          String mediaInfo =
              "{\"type\":\"MEDIA_INFO\", \"payload\":{\"media\":{\"video\": true, \"audio\":true} } }";
          mSocket.send(mediaInfo);
          break;
        }
        case MEDIA_INFO: {
          //{"type":"MEDIA_INFO","payload":{"media":{"video":false,"audio":true}}}
          JSONObject media = payload.getJSONObject("media");
          mIsVideo = media.getBoolean("video");
          misAudio = media.getBoolean("audio");
          break;
        }
        case ROOM_FULL:{
          mEvents.onChannelError("Room is full");
          break;
        }
        default: {
          //if (TextUtils.isEmpty(error)) {
          //  reportError("WebSocket error message: " + error);
          //} else {
          //reportError("Unexpected WebSocket message:\n" + text);
          Log.e(TAG, "dealWebSocketMessage: Unexpected WebSocket message:\n" + text);
          //}
          break;
        }
      }
    } catch (JSONException e) {
      reportError("webSocket message JSON parsing error: " + e.toString());
    }
  }

  int storePcInfo(PCInfo pcInfo) {
    int label;
    if (pcInfo.isData()){
      mDcPcInfo = pcInfo;
      label = TYPE_DC;
    } else {
      mMediaPcInfo = pcInfo;
      label = TYPE_MS;
    }
    return label;
  }

  PCInfo get(int label){
    if (label == TYPE_DC) return mDcPcInfo;
    else return mMediaPcInfo;
  }

  IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
    return new IceCandidate(json.getString("sdpMid"), json.getInt("sdpMLineIndex"),
        json.getString("candidate"));
  }

  MediaConstraints createSDPConstrains(boolean useAudio, boolean useVideo) {
    MediaConstraints constraints = new MediaConstraints();
    constraints.mandatory.add(
        new MediaConstraints.KeyValuePair("OfferToReceiveAudio", useAudio ? "true" : "false"));
    constraints.mandatory.add(
        new MediaConstraints.KeyValuePair("OfferToReceiveVideo", useVideo ? "true" : "false"));
    return constraints;
  }

  void reportError(final String msg) {
    Log.e(TAG, msg);
    if (mConnectionState != ConnectionState.ERROR) mConnectionState = ConnectionState.ERROR;
    mExecutor.execute(() -> mEvents.onChannelError(msg));
  }

  static void jsonPut(JSONObject json, String key, Object value) {
    try {
      json.put(key, value);
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  JSONObject toJsonCandidate(final IceCandidate candidate) {
    JSONObject object = new JSONObject();
    jsonPut(object, "label", candidate.sdpMLineIndex);
    jsonPut(object, "id", candidate.sdpMid);
    jsonPut(object, "candidate", candidate.sdp);
    return object;
  }

  SignalingParameters createSignalingParameters(SessionDescription sdp, MediaConstraints constraints) {
    LinkedList<PeerConnection.IceServer> turnServers = new LinkedList<PeerConnection.IceServer>();
    String username = "28224511:1379330808";
    String credential = "JZEOEt2V3Qb0y27GRntt2u2PAYA=";
    String turnUrl = "turn:192.158.29.39:3478?transport=tcp";
    turnServers.add(new PeerConnection.IceServer(turnUrl, username, credential));
    turnServers.add(new PeerConnection.IceServer(turnUrl, username, credential));
    turnServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302", "", ""));

    SignalingParameters parameters = new SignalingParameters(
        // Ice servers are not needed for direct connections.
        turnServers, false, // Server side acts as the initiator on direct connections.
        null, // clientId
        null, // wssUrl
        null, // wwsPostUrl
        sdp, // offerSdp
        null, // iceCandidates
        constraints
    );
    return parameters;
  }

  static class PCInfo {
    static final String TYPE_DC = "data";
    static final String TYPE_MEDIA = "media";
    String peer;
    String type;
    String label;
    String connectionID;

    PCInfo(JSONObject json) {
      try {
        peer = json.getString("peer");
        type = json.getString("type");
        label = json.getString("label");
        connectionID = json.getString("connectionID");
      } catch (JSONException e) {
        Log.e("PCINFO", "PCInfo create error: ", e);
      }
    }

    boolean isData(){
      return TYPE_DC.equals(type);
    }

    void restore(JSONObject json) {
      jsonPut(json, "peer", peer);
      jsonPut(json, "type", type);
      jsonPut(json, "label", label);
      jsonPut(json, "connectionID", connectionID);
    }
  }
}
