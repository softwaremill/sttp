package sttp.client

package object json {
  implicit class RichResponseAs[T, R](ra: ResponseAs[T, R]) {
    def showAsJson: ResponseAs[T, R] = ra.showAs("either(as string, as json)")
    def showAsJsonAlways: ResponseAs[T, R] = ra.showAs("as json")
    def showAsJsonEither: ResponseAs[T, R] = ra.showAs("either(as json, as json)")
  }
}
