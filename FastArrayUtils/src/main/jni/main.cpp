//
// Created by alex on 3/16/19.
//

#include <jni.h>

#include "common.h"

#define REQ_JNI_VERSION JNI_VERSION_1_6

#define CLASS_NAME PKG_NAME "/FastArrayUtils"

static jclass g_NullPointerException_C;
static jclass g_IndexOutOfBoundsException_C;

// Loops over even primitives seems extremely inefficient while executed by ART

template<typename AT>
static inline jint HIDDEN
_argumentsCheck(JNIEnv *const env, const AT array, const jint start, jint end) {
    if (start < 0) {
        env->ThrowNew(g_IndexOutOfBoundsException_C, "Start is negative");
        return -1;
    }
    if (array == NULL) {
        env->ThrowNew(g_NullPointerException_C, "Array cannot be null");
        return -1;
    }
    const jsize arrayLen = env->GetArrayLength(array);
    if (env->ExceptionCheck() == JNI_TRUE) return -1;
    if (end > arrayLen || end < 0) end = arrayLen;
    if (start > end) {
        env->ThrowNew(g_IndexOutOfBoundsException_C, "Start exceeds end or array bounds");
        return -1;
    }
    return end;
}

template<typename T, typename AT>
static jint JNICALL
m_indexOf(JNIEnv *const env, const jobject jthis, const AT array,
          const jint start, jint end, const T value) {
    end = _argumentsCheck(env, array, start, end);
    if (end < 0) return -1;
    if (start == end) return -1;
    jint i = start;
    T *const ptr = (T *) env->GetPrimitiveArrayCritical(array, nullptr);
    for (; i < end; ++i)
        if (ptr[i] == value) break;
    env->ReleasePrimitiveArrayCritical(array, ptr, JNI_ABORT);
    return i < end ? i : -1;
}

template<typename T, typename AT>
static jint JNICALL
m_getArrayEqualElementsLength(JNIEnv *const env, const jobject jthis, const AT array,
                              const jint start, jint end) {
    end = _argumentsCheck(env, array, start, end);
    if (end < 0) return 0;
    if (start == end) return 0;
    T *const ptr = (T *) env->GetPrimitiveArrayCritical(array, nullptr);
    T *const sp = ptr + start;
    T *const ep = ptr + end;
    const T v = *sp;
    T *p = sp + 1;
    for (; p < ep; ++p)
        if (v != *p) break;
    env->ReleasePrimitiveArrayCritical(array, ptr, JNI_ABORT);
    return (jint) (p - sp);
}

static JNINativeMethod methodTable[] = {
        {"indexOf",                "([BIIB)I", (void *) m_indexOf<jbyte, jbyteArray>},
        {"indexOf",                "([CIIC)I", (void *) m_indexOf<jchar, jcharArray>},
        {"indexOf",                "([IIII)I", (void *) m_indexOf<jint, jintArray>},
        {"getEqualElementsLength", "([BII)I",  (void *) m_getArrayEqualElementsLength<jbyte, jbyteArray>},
        {"getEqualElementsLength", "([CII)I",  (void *) m_getArrayEqualElementsLength<jchar, jcharArray>},
        {"getEqualElementsLength", "([III)I",  (void *) m_getArrayEqualElementsLength<jint, jintArray>}
};

extern "C"
JNIEXPORT jint JNI_OnLoad(JavaVM *const vm, void *const reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), REQ_JNI_VERSION) != JNI_OK) {
        return -1;
    }

    g_NullPointerException_C = (jclass) env->NewGlobalRef(
            env->FindClass("java/lang/NullPointerException"));
    if (env->ExceptionCheck() == JNI_TRUE) return -1;
    g_IndexOutOfBoundsException_C = (jclass) env->NewGlobalRef(
            env->FindClass("java/lang/IndexOutOfBoundsException"));
    if (env->ExceptionCheck() == JNI_TRUE) return -1;

    env->RegisterNatives(env->FindClass(CLASS_NAME), methodTable, SIZEOFTBL(methodTable));

    return REQ_JNI_VERSION;
}

extern "C"
JNIEXPORT void JNI_OnUnload(JavaVM *const vm, void *const reserved) {
    JNIEnv *env;
    vm->GetEnv(reinterpret_cast<void **>(&env), REQ_JNI_VERSION);

    env->DeleteGlobalRef(g_NullPointerException_C);
    env->DeleteGlobalRef(g_IndexOutOfBoundsException_C);
}
