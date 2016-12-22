package org.appspot.apprtc.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import org.json.JSONException;
import org.json.JSONObject;

import static android.content.Intent.ACTION_BATTERY_CHANGED;

/**
 * @author leon.tan on 2016/12/22.
 */
// TODO: 2016/12/22 改成定时 rxjava
public class DeviceState {
  final Context mContext;
  DeviceReceiver mReceiver;
  private DeviceInfo mInfo;
  final DeviceState.Observer mObserver;

  public DeviceState(Context context, Observer observer) {
    mContext = context;
    mObserver = observer;
  }

  public void register() {
    if (mReceiver != null) return;
    mReceiver = new DeviceReceiver();
    Intent intent = mContext.registerReceiver(mReceiver, getFilter());
    mInfo = new DeviceInfo();
    mInfo.batteryPercent = getBatteryPercent(intent);
    mInfo.isCharge = isPhoneCharging(intent);
    mInfo.isWifi = isWifi(mContext);
    mInfo.wifiStrength = getStrength(mContext);
  }

  public void unRegister() {
    if (mReceiver != null) mContext.unregisterReceiver(mReceiver);
  }

  IntentFilter getFilter() {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
    intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    intentFilter.addAction(ACTION_BATTERY_CHANGED);
    return intentFilter;
  }

  static class DeviceInfo {
    int batteryPercent;
    boolean isCharge;
    boolean isWifi;
    int wifiStrength;

    public int getBatteryPercent() {
      return batteryPercent;
    }

    public boolean isCharge() {
      return isCharge;
    }

    public boolean isWifi() {
      return isWifi;
    }

    public int getWifiStrength() {
      return wifiStrength;
    }

    public JSONObject toJSON() {
      JSONObject json = new JSONObject();
      try {
        json.put("batteryPercent", batteryPercent);
        json.put("isCharge", isCharge);
        json.put("isWifi", isWifi);
        json.put("wifiStrength", wifiStrength);
      } catch (JSONException e) {
        return null;
      }
      return json;
    }
  }

  interface Observer {
    void onStateChange(DeviceInfo info);
  }

  //电池信息，wifi改变
  class DeviceReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (action.equals(ACTION_BATTERY_CHANGED)) {
        dealBattery(intent);
      } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
        dealWifi(context, intent);
      } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
        dealRSSI(context);
      }
    }
  }

  private void dealRSSI(Context context) {
    mInfo.isWifi = true;
    mInfo.wifiStrength = getStrength(context);
    mObserver.onStateChange(mInfo);
  }

  private void dealWifi(Context context, Intent intent) {
    int wifistate =
        intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);

    if (wifistate == WifiManager.WIFI_STATE_DISABLED) {
      mInfo.isWifi = false;
      mInfo.wifiStrength = 0;
    } else if (wifistate == WifiManager.WIFI_STATE_ENABLED) {
      mInfo.isWifi = true;
      mInfo.wifiStrength = getStrength(context);
    }
    mObserver.onStateChange(mInfo);
  }

  private void dealBattery(Intent intent) {
    mInfo.batteryPercent = getBatteryPercent(intent);
    mInfo.isCharge = isPhoneCharging(intent);
    mObserver.onStateChange(mInfo);
  }

  public int getBatteryPercent(Intent intent) {
    int raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    int level = -1;
    if (raw >= 0 && scale > 0) {
      level = (raw * 100) / scale;
    }
    return level;
  }

  public boolean isPhoneCharging(Intent intent) {
    int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
    return plugged != 0;
  }

  public boolean isWifi(Context context) {
    WifiManager wifiManager =
        (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    return wifiManager.isWifiEnabled();
  }

  public int getStrength(Context context) {
    WifiManager wifiManager =
        (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    WifiInfo info = wifiManager.getConnectionInfo();
    if (info.getBSSID() != null) {
      return WifiManager.calculateSignalLevel(info.getRssi(), 4);
    }
    return 0;
  }
}
