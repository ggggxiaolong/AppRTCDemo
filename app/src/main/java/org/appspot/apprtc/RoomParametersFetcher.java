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

import org.appspot.apprtc.AppRTCClient.SignalingParameters;
import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.AsyncHttpURLConnection.AsyncHttpEvents;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.Scanner;

/**
 * AsyncTask that converts an AppRTC room URL into the set of signaling
 * parameters to use with that room.
 * 异步任务 将AppRTC房间URL转换成信令的参数集合
 */
public class RoomParametersFetcher {
  private static final String TAG = "RoomRTCClient";
  private static final int TURN_HTTP_TIMEOUT_MS = 5000;
  private final RoomParametersFetcherEvents events;
  private final String roomUrl;
  private final String roomMessage;
  private AsyncHttpURLConnection httpConnection;

  /**
   * Room parameters fetcher callbacks.
   */
  public interface RoomParametersFetcherEvents {
    /**
     * Callback fired once the room's signaling parameters
     * SignalingParameters are extracted.
     * 当信令服务器返回成功时的回调（将返回值转换成SignalingParameters）
     */
    void onSignalingParametersReady(final SignalingParameters params);

    /**
     * Callback for room parameters extraction error.
     * 当信令服务器失败时的回调
     */
    void onSignalingParametersError(final String description);
  }

  public RoomParametersFetcher(String roomUrl, String roomMessage,
      final RoomParametersFetcherEvents events) {
    this.roomUrl = roomUrl;
    this.roomMessage = roomMessage;
    this.events = events;
  }

  public void makeRequest() {
    Log.d(TAG, "Connecting to room: " + roomUrl);//https://appr.tc/join/193423508 日志3：11
    httpConnection =
        new AsyncHttpURLConnection("POST", roomUrl, roomMessage, new AsyncHttpEvents() {
          @Override public void onHttpError(String errorMessage) {
            Log.e(TAG, "Room connection error: " + errorMessage);
            events.onSignalingParametersError(errorMessage);
          }

          @Override public void onHttpComplete(String response) {
            roomHttpResponseParse(response);
          }
        });
    httpConnection.send();
  }

