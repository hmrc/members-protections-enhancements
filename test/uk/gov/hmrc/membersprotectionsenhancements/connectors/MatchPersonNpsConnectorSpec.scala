/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.membersprotectionsenhancements.connectors

import base.ItBaseSpec
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{ResponseDefinitionBuilder, WireMock}
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsObject
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.{CorrelationId, PensionSchemeMemberRequest}
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{ErrorWrapper, MpeError}
import uk.gov.hmrc.membersprotectionsenhancements.models.response._

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class MatchPersonNpsConnectorSpec extends ItBaseSpec with DefaultAwaitTimeout {

  trait Test {
    val application: Application = new GuiceApplicationBuilder()
      .configure(
        "microservice.services.nps.port" -> wireMockPort,
        "urls.npsContext" -> ""
      )
      .build()

    val connector: MatchPersonNpsConnector = application.injector.instanceOf[MatchPersonNpsConnector]

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val correlationId: CorrelationId = "X-123"
  }

  "NpsConnector" -> {
    "matchPerson" -> {
      val npsUrl = "/paye/individual/match"

      val request: PensionSchemeMemberRequest = PensionSchemeMemberRequest(
        firstName = "Paul",
        lastName = "Smith",
        dateOfBirth = LocalDate.of(2024, 12, 31),
        identifier = "AA123456C",
        psaCheckRef = "PSA12345678A"
      )

      def recognisedErrorTest(errorStatus: Int, errorBody: String, errorCode: String): Unit =
        s"[matchPerson] should return the expected result when NPS returns a recognised error with status: $errorStatus" in new Test {
          val response: ResponseDefinitionBuilder = aResponse()
            .withStatus(errorStatus)
            .withBody(errorBody)
            .withHeader("correlationId", "X-123")

          stubPost(
            url = npsUrl,
            requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
            response = response
          )

          val result: Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]] =
            await(connector.matchPerson(request).value)

          WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

          result mustBe a[Left[_, _]]
          result.swap.getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A"))).error.code mustBe errorCode
        }

      val recognisedErrorScenarios: Seq[(Int, String, String)] = Seq(
        (BAD_REQUEST, """{"response": {"failures": [{"reason": "a reason", "code": "a code"}]}}""", "BAD_REQUEST"),
        (FORBIDDEN, """{"reason": "a reason", "code": "a code"}""", "FORBIDDEN"),
        (NOT_FOUND, "", "NOT_FOUND"),
        (INTERNAL_SERVER_ERROR, """{"response": {"failures": [{"type": "a reason", "reason": "a code"}]}}""", "INTERNAL_ERROR"),
        (SERVICE_UNAVAILABLE, """{"response": {"failures": [{"type": "a reason", "reason": "a code"}]}}""", "SERVICE_UNAVAILABLE")
      )

      recognisedErrorScenarios.foreach(scenario => recognisedErrorTest(scenario._1, scenario._2, scenario._3))

      "[matchPerson] should return the expected result when NPS returns unparsable error" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = aResponse().withStatus(BAD_REQUEST).withHeader("correlationId", "X-123")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]] =
          await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Left[_, _]]
        result.swap
          .getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A")))
          .error
          .code mustBe "INTERNAL_SERVER_ERROR"
      }

      "[matchPerson] should return the expected result when NPS returns a unrecognised error code" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = aResponse().withStatus(IM_A_TEAPOT).withHeader("correlationId", "X-123")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]] =
          await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Left[_, _]]
        result.swap
          .getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A")))
          .error
          .code mustBe "UNEXPECTED_STATUS_ERROR"
      }

      "[matchPerson] should return the expected result when NPS returns an unparsable OK response" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = okJson(JsObject.empty.toString()).withHeader("correlationId", "X-123")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]] =
          await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Left[_, _]]
        result.swap
          .getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A")))
          .error
          .code mustBe "INTERNAL_SERVER_ERROR"
      }

      "[matchPerson] should return the expected result when NPS returns an invalid OK response" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = okJson(
            """
              |{
              | "matchResult": "beep"
              |}
            """.stripMargin
          ).withHeader("correlationId", "X-123")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]] =
          await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Left[_, _]]
        result.swap
          .getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A")))
          .error
          .code mustBe "INTERNAL_SERVER_ERROR"
      }

      "[matchPerson] should return the expected result when NPS returns a valid OK response" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = okJson(
            """
              |{
              | "matchResult": "MATCH"
              |}
            """.stripMargin
          ).withHeader("correlationId", "X-123")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]] =
          await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Right[_, _]]
        result.getOrElse(ResponseWrapper(correlationId, `NO MATCH`)).responseData mustBe `MATCH`
      }

      "[matchPerson] should handle a success response where correlation ID is missing" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = okJson(
            """
              |{
              | "matchResult": "MATCH"
              |}
            """.stripMargin
          )
        )

        val result: Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]] =
          await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Right[_, _]]
        result.getOrElse(ResponseWrapper(correlationId, `NO MATCH`)).responseData mustBe `MATCH`
      }

      "[matchPerson] should handle a success response where correlation ID doesn't match request" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = okJson(
            """
              |{
              | "matchResult": "MATCH"
              |}
            """.stripMargin
          ).withHeader("correlationId", "nonMatching")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]] =
          await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Right[_, _]]
        result.getOrElse(ResponseWrapper(correlationId, `NO MATCH`)).responseData mustBe `MATCH`
      }
    }
  }
}
