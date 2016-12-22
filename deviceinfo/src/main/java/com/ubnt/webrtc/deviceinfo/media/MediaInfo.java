package com.ubnt.webrtc.deviceinfo.media;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author leon.tan on 2016/12/22.
 */

public class MediaInfo {
  public final int photo;
  public final int video;
  public final int music;
  public final int zipFile;
  public final int document;

  public MediaInfo(int photo, int video, int music, int zipFile, int document) {
    this.photo = photo;
    this.video = video;
    this.music = music;
    this.zipFile = zipFile;
    this.document = document;
  }

  public JSONObject toJSON() {
    JSONObject json = new JSONObject();
    try {
      json.put("photo", photo);
      json.put("video", video);
      json.put("music", music);
      json.put("zipFile", zipFile);
      json.put("document", document);
    } catch (JSONException e) {
      return null;
    }
    return json;
  }
}
