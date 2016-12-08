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

import org.appspot.apprtc.RoomParametersFetcher.RoomParametersFetcherEvents;
import org.appspot.apprtc.WebSocketChannelClient.WebSocketChannelEvents;
import org.appspot.apprtc.WebSocketChannelClient.WebSocketConnectionState;
import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.AsyncHttpURLConnection.AsyncHttpEvents;
import org.appspot.apprtc.util.LooperExecutor;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Negotiates signaling for chatting with https://appr.tc "rooms".用于和服务器交换信令
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can（SDP是会话描述协议的缩写）
 * be sent after WebSocket connection is established.
 *
 * 创建一个这个对象的实例（传入一个消息处理器），然后调用connectToRoom()方法。
 * 一旦房间连接建立带有房间参数的onConnectedToRoom（）回调就会回调。
 * 当webSocket连接建立时就会把携带本地Ice candidates和SDP应答的消息发送给另一个连接者
 */
public class WebSocketRTCClient implements AppRTCClient, WebSocketChannelEvents {
  private static final String TAG = "WSRTCClient";
  private static final String ROOM_JOIN = "join";
  private static final String ROOM_MESSAGE = "message";
  private static final String ROOM_LEAVE = "leave";

  //连接的状态
  private enum ConnectionState {
    NEW, CONNECTED, CLOSED, ERROR
  }

  //消息的类型
  private enum MessageType {
    MESSAGE, LEAVE
  }

  private final LooperExecutor executor;
  private boolean initiator;
  //信令服务的回调
  private SignalingEvents events;
  private WebSocketChannelClient wsClient;//WebSocket的客户端
  private ConnectionState roomState;
  private RoomConnectionParameters connectionParameters;
  private String messageUrl;
  private String leaveUrl;

  public WebSocketRTCClient(SignalingEvents events, LooperExecutor executor) {
    this.events = events;
    this.executor = executor;
    roomState = ConnectionState.NEW;
    executor.requestStart();//开启消息处理器
  }

  // --------------------------------------------------------------------
  // AppRTCClient interface implementation.
  // Asynchronously connect to an AppRTC room URL using supplied connection
  // parameters, retrieves room parameters and connect to WebSocket server.
  // AppRTCClient的一种实现
  // 使用支持的方式异步连接到AppRTC房间url
  // 参数，检索房间参数并且连接到WebSocket服务器。
  @Override public void connectToRoom(RoomConnectionParameters connectionParameters) {
    this.connectionParameters = connectionParameters;
    executor.execute(new Runnable() {
      @Override public void run() {
        connectToRoomInternal();
      }
    });
  }

  @Override public void disconnectFromRoom() {
    executor.execute(new Runnable() {
      @Override public void run() {
        disconnectFromRoomInternal();
      }
    });
    executor.requestStop();
  }

  // Connects to room - function runs on a local looper thread.
  // 连接到房间 此方法运行在本地looper线程
  private void connectToRoomInternal() {
    String connectionUrl = getConnectionUrl(connectionParameters);
    Log.d(TAG, "Connect to room: " + connectionUrl);
    //修改房间的状态
    roomState = ConnectionState.NEW;
    //初始化WebSocket通道客户端
    wsClient = new WebSocketChannelClient(executor, this);

    //初始化通过信令服务器建立连接的回调
    RoomParametersFetcherEvents callbacks = new RoomParametersFetcherEvents() {

      @Override public void onSignalingParametersReady(final SignalingParameters params) {
        WebSocketRTCClient.this.executor.execute(new Runnable() {
          @Override public void run() {
            WebSocketRTCClient.this.signalingParametersReady(params);
          }
        });
      }

      @Override public void onSignalingParametersError(String description) {
        WebSocketRTCClient.this.reportError(description);
      }
    };

    new RoomParametersFetcher(connectionUrl, null, callbacks).makeRequest();
  }

  // Disconnect from room and send bye messages - runs on a local looper thread.
  private void disconnectFromRoomInternal() {
    Log.d(TAG, "Disconnect. Room state: " + roomState);
    if (roomState == ConnectionState.CONNECTED) {
      Log.d(TAG, "Closing room.");
      sendPostMessage(MessageType.LEAVE, leaveUrl, null);
    }
    roomState = ConnectionState.CLOSED;
    if (wsClient != null) {
      wsClient.disconnect(true);
    }
  }

  // Helper functions to get connection, post message and leave message URLs
  // 将房间参数对象转换成建立连接，传递信息，和留言的地址
  private String getConnectionUrl(RoomConnectionParameters connectionParameters) {
    //https://appr.tc/join/456041265
    return connectionParameters.roomUrl + "/" + ROOM_JOIN + "/" + connectionParameters.roomId;
  }

