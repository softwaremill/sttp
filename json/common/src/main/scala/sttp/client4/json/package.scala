package sttp.client4

package object json {
  implicit class RichResponseAs[T](ra: ResponseAs[T]) {
    def showAsJson: ResponseAs[T] = ra.showAs("either(as string, as json)")
    def showAsJsonAlways: ResponseAs[T] = ra.showAs("as json")
    def showAsJsonOrFail: ResponseAs[T] = ra.showAs("as json or fail")
    def showAsJsonEither: ResponseAs[T] = ra.showAs("either(as json, as json)")
    def showAsJsonEitherOrFail: ResponseAs[T] = ra.showAs("either(as json, as json) or fail")
  }
}
