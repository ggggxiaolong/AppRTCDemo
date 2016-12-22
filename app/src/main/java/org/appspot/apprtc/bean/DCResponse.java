package org.appspot.apprtc.bean;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * @author leon.tan on 2016/12/22.
 */

public class DCResponse extends DCMetaData {
  public final byte version;
  public final byte from;
  public final byte apiCode;
  public final byte dataType;
  public final byte sessionId;
  public final byte responseCode;
  public final byte more;
  public final byte[] data;

  public DCResponse(byte version, byte from, byte apiCode, byte dataType, byte sessionId,
      byte responseCode, byte more, byte[] data) {
    this.version = version;
    this.from = from;
    this.apiCode = apiCode;
    this.dataType = dataType;
    this.sessionId = sessionId;
    this.responseCode = responseCode;
    this.more = more;
    this.data = data;
  }

  public DCResponse(ByteBuffer buffer) {
    version = buffer.get();
    from = buffer.get();
    apiCode = buffer.get();
    dataType = buffer.get();
    sessionId = buffer.get();
    responseCode = buffer.get();
    more = buffer.get();
    data = new byte[buffer.capacity() - 7];
    buffer.get(data, 0, data.length);
  }

  @Override public ByteBuffer map() {
    byte[] bytes = new byte[data.length + 7];
    int i = 0;
    bytes[i++] = version;
    bytes[i++] = from;
    bytes[i++] = apiCode;
    bytes[i++] = dataType;
    bytes[i++] = sessionId;
    bytes[i++] = responseCode;
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

  public static class Builder {
    byte mVersion;
    byte mFrom = RESPONSE_MOBILE;
    byte mApiCode;
    byte mDataType;
    byte mSessionId;
    byte mResponseCode;
    byte mMore;
    byte[] mData;

    public Builder version(byte version) {
      mVersion = version;
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

    public Builder sessionId(byte sessionId) {
      mSessionId = sessionId;
      return this;
    }

    public Builder responseCode(@ResponseCode int responseCode) {
      mResponseCode = (byte) responseCode;
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

    public DCResponse build() {
      return new DCResponse(mVersion, mFrom, mApiCode, mDataType, mSessionId, mResponseCode, mMore,
          mData);
    }
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DCResponse that = (DCResponse) o;

    if (version != that.version) return false;
    if (from != that.from) return false;
    if (apiCode != that.apiCode) return false;
    if (dataType != that.dataType) return false;
    if (sessionId != that.sessionId) return false;
    if (responseCode != that.responseCode) return false;
    if (more != that.more) return false;
    return Arrays.equals(data, that.data);
  }

  @Override public int hashCode() {
    int result = (int) version;
    result = 31 * result + (int) from;
    result = 31 * result + (int) apiCode;
    result = 31 * result + (int) dataType;
    result = 31 * result + (int) sessionId;
    result = 31 * result + (int) responseCode;
    result = 31 * result + (int) more;
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  @Override public String toString() {
    return "DCResponse{"
        + "version="
        + version
        + ", from="
        + from
        + ", apiCode="
        + apiCode
        + ", dataType="
        + dataType
        + ", sessionId="
        + sessionId
        + ", responseCode="
        + responseCode
        + ", more="
        + more
        + '}';
  }
}
