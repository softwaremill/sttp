#if defined(STTP_CURL_FFI)
#include <curl/curl.h>

int sttp_curl_setopt_int(CURL *curl, CURLoption opt, int arg) {return curl_easy_setopt(curl, opt, arg); }
int sttp_curl_setopt_long(CURL *curl, CURLoption opt, long arg) {return curl_easy_setopt(curl, opt, arg); }
int sttp_curl_setopt_pointer(CURL *curl, CURLoption opt, void* arg) {return curl_easy_setopt(curl, opt, arg); }
const char* sttp_curl_get_version() {
    return curl_version_info(CURLVERSION_NOW)->version;
}
int sttp_curl_getinfo_pointer(CURL *curl, CURLINFO info, void* arg) {return curl_easy_getinfo(curl, info, arg); }

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
