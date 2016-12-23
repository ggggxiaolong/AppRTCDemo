package org.appspot.apprtc.bean;

import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author leon.tan on 2016/12/22.
 */

public class TestResponse {
  byte version = 1;
  byte from = -2;
  byte apiCode = 0;
  byte dataType = 0;
  byte sessionId = 1;
  byte responseCode = 0;
  byte more = 0;
  String str = "hi web";
  DCResponse mResponse = new DCResponse.Builder()
      .apiCode(apiCode)
      .dataType(DCMetaData.DATA_STRING)
      .sessionId(sessionId)
      .responseCode(DCMetaData.RESPONSE_CODE_SUCCESS)
      .more(false)
      .data(str.getBytes())
      .build();

  @Test public void testInt() {
    ByteBuffer map = mResponse.map();
    DCResponse response = new DCResponse(map);
    assertEquals(mResponse.version, response.version);
    assertEquals(mResponse.from, response.from);
    assertEquals(mResponse.apiCode, response.apiCode);
    assertEquals(mResponse.dataType, response.dataType);
    assertEquals(mResponse.sessionId, response.sessionId);
    assertEquals(mResponse.responseCode, response.responseCode);
    assertEquals(mResponse.more, response.more);
    assertArrayEquals(mResponse.data, response.data);
  }
}
