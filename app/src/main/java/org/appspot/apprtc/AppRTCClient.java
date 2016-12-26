/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;

/**
 * AppRTCClient is the interface representing an AppRTC client.
 */
public interface AppRTCClient {

  /**
   * Struct holding the connection parameters of an AppRTC room.
   * 持有AppRtc房间连接参数的结构体
   */
  class RoomConnectionParameters {
    public final String roomUrl;
    public final String roomId;
    public final boolean loopback;

    public RoomConnectionParameters(String roomUrl, String roomId, boolean loopback) {
      this.roomUrl = roomUrl;
      this.roomId = roomId;
      this.loopback = loopback;
    }
  }

  /**
   * Asynchronously connect to an AppRTC room URL using supplied connection
   * parameters. Once connection is established onConnectedToRoom()
   * callback with room parameters is invoked.
   */
  void connectToRoom(RoomConnectionParameters connectionParameters);

  /**
   * Send offer SDP to the other participant.
   */
  void sendOfferSdp(final SessionDescription sdp, int label);

  /**
   * Send answer SDP to the other participant.
   */
  void sendAnswerSdp(final SessionDescription sdp, int label);

  /**
   * Send Ice candidate to the other participant.
   */
  void sendLocalIceCandidate(final IceCandidate candidate, int label);

  /**
   * Send removed ICE candidates to the other participant.
   */
  void sendLocalIceCandidateRemovals(final IceCandidate[] candidates, int label);

  /**
   * Disconnect from room.
   */
  void disconnectFromRoom();

  /**
   * Struct holding the signaling parameters of an AppRTC room.
   */
  class SignalingParameters {
    final List<PeerConnection.IceServer> iceServers;
    final boolean initiator;
    final String clientId;
    final String wssUrl;
    final String wssPostUrl;
    final SessionDescription offerSdp;
    final List<IceCandidate> iceCandidates;
    final MediaConstraints mediaConstraints;

    SignalingParameters(List<PeerConnection.IceServer> iceServers, boolean initiator,
        String clientId, String wssUrl, String wssPostUrl, SessionDescription offerSdp,
        List<IceCandidate> iceCandidates, MediaConstraints mediaConstraints) {
      this.iceServers = iceServers;
      this.initiator = initiator;
      this.clientId = clientId;
      this.wssUrl = wssUrl;
      this.wssPostUrl = wssPostUrl;
      this.offerSdp = offerSdp;
      this.iceCandidates = iceCandidates;
      this.mediaConstraints = mediaConstraints;
    }
  }

  /**
   * Callback interface for messagesSignalingEvents delivered on signaling channel.
   *
   * <p>Methods are guaranteed to be invoked on the UI thread of |activity|.
   */
  interface SignalingEvents {
    int TYPE_PC = 0;
    int TYPE_DC = 1;
    int TYPE_MS = 2;
    /**
     * Callback fired once the room's signaling parameters
     * SignalingParameters are extracted.
     */
    void onConnectedToRoom(final SignalingParameters params, int label);

    /**
     * Callback fired once remote SDP is received.
     */
    void onRemoteDescription(final SessionDescription sdp, MediaConstraints constraints, int label);

    /**
     * Callback fired once remote Ice candidate is received.
     */
    void onRemoteIceCandidate(final IceCandidate candidate, int label);

    /**
     * Callback fired once remote Ice candidate removals are received.
     */
    void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates, int label);

    /**
     * Callback fired once channel is closed.
     */
    void onChannelClose(int label);

    /**
     * Callback fired once channel error happened.
     */
    void onChannelError(final String description);
  }

  String ROOM_CREATE = "CREATED";
  String ROOM_JOIN = "JOINED";
  String ROOM_FULL = "ROOM_FULL";
  String ROOM_CREATE_OR_JOIN = "CREATE_OR_JOIN";
  String ICE_CANDIDATE = "ICE_CANDIDATE";
  String SDP_ANSWER = "SESSION_DESCRIPTION_ANSWER";
  String SDP_OFFER = "SESSION_DESCRIPTION_OFFER";
  String MEDIA_INFO = "MEDIA_INFO";
}