  //获取消息的url地址   https://appr.tc/message/456041265/18946313
  private String getMessageUrl(RoomConnectionParameters connectionParameters,
      SignalingParameters signalingParameters) {
    return connectionParameters.roomUrl
        + "/"
        + ROOM_MESSAGE
        + "/"
        + connectionParameters.roomId
        + "/"
        + signalingParameters.clientId;
  }

  //离开房间的URL https://appr.tc/leave/456041265/18946313
  private String getLeaveUrl(RoomConnectionParameters connectionParameters,
      SignalingParameters signalingParameters) {
    return connectionParameters.roomUrl
        + "/"
        + ROOM_LEAVE
        + "/"
        + connectionParameters.roomId
        + "/"
        + signalingParameters.clientId;
  }

  // Callback issued when room parameters are extracted. Runs on local
  // looper thread.
  //信令服务器返回成功时的回调，运行在looper线程
  private void signalingParametersReady(final SignalingParameters signalingParameters) {
    Log.d(TAG, "Room connection completed.");
    //在和自己通讯的前提下，信令服务器返回的SDP消息不为空||信令的初始化为false
    if (connectionParameters.loopback && (!signalingParameters.initiator
        || signalingParameters.offerSdp != null)) {
      reportError("Loopback room is busy.");
      return;
    }
    if (!connectionParameters.loopback //不是和自己通讯
        && !signalingParameters.initiator  //信令服务器已经ok
        && signalingParameters.offerSdp == null) {//信令服务器没有返回sdp信息
      Log.w(TAG, "No offer SDP in room response.");
    }
    initiator = signalingParameters.initiator;
    messageUrl = getMessageUrl(connectionParameters, signalingParameters);//消息通讯的url
    leaveUrl = getLeaveUrl(connectionParameters, signalingParameters);//离开房间的url
    Log.d(TAG, "Message URL: " + messageUrl);
    Log.d(TAG, "Leave URL: " + leaveUrl);
    //修改房间的状态
    roomState = ConnectionState.CONNECTED;

    // Fire connection and signaling parameters events.
    // 回调
    events.onConnectedToRoom(signalingParameters);

    // Connect and register WebSocket client. 注册并建立WebSocket客户端的连接
    wsClient.connect(signalingParameters.wssUrl, signalingParameters.wssPostUrl);
    wsClient.register(connectionParameters.roomId, signalingParameters.clientId);
  }

  // Send local offer SDP to the other participant.
  @Override public void sendOfferSdp(final SessionDescription sdp) {
    executor.execute(new Runnable() {
      @Override public void run() {
        if (roomState != ConnectionState.CONNECTED) {
          reportError("Sending offer SDP in non connected state.");
          return;
        }
        JSONObject json = new JSONObject();
        jsonPut(json, "sdp", sdp.description);
        jsonPut(json, "type", "offer");
        sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
        if (connectionParameters.loopback) {
          // In loopback mode rename this offer to answer and route it back.
          SessionDescription sdpAnswer =
              new SessionDescription(SessionDescription.Type.fromCanonicalForm("answer"),
                  sdp.description);
          events.onRemoteDescription(sdpAnswer);
        }
      }
    });
  }

  // Send local answer SDP to the other participant.
  @Override public void sendAnswerSdp(final SessionDescription sdp) {
    executor.execute(new Runnable() {
      @Override public void run() {
        if (connectionParameters.loopback) {
          Log.e(TAG, "Sending answer in loopback mode.");
          return;
        }
        JSONObject json = new JSONObject();
        jsonPut(json, "sdp", sdp.description);
        jsonPut(json, "type", "answer");
        wsClient.send(json.toString());
      }
    });
  }

