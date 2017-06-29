package com.softwaremill.sttp

// from https://gist.github.com/teigen/5865923
object UriInterpolator {

  private val unreserved = {
    val alphanum = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toSet
    val mark     = Set('-', '_', '.', '!', '~', '*', '\'', '(', ')')
    alphanum ++ mark
  }

  implicit class UriContext(val sc:StringContext) extends AnyVal {
    def uri(args:String*) = {
      val strings     = sc.parts.iterator
      val expressions = args.iterator
      val sb          = new StringBuffer(strings.next())

      while(strings.hasNext){
        for(c <- expressions.next()){
          if(unreserved(c))
            sb.append(c)
          else for(b <- c.toString.getBytes("UTF-8")){
            sb.append("%")
            sb.append("%02X".format(b))
          }
        }
        sb.append(strings.next())
      }
      sb.toString
    }
  }
}
