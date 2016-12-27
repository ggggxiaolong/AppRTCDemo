package org.appspot.apprtc;

import android.app.Application;
import android.os.Build;
import android.support.multidex.MultiDex;

/**
 * @author leon.tan on 2016/12/27.
 */

public final class App extends Application {

  @Override public void onCreate() {
    super.onCreate();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      MultiDex.install(this);
    }
  }
}