  /*
  {
    "params": {
        "is_initiator": "false",
        "room_link": "https://appr.tc/r/456041265",
        "turn_server_override": [],
        "ice_server_transports": "",
        "media_constraints": "{\"audio\": true, \"video\": true}",
        "include_loopback_js": "",
        "turn_url": "https://computeengineondemand.appspot.com/turn?username=18946313&key=4080218913",
        "wss_url": "wss://apprtc-ws.webrtc.org:443/ws",
        "pc_constraints": "{\"optional\": []}",
        "pc_config": "{\"rtcpMuxPolicy\": \"require\", \"bundlePolicy\": \"max-bundle\", \"iceServers\": []}",
        "wss_post_url": "https://apprtc-ws.webrtc.org:443",
        "ice_server_url": "https://networktraversal.googleapis.com/v1alpha/iceconfig?key=AIzaSyAJdh2HkajseEIltlZ3SIXO02Tze9sO3NY",
        "warning_messages": [],
        "room_id": "456041265",
        "version_info": "{\"gitHash\": \"6e89f36e0d67d4ae535849e11bc2aa7910525948\", \"branch\": \"master\", \"time\": \"Wed Nov 2 01:50:02 2016 -0700\"}",
        "error_messages": [],
        "client_id": "18946313",
        "bypass_join_confirmation": "false",
        "is_loopback": "false",
        "offer_options": "{}",
        "messages": [
            {
                "type": "candidate",
                "label": 0,
                "id": "audio",
                "candidate": "candidate:3468416280 1 udp 2113937151 192.168.123.169 49169 typ host generation 0 ufrag 5NDP network-cost 50"
            }
            {
                "type": "offer"
                "sdp": "v=0\r\no=- 1114094258641161353 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=group:BUNDLE audio video\r\na=msid-semantic: WMS\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111 103 104 9 0 8 126\r\nc=IN IP4 0.0.0.0\r\na=rtcp:9 IN IP4 0.0.0.0\r\na=ice-ufrag:5NDP\r\na=ice-pwd:oIJzRHIGuZoYrzqYMrBvLdLZ\r\na=fingerprint:sha-256 7B:4D:98:EE:BA:A8:5D:E4:2C:E9:93:C0:95:89:A1:84:D8:CA:73:0F:2E:D0:9D:F9:BF:50:8B:1B:39:86:6A:58\r\na=setup:actpass\r\na=mid:audio\r\na=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\na=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\na=recvonly\r\na=rtcp-mux\r\na=rtpmap:111 opus/48000/2\r\na=rtcp-fb:111 transport-cc\r\na=fmtp:111 minptime=10;useinbandfec=1\r\na=rtpmap:103 isac/16000\r\na=rtpmap:104 isac/32000\r\na=rtpmap:9 G722/8000\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:8 PCMA/8000\r\na=rtpmap:126 telephone-event/8000\r\nm=video 9 UDP/TLS/RTP/SAVPF 101 100 107 116 117 96 97 99 98\r\nc=IN IP4 0.0.0.0\r\na=rtcp:9 IN IP4 0.0.0.0\r\na=ice-ufrag:5NDP\r\na=ice-pwd:oIJzRHIGuZoYrzqYMrBvLdLZ\r\na=fingerprint:sha-256 7B:4D:98:EE:BA:A8:5D:E4:2C:E9:93:C0:95:89:A1:84:D8:CA:73:0F:2E:D0:9D:F9:BF:50:8B:1B:39:86:6A:58\r\na=setup:actpass\r\na=mid:video\r\na=extmap:2 urn:ietf:params:rtp-hdrext:toffset\r\na=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\na=extmap:4 urn:3gpp:video-orientation\r\na=extmap:6 http://www.webrtc.org/experiments/rtp-hdrext/playout-delay\r\na=recvonly\r\na=rtcp-mux\r\na=rtcp-rsize\r\na=rtpmap:100 VP8/90000\r\na=rtcp-fb:100 ccm fir\r\na=rtcp-fb:100 nack\r\na=rtcp-fb:100 nack pli\r\na=rtcp-fb:100 goog-remb\r\na=rtcp-fb:100 transport-cc\r\na=rtpmap:101 VP9/90000\r\na=rtcp-fb:101 ccm fir\r\na=rtcp-fb:101 nack\r\na=rtcp-fb:101 nack pli\r\na=rtcp-fb:101 goog-remb\r\na=rtcp-fb:101 transport-cc\r\na=rtpmap:107 H264/90000\r\na=rtcp-fb:107 ccm fir\r\na=rtcp-fb:107 nack\r\na=rtcp-fb:107 nack pli\r\na=rtcp-fb:107 goog-remb\r\na=rtcp-fb:107 transport-cc\r\na=fmtp:107 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f\r\na=rtpmap:116 red/90000\r\na=rtpmap:117 ulpfec/90000\r\na=rtpmap:96 rtx/90000\r\na=fmtp:96 apt=100\r\na=rtpmap:97 rtx/90000\r\na=fmtp:97 apt=101\r\na=rtpmap:99 rtx/90000\r\na=fmtp:99 apt=107\r\na=rtpmap:98 rtx/90000\r\na=fmtp:98 apt=116\r\n",
            }
            {
                "type": "candidate",
                "label": 0,
                "id": "audio",
                "candidate": "candidate:2161414053 1 udp 16785407 216.58.221.30 27145 typ relay raddr 108.61.223.167 rport 52974 generation 0 ufrag 5NDP network-cost 50"
            }
        ],
        "callstats_params": "{\"appSecret\": \"IhG4J05kwq3Pxo+LF4uF0mmSdno=\", \"appId\": \"423310139\"}"
    },
    "result": "SUCCESS"
  }
   */
  private void roomHttpResponseParse(String response) {
    Log.d(TAG, "Room response: " + response);
    try {
      LinkedList<IceCandidate> iceCandidates = null;
      SessionDescription offerSdp = null;
      JSONObject roomJson = new JSONObject(response);

      String result = roomJson.getString("result");
      if (!result.equals("SUCCESS")) {
        events.onSignalingParametersError("Room response error: " + result);
        return;
      }
      response = roomJson.getString("params");
      roomJson = new JSONObject(response);
      String roomId = roomJson.getString("room_id");
      String clientId = roomJson.getString("client_id");
      String wssUrl = roomJson.getString("wss_url");
      String wssPostUrl = roomJson.getString("wss_post_url");
      boolean initiator = (roomJson.getBoolean("is_initiator"));
      if (!initiator) {
        iceCandidates = new LinkedList<IceCandidate>();
        String messagesString = roomJson.getString("messages");//如果为发起者的情况下messages为空
        JSONArray messages = new JSONArray(messagesString);
        for (int i = 0; i < messages.length(); ++i) {
          String messageString = messages.getString(i);
          JSONObject message = new JSONObject(messageString);
          String messageType = message.getString("type");
          Log.d(TAG, "GAE->C #" + i + " : " + messageString);
          if (messageType.equals("offer")) {
            offerSdp =
                new SessionDescription(SessionDescription.Type.fromCanonicalForm(messageType),
                    message.getString("sdp"));
          } else if (messageType.equals("candidate")) {
            IceCandidate candidate =
                new IceCandidate(message.getString("id"), message.getInt("label"),
                    message.getString("candidate"));
            iceCandidates.add(candidate);
          } else {
            Log.e(TAG, "Unknown message: " + messageString);
          }
        }
      }
      Log.d(TAG, "RoomId: " + roomId + ". ClientId: " + clientId);
      Log.d(TAG, "Initiator: " + initiator);
      Log.d(TAG, "WSS url: " + wssUrl);
      Log.d(TAG, "WSS POST url: " + wssPostUrl);

      LinkedList<PeerConnection.IceServer> iceServers =
          iceServersFromPCConfigJSON(roomJson.getString("pc_config"));
      boolean isTurnPresent = false;
      for (PeerConnection.IceServer server : iceServers) {
        Log.d(TAG, "IceServer: " + server);
        if (server.uri.startsWith("turn:")) {
          isTurnPresent = true;
          break;
        }
      }
      // Request TURN servers. 请求TURN服务器
      if (!isTurnPresent) {
        LinkedList<PeerConnection.IceServer> turnServers =
            requestTurnServers(roomJson.getString("ice_server_url"));
        for (PeerConnection.IceServer turnServer : turnServers) {
          Log.d(TAG, "TurnServer: " + turnServer);
          iceServers.add(turnServer);
        }
      }

      SignalingParameters params =
          new SignalingParameters(iceServers, initiator, clientId, wssUrl, wssPostUrl, offerSdp,
              iceCandidates);
      events.onSignalingParametersReady(params);
    } catch (JSONException e) {
      events.onSignalingParametersError("Room JSON parsing error: " + e.toString());
    } catch (IOException e) {
      events.onSignalingParametersError("Room IO error: " + e.toString());
    }
  }

