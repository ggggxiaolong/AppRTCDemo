package org.appspot.apprtc.PCManager;

import android.support.annotation.NonNull;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
  final ExecutorService mExecutorService;

  DCManager() {
    mExecutorService = Executors.newSingleThreadScheduledExecutor();
  }

  void setDataChannel(@NonNull DataChannel dataChannel) {
    Log.i(TAG, "setDataChannel: receive data channel");
    mDataChannel = dataChannel;
    //if (mDataChannel.state() == DataChannel.State.OPEN) {
      mDataChannel.registerObserver(mDCObserver);
      isRegister = true;
    //}
  }

  void setObserver(Observer observer) {
    mObserver = observer;
  }

  public void sendMessage(DCMetaData data) {
    mExecutorService.execute(() -> {
      if (mDataChannel != null) {
        mDataChannel.send(new DataChannel.Buffer(data.map(), true));
        Log.i(TAG, "sendMessage from data channel :" + data.toString());
      } else {
        Log.e(TAG, "sendMessage from data channel fail it's null");
      }
    });
  }

  void register() {
    if (!isRegister && mDataChannel != null) {
      mDataChannel.registerObserver(mDCObserver);
      isRegister = true;
    }
  }

  void close() {
    if (mDataChannel != null) {
      mDataChannel.close();
    }
  }

  public interface Observer {
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
          Log.i(TAG, " data channel onMessage: request");
          mObserver.onRequest(new DCRequest(buffer.data));
        } else {
          Log.i(TAG, " data channel onMessage: response");
          mObserver.onResponse(new DCResponse(buffer.data));
        }
      }
    }
  };
}
