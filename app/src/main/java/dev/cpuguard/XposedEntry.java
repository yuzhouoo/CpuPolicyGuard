package dev.cpuguard;

import android.app.Application;
import android.content.Context;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Optional LSPosed boot reapply path. Scope ONLY com.android.systemui. */
public class XposedEntry implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"com.android.systemui".equals(lp.packageName) || !"com.android.systemui".equals(lp.processName)) return;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                new Thread(() -> {
                    try { Root.run("if [ -f /data/adb/cpuguard/apply.sh ]; then sh /data/adb/cpuguard/apply.sh; fi"); }
                    catch (Throwable ignored) {}
                }, "cpuguard-lsp").start();
            }
        });
    }
}