  // Requests & returns a TURN ICE Server based on a request URL.  Must be run
  // off the main thread!
  //通过返回的ice服务器地址请求TURN ICE服务器地址[必须运行在非UI线程]
  private LinkedList<PeerConnection.IceServer> requestTurnServers(String url)
      throws IOException, JSONException {
    LinkedList<PeerConnection.IceServer> turnServers = new LinkedList<PeerConnection.IceServer>();
    Log.d(TAG, "Request TURN from: " + url);
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setDoOutput(true);
    connection.setRequestProperty("REFERER", "https://appr.tc");
    connection.setConnectTimeout(TURN_HTTP_TIMEOUT_MS);
    connection.setReadTimeout(TURN_HTTP_TIMEOUT_MS);
    int responseCode = connection.getResponseCode();
    if (responseCode != 200) {
      throw new IOException("Non-200 response when requesting TURN server from "
          + url
          + " : "
          + connection.getHeaderField(null));
    }
    InputStream responseStream = connection.getInputStream();
    String response = drainStream(responseStream);
    connection.disconnect();
    Log.d(TAG, "TURN response: " + response);
    /*
    {
        "lifetimeDuration": "86400s",
        "iceServers": [
            {
                "urls": [
                    "turn:216.58.221.30:19305?transport=udp",
                    "turn:[2404:6800:4004:815::201E]:19305?transport=udp",
                    "turn:216.58.221.30:443?transport=tcp",
                    "turn:[2404:6800:4004:815::201E]:443?transport=tcp"
                ],
                "username": "COuTpMIFEgbORIIk+mwYzc/s6OMT",
                "credential": "CDFVVw+X5L2z4BJLCEv/9OfQY2E="
            },
            {
                "urls": [
                    "stun:stun.l.google.com:19302"
                ]
            }
        ]
    }
     */
    JSONObject responseJSON = new JSONObject(response);
    JSONArray iceServers = responseJSON.getJSONArray("iceServers");
    for (int i = 0; i < iceServers.length(); ++i) {
      JSONObject server = iceServers.getJSONObject(i);
      JSONArray turnUrls = server.getJSONArray("urls");
      String username = server.has("username") ? server.getString("username") : "";
      String credential = server.has("credential") ? server.getString("credential") : "";
      for (int j = 0; j < turnUrls.length(); j++) {
        String turnUrl = turnUrls.getString(j);
        turnServers.add(new PeerConnection.IceServer(turnUrl, username, credential));
      }
    }
    return turnServers;
  }

  // Return the list of ICE servers described by a WebRTCPeerConnection
  // configuration string.
  //"{"rtcpMuxPolicy": "require", "bundlePolicy": "max-bundle", "iceServers": []}"
  //提取pc_config中的iceServer变量
  private LinkedList<PeerConnection.IceServer> iceServersFromPCConfigJSON(String pcConfig)
      throws JSONException {
    JSONObject json = new JSONObject(pcConfig);
    JSONArray servers = json.getJSONArray("iceServers");
    LinkedList<PeerConnection.IceServer> ret = new LinkedList<PeerConnection.IceServer>();
    for (int i = 0; i < servers.length(); ++i) {
      JSONObject server = servers.getJSONObject(i);
      String url = server.getString("urls");
      String credential = server.has("credential") ? server.getString("credential") : "";
      ret.add(new PeerConnection.IceServer(url, "", credential));
    }
    return ret;
  }

  // Return the contents of an InputStream as a String.
  private static String drainStream(InputStream in) {
    Scanner s = new Scanner(in).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
