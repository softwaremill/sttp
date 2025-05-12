#if defined(STTP_CURL_FFI)
#include <curl/curl.h>

int sttp_curl_setopt_int(CURL *curl, CURLoption opt, int arg) {return curl_easy_setopt(curl, opt, arg); }
int sttp_curl_setopt_long(CURL *curl, CURLoption opt, long arg) {return curl_easy_setopt(curl, opt, arg); }
int sttp_curl_setopt_pointer(CURL *curl, CURLoption opt, void* arg) {return curl_easy_setopt(curl, opt, arg); }
const char* sttp_curl_get_version() {
    return curl_version_info(CURLVERSION_NOW)->version;
}
int sttp_curl_getinfo_pointer(CURL *curl, CURLINFO info, void* arg) {return curl_easy_getinfo(curl, info, arg); }
#endif
