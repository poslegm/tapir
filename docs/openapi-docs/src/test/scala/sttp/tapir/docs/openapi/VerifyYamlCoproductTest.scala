package sttp.tapir.docs.openapi

import io.circe.Codec
import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.docs.openapi.dtos.VerifyYamlCoproductTestData._
import sttp.tapir.docs.openapi.dtos.VerifyYamlCoproductTestData2._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.tests.{Entity, Organization, Person}

class VerifyYamlCoproductTest extends AnyFunSuite with Matchers {
  test("should match expected yaml for coproduct with enum field") {
    implicit val shapeCodec: Codec[Shape] = null
    implicit val schema: Schema[Shape] = Schema
      .oneOfUsingField[Shape, String](
        _.shapeType,
        identity
      )(
        Square.toString() -> implicitly[Schema[Square]]
      )

    val endpoint = sttp.tapir.endpoint.get.out(jsonBody[Shape])

    val expectedYaml = load("coproduct/expected_coproduct_discriminator_with_enum_circe.yml")
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint, "My Bookshop", "1.0").toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should match the expected yaml when using coproduct types") {
    val expectedYaml = load("coproduct/expected_coproduct.yml")

    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, Entity, Any] = endpoint
      .out(jsonBody[Entity])

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using coproduct types with discriminator") {
    val sPerson = implicitly[Schema[Person]]
    val sOrganization = implicitly[Schema[Organization]]
    implicit val sEntity: Schema[Entity] =
      Schema.oneOfUsingField[Entity, String](_.name, _.toString)("john" -> sPerson, "sml" -> sOrganization)

    val expectedYaml = load("coproduct/expected_coproduct_discriminator.yml")
    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, Entity, Any] = endpoint
      .out(jsonBody[Entity])
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using nested coproduct types") {
    val expectedYaml = load("coproduct/expected_coproduct_nested.yml")

    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, NestedEntity, Any] = endpoint
      .out(jsonBody[NestedEntity])

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml when using nested coproduct types with discriminator") {
    val sPerson = implicitly[Schema[Person]]
    val sOrganization = implicitly[Schema[Organization]]
    implicit val sEntity: Schema[Entity] =
      Schema.oneOfUsingField[Entity, String](_.name, _.toString)("john" -> sPerson, "sml" -> sOrganization)

    val expectedYaml = load("coproduct/expected_coproduct_discriminator_nested.yml")
    val endpoint_wit_sealed_trait: Endpoint[Unit, Unit, NestedEntity, Any] = endpoint
      .out(jsonBody[NestedEntity])
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint_wit_sealed_trait, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should unfold coproducts from unfolded arrays") {
    val expectedYaml = load("coproduct/expected_unfolded_coproduct_unfolded_array.yml")

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint.out(jsonBody[List[Entity]]), Info("Entities", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should differentiate when a generic coproduct type is used multiple times") {
    val expectedYaml = load("coproduct/expected_generic_coproduct.yml")

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        List(endpoint.in("p1" and jsonBody[GenericEntity[String]]), endpoint.in("p2" and jsonBody[GenericEntity[Int]])),
        Info("Fruits", "1.0")
      )
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("support recursive coproducts") {
    val expectedYaml = load("coproduct/expected_recursive_coproducts.yml")
    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(
        endpoint.post.in(jsonBody[Clause]),
        Info("Entities", "1.0")
      )
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }
}
