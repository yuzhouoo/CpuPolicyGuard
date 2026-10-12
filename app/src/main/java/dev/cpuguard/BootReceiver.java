package dev.cpuguard;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** Intentionally no boot-time CPU writes. */
public class BootReceiver extends BroadcastReceiver { @Override public void onReceive(Context c, Intent i) {} }
