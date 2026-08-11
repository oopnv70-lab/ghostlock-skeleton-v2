package com.ghostlock.skeleton;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import android.util.Log;

public class UserService extends IUserService.Stub {

    private static final String TAG = "GhostLockService";

    @Override
    public void destroy() {
        Log.i(TAG, "destroy called, exiting");
        System.exit(0);
    }

    @Override
    public String exec(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "empty command";
        }

        String trimmed = command.trim();

        // ghostlock 命令走 native 路径
        if (trimmed.startsWith("ghostlock ") || trimmed.equals("ghostlock")) {
            return execNative(trimmed);
        }

        // 其他命令走 shell
        return execShell(trimmed);
    }

    /**
     * 加载 libghostlock.so 并通过 JNI 调用 native 入口。
     * 所有 native 输出（之前的 fprintf）现在通过 outbuf 缓冲区返回为字符串。
     */
    private String execNative(String cmd) {
        try {
            NativeBridge.ensureLoaded();
        } catch (UnsatisfiedLinkError e) {
            return "✗ 无法加载 libghostlock.so: " + e.getMessage() +
                   "\n请确认 APK 包含 arm64-v8a 的 native 库。";
        }

        // 解析参数：跳过 "ghostlock" 前缀
        String[] parts = cmd.split("\\s+");
        String[] args;
        if (parts.length > 0 && "ghostlock".equals(parts[0])) {
            args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, args.length);
        } else {
            args = parts;
        }

        // 无参数默认 info
        if (args.length == 0) {
            args = new String[]{"info"};
        }

        // 调用 native，直接获取输出字符串
        return NativeBridge.execute(args);
    }

    /**
     * 以 shell 身份执行系统命令。
     */
    private String execShell(String command) {
        try {
            java.lang.Process p = Runtime.getRuntime().exec(
                    new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(p.getErrorStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
            while ((line = errReader.readLine()) != null) {
                out.append("[stderr] ").append(line).append("\n");
            }
            p.waitFor();
            int exit = p.exitValue();
            if (exit != 0) {
                out.append("(exit: ").append(exit).append(")");
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "shell error: " + e.getMessage();
        }
    }
}
