package dev.cpuguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        final PendingResult token = goAsync();
        new Thread(() -> { try {
            Root.run("if [ -f /data/adb/cpuguard/apply.sh ]; then sh /data/adb/cpuguard/apply.sh; fi");
        } catch (Exception ignored) {} finally { token.finish(); } }, "cpuguard-boot").start();
    }
}
