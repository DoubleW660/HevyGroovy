package com.example.hevygroovy.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalUserIdentityTest {
    @Test
    public void identityPersistsAcrossInstancesAndRetainsExistingProfile() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String isolated = "identity-" + UUID.randomUUID();
        Context context = new ContextWrapper(target) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(isolated, mode);
            }
        };
        try {
            long initial = new LocalUserIdentity(context).getUserId();
            assertEquals(1L, initial);
            assertEquals(initial, new LocalUserIdentity(context).getUserId());
            context.getSharedPreferences("local_profile", Context.MODE_PRIVATE).edit().putLong("user_id", 42L).commit();
            assertEquals(42L, new LocalUserIdentity(context).getUserId());
        } finally { target.deleteSharedPreferences(isolated); }
    }
}
