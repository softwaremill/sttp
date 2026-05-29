
## CookieStorage / FollowRedirectsBackend (feature/cookie-storage-redirects)
- Hot path: FollowRedirectsBackend wraps all backends; sendWithCounter runs per request + per redirect hop.
- No-storage common case: applyStoredCookies + updateStoredCookies each do request.attribute(key) = AttributeMap.get = Map.get(typeName String) -> Option. AttributeMap.get returns Some/None then matches None. Near-zero cost (one immutable Map.get + boxing). Acceptable.
- updateStoredCookies only called on redirect responses (not common path) - fine.
- CookieStorage.cookiesFor: iterates ALL entries (no domain index) - O(n) per request; n = total cookies stored. Fine for jar use.
- Uri.path is `def` returning .toList (allocates) every call; pathOf builds "/"+mkString - allocs per cookiesFor.
- parseSetCookie: split(";") + .toList + attrs .toMap + Try(toLong) per Set-Cookie header. Only on redirect responses with storage. Try-based long parse allocs on success path too.
