package org.appspot.apprtc.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import rx.Observable;

/**
 * @author leon.tan on 2016/12/27.
 *         task
 *         - 获取图片列表（分页查询）
 *         - 获取某张图片的缩略图
 *         - 获取某张图片的原始图片
 */

public class MediaPresenter {
  public static final String TAG = MediaPresenter.class.getCanonicalName();
  ContentResolver mContentResolver;

  public MediaPresenter(Context context) {
    mContentResolver = context.getContentResolver();
  }

  public Observable<ArrayList<Integer>> getImageThumbnails(int pageNumber, int pageSize) {
    assert mContentResolver != null;
    return Observable.create(subscriber -> {
      subscriber.onNext(getThumbnailsId(pageNumber, pageSize));
    });
  }

  public void getImageThumbnail(int thumbnailId) {
    assert mContentResolver != null;
    Observable.create(subscriber -> {
      String path = getThumbnailsPath(thumbnailId);
      if (path == null){
        subscriber.onError(new IllegalStateException("fail was null"));
        return;
      }
      try {
        FileInputStream fis = new FileInputStream(path);
        FileChannel channel = fis.getChannel();
        byte[] data = new byte[1024];
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int len;
        while((len = channel.read(buffer)) != -1){
          buffer.flip();
          buffer.get(data, 0, len);
          buffer.clear();
          // TODO: 2016/12/27
          //subscriber.onNext();
        }
      } catch (java.io.IOException e) {
        Log.e(TAG, "getImageThumbnail: ", e);
        subscriber.onError(new IllegalStateException("fail open file"));
      }
    });
  }

  public void getOriginalImage(int thumbnailId) {
    assert mContentResolver != null;

  }

  public void close() {
    if (mContentResolver != null) mContentResolver = null;
  }

  /**
   * run on thread
   */
  ArrayList<Integer> getThumbnailsId(int pageNumber, int pageSize) {
    ArrayList<Integer> data = new ArrayList<>(pageNumber);
    Uri uri = MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI;
    String[] projection = { MediaStore.Images.Thumbnails.IMAGE_ID };
    String limitation = "limit " + pageSize + " offset " + ((pageNumber - 1) * pageSize);
    //uri, projection, selection, selectionArgs, sortOrder
    Cursor cursor = mContentResolver.query(uri, projection, null, null, limitation);
    if (cursor != null) {
      while (cursor.moveToNext()) {
        data.add(cursor.getInt(0));
      }
    }
    cursor.close();
    return data;
  }

  /**
   * run on thread
   */
  @Nullable String getThumbnailsPath(int id) {
    Uri uri = MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI;
    String[] projection = { MediaStore.Images.Thumbnails.IMAGE_ID };
    String selection = MediaStore.Images.Thumbnails.IMAGE_ID + "= ? ";
    //String selectionArgs = {String.valueOf(id)};
    Cursor cursor = mContentResolver.query(uri, projection, selection, null, null);
    String path = null;
    if (cursor != null && cursor.moveToFirst()) {
      path = cursor.getString(0);
    }
    cursor.close();
    return path;
  }

  /**
   * run on thread
   */

  @Nullable String getOriginalPath(int id){
    return null;
  }
}