  // Send Ice candidate to the other participant.
  // 将Ice候选发给另一个参与者
  @Override public void sendLocalIceCandidate(final IceCandidate candidate) {
    executor.execute(new Runnable() {
      @Override public void run() {
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "candidate");
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        if (initiator) {
          // Call initiator sends ice candidates to GAE server.
          if (roomState != ConnectionState.CONNECTED) {
            reportError("Sending ICE candidate in non connected state.");
            return;
          }
          sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
          if (connectionParameters.loopback) {//和自己通讯
            events.onRemoteIceCandidate(candidate);
          }
        } else {
          // Call receiver sends ice candidates to websocket server.
          wsClient.send(json.toString());
        }
      }
    });
  }

  // Send removed Ice candidates to the other participant.
  @Override public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
    executor.execute(new Runnable() {
      @Override public void run() {
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "remove-candidates");
        JSONArray jsonArray = new JSONArray();
        for (final IceCandidate candidate : candidates) {
          jsonArray.put(toJsonCandidate(candidate));
        }
        jsonPut(json, "candidates", jsonArray);
        if (initiator) {
          // Call initiator sends ice candidates to GAE server.
          if (roomState != ConnectionState.CONNECTED) {
            reportError("Sending ICE candidate removals in non connected state.");
            return;
          }
          sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
          if (connectionParameters.loopback) {
            events.onRemoteIceCandidatesRemoved(candidates);
          }
        } else {
          // Call receiver sends ice candidates to websocket server.
          wsClient.send(json.toString());
        }
      }
    });
  }

  // --------------------------------------------------------------------
  // WebSocketChannelEvents interface implementation.
  // All events are called by WebSocketChannelClient on a local looper thread
  // (passed to WebSocket client constructor).
  @Override public void onWebSocketMessage(final String msg) {
    if (wsClient.getState() != WebSocketConnectionState.REGISTERED) {
      Log.e(TAG, "Got WebSocket message in non registered state.");
      return;
    }
    try {
      JSONObject json = new JSONObject(msg);
      String msgText = json.getString("msg");
      String errorText = json.optString("error");
      if (msgText.length() > 0) {
        json = new JSONObject(msgText);
        String type = json.optString("type");
        if (type.equals("candidate")) {
          events.onRemoteIceCandidate(toJavaCandidate(json));
        } else if (type.equals("remove-candidates")) {
          JSONArray candidateArray = json.getJSONArray("candidates");
          IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
          for (int i = 0; i < candidateArray.length(); ++i) {
            candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
          }
          events.onRemoteIceCandidatesRemoved(candidates);
        } else if (type.equals("answer")) {
          if (initiator) {
            SessionDescription sdp =
                new SessionDescription(SessionDescription.Type.fromCanonicalForm(type),
                    json.getString("sdp"));
            events.onRemoteDescription(sdp);
          } else {
            reportError("Received answer for call initiator: " + msg);
          }
        } else if (type.equals("offer")) {
          if (!initiator) {
            SessionDescription sdp =
                new SessionDescription(SessionDescription.Type.fromCanonicalForm(type),
                    json.getString("sdp"));
            events.onRemoteDescription(sdp);
          } else {
            reportError("Received offer for call receiver: " + msg);
          }
        } else if (type.equals("bye")) {
          events.onChannelClose();
        } else {
          reportError("Unexpected WebSocket message: " + msg);
        }
      } else {
        if (errorText != null && errorText.length() > 0) {
          reportError("WebSocket error message: " + errorText);
        } else {
          reportError("Unexpected WebSocket message: " + msg);
        }
      }
    } catch (JSONException e) {
      reportError("WebSocket message JSON parsing error: " + e.toString());
    }
  }

  @Override public void onWebSocketClose() {
    events.onChannelClose();
  }

  @Override public void onWebSocketError(String description) {
    reportError("WebSocket error: " + description);
  }

  // --------------------------------------------------------------------
  // Helper functions.
  private void reportError(final String errorMessage) {
    Log.e(TAG, errorMessage);
    executor.execute(new Runnable() {
      @Override public void run() {
        if (roomState != ConnectionState.ERROR) {
          roomState = ConnectionState.ERROR;
          events.onChannelError(errorMessage);
        }
      }
    });
  }

  // Put a |key|->|value| mapping in |json|.
  private static void jsonPut(JSONObject json, String key, Object value) {
    try {
      json.put(key, value);
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  // Send SDP or ICE candidate to a room server.
  private void sendPostMessage(final MessageType messageType, final String url,
      final String message) {
    String logInfo = url;
    if (message != null) {
      logInfo += ". Message: " + message;
    }
    Log.d(TAG, "C->GAE: " + logInfo);
    AsyncHttpURLConnection httpConnection =
        new AsyncHttpURLConnection("POST", url, message, new AsyncHttpEvents() {
          @Override public void onHttpError(String errorMessage) {
            reportError("GAE POST error: " + errorMessage);
          }

          @Override public void onHttpComplete(String response) {
            if (messageType == MessageType.MESSAGE) {
              try {
                JSONObject roomJson = new JSONObject(response);
                String result = roomJson.getString("result");
                if (!result.equals("SUCCESS")) {
                  reportError("GAE POST error: " + result);
                }
              } catch (JSONException e) {
                reportError("GAE POST JSON error: " + e.toString());
              }
            }
          }
        });
    httpConnection.send();
  }

  // Converts a Java candidate to a JSONObject.
  private JSONObject toJsonCandidate(final IceCandidate candidate) {
    JSONObject json = new JSONObject();
    jsonPut(json, "label", candidate.sdpMLineIndex);
    jsonPut(json, "id", candidate.sdpMid);
    jsonPut(json, "candidate", candidate.sdp);
    return json;
  }

  // Converts a JSON candidate to a Java object.
  IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
    return new IceCandidate(json.getString("id"), json.getInt("label"),
        json.getString("candidate"));
  }
}
