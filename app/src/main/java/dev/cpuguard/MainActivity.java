package dev.cpuguard;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final String BASE = "/sys/devices/system/cpu/cpufreq/";
    private static final String STORE = "/data/adb/cpuguard/apply.sh"; // legacy file: cleanup only
    private static final int BG=0xff0b1220, CARD=0xff172235, TEXT=0xfff0f5ff, MUTED=0xffa5b4c8, BLUE=0xff608eff;
    private final ArrayList<Policy> policies = new ArrayList<>();
    private final ArrayList<GroupCard> cards = new ArrayList<>();
    private LinearLayout root, groupPanel, governorPanel;
    private Spinner globalGovernor;
    private TextView status, summary;
    private SharedPreferences pref;
    private volatile boolean busy;

    private static final class Policy {
        String name, governor, governors, frequencies, relatedCpus;
        long hardwareMin, hardwareMax, min, max, current;
        String group;
    }
    private static final class RowControls {
        Policy policy;
        EditText low, high;
    }
    private static final class GroupCard {
        String label;
        ArrayList<RowControls> rows = new ArrayList<>();
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        pref = getSharedPreferences("settings", MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        root=column(); root.setPadding(dp(18),dp(20),dp(18),dp(30)); scroll.addView(root); setContentView(scroll);
        label(root,"CPU CONTROL",12,BLUE,true);
        label(root,"处理器性能管理",26,TEXT,true);
        label(root,"OnePlus 12 · Android 16 / ColorOS 16\n根据实际 CPU policy 自动识别，不预设频点。",13,MUTED,false);
        Button refresh=button(root,"⟳  扫描 / 刷新 CPU",true,()->scan());
        button(root,"复制设备与 CPU 内核检测报告（只读）",false,()->report());
        summary=label(root,"正在扫描……",13,MUTED,false);
        governorPanel=column();root.addView(governorPanel);
        groupPanel=column(); root.addView(groupPanel);
        label(root,"说明",17,TEXT,true);
        label(root,"调速器全局统一选择并一次应用到所有 policy；小核、中核、大核分别设置频率。只允许手动应用，不在开机或 SystemUI 启动时重写频率。数值单位 MHz。",13,MUTED,false);
        Button restore=button(root,"恢复首次扫描值 / 清理旧开机脚本",false,()->restore());
        status=label(root,"需要 Root 授权。首次更改建议小幅调整，并确认温控和稳定性。",13,MUTED,false);
        scan();
    }
    private int dp(int px){ return (int)(getResources().getDisplayMetrics().density*px+0.5f); }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);return l;}
    private GradientDrawable background(int color, int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(LinearLayout parent,String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);if(bold)t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,dp(6),0,dp(6));parent.addView(t);return t;
    }
    private Button button(LinearLayout parent,String s,boolean primary,Runnable fn){
        Button b=new Button(this);b.setText(s);b.setTextColor(primary?Color.WHITE:TEXT);b.setTextSize(14);b.setAllCaps(false);
        b.setBackground(background(primary?0xff345ddd:0xff273650,12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(10);lp.bottomMargin=dp(5);parent.addView(b,lp);b.setOnClickListener(v->fn.run());return b;
    }
    private EditText input(LinearLayout parent,long khz){
        EditText e=new EditText(this);e.setSingleLine();e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(16);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(String.format(Locale.US,"%.3f",khz/1000.0).replaceAll("0+$","").replaceAll("\\.$",""));
        e.setPadding(dp(12),dp(7),dp(12),dp(7));e.setBackground(background(0xff0e1829,10));parent.addView(e,new LinearLayout.LayoutParams(-1,dp(46)));return e;
    }
    private void task(String start,Job fn){if(busy){status.setText("上一项操作尚未完成");return;}busy=true;status.setText(start);
        new Thread(()->{String result;try{result=fn.run();}catch(Exception ex){result="操作失败："+ex.getMessage();}
            final String s=result;runOnUiThread(()->{busy=false;status.setText(s);});},"cpu-control").start();}
    private interface Job {String run() throws Exception;}
    private static long number(String s){try{return Long.parseLong(s.trim());}catch(Exception e){return -1;}}
    private static boolean nameOk(String s){return s!=null && s.matches("policy[0-9]+");}
    private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    private static long mhz(EditText e){try{double n=Double.parseDouble(e.getText().toString().trim());return Double.isFinite(n)?Math.round(n*1000):-1;}catch(Exception ex){return -1;}}
    private void scan(){task("正在读取 CPU 频率与调速器……",()->{
        String cmd="for d in "+BASE+"policy*; do [ -d \"$d\" ] || continue; n=${d##*/}; printf '%s|' \"$n\"; "+
            "for f in cpuinfo_min_freq cpuinfo_max_freq scaling_min_freq scaling_max_freq scaling_cur_freq scaling_governor scaling_available_governors scaling_available_frequencies related_cpus; do "+
            "v=$(cat \"$d/$f\" 2>/dev/null || true); v=$(printf '%s' \"$v\" | tr '\\n|' '  '); printf '%s|' \"$v\"; done; echo; done";
        String data=Root.run(cmd);ArrayList<Policy> found=new ArrayList<>();
        for(String line:data.split("\\n")){
            String[] a=line.split("\\|",-1);if(a.length<10 || !nameOk(a[0]))continue;
            Policy p=new Policy();p.name=a[0];p.hardwareMin=number(a[1]);p.hardwareMax=number(a[2]);p.min=number(a[3]);p.max=number(a[4]);p.current=number(a[5]);
            p.governor=a[6].trim();p.governors=a[7].trim();p.frequencies=a[8].trim();p.relatedCpus=a[9].trim();
            if(p.hardwareMin<=0 || p.hardwareMax<p.hardwareMin || p.min<=0 || p.max<p.min)continue;
            found.add(p);
            if(!pref.contains("original."+p.name))pref.edit().putString("original."+p.name,p.governor+","+p.min+","+p.max).apply();
        }
        found.sort(Comparator.comparingLong(p->p.hardwareMax));
        ArrayList<Long> tiers=new ArrayList<>();for(Policy p:found)if(!tiers.contains(p.hardwareMax))tiers.add(p.hardwareMax);
        for(Policy p:found){int i=tiers.indexOf(p.hardwareMax);p.group=tiers.size()==1?"全部核心":i==0?"小核":i==tiers.size()-1?"大核":"中核";}
        runOnUiThread(()->render(found));return "扫描完成："+found.size()+" 个 policy · "+tiers.size()+" 种最高频率档位。";
    });}
    private void render(ArrayList<Policy> found){policies.clear();policies.addAll(found);cards.clear();groupPanel.removeAllViews();governorPanel.removeAllViews();
        summary.setText("已读取 "+found.size()+" 个 policy · 不预设频点 / CPU 编号");
        LinearLayout govCard=column();govCard.setPadding(dp(16),dp(14),dp(16),dp(18));govCard.setBackground(background(CARD,18));
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=dp(14);governorPanel.addView(govCard,gp);
        label(govCard,"统一 CPU 调速器",19,TEXT,true);
        label(govCard,"仅列出所有 policy 共同支持的调速器。此按钮只修改调速器，不触碰频率。",12,MUTED,false);
        LinkedHashSet<String> common=null;
        for(Policy p:found){LinkedHashSet<String> opts=new LinkedHashSet<>(Arrays.asList(p.governors.split("\\s+")));opts.remove("");
            if(common==null)common=opts;else common.retainAll(opts);
        }
        ArrayList<String> choices=new ArrayList<>();if(common!=null)choices.addAll(common);
        globalGovernor=new Spinner(this);ArrayAdapter<String> ga=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);
        globalGovernor.setAdapter(ga);govCard.addView(globalGovernor);
        String current=found.isEmpty()?"":found.get(0).governor;
        if(choices.contains(current))globalGovernor.setSelection(choices.indexOf(current));
        else if(!found.isEmpty())label(govCard,"各 policy 当前调速器不同，请选择后统一应用。",12,MUTED,false);
        Button govButton=button(govCard,"统一应用调速器（所有策略组）",true,()->applyGlobalGovernor());
        govButton.setEnabled(!choices.isEmpty());
        if(choices.isEmpty())label(govCard,"没有检测到共同支持的调速器，已禁用写入。",12,0xffffab91,false);
        label(groupPanel,"核心频率 · 独立控制",18,TEXT,true);
        for(String tier:new String[]{"小核","中核","大核","全部核心"}){
            ArrayList<Policy> list=new ArrayList<>();for(Policy p:found)if(tier.equals(p.group))list.add(p);if(list.isEmpty())continue;
            GroupCard group=new GroupCard();group.label=tier;cards.add(group);
            LinearLayout card=column();card.setPadding(dp(16),dp(15),dp(16),dp(19));card.setBackground(background(CARD,18));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(14);groupPanel.addView(card,cp);
            label(card,tier+"  ·  "+list.size()+" 个 policy",19,TEXT,true);
            for(Policy p:list){RowControls r=new RowControls();r.policy=p;group.rows.add(r);
                label(card,p.name+"  |  CPU "+(p.relatedCpus.isEmpty()?"未知":p.relatedCpus),14,BLUE,true);
                label(card,"当前 "+fmt(p.current)+" MHz · 调速器 "+p.governor,12,MUTED,false);
                label(card,"硬件频率范围 "+fmt(p.hardwareMin)+"–"+fmt(p.hardwareMax)+" MHz",12,MUTED,false);
                label(card,"最低频率 (MHz)",13,MUTED,false);r.low=input(card,p.min);
                label(card,"最高频率 (MHz)",13,MUTED,false);r.high=input(card,p.max);
                if(p.frequencies.isEmpty())label(card,"内核未公开离散频点，将依据实时范围验证。",12,MUTED,false);
            }
            button(card,"仅应用"+tier+"频率限制",true,()->applyFrequencies(group.rows));
        }
        if(found.isEmpty())label(groupPanel,"没有读取到 CPU policy。请检查 Root 授权和 cpufreq 节点。",14,MUTED,false);
    }
    private static String fmt(long khz){return String.format(Locale.US,"%.1f",khz/1000.0);}
    private static String lineGov(Policy p,String gov){String file=BASE+p.name+"/scaling_governor";return "printf '%s' "+quote(gov)+" > "+file+"\n";}
    private static String lineFreq(Policy p,long min,long max){String d=BASE+p.name+"/";
        return "printf '%s' "+p.hardwareMin+" > "+d+"scaling_min_freq\n"+
               "printf '%s' "+p.hardwareMax+" > "+d+"scaling_max_freq\n"+
               "printf '%s' "+max+" > "+d+"scaling_max_freq\n"+
               "printf '%s' "+min+" > "+d+"scaling_min_freq\n";
    }
    private String executeManual(String command) throws Exception {
        // No persistence. Remove legacy autostart script left by v1.2.
        return Root.run("rm -f "+STORE+"\n"+command);
    }
    private boolean allowed(Policy p,String gov){return gov!=null && !gov.isEmpty() && Arrays.asList(p.governors.split("\\s+")).contains(gov);}
    private boolean validRange(Policy p,long lo,long hi){return lo>=p.hardwareMin && hi<=p.hardwareMax && lo<=hi;}
    private void applyGlobalGovernor(){
        if(policies.isEmpty() || globalGovernor.getSelectedItem()==null)return;
        String gov=String.valueOf(globalGovernor.getSelectedItem());
        StringBuilder cmd=new StringBuilder("set -e\n");
        for(Policy p:policies){if(!allowed(p,gov)){status.setText(p.name+" 不支持 "+gov);return;}
            cmd.append("[ -d ").append(BASE).append(p.name).append(" ] || exit 2\n");
        }
        for(Policy p:policies){cmd.append(lineGov(p,gov));cmd.append("echo ").append(quote(p.name+" governor:")).append("; cat ").append(BASE).append(p.name).append("/scaling_governor\n");}
        task("正在统一应用调速器（不修改频率）……",()->"调速器结果：\n"+executeManual(cmd.toString())+"\n请重新扫描确认。");
    }
    private void applyFrequencies(List<RowControls> target){
        StringBuilder cmd=new StringBuilder("set -e\n");
        for(RowControls r:target){Policy p=r.policy;long lo=mhz(r.low),hi=mhz(r.high);
            if(!validRange(p,lo,hi)){status.setText(p.name+" 频率无效：必须满足硬件范围且最低 ≤ 最高");return;}
            // A single transition may be rejected by a vendor driver. Do not expand to hardware maximum first.
            cmd.append("d=").append(BASE).append(p.name).append("\n");
            cmd.append("old_lo=$(cat ${d}/scaling_min_freq)\nold_hi=$(cat ${d}/scaling_max_freq)\n");
            cmd.append("[ ").append(lo).append(" -le ").append(hi).append(" ] || exit 3\n");
            cmd.append("if [ ").append(lo).append(" -gt "$old_hi" ]; then printf '%s' ").append(hi).append(" > ${d}/scaling_max_freq; fi\n");
            cmd.append("if [ ").append(hi).append(" -lt "$old_lo" ]; then printf '%s' ").append(lo).append(" > ${d}/scaling_min_freq; fi\n");
            cmd.append("printf '%s' ").append(lo).append(" > ${d}/scaling_min_freq\n");
            cmd.append("printf '%s' ").append(hi).append(" > ${d}/scaling_max_freq\n");
            cmd.append("echo ").append(quote(p.name+" frequency kHz:")).append("; cat ${d}/scaling_min_freq ${d}/scaling_max_freq\n");
        }
        task("仅应用所选核心组频率……",()->"频率回读（kHz）：\n"+executeManual(cmd.toString())+"\n如系统覆盖，请重新扫描。重启后本模块不会自动恢复限制。");
    }
    private void report(){task("正在获取只读设备报告……",()->{
        String command="echo '=== DEVICE ==='; getprop ro.product.model; getprop ro.product.device; getprop ro.build.fingerprint; " +
            "echo '=== SOC ==='; getprop ro.soc.model; getprop ro.board.platform; " +
            "echo '=== KERNEL ==='; uname -a; echo '=== POLICY ==='; " +
            "for d in /sys/devices/system/cpu/cpufreq/policy*; do [ -d \"$d\" ] || continue; echo \"[$d]\"; " +
            "for f in related_cpus affected_cpus cpuinfo_min_freq cpuinfo_max_freq scaling_min_freq scaling_max_freq scaling_cur_freq scaling_driver scaling_governor scaling_available_governors scaling_available_frequencies; do " +
            "printf '%s: ' \"$f\"; cat \"$d/$f\" 2>/dev/null || echo unsupported; done; done";
        String output=Root.run(command);
        runOnUiThread(()->{ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("CPU policy report",output));});
        return "已复制设备与 CPU policy 报告到剪贴板。请发送报告以核实 8 Gen 3 实际分组和频点。";
    });}
    private void restore(){task("正在停用旧版本开机脚本……",()->{
        String result=Root.run("rm -f /data/adb/cpuguard/apply.sh; echo 已清理旧脚本，请重启让系统重新管理 CPU");
        pref.edit().clear().commit();
        return result+"\n为避免错误恢复旧频点，本版不自动重写首次扫描值。";
    });}
}
