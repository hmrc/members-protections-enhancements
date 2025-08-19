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

import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{ErrorWrapper, MpeError}
import com.github.tomakehurst.wiremock.client.WireMock._
import base.ItBaseSpec
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.PensionSchemeMemberRequest
import uk.gov.hmrc.membersprotectionsenhancements.models.response._
import play.api.test.DefaultAwaitTimeout
import com.github.tomakehurst.wiremock.client.WireMock
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.await
import play.api.Application
import play.api.libs.json.JsObject
import play.api.http.Status._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

import java.time.LocalDate

class NpsConnectorSpec extends ItBaseSpec with DefaultAwaitTimeout {

  trait Test {
    val application: Application = new GuiceApplicationBuilder()
      .configure(
        "microservice.services.nps.port" -> wireMockPort,
        "urls.npsContext" -> ""
      )
      .build()

    val connector: NpsConnector = application.injector.instanceOf[NpsConnector]

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val correlationId: String = "X-123"
  }

  "NpsConnector" -> {
    val nino: String = "AA123456C"
    val psaCheckRef: String = "PSA12345678A"

    "matchPerson" -> {
      val npsUrl = "/paye/individual/match"

      val request: PensionSchemeMemberRequest = PensionSchemeMemberRequest(
        firstName = "Paul",
        lastName = "Smith",
        dateOfBirth = LocalDate.of(2024, 12, 31),
        identifier = "AA123456C",
        psaCheckRef = "PSA12345678A"
      )

      def recognisedErrorTest(errorStatus: Int, errorCode: String): Unit =
        s"[matchPerson] should return the expected result when NPS returns a recognised error with status: $errorStatus" in new Test {
          stubPost(
            url = npsUrl,
            requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
            response = aResponse().withStatus(errorStatus)
          )

          val result: Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]] =
            await(connector.matchPerson(request).value)

          WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

          result mustBe a[Left[_, _]]
          result.swap.getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A"))).error.code mustBe errorCode
        }

      val recognisedErrorScenarios: Map[Int, String] = Map(
        BAD_REQUEST -> "BAD_REQUEST",
        FORBIDDEN -> "FORBIDDEN",
        INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR",
        SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
      )

      recognisedErrorScenarios.foreach(scenario => recognisedErrorTest(scenario._1, scenario._2))

      "[matchPerson] should return the expected result when NPS returns a unrecognised error code" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = aResponse().withStatus(IM_A_TEAPOT)
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
          response = okJson(JsObject.empty.toString())
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
          )
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
          )
        )

        val result: Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]] =
          await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Right[_, _]]
        result.getOrElse(ResponseWrapper(correlationId, `NO MATCH`)).responseData mustBe `MATCH`
      }
    }

    "retrieveMpe" -> {
      val npsUrl = s"/paye/lifetime-allowance/person/$nino/admin-reference/$psaCheckRef/lookup"

      def recognisedErrorTest(errorStatus: Int, errorCode: String): Unit =
        s"[retrieveMpe] should return the expected result when NPS returns a recognised error with status: $errorStatus" in new Test {
          stubGet(
            url = npsUrl,
            response = aResponse().withStatus(errorStatus)
          )

          val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
            await(connector.retrieveMpe(nino, psaCheckRef).value)

          WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

          result mustBe a[Left[_, _]]
          result.swap.getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A"))).error.code mustBe errorCode
        }

      val recognisedErrorScenarios: Map[Int, String] = Map(
        BAD_REQUEST -> "BAD_REQUEST",
        FORBIDDEN -> "FORBIDDEN",
        NOT_FOUND -> "NOT_FOUND",
        UNPROCESSABLE_ENTITY -> "NOT_FOUND",
        INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR",
        SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
      )

      recognisedErrorScenarios.foreach(scenario => recognisedErrorTest(scenario._1, scenario._2))

      "[retrieveMpe] should return the expected result when NPS returns an unrecognised error code" in new Test {
        stubGet(
          url = npsUrl,
          response = aResponse().withStatus(IM_A_TEAPOT)
        )

        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Left[_, _]]
        result.swap
          .getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A")))
          .error
          .code mustBe "UNEXPECTED_STATUS_ERROR"
      }

      "[retrieveMpe] should return the expected result when NPS returns an unparsable OK response" in new Test {
        stubGet(
          url = npsUrl,
          response = okJson("")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Left[_, _]]
        result.swap
          .getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A")))
          .error
          .code mustBe "INTERNAL_SERVER_ERROR"
      }

      "[retrieveMpe] should return the expected result when NPS returns an invalid OK response" in new Test {
        stubGet(
          url = npsUrl,
          response = okJson(
            """
              |{
              | "protectionRecords": [
              |   {
              |     "protectionReference": "some-id",
              |     "type": 2,
              |     "status": "some-status",
              |     "protectedAmount": 1,
              |     "lumpSumAmount": 1,
              |     "lumpSumPercentage": 1,
              |     "enhancementFactor": 0.5
              |   }
              | ]
              |}
            """.stripMargin
          )
        )

        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Left[_, _]]
        result.swap
          .getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A")))
          .error
          .code mustBe "INTERNAL_SERVER_ERROR"
      }

      "[retrieveMpe] should return the expected result when NPS returns a valid OK response" in new Test {
        stubGet(
          url = npsUrl,
          response = okJson(
            """
              |{
              | "protectionRecords": [
              |   {
              |     "protectionReference": "some-id",
              |     "type": "some-type",
              |     "status": "some-status",
              |     "protectedAmount": 1,
              |     "lumpSumAmount": 1,
              |     "lumpSumPercentage": 1,
              |     "enhancementFactor": 0.5
              |   }
              | ]
              |}
            """.stripMargin
          )
        )

        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Right[_, _]]
        result
          .getOrElse(ResponseWrapper(correlationId, ProtectionRecordDetails(Nil)))
          .responseData
          .protectionRecords
          .head mustBe ProtectionRecord(
          protectionReference = Some("some-id"),
          `type` = "some-type",
          status = "some-status",
          protectedAmount = Some(1),
          lumpSumAmount = Some(1),
          lumpSumPercentage = Some(1),
          enhancementFactor = Some(0.5),
          pensionCreditLegislation = None
        )
      }
    }
  }
}
