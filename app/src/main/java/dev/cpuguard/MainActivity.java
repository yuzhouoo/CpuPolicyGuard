package dev.cpuguard;

import android.app.Activity;
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
    private static final String STORE = "/data/adb/cpuguard/apply.sh";
    private static final int BG=0xff0b1220, CARD=0xff172235, TEXT=0xfff0f5ff, MUTED=0xffa5b4c8, BLUE=0xff608eff;
    private final ArrayList<Policy> policies = new ArrayList<>();
    private final ArrayList<GroupCard> cards = new ArrayList<>();
    private LinearLayout root, groupPanel;
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
        Spinner governor;
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
        summary=label(root,"正在扫描……",13,MUTED,false);
        groupPanel=column(); root.addView(groupPanel);
        label(root,"说明",17,TEXT,true);
        label(root,"调速器和频率独立应用；每个核心组拥有单独的频率按钮。数值单位为 MHz，应用时自动换算为 kHz。若一个核心组包含多个 policy，将逐个写入并回读。",13,MUTED,false);
        Button restore=button(root,"恢复首次扫描值 / 关闭开机应用",false,()->restore());
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
    private void render(ArrayList<Policy> found){policies.clear();policies.addAll(found);cards.clear();groupPanel.removeAllViews();
        summary.setText("已发现 "+found.size()+" 个 CPU 策略组  ·  频率以 MHz 显示");
        for(String tier:new String[]{"小核","中核","大核","全部核心"}){
            ArrayList<Policy> list=new ArrayList<>();for(Policy p:found)if(tier.equals(p.group))list.add(p);if(list.isEmpty())continue;
            GroupCard group=new GroupCard();group.label=tier;cards.add(group);
            LinearLayout card=column();card.setPadding(dp(16),dp(15),dp(16),dp(19));card.setBackground(background(CARD,18));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(14);groupPanel.addView(card,cp);
            label(card,tier+"  ·  "+list.size()+" 个 policy",19,TEXT,true);
            for(Policy p:list){RowControls r=new RowControls();r.policy=p;group.rows.add(r);
                label(card,p.name+"  |  CPU "+(p.relatedCpus.isEmpty()?"未知":p.relatedCpus),14,BLUE,true);
                label(card,"当前 "+fmt(p.current)+" MHz  ·  硬件范围 "+fmt(p.hardwareMin)+"–"+fmt(p.hardwareMax)+" MHz",12,MUTED,false);
                label(card,"调速器",13,MUTED,false);
                Spinner sp=new Spinner(this);ArrayList<String> options=new ArrayList<>();for(String s:p.governors.split("\\s+"))if(!s.isEmpty())options.add(s);
                if(!options.contains(p.governor) && !p.governor.isEmpty()) options.add(p.governor);
                ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
                sp.setAdapter(adapter);sp.setSelection(Math.max(0,options.indexOf(p.governor)));card.addView(sp);r.governor=sp;
                button(card,"应用 "+p.name+" 调速器",false,()->applyGovernors(Collections.singletonList(r)));
                label(card,"最低频率 (MHz)",13,MUTED,false);r.low=input(card,p.min);
                label(card,"最高频率 (MHz)",13,MUTED,false);r.high=input(card,p.max);
                if(p.frequencies.isEmpty())label(card,"内核未公开可选频点，将按范围校验。",12,MUTED,false);
            }
            button(card,"应用"+tier+"频率限制",true,()->applyFrequencies(group.rows));
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
    private String saveScriptAndRun(String immediate) throws Exception {
        // Only the edited dimension is applied now; boot reapply combines all saved dimensions.
        StringBuilder script=new StringBuilder("#!/system/bin/sh\n");
        for(Policy p:policies){if(!nameOk(p.name))continue;
            String gov=pref.getString("governor."+p.name,null);
            String range=pref.getString("frequency."+p.name,null);
            script.append("if [ -d ").append(BASE).append(p.name).append(" ]; then\n");
            if(gov!=null && allowed(p,gov))script.append(lineGov(p,gov));
            if(range!=null){String[] a=range.split(",");if(a.length==2){long lo=number(a[0]),hi=number(a[1]);if(validRange(p,lo,hi))script.append(lineFreq(p,lo,hi));}}
            script.append("fi\n");
        }
        String upload="umask 077\nmkdir -p /data/adb/cpuguard\ncat > "+STORE+" <<'CPUGUARD_EOF'\n"+script+"CPUGUARD_EOF\nchmod 700 "+STORE+"\n";
        return Root.run(upload+immediate);
    }
    private boolean allowed(Policy p,String gov){return gov!=null && !gov.isEmpty() && Arrays.asList(p.governors.split("\\s+")).contains(gov);}
    private boolean validRange(Policy p,long lo,long hi){return lo>=p.hardwareMin && hi<=p.hardwareMax && lo<=hi;}
    private void applyGovernors(List<RowControls> target){
        ArrayList<String[]> update=new ArrayList<>();StringBuilder immediate=new StringBuilder();
        for(RowControls r:target){Policy p=r.policy;String gov=String.valueOf(r.governor.getSelectedItem());
            if(!allowed(p,gov)){status.setText(p.name+" 不支持调速器 "+gov);return;}
            update.add(new String[]{p.name,gov});immediate.append(lineGov(p,gov));
            immediate.append("echo '").append(p.name).append(" governor:'; cat ").append(BASE).append(p.name).append("/scaling_governor\n");
        }
        task("正在单独应用调速器……",()->{for(String[] u:update)pref.edit().putString("governor."+u[0],u[1]).commit();
            return "调速器应用结果：\n"+saveScriptAndRun(immediate.toString());});
    }
    private void applyFrequencies(List<RowControls> target){
        ArrayList<String[]> update=new ArrayList<>();StringBuilder immediate=new StringBuilder();
        for(RowControls r:target){Policy p=r.policy;long lo=mhz(r.low),hi=mhz(r.high);
            if(!validRange(p,lo,hi)){status.setText(p.name+" 频率无效：必须满足硬件范围且最低 ≤ 最高");return;}
            update.add(new String[]{p.name,String.valueOf(lo),String.valueOf(hi)});immediate.append(lineFreq(p,lo,hi));
            immediate.append("echo '").append(p.name).append(" frequency:'; cat ").append(BASE).append(p.name).append("/scaling_min_freq ").append(BASE).append(p.name).append("/scaling_max_freq\n");
        }
        task("正在单独应用频率……",()->{for(String[] u:update)pref.edit().putString("frequency."+u[0],u[1]+","+u[2]).commit();
            return "频率应用结果（kHz）：\n"+saveScriptAndRun(immediate.toString())+"\n如系统覆盖设置，请重新扫描确认。";});
    }
    private void restore(){task("正在尝试恢复首次扫描值……",()->{
        StringBuilder cmd=new StringBuilder("rm -f "+STORE+"\n");
        for(Policy p:policies){String initial=pref.getString("original."+p.name,null);if(initial==null)continue;
            String[] fields=initial.split(",");if(fields.length!=3)continue;long lo=number(fields[1]),hi=number(fields[2]);
            if(validRange(p,lo,hi))cmd.append(lineFreq(p,lo,hi));
            if(allowed(p,fields[0]))cmd.append(lineGov(p,fields[0]));
        }
        String result=Root.run(cmd.toString());pref.edit().clear().commit();
        return "已删除开机应用脚本，并尝试恢复首次扫描值。"+result;
    });}
}
