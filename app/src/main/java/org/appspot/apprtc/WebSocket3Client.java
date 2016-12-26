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

/**
 * @author leon.tan on 2016/12/13.
 */

public final class WebSocket3Client implements AppRTCClient {

  ConnectionState mConnectionState;
  final SignalingEvents mEvents;
  final LooperExecutor mExecutor;
  final String TAG = "WebSocket2Client";
  private Socket mSocket;
  private boolean mIsVideo;
  private boolean misAudio;

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

        SignalingParameters parameters = new SignalingParameters(
            // Ice servers are not needed for direct connections.
            new LinkedList<PeerConnection.IceServer>(), false,
            // Server side acts as the initiator on direct connections.
            null, // clientId
            null, // wssUrl
            null, // wwsPostUrl
            null, // offerSdp
            null // iceCandidates
        );
        mEvents.onConnectedToRoom(parameters);
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
        reportError(args.toString());
      });
      mSocket.connect();
    } catch (URISyntaxException e) {
      Log.i(TAG, "WebSocketListener --> URISyntaxException");
      mConnectionState = ConnectionState.ERROR;
      reportError(e.toString());
    }
  }

  @Override public void sendOfferSdp(SessionDescription sdp) {
    Log.i(TAG, "sendOfferSdp: " + sdp.type);
    if (mConnectionState != ConnectionState.CONNECTED) {
      reportError("sending sdp offer in non connected");
      return;
    }

    JSONObject top = new JSONObject();
    jsonPut(top, "type", sdp.type == SessionDescription.Type.OFFER ? SDP_OFFER : SDP_ANSWER);
    final JSONObject object = new JSONObject();
    jsonPut(object, "sdp", sdp.description);
    jsonPut(object, "type", sdp.type);
    jsonPut(object, "peer", "10010");
    jsonPut(top, "payload", object);

    //final JSONObject object = new JSONObject();
    //jsonPut(object, "sdp", sdp.description);
    //jsonPut(object, "type", sdp.type);
    String jsonString = top.toString();
    sendMessage(jsonString);
  }

  @Override public void sendAnswerSdp(SessionDescription sdp) {
    Log.i(TAG, "sendAnswerSdp: " + sdp.type);
    if (mConnectionState != ConnectionState.CONNECTED) {
      reportError("sending sdp answer in non connected");
      return;
    }
    JSONObject top = new JSONObject();
    jsonPut(top, "type", sdp.type == SessionDescription.Type.OFFER ? SDP_OFFER : SDP_ANSWER);
    final JSONObject sdpJson = new JSONObject();
    jsonPut(sdpJson, "sdp", sdp.description);
    jsonPut(sdpJson, "type", "answer");

    JSONObject payload = new JSONObject();
    jsonPut(payload, "sdp", sdpJson);
    jsonPut(payload, "peer", "10010");
    jsonPut(top, "payload", payload);
    String jsonString = top.toString();
    sendMessage(jsonString);
  }

  @Override public void sendLocalIceCandidate(IceCandidate candidate) {
    Log.i(TAG, "sendLocalIceCandidate: ");
    if (mConnectionState != ConnectionState.CONNECTED) {
      reportError("sending local ice in non connected");
      return;
    }
    final JSONObject json = new JSONObject();
    jsonPut(json, "type", "candidate");
    jsonPut(json, "label", candidate.sdpMLineIndex);
    jsonPut(json, "id", candidate.sdpMid);
    jsonPut(json, "candidate", candidate.sdp);
    String jsonString = json.toString();
    sendMessage(jsonString);
  }

  @Override public void sendLocalIceCandidateRemovals(IceCandidate[] candidates) {
    Log.i(TAG, "sendLocalIceCandidateRemovals: " + candidates.length);
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
    sendMessage(jsonString);
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
        case "ICE_CANDIDATE": {
          Log.d(TAG, "webSocket return :candidate");
          if (payload != null) mEvents.onRemoteIceCandidate(toJavaCandidate(payload));
          break;
        }
        case "remove-candidate": {
          Log.d(TAG, "webSocket return :remove-candidate:\n" + text);
          if (payload == null) return;
          JSONArray array = payload.getJSONArray("remove-candidates");
          IceCandidate[] candidates = new IceCandidate[array.length()];
          for (int i = 0; i < array.length(); i++) {
            candidates[i] = toJavaCandidate(array.getJSONObject(i));
          }
          mEvents.onRemoteIceCandidatesRemoved(candidates);
          break;
        }
        case "SESSION_DESCRIPTION_ANSWER": {
          Log.d(TAG, "webSocket return :answer");
          //SessionDescription sdp =
          //    new SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"));
          //mEvents.onRemoteDescription(sdp ,mRemoteMediaConstrains);
          //reportError("received answer for call initiator " + msg);
          break;
        }
        case "SESSION_DESCRIPTION_OFFER": {
          Log.d(TAG, "webSocket return :offer\n" + text);
          if (payload == null) return;
          String string = payload.getJSONObject("sdp").getString("sdp");
          SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, string);
          mEvents.onRemoteDescription(sdp, mIsVideo, misAudio);
          break;
        }
        case "bye": {
          Log.d(TAG, "webSocket return :bye");
          mEvents.onChannelClose();
          break;
        }
        case "CREATED": {
          Log.d(TAG, "webSocket return :CREATED \n" + text);
          break;
        }
        case ROOM_JOIN: {
          //exchange media info
          Log.i(TAG, "dealWebSocketMessage: room join");
          String mediaInfo =
              "{\"type\":\"MEDIA_INFO\", \"payload\":{\"media\":{\"video\": true, \"audio\":true} } }";
          mSocket.send(mediaInfo);
          mEvents.onConnectedToRoom(createSignalingParameters());
          break;
        }
        case MEDIA_INFO: {
          //{"type":"MEDIA_INFO","payload":{"media":{"video":false,"audio":true}}}
          JSONObject media = payload.getJSONObject("media");
          mIsVideo = media.getBoolean("video");
          misAudio = media.getBoolean("audio");
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

  IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
    return new IceCandidate(json.getString("id"), json.getInt("label"),
        json.getString("candidate"));
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

  SignalingParameters createSignalingParameters() {
    LinkedList<PeerConnection.IceServer> turnServers = new LinkedList<PeerConnection.IceServer>();
    String username = "28224511:1379330808";
    String credential = "JZEOEt2V3Qb0y27GRntt2u2PAYA=";
    String turnUrl = "turn:192.158.29.39:3478?transport=tcp";
    turnServers.add(new PeerConnection.IceServer(turnUrl, username, credential));
    turnServers.add(new PeerConnection.IceServer(turnUrl, username, credential));
    turnServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302", "", ""));

    SignalingParameters parameters = new SignalingParameters(
        // Ice servers are not needed for direct connections.
        turnServers,
        false, // Server side acts as the initiator on direct connections.
        null, // clientId
        null, // wssUrl
        null, // wwsPostUrl
        null, // offerSdp
        null // iceCandidates
    );
    return parameters;
  }

  MediaConstraints createRemoteMediaConstrains(boolean video, boolean audio) {
    MediaConstraints mediaConstraints = new MediaConstraints();
    mediaConstraints.mandatory.add(
        new MediaConstraints.KeyValuePair("OfferToReceiveAudio", audio ? "true" : "false"));
    //设备存在可以使用的摄像头，或者和自己通讯
    mediaConstraints.mandatory.add(
        new MediaConstraints.KeyValuePair("OfferToReceiveVideo", video ? "true" : "false"));
    //mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    return mediaConstraints;
  }
}
