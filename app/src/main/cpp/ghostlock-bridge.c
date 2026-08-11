#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define TAG "GhostLockNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/*
 * 未来对接 ghostlock ELF 的真实入口。
 * 当前为占位实现，返回参数列表作为确认。
 */
extern int ghostlock_main(int argc, char *argv[]);

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

    /* 调用真正的入口 */
    int ret = ghostlock_main((int)argc, argv);

    /* 清理 */
    for (jint i = 0; i < argc; i++) {
        free(argv[i]);
    }
    free(argv);

    return ret;
}

/*
 * 占位 ghostlock_main —— 未来会替换为真正的 exploit 入口。
 */
int ghostlock_main(int argc, char *argv[]) {
    LOGI("ghostlock_main: placeholder, argc=%d", argc);
    for (int i = 0; i < argc; i++) {
        LOGI("  arg[%d] = %s", i, argv[i]);
    }
    return 0;
}
