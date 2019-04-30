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

#include <android/log.h>

ssize_t sendFds(const int sockfd, const void *const data, const size_t len,
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

int main(int argc, char **argv) {
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
        perror("Can't connect to termsh backend");
        exit(1);
    }
    struct ucred cr;
    socklen_t cr_len = sizeof(cr);
    if (getsockopt(sock, SOL_SOCKET, SO_PEERCRED, &cr, &cr_len) != 0) {
        close(sock);
        perror("Can't check termsh backend");
        exit(1);
    }
    if (cr.uid != getuid()) {
        close(sock);
        fprintf(stderr, "Spoofing detected!\n");
        __android_log_write(ANDROID_LOG_ERROR, "termsh", "Spoofing detected!");
        exit(1);
    }
    const int fds[] = {0, 1, 2};
    const char _argc = (char) (argc - 1);
    if (sendFds(sock, &_argc, 1, fds, 3) < 0) goto error_args;
    for (int i = 1; i < argc; ++i) {
        const size_t l = strlen(argv[i]);
        const uint32_t _l = htonl(l); // always big-endian
        if (write(sock, &_l, 4) < 0) goto error_args;
        if (write(sock, argv[i], l) < 0) goto error_args;
    }
    close(0);
    close(1);
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
