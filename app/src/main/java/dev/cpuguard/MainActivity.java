package dev.cpuguard;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final String BASE = "/sys/devices/system/cpu/cpufreq/";
    private static final String STORE = "/data/adb/cpuguard/apply.sh";
    private final ArrayList<Policy> policies = new ArrayList<>();
    private LinearLayout panel; private Spinner selector, governors;
    private EditText minimum, maximum; private TextView details, status;
    private SharedPreferences pref;
    private Policy selected;
    private static final class Policy {
        String name, governor, governors, frequencies;
        long hardwareMin, hardwareMax, min, max, current;
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); pref = getSharedPreferences("settings", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this); panel = new LinearLayout(this); panel.setPadding(24,24,24,24);
        panel.setOrientation(LinearLayout.VERTICAL); scroll.addView(panel); setContentView(scroll);
        TextView title = new TextView(this); title.setText("CPU 策略控制 · LSPosed"); title.setTextSize(23); title.setTypeface(null, Typeface.BOLD); panel.addView(title);
        text("需要 Root 授权。配置按 CPU policy 生效，不支持的调速器不会写入。频率单位为 kHz，1000000 kHz = 1 GHz。");
        button("扫描 CPU 策略组", v -> scan());
        selector = new Spinner(this); panel.addView(selector);
        details = text("先点击扫描");
        text("调速器"); governors = new Spinner(this); panel.addView(governors);
        text("最低频率 (kHz)"); minimum = new EditText(this); minimum.setInputType(2); panel.addView(minimum);
        text("最高频率 (kHz)"); maximum = new EditText(this); maximum.setInputType(2); panel.addView(maximum);
        button("保存当前策略并立即应用", v -> apply());
        button("恢复首次扫描值并关闭开机应用", v -> restore());
        button("查看实际生效状态", v -> scan());
        status = text("提示：开机应用同时提供 BOOT_COMPLETED 和 LSPosed SystemUI 重启触发。建议只在 LSPosed 中勾选 系统界面 (com.android.systemui)，不要勾选系统框架。");
        selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < policies.size()) showPolicy(policies.get(pos));
            }
        });
        scan();
    }
    private TextView text(String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(15); t.setPadding(0,14,0,10); panel.addView(t); return t; }
    private void button(String label, View.OnClickListener l) { Button b = new Button(this); b.setText(label); panel.addView(b); b.setOnClickListener(l); }
    private void work(String message, Task t) {
        status.setText(message);
        new Thread(() -> {
            try { String result = t.run(); runOnUiThread(() -> status.setText(result)); }
            catch(Exception ex) { runOnUiThread(() -> status.setText("失败：" + ex.getMessage())); }
        }, "cpuguard-worker").start();
    }
    private interface Task { String run() throws Exception; }
    private static long number(String s) { try { return Long.parseLong(s.trim()); } catch(Exception ex) { return -1; } }
    private static boolean validName(String s) { return s.matches("policy[0-9]+"); }
    private void scan() {
        work("正在读取 CPU policy...", () -> {
            String output = Root.run("for d in " + BASE + "policy*; do\n" +
                    "[ -d \"$d\" ] || continue\n" +
                    "n=${d##*/}; printf '%s|' \"$n\"\n" +
                    "for f in cpuinfo_min_freq cpuinfo_max_freq scaling_min_freq scaling_max_freq scaling_cur_freq scaling_governor scaling_available_governors scaling_available_frequencies; do " +
                    "v=$(cat \"$d/$f\" 2>/dev/null || true); v=$(printf '%s' \"$v\" | tr '\\n|' '  '); printf '%s|' \"$v\"; done; echo\n" +
                    "done");
            ArrayList<Policy> found = new ArrayList<>();
            for(String line : output.split("\\n")) {
                String[] a = line.split("\\|", -1);
                if(a.length < 9 || !validName(a[0])) continue;
                Policy p = new Policy(); p.name=a[0]; p.hardwareMin=number(a[1]); p.hardwareMax=number(a[2]);
                p.min=number(a[3]); p.max=number(a[4]); p.current=number(a[5]); p.governor=a[6].trim();
                p.governors=a[7].trim(); p.frequencies=a[8].trim();
                if(p.hardwareMin <= 0 || p.hardwareMax < p.hardwareMin) continue;
                found.add(p);
                if(!pref.contains("original." + p.name))
                    pref.edit().putString("original." + p.name, p.governor+","+p.min+","+p.max).apply();
            }
            runOnUiThread(() -> {
                String prior=selected == null ? "" : selected.name;
                policies.clear(); policies.addAll(found);
                ArrayList<String> names = new ArrayList<>(); int target=0;
                for(int i=0;i<policies.size();i++) { Policy p=policies.get(i); names.add(p.name); if(p.name.equals(prior)) target=i; }
                selector.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
                if(!policies.isEmpty()) selector.setSelection(target);
                else details.setText("找不到 cpufreq 策略组（或 root 权限不可用）");
            });
            return "找到 " + found.size() + " 个 CPU 策略组。";
        });
    }
    private void showPolicy(Policy p) {
        selected = p;
        details.setText(p.name + " | 当前 " + p.current + " kHz\n内核硬件范围 " + p.hardwareMin + " ~ " + p.hardwareMax +
                " kHz\n当前限制 " + p.min + " ~ " + p.max + " kHz\n可用频点：" + (p.frequencies.isEmpty()?"内核未公开":p.frequencies));
        ArrayList<String> options = new ArrayList<>(Arrays.asList(p.governors.split("\\s+")));
        if(options.isEmpty() || options.get(0).isEmpty()) options = new ArrayList<>(Collections.singletonList(p.governor));
        governors.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        int idx=options.indexOf(p.governor); governors.setSelection(Math.max(0,idx));
        minimum.setText(String.valueOf(p.min)); maximum.setText(String.valueOf(p.max));
    }
    private static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
    private static String commands(String name, String gov, long min, long max, long hwMin, long hwMax) {
        String p = BASE + name + "/";
        // Expand bounds before narrowing: avoids EINVAL if old min > new max.
        return "printf '%s' " + q(String.valueOf(hwMin)) + " > " + p + "scaling_min_freq\n" +
               "printf '%s' " + q(String.valueOf(hwMax)) + " > " + p + "scaling_max_freq\n" +
               "printf '%s' " + q(gov) + " > " + p + "scaling_governor\n" +
               "printf '%s' " + q(String.valueOf(max)) + " > " + p + "scaling_max_freq\n" +
               "printf '%s' " + q(String.valueOf(min)) + " > " + p + "scaling_min_freq\n";
    }
    private void apply() {
        if(selected == null) return;
        final Policy p=selected; final String gov=String.valueOf(governors.getSelectedItem());
        final long lo=number(minimum.getText().toString()), hi=number(maximum.getText().toString());
        if(!validName(p.name) || !Arrays.asList(p.governors.split("\\s+")).contains(gov) ||
           lo < p.hardwareMin || hi > p.hardwareMax || lo > hi) { status.setText("参数超出硬件范围、min > max 或调速器不可用"); return; }
        pref.edit().putString("profile."+p.name,gov+","+lo+","+hi).apply();
        work("正在保存和应用（会请求 Root）...", () -> {
            StringBuilder script = new StringBuilder("#!/system/bin/sh\n");
            for(Policy item: policies) {
                String v=pref.getString("profile."+item.name, null);
                if(v==null) continue;
                String[] fields=v.split(","); if(fields.length!=3) continue;
                long min=number(fields[1]), max=number(fields[2]);
                if(!validName(item.name) || !Arrays.asList(item.governors.split("\\s+")).contains(fields[0]) ||
                    min<item.hardwareMin || max>item.hardwareMax || min>max) continue;
                script.append("if [ -d ").append(BASE).append(item.name).append(" ]; then\n");
                script.append(commands(item.name,fields[0],min,max,item.hardwareMin,item.hardwareMax));
                script.append("fi\n");
            }
            String command="umask 077\nmkdir -p /data/adb/cpuguard\ncat > " + STORE + " <<'CPU_GUARD_PROFILE'\n" + script + "CPU_GUARD_PROFILE\nchmod 700 " + STORE + "\nsh " + STORE + "\n" +
                "for f in " + BASE + "policy*/scaling_governor; do [ -f \"$f\" ] || continue; d=${f%/*}; echo \"${d##*/}: $(cat \"$f\") $(cat \"$d/scaling_min_freq\")-$(cat \"$d/scaling_max_freq\")\"; done\n";
            return "已应用，实际回读：\n" + Root.run(command) + "\n若回读不同，可能被内核或电源 HAL 覆盖。";
        });
    }
    private void restore() {
        work("恢复记录的初始值...", () -> {
            StringBuilder s=new StringBuilder("rm -f " + STORE + "\n");
            for(Policy p: policies) {
                String v=pref.getString("original."+p.name,null); if(v==null) continue;
                String[] a=v.split(","); if(a.length!=3 || !validName(p.name) || !Arrays.asList(p.governors.split("\\s+")).contains(a[0])) continue;
                long low=number(a[1]), high=number(a[2]);
                if(low<p.hardwareMin || high>p.hardwareMax || low>high) continue;
                s.append(commands(p.name,a[0],low,high,p.hardwareMin,p.hardwareMax));
            }
            String output=Root.run(s.toString());
            pref.edit().clear().apply();
            return "已移除开机脚本，尝试恢复首次扫描值。" + output;
        });
    }
}
