package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.EndpointIO.Example
import sttp.tapir.docs.openapi.dtos.{Author, Book, Country, Genre}
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.tests.{Person, _}
import sttp.tapir.{endpoint, _}

import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

class VerifyYamlExampleTest extends AnyFunSuite with Matchers {

  test("support example of list and not-list types") {
    val expectedYaml = load("example/expected_examples_of_list_and_not_list_types.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post
          .in(query[List[String]]("friends").example(List("bob", "alice")))
          .in(query[String]("current-person").example("alan"))
          .in(jsonBody[Person].example(Person("bob", 23))),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support multiple examples with explicit names") {
    val expectedYaml = load("example/expected_multiple_examples_with_names.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post
          .out(
            jsonBody[Entity].examples(
              List(
                Example.of(Person("michal", 40), Some("Michal"), Some("Some summary")),
                Example.of(Organization("acme"), Some("Acme"))
              )
            )
          ),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support multiple examples with default names") {
    val expectedYaml = load("example/expected_multiple_examples_with_default_names.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post
          .in(jsonBody[Person].example(Person("bob", 23)).example(Person("matt", 30))),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support example name even if there is a single example") {
    val expectedYaml = load("example/expected_single_example_with_name.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post
          .out(
            jsonBody[Entity].example(
              Example(Person("michal", 40), Some("Michal"), Some("Some summary"))
            )
          ),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support multiple examples with both explicit and default names ") {
    val expectedYaml = load("example/expected_multiple_examples_with_explicit_and_default_names.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post
          .in(jsonBody[Person].examples(List(Example.of(Person("bob", 23), name = Some("Bob")), Example.of(Person("matt", 30))))),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support examples in different IO params") {
    val expectedYaml = load("example/expected_multiple_examples.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post
          .in(path[String]("country").example("Poland").example("UK"))
          .in(query[String]("current-person").example("alan").example("bob"))
          .in(jsonBody[Person].example(Person("bob", 23)).example(Person("alan", 50)))
          .in(header[String]("X-Forwarded-User").example("user1").example("user2"))
          .in(cookie[String]("cookie-param").example("cookie1").example("cookie2"))
          .out(jsonBody[Entity].example(Person("michal", 40)).example(Organization("acme"))),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("automatically add example for fixed header") {
    val expectedYaml = load("example/expected_fixed_header_example.yml")

    val e = endpoint.in(header("Content-Type", "application/json"))
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using schema with custom example") {
    val expectedYaml = load("example/expected_schema_example.yml")

    val expectedDateTime = ZonedDateTime.of(2021, 1, 1, 1, 1, 1, 1, UTC)
    val expectedBook = Book("title", Genre("name", "desc"), 2021, Author("name", Country("country")))

    implicit val testSchemaZonedDateTime: Schema[ZonedDateTime] = Schema.schemaForZonedDateTime.encodedExample(expectedDateTime)
    implicit val testSchemaBook: Schema[Book] = {
      val schema: Schema[Book] = implicitly[Derived[Schema[Book]]].value
      schema.encodedExample(circeCodec[Book](implicitly, implicitly, schema).encode(expectedBook))
    }

    val endpoint_with_dateTimes = endpoint.post.in(jsonBody[ZonedDateTime]).out(jsonBody[Book])

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_with_dateTimes, Info("Examples", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

}
