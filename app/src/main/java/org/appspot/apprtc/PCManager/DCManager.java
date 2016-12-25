package org.appspot.apprtc.PCManager;

import android.support.annotation.NonNull;
import android.util.Log;
import org.appspot.apprtc.bean.DCMetaData;
import org.appspot.apprtc.bean.DCRequest;
import org.appspot.apprtc.bean.DCResponse;
import org.webrtc.DataChannel;

/**
 * @author leon.tan on 2016/12/22.
 */

public class DCManager {
  public static final String TAG = "DCManager";
  DataChannel mDataChannel;
  Observer mObserver;
  boolean isRegister;

  void setDataChannel(@NonNull DataChannel dataChannel) {
    mDataChannel = dataChannel;
    if (mDataChannel.state() == DataChannel.State.OPEN) {
      mDataChannel.registerObserver(mDCObserver);
      isRegister = true;
    }
  }

  void setObserver(Observer observer) {
    mObserver = observer;
  }

  void sendMessage(DCMetaData message) {

  }

  void register() {
    if (!isRegister && mDataChannel != null) {
      mDataChannel.registerObserver(mDCObserver);
      isRegister = true;
    }
  }


  interface Observer {
    void onRequest(DCRequest request);

    void onResponse(DCResponse response);
  }

  final DataChannel.Observer mDCObserver = new DataChannel.Observer() {

    @Override public void onBufferedAmountChange(long l) {
      Log.d(TAG, "onBufferedAmountChange new length: " + l);
    }

    @Override public void onStateChange() {
      Log.d(TAG, "onStateChange: " + mDataChannel.state());
    }

    @Override public void onMessage(DataChannel.Buffer buffer) {
      if (mObserver != null) {
        if (DCMetaData.isRequest(buffer.data)) {
          mObserver.onRequest(new DCRequest(buffer.data));
        } else {
          mObserver.onResponse(new DCResponse(buffer.data));
        }
      }
    }
  };
}
