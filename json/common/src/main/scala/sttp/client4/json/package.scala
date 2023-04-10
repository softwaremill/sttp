package sttp.client4

package object json {
  implicit class RichResponseAs[T](ra: ResponseAs[T]) {
    def showAsJson: ResponseAs[T] = ra.showAs("either(as string, as json)")
    def showAsJsonAlways: ResponseAs[T] = ra.showAs("as json")
    def showAsJsonEither: ResponseAs[T] = ra.showAs("either(as json, as json)")
  }
}
