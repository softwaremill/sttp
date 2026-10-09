#if defined(STTP_CURL_FFI)
#include <curl/curl.h>

int sttp_curl_setopt_int(CURL *curl, CURLoption opt, int arg) {return curl_easy_setopt(curl, opt, arg); }
int sttp_curl_setopt_long(CURL *curl, CURLoption opt, long arg) {return curl_easy_setopt(curl, opt, arg); }
int sttp_curl_setopt_pointer(CURL *curl, CURLoption opt, void* arg) {return curl_easy_setopt(curl, opt, arg); }
const char* sttp_curl_get_version() {
    return curl_version_info(CURLVERSION_NOW)->version;
}
int sttp_curl_getinfo_pointer(CURL *curl, CURLINFO info, void* arg) {return curl_easy_getinfo(curl, info, arg); }

/* curl_multi_setopt is variadic, so it can't be bound directly */
int sttp_curl_multi_setopt_pointer(CURLM *multi, CURLMoption opt, void *arg) { return curl_multi_setopt(multi, opt, arg); }
int sttp_curl_multi_setopt_long(CURLM *multi, CURLMoption opt, long arg) { return curl_multi_setopt(multi, opt, arg); }

/* Non-blocking readiness check of a socket: want = 1 (read), 2 (write), 3 (both).
   Returns non-zero if the socket is ready (or in an error/hangup state). Used to drain edge-triggered notifications. */
#ifdef _WIN32
int sttp_curl_fd_ready(int fd, int want) { return 1; }
#else
#include <poll.h>
int sttp_curl_fd_ready(int fd, int want) {
    struct pollfd p;
    p.fd = fd;
    p.events = ((want & 1) ? POLLIN : 0) | ((want & 2) ? POLLOUT : 0);
    p.revents = 0;
    return poll(&p, 1, 0) > 0 && p.revents != 0;
}
#endif

/* curl_multi wrappers */
/* Most curl_multi functions are called directly via @extern bindings.
   This wrapper exists because CURLMsg contains a union that can't be
   safely represented in Scala Native's type system. */
int sttp_curl_multi_info_read_result(CURLM *multi, CURL **easy_out) {
    int msgs_in_queue;
    CURLMsg *msg = curl_multi_info_read(multi, &msgs_in_queue);
    if (msg && msg->msg == CURLMSG_DONE) {
        if (easy_out) *easy_out = msg->easy_handle;
        return (int)msg->data.result;
    }
    return -1; /* no completed transfer */
}
#endif
