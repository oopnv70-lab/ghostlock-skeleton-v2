package com.ghostlock.skeleton;

/**
 * GhostLock exploit 本地代码桥接。
 * 负责加载 libghostlock.so 并将用户空间参数传递给 native 入口。
 *
 * 调用链：
 *   UserService.exec("ghostlock <args>")  → shell 身份
 *   或
 *   NativeBridge.execute(args)             → app 身份 (Shizuku 已连接后)
 */
public class NativeBridge {
    private static volatile boolean sLoaded;

    /** 同步加载 libghostlock.so，多次调用安全 */
    public static synchronized void ensureLoaded() {
        if (sLoaded) return;
        System.loadLibrary("ghostlock");
        sLoaded = true;
    }

    /**
     * 执行 GhostLock 本地载荷。
     * @param args 命令行参数数组，第一个元素通常是操作名 ("trigger" | "spray" | "write" | "pwn")
     * @return 返回码，0 表示成功
     */
    public static native int execute(String[] args);
}
