package org.appspot.apprtc;

import android.text.TextUtils;
import android.util.Log;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.appspot.apprtc.util.LooperExecutor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * @author leon.tan on 2016/12/13.
 */

public final class WebSocket2Client extends WebSocketListener implements AppRTCClient {

  ConnectionState mConnectionState;
  final SignalingEvents mEvents;
  final LooperExecutor mExecutor;
  final String TAG = "WebSocket2Client";
  private WebSocket mWebSocket;

  public WebSocket2Client(SignalingEvents events, LooperExecutor executor) {
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
    OkHttpClient client =
        new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build();
    //add check
    String roomUrl = connectionParameters.roomId;
    //if (!roomUrl.startsWith("ws://")) {
    //  throw new IllegalArgumentException("not a webSocket address");
    //}
    Request request = new Request.Builder()
        .url(roomUrl)
        //.addHeader("Origin", "http://stackexchange.com")
        .build();
    mWebSocket = client.newWebSocket(request, this);

    client.dispatcher().executorService().shutdown();
  }

  @Override public void sendOfferSdp(SessionDescription sdp) {
    Log.i(TAG, "sendOfferSdp: " + sdp.type);
    if (mConnectionState != ConnectionState.CONNECTED) {
      reportError("sending sdp offer in non connected");
      return;
    }
    final JSONObject object = new JSONObject();
    jsonPut(object, "sdp", sdp.description);
    jsonPut(object, "type", sdp.type);
    String jsonString = object.toString();
    Log.d(TAG, "C -> WS: " + jsonString);
    mExecutor.execute(() -> mWebSocket.send(jsonString));
  }

  @Override public void sendAnswerSdp(SessionDescription sdp) {
    Log.i(TAG, "sendAnswerSdp: " + sdp.type);
    if (mConnectionState != ConnectionState.CONNECTED) {
      reportError("sending sdp answer in non connected");
      return;
    }
    final JSONObject object = new JSONObject();
    jsonPut(object, "sdp", sdp.description);
    jsonPut(object, "type", sdp.type);
    String jsonString = object.toString();
    Log.d(TAG, "C -> WS: " + jsonString);
    mExecutor.execute(() -> mWebSocket.send(jsonString));
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
    Log.d(TAG, "C -> WS: " + jsonString);
    mExecutor.execute(() -> mWebSocket.send(jsonString));
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
    Log.d(TAG, "C -> WS: " + jsonString);
    mExecutor.execute(() -> mWebSocket.send(jsonString));
  }

  @Override public void disconnectFromRoom() {
    Log.i(TAG, "disconnectFromRoom");
    mConnectionState = ConnectionState.CLOSED;
    mWebSocket.close(1000, "bye");
  }

  //------------------------AppRTCClient implement(end)---------------------------------

  //------------------------WebSocketListener implement(start)----------------------------
  @Override public void onOpen(WebSocket webSocket, Response response) {
    Log.i(TAG, "WebSocketListener --> onOpen: ");
    mConnectionState = ConnectionState.CONNECTED;
    //webSocket.send("Hello...");
    //webSocket.send("...World!");
    //webSocket.send(ByteString.decodeHex("deadbeef"));
    //webSocket.close(1000, "Goodbye, World!");
  }

  @Override public void onMessage(WebSocket webSocket, String text) {
    Log.i(TAG, "WebSocketListener --> onMessage: ");
    dealWebSocketMessage(text);
  }

  @Override public void onMessage(WebSocket webSocket, ByteString bytes) {
    Log.i(TAG, "WebSocketListener --> onMessage: ");
  }

  @Override public void onClosing(WebSocket webSocket, int code, String reason) {
    Log.i(TAG, "WebSocketListener --> onClosing, code: " + code + ", reason: " + reason);
    mConnectionState = ConnectionState.CLOSED;
    webSocket.close(1000, null);
  }

  @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
    Log.i(TAG, "WebSocketListener --> onFailure, reason: " + response);
    mConnectionState = ConnectionState.ERROR;
    reportError(t.toString());
    t.printStackTrace();
  }
  //------------------------WebSocketListener implement(end)----------------------------

  private enum ConnectionState {
    NEW, CONNECTED, CLOSED, ERROR
  }

  void dealWebSocketMessage(String text) {
    Log.i(TAG, "WS -> C: " + text);
    // TODO: 2016/12/13
    try {
      JSONObject json = new JSONObject(text);
      String msg = json.getString("msg");
      String error = json.optString("error");
      switch (msg) {
        case "candidate": {
          Log.d(TAG, "webSocket return :candidate");
          mEvents.onRemoteIceCandidate(toJavaCandidate(json));
          break;
        }
        case "remove-candidate": {
          Log.d(TAG, "webSocket return :remove-candidate");
          JSONArray array = json.getJSONArray("remove-candidates");
          IceCandidate[] candidates = new IceCandidate[array.length()];
          for (int i = 0; i < array.length(); i++) {
            candidates[i] = toJavaCandidate(array.getJSONObject(i));
          }
          mEvents.onRemoteIceCandidatesRemoved(candidates);
          break;
        }
        case "answer": {
          Log.d(TAG, "webSocket return :answer");
          //SessionDescription sdp =
          //    new SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"));
          //mEvents.onRemoteDescription(sdp);
          reportError("received answer for call initiator " + msg);
          break;
        }
        case "offer": {
          Log.d(TAG, "webSocket return :offer");
          SessionDescription sdp =
              new SessionDescription(SessionDescription.Type.OFFER, json.getString("sdp"));
          mEvents.onRemoteDescription(sdp , null);
          break;
        }
        case "bye": {
          Log.d(TAG, "webSocket return :bye");
          mEvents.onChannelClose();
          break;
        }
        default: {
          if (TextUtils.isEmpty(error)) {
            reportError("WebSocket error message: " + error);
          } else {
            reportError("Unexpected WebSocket message: " + msg);
          }
          break;
        }
      }
    } catch (JSONException e) {
      reportError("webSocket message JSON parsing error: " + e.toString());
    }
  }

  IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
    return new IceCandidate(json.getString("id"), json.getInt("label"), json.getString("candidate"));
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
}
