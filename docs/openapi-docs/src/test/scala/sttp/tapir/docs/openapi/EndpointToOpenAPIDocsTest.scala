package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.openapi.Info
import sttp.tapir.tests._

class EndpointToOpenAPIDocsTest extends AnyFunSuite with Matchers {
  for (e <- allTestEndpoints) {
    test(s"${e.showDetail} should convert to open api") {
      e.toOpenAPI(Info("title", "19.2-beta-RC1"))
    }
  }
}
