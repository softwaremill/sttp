package com.softwaremill.sttp.testing.streaming

import com.softwaremill.sttp.testing.AsyncExecutionContext

trait StreamingTestExtensions[R[_], S] extends AsyncExecutionContext {}
