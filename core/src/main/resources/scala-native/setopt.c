#include <stdarg.h>
#include <curl/curl.h>

void setopt_wrapper(CURL *curl, CURLoption option, va_list list)
{
    void *a = va_arg(list, void*);
    curl_easy_setopt(curl, option, a);
}

void setopt_wrapper_ptr(CURL *curl, CURLoption option, void* ptr)
{
    curl_easy_setopt(curl, option, ptr);
}
