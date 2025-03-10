package sttp.tapir.examples

import cats.effect.{Blocker, ContextShift, IO, Timer}
import io.circe.generic.auto._
import fs2._
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.generic.auto._
import sttp.client3._
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.tapir._
import sttp.tapir.docs.asyncapi.AsyncAPIInterpreter
import sttp.tapir.asyncapi.Server
import sttp.tapir.asyncapi.circe.yaml._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.ws.WebSocket

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object WebSocketHttp4sServer extends App {
  // mandatory implicits
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  val blocker: Blocker = Blocker.liftExecutionContext(ec)

  //

  case class CountResponse(received: Int)

  // The web socket endpoint: GET /count.
  // We need to provide both the type & media type for the requests, and responses. Here, the requests will be
  // byte arrays, and responses will be returned as json.
  val wsEndpoint: Endpoint[Unit, Unit, Pipe[IO, String, CountResponse], Fs2Streams[IO] with WebSockets] =
    endpoint.get.in("count").out(webSocketBody[String, CodecFormat.TextPlain, CountResponse, CodecFormat.Json](Fs2Streams[IO]))

  // A pipe which counts the number of bytes received each second
  val countBytes: Pipe[IO, String, CountResponse] = { in =>
    sealed trait CountAction
    case class AddAction(n: Int) extends CountAction
    case object EmitAction extends CountAction

    sealed trait CountEffect
    case class EmitCount(n: Int) extends CountEffect
    case class IntermediateCount(n: Int) extends CountEffect

    val incomingByteCount = in.map(s => AddAction(s.getBytes().length))
    val everySecond = Stream.awakeEvery[IO](1.second).map(_ => EmitAction)

    incomingByteCount
      .mergeHaltL(everySecond)
      .scan(IntermediateCount(0): CountEffect) {
        case (IntermediateCount(total), AddAction(next)) => IntermediateCount(total + next)
        case (IntermediateCount(total), EmitAction)      => EmitCount(total)
        case (EmitCount(_), AddAction(next))             => IntermediateCount(next)
        case (EmitCount(_), EmitAction)                  => EmitCount(0)
      }
      .collect { case EmitCount(n) =>
        CountResponse(n)
      }
  }

  // Implementing the endpoint's logic, by providing the web socket pipe
  val wsRoutes: HttpRoutes[IO] = Http4sServerInterpreter.toRoutes(wsEndpoint)(_ => IO.pure(Right(countBytes)))

  // Documentation
  val apiDocs = AsyncAPIInterpreter.toAsyncAPI(wsEndpoint, "Byte counter", "1.0", List("dev" -> Server("localhost:8080", "ws"))).toYaml
  println(s"Paste into https://playground.asyncapi.io/ to see the docs for this endpoint:\n$apiDocs")

  // Starting the server
  BlazeServerBuilder[IO](ec)
    .bindHttp(8080, "localhost")
    .withHttpApp(Router("/" -> wsRoutes).orNotFound)
    .resource
    .flatMap(_ => AsyncHttpClientFs2Backend.resource[IO](blocker))
    .use { backend =>
      // Client which interacts with the web socket
      basicRequest
        .response(asWebSocket { (ws: WebSocket[IO]) =>
          for {
            _ <- ws.sendText("7 bytes")
            _ <- ws.sendText("7 bytes")
            r1 <- ws.receiveText()
            _ = println(r1)
            _ <- ws.sendText("10   bytes")
            _ <- ws.sendText("12     bytes")
            r2 <- ws.receiveText()
            _ = println(r2)
            _ <- IO.sleep(3.seconds)
            _ <- ws.sendText("7 bytes")
            r3 <- ws.receiveText()
            r4 <- ws.receiveText()
            r5 <- ws.receiveText()
            r6 <- ws.receiveText()
            _ = println(r3)
            _ = println(r4)
            _ = println(r5)
            _ = println(r6)
          } yield ()
        })
        .get(uri"ws://localhost:8080/count")
        .send(backend)
        .map(_ => println("Counting complete, bye!"))
    }
    .unsafeRunSync()
}
