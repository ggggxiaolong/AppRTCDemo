package org.appspot.apprtc.util;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author leon.tan on 2016/12/23.
 */

public class Common {
  public static void putJson(JSONObject json, String key, Object value) {
    try {
      json.put(key, value);
    } catch (JSONException e) {
    }
  }
}
