#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <dlfcn.h>
#include <android/log.h>

#define TAG "GhostLockNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ── GhostLock 子命令路由 ─────────────────────────────────────── */

/*
 * ghostlock 命令行入口。
 *
 * 支持的命令：
 *   ghostlock load <path>       从指定路径 dlopen 加载 SO/ELF，调用入口 gadget
 *   ghostlock trigger           触发 GhostLock UAF（未来实现）
 *   ghostlock spray             栈喷射（未来实现）
 *   ghostlock write             受限写入原语（未来实现）
 *   ghostlock pwn               完整提权（未来实现）
 *   ghostlock info              显示编译信息
 */

static int cmd_load(int argc, char *argv[]);
static int cmd_info(int argc, char *argv[]);

int ghostlock_main(int argc, char *argv[]) {
    if (argc < 1) {
        LOGI("ghostlock: no command");
        return cmd_info(0, NULL);
    }

    const char *cmd = argv[0];

    if (strcmp(cmd, "load") == 0) {
        return cmd_load(argc - 1, argv + 1);
    } else if (strcmp(cmd, "info") == 0) {
        return cmd_info(argc - 1, argv + 1);
    } else if (strcmp(cmd, "trigger") == 0 ||
               strcmp(cmd, "spray") == 0 ||
               strcmp(cmd, "write") == 0 ||
               strcmp(cmd, "pwn") == 0) {
        LOGI("ghostlock: command '%s' not yet implemented", cmd);
        fprintf(stderr, "ghostlock: '%s' — 尚未实现。等待 GhostLock exploit 源文件接入。\n", cmd);
        return 1;
    } else {
        LOGI("ghostlock: unknown command '%s'", cmd);
        fprintf(stderr, "ghostlock: 未知命令 '%s'\n"
                        "用法: ghostlock load|trigger|spray|write|pwn|info\n", cmd);
        return 2;
    }
}

/* ── load: 动态加载 SO/ELF ──────────────────────────────────── */

static int cmd_load(int argc, char *argv[]) {
    if (argc < 1) {
        fprintf(stderr, "用法: ghostlock load <文件路径> [入口符号名]\n");
        return 1;
    }

    const char *path = argv[0];
    const char *entry_sym = (argc >= 2) ? argv[1] : "ghostlock_entry";

    LOGI("cmd_load: path=%s, entry=%s", path, entry_sym);

    /* 检查文件是否存在 */
    FILE *test = fopen(path, "rb");
    if (test == NULL) {
        LOGE("cmd_load: 无法打开文件 %s", path);
        fprintf(stderr, "错误: 无法打开文件 %s\n", path);
        return 3;
    }
    fclose(test);

    /* dlopen 加载 */
    void *handle = dlopen(path, RTLD_NOW);
    if (handle == NULL) {
        const char *err = dlerror();
        LOGE("cmd_load: dlopen 失败: %s", err ? err : "unknown");
        fprintf(stderr, "错误: dlopen 失败: %s\n", err ? err : "unknown");
        return 4;
    }
    LOGI("cmd_load: dlopen 成功, handle=%p", handle);

    /* 查找入口符号 */
    dlerror(); /* 清除之前的错误 */
    typedef int (*entry_fn_t)(int, char *[]);
    entry_fn_t entry = (entry_fn_t)dlsym(handle, entry_sym);
    const char *sym_err = dlerror();
    if (sym_err != NULL) {
        LOGE("cmd_load: dlsym('%s') 失败: %s", entry_sym, sym_err);
        fprintf(stderr, "错误: 符号 '%s' 未找到: %s\n", entry_sym, sym_err);
        dlclose(handle);
        return 5;
    }
    LOGI("cmd_load: dlsym('%s') 成功, func=%p", entry_sym, entry);

    /* 调用入口函数 */
    char *load_args[] = {"ghostlock-payload", NULL};
    int ret = entry(1, load_args);
    LOGI("cmd_load: entry 返回 %d", ret);

    dlclose(handle);
    fprintf(stdout, "加载成功, 入口返回: %d\n", ret);
    return ret;
}

/* ── info: 编译信息 ──────────────────────────────────────────── */

static int cmd_info(int argc, char *argv[]) {
    (void)argc;
    (void)argv;

    fprintf(stdout,
        "GhostLock Native Bridge\n"
        "───────────────────────\n"
        "CVE:     CVE-2026-43499 (GhostLock)\n"
        "类型:     Linux 内核 rt_mutex futex PI 栈 UAF 提权\n"
        "影响:     2.6.39 ~ 7.1 (含当前 kernel 6.6.89)\n"
        "CVSS:     7.8 HIGH\n"
        "状态:     loader 就绪, exploit 阶段待接入\n"
        "───────────────────────\n"
        "架构:     " __aarch64__ ? "aarch64" : "unknown" "\n"
        "编译:     " __DATE__ " " __TIME__ "\n"
        "工具链:   " __VERSION__ "\n"
        "命令:\n"
        "  load <path> [entry]  动态加载 SO/ELF 并调用入口\n"
        "  trigger              触发 GhostLock UAF（待实现）\n"
        "  spray                栈喷射（待实现）\n"
        "  write                受限写入原语（待实现）\n"
        "  pwn                  完整提权（待实现）\n"
        "  info                 显示此信息\n"
    );
    return 0;
}

/* ── JNI 入口 ────────────────────────────────────────────────── */

JNIEXPORT jint JNICALL
Java_com_ghostlock_skeleton_NativeBridge_execute(JNIEnv *env, jclass clazz, jobjectArray args) {
    if (args == NULL) {
        LOGE("execute: args is null");
        return -1;
    }

    jint argc = (*env)->GetArrayLength(env, args);
    if (argc == 0) {
        LOGI("execute: no args, nothing to do");
        return 0;
    }

    /* 将 Java String[] 转为 C char*[] */
    char **argv = (char **)calloc((size_t)(argc + 1), sizeof(char *));
    if (argv == NULL) {
        LOGE("execute: calloc failed");
        return -2;
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

    /* 调用 ghostlock_main */
    int ret = ghostlock_main((int)argc, argv);

    /* 清理 */
    for (jint i = 0; i < argc; i++) {
        free(argv[i]);
    }
    free(argv);

    return ret;
}
