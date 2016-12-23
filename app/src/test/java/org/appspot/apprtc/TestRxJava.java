package org.appspot.apprtc;

import android.os.SystemClock;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;

import static org.junit.Assert.*;

/**
 * @author leon.tan on 2016/12/23.
 */

public class TestRxJava {

  @Test public void testInterval(){
    final long[] tem = {1};
    Subscription subscribe = Observable.interval(1, TimeUnit.SECONDS).take(5).subscribe(l -> {
      tem[0] = tem[0] + 1;
      System.out.printf(String.valueOf(l));
      assertTrue(tem[0] - l == 0);
    }, e -> {
      System.out.printf(e.getMessage());
      assertFalse(true);
    });
    subscribe.unsubscribe();
  }
}
