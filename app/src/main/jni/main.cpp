//
// Created by alex on 4/16/19.
//

#include <sys/socket.h>
#include <sys/un.h>
#include <sys/user.h>
#include <errno.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <stdio.h>
#include <stdlib.h>
#include <termios.h>
#include <signal.h>

#include <android/log.h>

typedef struct {
    bool raw;
} options_t;

typedef struct {
    const size_t field_offset;

    void (*const field_setter)(void *const field_ptr, const char *const value);

    const char *const arg;
    const size_t arg_len;
} option_desc_t;

static void field_bool(void *const field_ptr, const char *const value) {
    *(bool *) field_ptr = true;
}

#define OPT_DESC(F, S, A) {offsetof(options_t, F), (S), (A), sizeof(A) - 1}

static const option_desc_t options_desc[] = {
        OPT_DESC(raw, field_bool, "-r"),
        OPT_DESC(raw, field_bool, "--raw")
};

static size_t getOpts(options_t *const options, const int argc, const char *const *const argv) {
    size_t i = 0;
    for (; i < argc; ++i) {
        for (int oi = 0; oi < (sizeof(options_desc) / sizeof(options_desc[0])); ++oi) {
            const option_desc_t *od = options_desc + oi;
            const char *const arg = argv[i];
            if (od->arg[od->arg_len - 1] == '=' ?
                strncmp(od->arg, arg, od->arg_len) == 0 :
                strcmp(od->arg, arg) == 0) {
                od->field_setter(options + od->field_offset, arg + od->arg_len);
                goto next;
            }
        }
        break;
        next:;
    }
    return i;
}

static ssize_t sendFds(const int sockfd, const void *const data, const size_t len,
                       const int *const fds, const size_t fdsc) {
    const size_t cmsg_space = CMSG_SPACE(sizeof(int) * fdsc);
    const size_t cmsg_len = CMSG_LEN(sizeof(int) * fdsc);
    if (cmsg_space >= PAGE_SIZE) {
        errno = ENOMEM;
        return -1;
    }
    alignas(struct cmsghdr) char cmsg_buf[cmsg_space];
    iovec iov = {.iov_base = const_cast<void *>(data), .iov_len = len};
    msghdr msg = {
            .msg_name = nullptr,
            .msg_namelen = 0,
            .msg_iov = &iov,
            .msg_iovlen = 1,
            .msg_control = cmsg_buf,
            // We can't cast to the actual type of the field, because it's different across platforms.
            .msg_controllen = static_cast<unsigned int>(cmsg_space),
            .msg_flags = 0,
    };
    struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);
    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type = SCM_RIGHTS;
    cmsg->cmsg_len = cmsg_len;
    int *cmsg_fds = reinterpret_cast<int *>(CMSG_DATA(cmsg));
    for (size_t i = 0; i < fdsc; ++i) {
        cmsg_fds[i] = fds[i];
    }
#if defined(__linux__)
    int flags = MSG_NOSIGNAL;
#else
    int flags = 0;
#endif
    return TEMP_FAILURE_RETRY(sendmsg(sockfd, &msg, flags));
}

static bool saved_in = false;
static struct termios def_mode_in;
static bool saved_out = false;
static struct termios def_mode_out;

static void saveMode() {
    if (tcgetattr(STDIN_FILENO, &def_mode_in) == 0) saved_in = true;
    if (tcgetattr(STDOUT_FILENO, &def_mode_out) == 0) saved_out = true;
}

static void setRawMode() {
    if (saved_in) {
        struct termios mode_in = def_mode_in;
        cfmakeraw(&mode_in);
        tcsetattr(STDIN_FILENO, TCSANOW, &mode_in);
    }
    if (saved_out) {
        struct termios mode_out = def_mode_out;
        cfmakeraw(&mode_out);
        tcsetattr(STDOUT_FILENO, TCSANOW, &mode_out);
    }
}

static void restoreMode() {
    if (saved_in) tcsetattr(STDIN_FILENO, TCSANOW, &def_mode_in);
    if (saved_out) tcsetattr(STDOUT_FILENO, TCSANOW, &def_mode_out);
}

static void _onExit() {
    restoreMode();
}

static void _onSignalExit(int s) {
    exit(1);
}

int main(const int argc, const char *const *const argv) {
    options_t options = {.raw = false};

    int c_argc = argc - 1;
    const char *const *c_argv = argv + 1;
    const size_t args_offset = getOpts(&options, c_argc, c_argv);
    c_argc -= args_offset;
    c_argv += args_offset;

    saveMode();
    atexit(_onExit);
    signal(SIGTERM, _onSignalExit);
    signal(SIGINT, _onSignalExit);
    signal(SIGPIPE, _onSignalExit);
    signal(SIGUSR1, _onSignalExit);
    signal(SIGUSR2, _onSignalExit);

    if (options.raw) setRawMode();

    const char socketName[] = "\0" APPLICATION_ID ".termsh";
    const int sock = socket(AF_LOCAL, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (sock < 0) {
        perror("Can't create socket");
        exit(1);
    }
    struct sockaddr_un sockAddr;
    sockAddr.sun_family = AF_LOCAL;
    memcpy(sockAddr.sun_path, socketName, sizeof(socketName));
    if (connect(sock, (struct sockaddr *) &sockAddr,
                sizeof(socketName) - 1 + offsetof(struct sockaddr_un, sun_path)) < 0) {
        close(sock);
        perror("Can't connect to termsh server");
        exit(1);
    }
    struct ucred cr;
    socklen_t cr_len = sizeof(cr);
    if (getsockopt(sock, SOL_SOCKET, SO_PEERCRED, &cr, &cr_len) != 0) {
        close(sock);
        perror("Can't check termsh server");
        exit(1);
    }
    const char *const uid_s = getenv("TERMSH_UID");
    int uid;
    if (!uid_s || (uid = atoi(uid_s)) == 0) uid = getuid();
    if (cr.uid != uid) {
        close(sock);
        fprintf(stderr, "Spoofing detected!\n");
        __android_log_write(ANDROID_LOG_ERROR, "termsh", "Spoofing detected!");
        exit(1);
    }
    {
        char buf[PATH_MAX];
        if (getcwd(buf, sizeof(buf)) == nullptr) {
            close(sock);
            perror("Error sending CWD");
            exit(1);
        }
        const size_t l = strlen(buf);
        const uint32_t _l = htonl(l); // always big-endian
        if (write(sock, &_l, 4) < 0 || write(sock, buf, l) < 0) {
            close(sock);
            perror("Error sending CWD");
            exit(1);
        }
    }
    const int fds[] = {STDIN_FILENO, STDOUT_FILENO, STDERR_FILENO};
    const char _argc = (char) c_argc;
    if (sendFds(sock, &_argc, 1, fds, 3) < 0) goto error_args;
    for (int i = 0; i < c_argc; ++i) {
        const size_t l = strlen(c_argv[i]);
        const uint32_t _l = htonl(l); // always big-endian
        if (write(sock, &_l, 4) < 0) goto error_args;
        if (write(sock, c_argv[i], l) < 0) goto error_args;
    }
    while (true) {
        char result;
        const ssize_t r = read(sock, &result, 1);
        if (r < 0) {
            close(sock);
            perror("Error receiving result");
            exit(1);
        }
        if (r == 0) {
            close(sock);
            perror("Error parsing arguments");
            exit(1);
        }
        close(sock);
        exit(result);
    }
    error_args:
    close(sock);
    perror("Error sending arguments");
    exit(1);
}
