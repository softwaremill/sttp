package sttp.client4.curl.internal

private[client4] object CurlMCode extends Enumeration {
  type CurlMCode = Value
  val CallMultiPerform = Value(-1, "CALL_MULTI_PERFORM")
  val Ok = Value(0, "OK")
  val BadHandle = Value(1, "BAD_HANDLE")
  val BadEasyHandle = Value(2, "BAD_EASY_HANDLE")
  val OutOfMemory = Value(3, "OUT_OF_MEMORY")
  val InternalError = Value(4, "INTERNAL_ERROR")
  val BadSocket = Value(5, "BAD_SOCKET")
  val UnknownOption = Value(6, "UNKNOWN_OPTION")
  val AddedAlready = Value(7, "ADDED_ALREADY")
  val RecursiveApiCall = Value(8, "RECURSIVE_API_CALL")
  val WakeupFailure = Value(9, "WAKEUP_FAILURE")
  val BadFunctionArgument = Value(10, "BAD_FUNCTION_ARGUMENT")
  val AbortedByCallback = Value(11, "ABORTED_BY_CALLBACK")
  val UnrecoverablePoll = Value(12, "UNRECOVERABLE_POLL")
}
