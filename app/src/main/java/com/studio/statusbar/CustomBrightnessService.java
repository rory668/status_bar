package com.studio.statusbar;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.ViewManager;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class CustomBrightnessService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int brightness = intent.getIntExtra("brightness", 0);
        setBrightness(brightness);
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setBrightness(int brightness) {
        if (Settings.System.canWrite(this)) {
        // Apply the brightness value to the system settings
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightness);

        // Notify the system about the brightness change
        ContentResolver contentResolver = getContentResolver();
        Uri uri = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS);
        contentResolver.notifyChange(uri, null);
    } else {
            Toast.makeText(this, "write permission required to change brightness", Toast.LENGTH_SHORT).show();
        }
    }

    // Other necessary methods and implementations of the Service class


}
