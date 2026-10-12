package dev.cpuguard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class Root {
    private Root() {}
    public static String run(String script) throws Exception {
        Process p = new ProcessBuilder("su", "-c", "sh").redirectErrorStream(true).start();
        try (OutputStream os = p.getOutputStream()) {
            os.write((script + "\nexit\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream is = p.getInputStream()) {
                byte[] b = new byte[4096]; int n;
                while ((n = is.read(b)) != -1 && result.size() < 200000) result.write(b, 0, n);
            } catch (IOException ignored) {}
        });
        reader.setDaemon(true); reader.start();
        if (!p.waitFor(15, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("Root 命令超时（检查授权弹窗）"); }
        reader.join(1000);
        String output = result.toString("UTF-8").trim();
        if (p.exitValue() != 0) throw new IOException("Root 执行失败 (" + p.exitValue() + "): " + output);
        return output;
    }
}
