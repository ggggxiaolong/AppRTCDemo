package org.appspot.apprtc.bean;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * @author leon.tan on 2016/12/22.
 */

public class DCRequest extends DCMetaData {
  public final byte version;
  public final byte from;
  public final byte apiCode;
  public final byte dataType;
  public final byte subscribe;
  public final byte sessionId;
  public final byte more;
  public final byte[] data;

  public DCRequest( byte from, byte apiCode, byte dataType, byte subscribe,
      byte sessionId, byte more, byte[] data) {
    this.version = VERSION;
    this.from = from;
    this.apiCode = apiCode;
    this.dataType = dataType;
    this.subscribe = subscribe;
    this.sessionId = sessionId;
    this.more = more;
    this.data = data;
  }

  public DCRequest(ByteBuffer buffer) {
    data = new byte[buffer.capacity() - 7];
    version = buffer.get();
    from = buffer.get();
    apiCode = buffer.get();
    dataType = buffer.get();
    subscribe = buffer.get();
    sessionId = buffer.get();
    more = buffer.get();
    buffer.get(data, 0, buffer.capacity() - 7);
  }

  @Override public ByteBuffer map() {
    int i = 0;
    byte[] bytes = new byte[data.length + 7];
    bytes[i++] = version;
    bytes[i++] = from;
    bytes[i++] = apiCode;
    bytes[i++] = dataType;
    bytes[i++] = subscribe;
    bytes[i++] = sessionId;
    bytes[i++] = more;
    System.arraycopy(data, 0, bytes, i, data.length);
    return ByteBuffer.wrap(bytes);
  }

  @Override public boolean isString() {
    return dataType == 0;
  }

  @Override public String dataString() {
    if (isString()){
      Charset charset = Charset.forName("UTF-8");
      return new String(data, charset);
    }
    return "";
  }

  public boolean isSubscribe(){
    return subscribe == SUBSCRIBE_REGISTER;
  }

  public boolean isUnSubscribe(){
    return subscribe == SUBSCRIBE_UNREGISTER;
  }

  public boolean isNone(){
    return subscribe == SUBSCRIBE_NONE;
  }

  public static class Builder {
    byte mFrom;
    byte mApiCode;
    byte mDataType;
    byte mSubscribe;
    byte mSessionId;
    byte mMore;
    byte[] mData;

    public Builder from(@RequestFrom int from) {
      mFrom = (byte) from;
      return this;
    }

    public Builder apiCode(byte apiCode) {
      mApiCode = apiCode;
      return this;
    }

    public Builder dataType(@Data int dataType) {
      mDataType = (byte) dataType;
      return this;
    }

    public Builder subscribe(@Subscribe int subscribe) {
      mSubscribe = (byte) subscribe;
      return this;
    }

    public Builder sessionId(byte sessionId) {
      mSessionId = sessionId;
      return this;
    }

    public Builder more(boolean hasMore) {
      mMore = (byte) (hasMore ? MORE_YES : MORE_NO);
      return this;
    }

    public Builder data(byte[] data) {
      mData = data;
      return this;
    }

    public DCRequest build() {
      return new DCRequest(mFrom, mApiCode, mDataType, mSubscribe, mSessionId, mMore,
          mData);
    }
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DCRequest dcRequest = (DCRequest) o;

    if (version != dcRequest.version) return false;
    if (from != dcRequest.from) return false;
    if (apiCode != dcRequest.apiCode) return false;
    if (dataType != dcRequest.dataType) return false;
    if (subscribe != dcRequest.subscribe) return false;
    if (sessionId != dcRequest.sessionId) return false;
    if (more != dcRequest.more) return false;
    return Arrays.equals(data, dcRequest.data);
  }

  @Override public int hashCode() {
    int result = (int) version;
    result = 31 * result + (int) from;
    result = 31 * result + (int) apiCode;
    result = 31 * result + (int) dataType;
    result = 31 * result + (int) subscribe;
    result = 31 * result + (int) sessionId;
    result = 31 * result + (int) more;
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  @Override public String toString() {
    return "DCRequest{"
        + "version="
        + version
        + ", from="
        + from
        + ", apiCode="
        + apiCode
        + ", dataType="
        + dataType
        + ", subscribe="
        + subscribe
        + ", sessionId="
        + sessionId
        + ", more="
        + more
        + '}';
  }
}
