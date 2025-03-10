package sttp.tapir.examples

import cats.effect._
import cats.syntax.all._
import io.circe.generic.auto._
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import sttp.client3._
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.time.Instant
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext

object OAuth2GithubHttp4sServer extends App {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val ce: ConcurrentEffect[IO] = IO.ioConcurrentEffect

  // github application details
  val clientId = "<put your client id here>"
  val clientSecret = "<put your client secret>"

  // algorithm used for jwt encoding and decoding
  val jwtAlgo = JwtAlgorithm.HS256
  val jwtKey = "my secret key"

  type Limit = Int
  type AuthToken = String

  case class AccessDetails(token: String)

  val authorizationUrl = "https://github.com/login/oauth/authorize"
  val accessTokenUrl = Some("https://github.com/login/oauth/access_token")

  val authOAuth2 = auth.oauth2.authorizationCode(authorizationUrl, ListMap.empty, accessTokenUrl)

  // endpoint declarations
  val login: Endpoint[Unit, Unit, String, Any] =
    endpoint.get
      .in("login")
      .out(statusCode(StatusCode.PermanentRedirect))
      .out(header[String]("Location"))

  val loginGithub: Endpoint[String, String, AccessDetails, Any] =
    endpoint.get
      .in("login" / "oauth2" / "github")
      .in(query[String]("code"))
      .out(jsonBody[AccessDetails])
      .errorOut(stringBody)

  val secretPlace: Endpoint[String, String, String, Any] =
    endpoint.get
      .in("secret-place")
      .in(authOAuth2)
      .out(stringBody)
      .errorOut(stringBody)

  // converting endpoints to routes

  // simply redirect to github auth service
  val loginRoute: HttpRoutes[IO] = Http4sServerInterpreter.toRoutes(login)(_ => IO(s"$authorizationUrl?client_id=$clientId".asRight[Unit]))

  // after successful authorization github redirects you here
  def loginGithubRoute(backend: SttpBackend[IO, Any]): HttpRoutes[IO] =
    Http4sServerInterpreter.toRoutes(loginGithub)(code =>
      basicRequest
        .response(asStringAlways)
        .post(uri"$accessTokenUrl?client_id=$clientId&client_secret=$clientSecret&code=$code")
        .header("Accept", "application/json")
        .send(backend)
        .map(resp => {
          // create jwt token, that client will use for authenticating to the app
          val now = Instant.now
          val claim =
            JwtClaim(expiration = Some(now.plusSeconds(15 * 60).getEpochSecond), issuedAt = Some(now.getEpochSecond), content = resp.body)
          AccessDetails(JwtCirce.encode(claim, jwtKey, jwtAlgo)).asRight[String]
        })
    )

  // try to decode the provided jwt
  def authenticate(token: String): Either[String, String] = {
    JwtCirce
      .decodeAll(token, jwtKey, Seq(jwtAlgo))
      .toEither
      .leftMap(err => "Invalid token: " + err)
      .map(decoded => decoded._2.content)
  }

  // get user details from decoded jwt
  val secretPlaceRoute: HttpRoutes[IO] = Http4sServerInterpreter.toRoutes(secretPlace)(token =>
    IO(
      for {
        authDetails <- authenticate(token)
        msg <- ("Your details: " + authDetails).asRight[String]
      } yield msg
    )
  )

  val httpClient = AsyncHttpClientCatsBackend.resource[IO]()

  // starting the server
  httpClient
    .use(backend =>
      BlazeServerBuilder[IO](ec)
        .bindHttp(8080, "localhost")
        .withHttpApp(Router("/" -> (secretPlaceRoute <+> loginRoute <+> loginGithubRoute(backend))).orNotFound)
        .resource
        .use { _ =>
          IO {
            println("Go to: http://localhost:8080")
            println("Press any key to exit ...")
            scala.io.StdIn.readLine()
          }
        }
    )
    .unsafeRunSync()
}
