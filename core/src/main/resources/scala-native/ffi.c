#if defined(STTP_CURL_FFI)
#include <curl/curl.h>
#include <stdlib.h>
#include <string.h>

int sttp_curl_setopt_int(CURL *curl, CURLoption opt, int arg) {return curl_easy_setopt(curl, opt, arg); }
int sttp_curl_setopt_long(CURL *curl, CURLoption opt, long arg) {return curl_easy_setopt(curl, opt, arg); }
int sttp_curl_setopt_pointer(CURL *curl, CURLoption opt, void* arg) {return curl_easy_setopt(curl, opt, arg); }
const char* sttp_curl_get_version() {
    return curl_version_info(CURLVERSION_NOW)->version;
}
int sttp_curl_getinfo_pointer(CURL *curl, CURLINFO info, void* arg) {return curl_easy_getinfo(curl, info, arg); }

/* Write callback, appending data to a NUL-terminated buffer (CurlFetch in Scala).
   Written in C, so that no managed code runs while a @blocking curl call is in progress. */
struct sttp_curl_fetch {
    char *data;
    size_t size;
};

static size_t sttp_curl_write_to_buffer(char *ptr, size_t size, size_t nmemb, void *userdata) {
    struct sttp_curl_fetch *fetch = (struct sttp_curl_fetch *)userdata;
    size_t increment = size * nmemb;
    char *data = realloc(fetch->data, fetch->size + increment + 1);
    if (data == NULL) return 0; /* fails the transfer with CURLE_WRITE_ERROR */
    memcpy(data + fetch->size, ptr, increment);
    fetch->data = data;
    fetch->size += increment;
    data[fetch->size] = 0;
    return increment;
}

int sttp_curl_setopt_write_to_buffer(CURL *curl) {
    return curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, sttp_curl_write_to_buffer);
}

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
