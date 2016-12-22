package org.appspot.apprtc.util;

import android.content.Context;
import com.ubnt.webrtc.deviceinfo.device.model.App;
import com.ubnt.webrtc.deviceinfo.device.model.Battery;
import com.ubnt.webrtc.deviceinfo.device.model.Device;
import com.ubnt.webrtc.deviceinfo.device.model.Memory;
import com.ubnt.webrtc.deviceinfo.device.model.Network;
import com.ubnt.webrtc.deviceinfo.media.DeviceMedia;
import com.ubnt.webrtc.deviceinfo.media.MediaInfo;
import org.appspot.apprtc.bean.DCMetaData;
import org.appspot.apprtc.bean.DCRequest;
import org.appspot.apprtc.bean.DCResponse;
import org.json.JSONException;
import org.json.JSONObject;
import rx.Observable;
import rx.schedulers.Schedulers;

import static org.appspot.apprtc.bean.DCMetaData.REQUEST_DEVICE_INFO;

/**
 * @author leon.tan on 2016/12/22.
 */

public class DCPresenter {
  final Context mContext;

  public DCPresenter(Context context) {
    mContext = context;
  }

  public Observable<DCResponse> onRequest(final DCRequest request){
    switch (request.apiCode){
      case REQUEST_DEVICE_INFO:{
        return dealDeviceInfo(request).subscribeOn(Schedulers.io());
      }
    }
    return null;
  }

  Observable<DCResponse> dealDeviceInfo(final DCRequest request) {
    return Observable.create(subscriber -> {
      App app = new App(mContext);
      Battery battery = new Battery(mContext);
      Device device = new Device(mContext);
      Memory memory = new Memory(mContext);
      Network network = new Network(mContext);
      MediaInfo mediaInfo = new DeviceMedia(mContext).getMediaInfo();
      JSONObject object = new JSONObject();
      putJson(object, "app", app.toJSON());
      putJson(object, "battery", battery.toJSON());
      putJson(object, "device", device.toJSON());
      putJson(object, "memory", memory.toJSON());
      putJson(object, "network", network.toJSON());
      putJson(object, "media", mediaInfo.toJSON());
      DCResponse response = new DCResponse.Builder().version(request.version)
          .apiCode(request.apiCode)
          .dataType(DCMetaData.DATA_STRING)
          .sessionId(request.sessionId)
          .responseCode(DCMetaData.RESPONSE_CODE_SUCCESS)
          .more(false)
          .data(object.toString().getBytes())
          .build();
      subscriber.onNext(response);
    });
  }

  static void putJson(JSONObject json, String key, Object value){
    try {
      json.put(key, value);
    } catch (JSONException e) {
    }
  }
}
