# How to test manually proxy with digest authentication

First of all you will need a local proxy. Below is an instruction of how to setup squid proxy.

## Setup squid proxy
Download squid-4.9.tar.gz from http://www.squid-cache.org/Versions/

1. extract archive
2. ```
   ./configure --enable-auth \
   --prefix=/usr \
   --localstatedir=/var \
   --libexecdir=${prefix}/lib/squid \
   --datadir=${prefix}/share/squid \
   --sysconfdir=/etc/squid \
   --with-default-user=proxy \
   --with-logdir=/var/log/squid \
   --with-pidfile=/var/run/squid.pid
   ```
3. `make`
4. `sudo make install`

## Configure squid proxy

Paste following options into the squid configuration (by default /etc/squid/squid.conf)

```
auth_param digest program /lib/squid/digest_file_auth -c /etc/squid/user_squid
auth_param digest children 20 startup=0 idle=1
auth_param digest realm WEBPROXY
auth_param digest nonce_garbage_interval 5 minutes
auth_param digest nonce_max_duration 30 minutes
auth_param digest nonce_max_count 50


acl auth_users proxy_auth REQUIRED
http_access allow auth_users
```

Create users:

`htdigest /etc/squid/user_squid WEBPROXY kasper`

Reload squid configuration:

`sudo squid -k reconfigure `

Run curl to verify if everything works:

`curl --proxy-digest -U kasper:<PASSWORD> --proxy http://localhost:3128 google.com -v`

At the end you should get:
```
<HTML><HEAD><meta http-equiv="content-type" content="text/html;charset=utf-8">
<TITLE>301 Moved</TITLE></HEAD><BODY>
<H1>301 Moved</H1>
The document has moved
<A HREF="http://www.google.com/">here</A>.
</BODY></HTML>
* Connection #0 to host localhost left intact
```

## Running the test
`okhttpBackend/testOnly sttp.client.okhttp.OkHttpSyncDigestAuthProxyManualTest`
