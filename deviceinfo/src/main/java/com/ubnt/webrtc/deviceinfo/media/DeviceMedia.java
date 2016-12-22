package com.ubnt.webrtc.deviceinfo.media;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;

/**
 * @author leon.tan on 2016/12/22.
 */

public class DeviceMedia {
  public final Context mContext;
  private final ContentResolver mContentResolver;

  public DeviceMedia(Context context) {
    mContext = context;
    mContentResolver = mContext.getContentResolver();
  }

  int getPhotoCount() {
    Uri uri = MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI;
    String[] projection = { MediaStore.Images.Thumbnails.IMAGE_ID };
    return getCount(uri, projection, null, null, null);
  }

  int getVideoCount() {
    Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    String[] projection = { MediaStore.Video.VideoColumns._ID };
    return getCount(uri, projection, null, null, null);
  }

  int getMusicCount() {
    Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    String[] projection = { MediaStore.Audio.AudioColumns._ID };
    return getCount(uri, projection, null, null, null);
  }

  int getDocumentCount() {
    Uri uri = MediaStore.Files.getContentUri("external");
    String[] projection = new String[] {
        MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.TITLE, MediaStore.Files.FileColumns.MIME_TYPE
    };

    String selection = MediaStore.Files.FileColumns.MIME_TYPE
        + "= ? "
        + " or "
        + MediaStore.Files.FileColumns.MIME_TYPE
        + " = ? "
        + " or "
        + MediaStore.Files.FileColumns.MIME_TYPE
        + " = ? "
        + " or "
        + MediaStore.Files.FileColumns.MIME_TYPE
        + " = ? "
        + " or "
        + MediaStore.Files.FileColumns.MIME_TYPE
        + " = ? ";

    String[] selectionArgs = new String[] {
        "text/plain", "application/msword", "application/pdf", "application/vnd.ms-powerpoint",
        "application/vnd.ms-excel"
    };
    return getCount(uri, projection, selection, selectionArgs, null);
  }

  int getZipCount() {
    String[] projection = new String[] {
        MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.TITLE
    };

    String selection = MediaStore.Files.FileColumns.MIME_TYPE + "= ? ";

    String[] selectionArgs = new String[] { "application/zip" };

    Uri uri = MediaStore.Files.getContentUri("external");
    return getCount(uri, projection, selection, selectionArgs, null);
  }

  int getCount(@RequiresPermission.Read @NonNull Uri uri, @Nullable String[] projection,
      @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
    Cursor cursor = mContentResolver.query(uri, projection, selection, selectionArgs, sortOrder);
    int count = cursor.getCount();
    cursor.close();
    return count;
  }

  public MediaInfo getMediaInfo(){
    return new MediaInfo(getPhotoCount(), getVideoCount(), getMusicCount(), getZipCount(), getDocumentCount());
  }
}
