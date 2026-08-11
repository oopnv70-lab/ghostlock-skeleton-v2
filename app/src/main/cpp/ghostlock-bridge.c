#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <dlfcn.h>
#include <android/log.h>
#include <stdarg.h>

#define TAG "GhostLockNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ── 输出缓冲区（替代 fprintf）────────────────────────────────── */

#define OUTBUF_SIZE 16384

static char g_outbuf[OUTBUF_SIZE];
static size_t g_outbuf_len;

static void outbuf_clear(void) {
    g_outbuf[0] = '\0';
    g_outbuf_len = 0;
}

static void outbuf_append(const char *fmt, ...)
    __attribute__((format(printf, 1, 2)));

static void outbuf_append(const char *fmt, ...) {
    if (g_outbuf_len >= OUTBUF_SIZE - 256) return;
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(g_outbuf + g_outbuf_len, OUTBUF_SIZE - g_outbuf_len, fmt, ap);
    va_end(ap);
    if (n > 0) g_outbuf_len += (size_t)n;
}

/* ── GhostLock 子命令路由 ─────────────────────────────────────── */

static void cmd_load(int argc, char *argv[]);
static void cmd_info(int argc, char *argv[]);

int ghostlock_main(int argc, char *argv[]) {
    outbuf_clear();

    if (argc < 1) {
        cmd_info(0, NULL);
        return 0;
    }

    const char *cmd = argv[0];

    if (strcmp(cmd, "load") == 0) {
        cmd_load(argc - 1, argv + 1);
    } else if (strcmp(cmd, "info") == 0) {
        cmd_info(argc - 1, argv + 1);
    } else if (strcmp(cmd, "trigger") == 0 ||
               strcmp(cmd, "spray") == 0  ||
               strcmp(cmd, "write") == 0   ||
               strcmp(cmd, "pwn") == 0) {
        outbuf_append("ghostlock: '%s' — 尚未实现\n等待 GhostLock exploit 源文件接入。\n", cmd);
        LOGI("ghostlock: command '%s' not yet implemented", cmd);
    } else {
        outbuf_append("用法: ghostlock load <path> [entry] | trigger | spray | write | pwn | info\n");
        LOGI("ghostlock: unknown command '%s'", cmd);
    }

    return 0;
}

/* ── load: 动态加载 SO/ELF ──────────────────────────────────── */

static void cmd_load(int argc, char *argv[]) {
    if (argc < 1) {
        outbuf_append("用法: ghostlock load <文件路径> [入口符号名]\n");
        return;
    }

    const char *path = argv[0];
    const char *entry_sym = (argc >= 2) ? argv[1] : "ghostlock_entry";

    LOGI("cmd_load: path=%s, entry=%s", path, entry_sym);
    outbuf_append("→ 加载: %s\n", path);
    outbuf_append("→ 入口: %s\n", entry_sym);

    /* 检查文件是否存在 */
    FILE *test = fopen(path, "rb");
    if (test == NULL) {
        outbuf_append("✗ 错误: 无法打开文件 %s\n", path);
        LOGE("cmd_load: 无法打开文件 %s", path);
        return;
    }
    /* 获取文件大小 */
    fseek(test, 0, SEEK_END);
    long fsize = ftell(test);
    fclose(test);
    outbuf_append("→ 大小: %ld 字节\n", fsize);

    /* dlopen 加载 */
    void *handle = dlopen(path, RTLD_NOW);
    if (handle == NULL) {
        const char *err = dlerror();
        outbuf_append("✗ dlopen 失败: %s\n", err ? err : "unknown");
        LOGE("cmd_load: dlopen 失败: %s", err ? err : "unknown");
        return;
    }
    outbuf_append("✓ dlopen 成功 (handle=%p)\n", handle);

    /* 查找入口符号 */
    dlerror();
    typedef int (*entry_fn_t)(int, char *[]);
    entry_fn_t entry = (entry_fn_t)dlsym(handle, entry_sym);
    const char *sym_err = dlerror();
    if (sym_err != NULL) {
        outbuf_append("✗ 符号 '%s' 未找到: %s\n", entry_sym, sym_err);
        LOGE("cmd_load: dlsym('%s') 失败: %s", entry_sym, sym_err);
        dlclose(handle);
        return;
    }
    outbuf_append("✓ dlsym('%s') → %p\n", entry_sym, entry);

    /* 调用入口函数 */
    char *load_args[] = {"ghostlock-payload", NULL};
    outbuf_append("→ 调用入口...\n");
    int ret = entry(1, load_args);
    outbuf_append("→ 入口返回: %d\n", ret);

    dlclose(handle);
    outbuf_append("✓ 已卸载, 完成\n");
}

/* ── info: 编译信息 ──────────────────────────────────────────── */

static void cmd_info(int argc, char *argv[]) {
    (void)argc;
    (void)argv;

    outbuf_append(
        "GhostLock Native Bridge\n"
        "───────────────────────\n"
        "CVE:     CVE-2026-43499 (GhostLock)\n"
        "类型:    Linux 内核 rt_mutex futex PI 栈 UAF 提权\n"
        "影响:    2.6.39 ~ 7.1 (含当前 kernel 6.6.89)\n"
        "CVSS:    7.8 HIGH / kernelCTF $92,337\n"
        "状态:    loader 就绪, exploit 阶段待接入\n"
        "───────────────────────\n"
#ifdef __aarch64__
        "架构:    aarch64 (arm64-v8a)\n"
#else
        "架构:    unknown\n"
#endif
        "编译:    " __DATE__ " " __TIME__ "\n"
        "工具链:  " __VERSION__ "\n"
        "\n"
        "命令:\n"
        "  load <path> [entry]  动态加载 SO/ELF 并调用入口\n"
        "  trigger              触发 GhostLock UAF（待实现）\n"
        "  spray                栈喷射（待实现）\n"
        "  write                受限写入原语（待实现）\n"
        "  pwn                  完整提权（待实现）\n"
        "  info                 显示此信息\n"
    );
}

/* ── JNI 入口：返回 String ───────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_com_ghostlock_skeleton_NativeBridge_execute(JNIEnv *env, jclass clazz, jobjectArray args) {
    if (args == NULL) {
        return (*env)->NewStringUTF(env, "✗ execute: args is null");
    }

    jint argc = (*env)->GetArrayLength(env, args);
    if (argc == 0) {
        return (*env)->NewStringUTF(env, "");
    }

    /* 将 Java String[] 转为 C char*[] */
    char **argv = (char **)calloc((size_t)(argc + 1), sizeof(char *));
    if (argv == NULL) {
        return (*env)->NewStringUTF(env, "✗ calloc 失败");
    }

    for (jint i = 0; i < argc; i++) {
        jstring js = (jstring)(*env)->GetObjectArrayElement(env, args, i);
        const char *str = (*env)->GetStringUTFChars(env, js, NULL);
        argv[i] = strdup(str);
        (*env)->ReleaseStringUTFChars(env, js, str);
        (*env)->DeleteLocalRef(env, js);
    }

    LOGI("execute: argc=%d", argc);
    for (jint i = 0; i < argc; i++) {
        LOGI("  argv[%d] = %s", i, argv[i]);
    }

    /* 调用 ghostlock_main（内部写入 g_outbuf） */
    ghostlock_main((int)argc, argv);

    /* 清理 */
    for (jint i = 0; i < argc; i++) {
        free(argv[i]);
    }
    free(argv);

    /* 返回 outbuf 内容 */
    return (*env)->NewStringUTF(env, g_outbuf);
}
