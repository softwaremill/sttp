package sttp.client4.curl.internal

private[client4] object CurlInfo extends Enumeration {
  type CurlInfo = Value

  private val String: Int = 0x100000
  private val Long: Int = 0x200000
  private val Double: Int = 0x300000
  private val Slist: Int = 0x400000
  private val Ptr: Int = 0x400000
  private val Socket: Int = 0x500000
  private val OffT: Int = 0x600000
  private val Mask: Int = 0x0fffff
  private val Typemask: Int = 0xf00000

  val EffectiveUrl = Value(String + 1)
  val ResponseCode = Value(Long + 2)
  val TotalTime = Value(Double + 3)
  val NamelookupTime = Value(Double + 4)
  val ConnectTime = Value(Double + 5)
  val PretransferTime = Value(Double + 6)
  val SizeUpload = Value(Double + 7)
  val SizeUploadT = Value(OffT + 7)
  val SizeDownload = Value(Double + 8)
  val SizeDownloadT = Value(OffT + 8)
  val SpeedDownload = Value(Double + 9)
  val SpeedDownloadT = Value(OffT + 9)
  val SpeedUpload = Value(Double + 10)
  val SpeedUploadT = Value(OffT + 10)
  val HeaderSize = Value(Long + 11)
  val RequestSize = Value(Long + 12)
  val SslVerifyresult = Value(Long + 13)
  val Filetime = Value(Long + 14)
  val FiletimeT = Value(OffT + 14)
  val ContentLengthDownload = Value(Double + 15)
  val ContentLengthDownloadT = Value(OffT + 15)
  val ContentLengthUpload = Value(Double + 16)
  val ContentLengthUploadT = Value(OffT + 16)
  val StarttransferTime = Value(Double + 17)
  val ContentType = Value(String + 18)
  val RedirectTime = Value(Double + 19)
  val RedirectCount = Value(Long + 20)
  val Private = Value(String + 21)
  val HttpConnectcode = Value(Long + 22)
  val HttpauthAvail = Value(Long + 23)
  val ProxyauthAvail = Value(Long + 24)
  val OsErrno = Value(Long + 25)
  val NumConnects = Value(Long + 26)
  val SslEngines = Value(Slist + 27)
  val Cookielist = Value(Slist + 28)
  val Lastsocket = Value(Long + 29)
  val FtpEntryPath = Value(String + 30)
  val RedirectUrl = Value(String + 31)
  val PrimaryIp = Value(String + 32)
  val AppconnectTime = Value(Double + 33)
  val Certinfo = Value(Ptr + 34)
  val ConditionUnmet = Value(Long + 35)
  val RtspSessionId = Value(String + 36)
  val RtspClientCseq = Value(Long + 37)
  val RtspServerCseq = Value(Long + 38)
  val RtspCseqRecv = Value(Long + 39)
  val PrimaryPort = Value(Long + 40)
  val LocalIp = Value(String + 41)
  val LocalPort = Value(Long + 42)
  val TlsSession = Value(Ptr + 43)
  val Activesocket = Value(Socket + 44)
  val TlsSslPtr = Value(Ptr + 45)
  val HttpVersion = Value(Long + 46)
  val ProxySslVerifyresult = Value(Long + 47)
  val Protocol = Value(Long + 48)
  val Scheme = Value(String + 49)
  val TotalTimeT = Value(OffT + 50)
  val NamelookupTimeT = Value(OffT + 51)
  val ConnectTimeT = Value(OffT + 52)
  val PretransferTimeT = Value(OffT + 53)
  val StarttransferTimeT = Value(OffT + 54)
  val RedirectTimeT = Value(OffT + 55)
  val AppconnectTimeT = Value(OffT + 56)
}
