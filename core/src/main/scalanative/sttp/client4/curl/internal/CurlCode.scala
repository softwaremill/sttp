package sttp.client4.curl.internal

private[client4] object CurlCode extends Enumeration {
  type CurlCode = Value
  val Ok = Value(0, "OK")
  val UnsupportedProtocol = Value(1, "UNSUPPORTED_PROTOCOL")
  val FailedInit = Value(2, "FAILED_INIT")
  val UrlMalformat = Value(3, "URL_MALFORMAT")
  val NotBuiltIn = Value(4, "NOT_BUILT_IN")
  val CouldntResolveProxy = Value(5, "COULDNT_RESOLVE_PROXY")
  val CouldntResolveHost = Value(6, "COULDNT_RESOLVE_HOST")
  val CouldntConnect = Value(7, "COULDNT_CONNECT")
  val WeirdServerReply = Value(8, "WEIRD_SERVER_REPLY")
  val RemoteAccessDenied = Value(9, "REMOTE_ACCESS_DENIED")
  val FtpAccessFailed = Value(10, "FTP_ACCEPT_FAILED")
  val FtpWeirdPassReply = Value(11, "FTP_WEIRD_PASS_REPLY")
  val FtpAccessTimeout = Value(12, "FTP_ACCEPT_TIMEOUT")
  val FtpWeirdPasvReply = Value(13, "FTP_WEIRD_PASV_REPLY")
  val FtpWeird227Format = Value(14, "FTP_WEIRD_227_FORMAT")
  val FtpCantGetHost = Value(15, "FTP_CANT_GET_HOST")
  val Http2 = Value(16, "HTTP2")
  val FtpCouldntSetType = Value(17, "FTP_COULDNT_SET_TYPE")
  val PartialFile = Value(18, "PARTIAL_FILE")
  val FtpCouldntRetrFile = Value(19, "FTP_COULDNT_RETR_FILE")
  val Obsolete20 = Value(20, "OBSOLETE20")
  val QuoteError = Value(21, "QUOTE_ERROR")
  val HttpReturnedError = Value(22, "HTTP_RETURNED_ERROR")
  val WriteError = Value(23, "WRITE_ERROR")
  val Obsolete24 = Value(24, "OBSOLETE24")
  val UploadFailed = Value(25, "UPLOAD_FAILED")
  val ReadError = Value(26, "READ_ERROR")
  val OutOfMemory = Value(27, "OUT_OF_MEMORY")
  val OperationTimedOut = Value(28, "OPERATION_TIMEDOUT")
  val Obsolete29 = Value(29, "OBSOLETE29")
  val FtpPortFailed = Value(30, "FTP_PORT_FAILED")
  val FtpCouldntUseRest = Value(31, "FTP_COULDNT_USE_REST")
  val Obsolete32 = Value(32, "OBSOLETE32")
  val RangeError = Value(33, "RANGE_ERROR")
  val HttpPortError = Value(34, "HTTP_POST_ERROR")
  val SslConnectError = Value(35, "SSL_CONNECT_ERROR")
  val BadDownloadResume = Value(36, "BAD_DOWNLOAD_RESUME")
  val FileCouldntReadFile = Value(37, "FILE_COULDNT_READ_FILE")
  val LdapCannotBind = Value(38, "LDAP_CANNOT_BIND")
  val LdapSearchFailed = Value(39, "LDAP_SEARCH_FAILED")
  val Obsolete40 = Value(40, "OBSOLETE40")
  val FunctionNotFound = Value(41, "FUNCTION_NOT_FOUND")
  val AbortedByCallback = Value(42, "ABORTED_BY_CALLBACK")
  val BadFunctionArgument = Value(43, "BAD_FUNCTION_ARGUMENT")
  val Obsolete44 = Value(44, "OBSOLETE44")
  val InterfaceFailed = Value(45, "INTERFACE_FAILED")
  val Obsolete46 = Value(46, "OBSOLETE46")
  val TooManyRedirects = Value(47, "TOO_MANY_REDIRECTS")
  val UnknownOption = Value(48, "UNKNOWN_OPTION")
  val TelnetOptionSyntax = Value(49, "TELNET_OPTION_SYNTAX")
  val Obsolete50 = Value(50, "OBSOLETE50")
  val PeerFailedVerification = Value(51, "PEER_FAILED_VERIFICATION")
  val GotNothing = Value(52, "GOT_NOTHING")
  val SslEngineNotFound = Value(53, "SSL_ENGINE_NOTFOUND")
  val SslEngineSetFailed = Value(54, "SSL_ENGINE_SETFAILED")
  val SendError = Value(55, "SEND_ERROR")
  val RecvError = Value(56, "RECV_ERROR")
  val Obsolete57 = Value(57, "OBSOLETE57")
  val SslCertProblem = Value(58, "SSL_CERTPROBLEM")
  val SslCipher = Value(59, "SSL_CIPHER")
  val SslCacert = Value(60, "SSL_CACERT")
  val BadContentEncoding = Value(61, "BAD_CONTENT_ENCODING")
  val LdapInvalidUrl = Value(62, "LDAP_INVALID_URL")
  val FileSizeExceeded = Value(63, "FILESIZE_EXCEEDED")
  val UseSslFailed = Value(64, "USE_SSL_FAILED")
  val SendFailRewind = Value(65, "SEND_FAIL_REWIND")
  val SslEngineInitFailed = Value(66, "SSL_ENGINE_INITFAILED")
  val LoginDenied = Value(67, "LOGIN_DENIED")
  val TftpNotFound = Value(68, "TFTP_NOTFOUND")
  val TftpPerm = Value(69, "TFTP_PERM")
  val RemoteDiskFull = Value(70, "REMOTE_DISK_FULL")
  val TftpIllegal = Value(71, "TFTP_ILLEGAL")
  val TftpUnknownId = Value(72, "TFTP_UNKNOWNID")
  val RemoteFileExists = Value(73, "REMOTE_FILE_EXISTS")
  val TftpNoSuchUser = Value(74, "TFTP_NOSUCHUSER")
  val ConvFailed = Value(75, "CONV_FAILED")
  val ConvReqd = Value(76, "CONV_REQD")
  val SslCacertBadfile = Value(77, "SSL_CACERT_BADFILE")
  val RemoteFileNotFound = Value(78, "REMOTE_FILE_NOT_FOUND")
  val Ssh = Value(79, "SSH")
  val SslShutdownFailed = Value(80, "SSL_SHUTDOWN_FAILED")
  val Again = Value(81, "AGAIN")
  val SslCrlBadFile = Value(82, "SSL_CRL_BADFILE")
  val SslIssuerError = Value(83, "SSL_ISSUER_ERROR")
  val FtpPretFailed = Value(84, "FTP_PRET_FAILED")
  val RtspCseqError = Value(85, "RTSP_CSEQ_ERROR")
  val RtspSessionError = Value(86, "RTSP_SESSION_ERROR")
  val FtpBadFileList = Value(87, "FTP_BAD_FILE_LIST")
  val ChunkFailed = Value(88, "CHUNK_FAILED")
  val NoConnectionAvailable = Value(89, "NO_CONNECTION_AVAILABLE")
  val SslPinnedPubKeyNoMatch = Value(90, "SSL_PINNEDPUBKEYNOTMATCH")
  val SslInvalidCertStatus = Value(91, "SSL_INVALIDCERTSTATUS")
  val Http2Stream = Value(92, "HTTP2_STREAM")
}
