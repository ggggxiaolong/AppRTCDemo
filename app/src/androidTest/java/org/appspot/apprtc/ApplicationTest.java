package org.appspot.apprtc;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import org.appspot.apprtc.util.DeviceState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
@RunWith(AndroidJUnit4.class)
public class ApplicationTest {
    Context mContext;
    @Before public void before(){
        mContext = InstrumentationRegistry.getTargetContext();
    }
    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        assertEquals("org.appspot.apprtc", mContext.getPackageName());
    }

    @Test public void testDeviceState(){
        DeviceState deviceState = new DeviceState(mContext);
        String s = deviceState.getInfo().toJSON().toString();
        assertNotNull(s);
    }
}