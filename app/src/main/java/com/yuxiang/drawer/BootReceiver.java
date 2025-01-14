package com.yuxiang.drawer;

import static android.content.Context.MODE_PRIVATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences settingsPreferences = context.getSharedPreferences("settings", MODE_PRIVATE);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (settingsPreferences.getBoolean("boot_state", false)) {
                Intent mainActivityIntent = new Intent(context, MainActivity.class);
                mainActivityIntent.putExtra("is_back", true);
                mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(mainActivityIntent);
            }
        }
    }
}