package dev.cpuguard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class Root {
    private Root() {}
    public static final class Result {
        public final int exitCode;
        public final String output;
        Result(int code, String text) { exitCode=code; output=text; }
    }
    public static Result execute(String script, long timeoutMillis) throws Exception {
        Process p = new ProcessBuilder("su", "-c", "sh").redirectErrorStream(true).start();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream is = p.getInputStream()) {
                byte[] b = new byte[4096]; int n;
                while ((n = is.read(b)) != -1) {
                    synchronized (result) {
                        if (result.size() < 256000) result.write(b,0,Math.min(n,256000-result.size()));
                    }
                }
            } catch (IOException ignored) {}
        });
        reader.setDaemon(true); reader.start();
        try (OutputStream os = p.getOutputStream()) {
            os.write((script + "\nexit\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        if (!p.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly(); throw new IOException("Root 命令超时：请检查 su 授权与系统状态");
        }
        reader.join(1500);
        synchronized (result) { return new Result(p.exitValue(), result.toString("UTF-8").trim()); }
    }
    public static String run(String script) throws Exception {
        Result r=execute(script,15000);
        if(r.exitCode!=0)throw new IOException("Root 执行失败 ("+r.exitCode+"): "+r.output);
        return r.output;
    }
}
