package org.appspot.apprtc.bean;

import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * @author leon.tan on 2016/12/22.
 */

public abstract class DCMetaData {
  public static final byte VERSION = 1;
  public static final int REQUEST_WEB = 1;
  public static final int REQUEST_MOBILE = 2;

  @Retention(RetentionPolicy.SOURCE) @IntDef({ REQUEST_WEB, REQUEST_MOBILE })
  @interface RequestFrom {
  }

  public static final int RESPONSE_WEB = -1;
  public static final int RESPONSE_MOBILE = -2;

  @Retention(RetentionPolicy.SOURCE) @IntDef({ RESPONSE_WEB, RESPONSE_MOBILE })
  @interface ResponseFrom {
  }

  public static final int DAT_BINARY = 1;
  public static final int DATA_STRING = 0;

  @Retention(RetentionPolicy.SOURCE) @IntDef({ DAT_BINARY, DATA_STRING }) @interface Data {
  }

  public static final int SUBSCRIBE_REGISTER = 0;
  public static final int SUBSCRIBE_UNREGISTER = -1;
  public static final int SUBSCRIBE_NONE = 1;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({ SUBSCRIBE_REGISTER, SUBSCRIBE_UNREGISTER, SUBSCRIBE_NONE }) @interface Subscribe {
  }

  public static final int MORE_NO = 0;
  public static final int MORE_YES = -1;

  @Retention(RetentionPolicy.SOURCE) @IntDef({ MORE_NO, MORE_YES }) @interface More {
  }

  public static final int REQUEST_DEVICE_INFO = 0;
  public static final int REQUEST_DEVICE_STATE = 1;
  public static final int REQUEST_DEVICE_PHOTO = 2;

  public static final int RESPONSE_CODE_SUCCESS = 0;
  public static final int RESPONSE_CODE_FAIL_UNKNOWN = -101;
  public static final int RESPONSE_CODE_FAIL_CHECK_AUTHORIZATION = -1;
  public static final int RESPONSE_CODE_FAIL_UNKNOWN_API_TYPE = -2;
  public static final int RESPONSE_CODE_FAIL_DECODE_PARAM = -3;
  public static final int RESPONSE_CODE_FAIL_DECRYPT = -4;

  @Retention(RetentionPolicy.SOURCE) @IntDef({
      RESPONSE_CODE_SUCCESS, RESPONSE_CODE_FAIL_UNKNOWN, RESPONSE_CODE_FAIL_CHECK_AUTHORIZATION,
      RESPONSE_CODE_FAIL_UNKNOWN_API_TYPE, RESPONSE_CODE_FAIL_DECODE_PARAM,
      RESPONSE_CODE_FAIL_DECRYPT
  }) @interface ResponseCode {
  }

  public abstract ByteBuffer map();

  public abstract boolean isString();

  public abstract String dataString();

  public static boolean isRequest(ByteBuffer buffer) {
    return (buffer.get(1) & 0x80) == 0;
  }
}
