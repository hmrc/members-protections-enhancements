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

import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{EmptyDataError, ErrorWrapper, MpeError}
import com.github.tomakehurst.wiremock.client.WireMock._
import base.ItBaseSpec
import play.api.Application
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.CorrelationId
import uk.gov.hmrc.membersprotectionsenhancements.models.response._
import play.api.test.DefaultAwaitTimeout
import com.github.tomakehurst.wiremock.client.WireMock
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.await
import play.api.http.Status._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class RetrieveMpeNpsConnectorSpec extends ItBaseSpec with DefaultAwaitTimeout {

  trait Test {
    val application: Application = new GuiceApplicationBuilder()
      .configure(
        "microservice.services.nps.port" -> wireMockPort,
        "urls.npsContext" -> ""
      )
      .build()

    val connector: RetrieveMpeNpsConnector = application.injector.instanceOf[RetrieveMpeNpsConnector]

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val correlationId: CorrelationId = "X-123"
  }

  "NpsConnector" -> {
    val nino: String = "AA123456C"
    val psaCheckRef: String = "PSA12345678A"

    "retrieveMpe" -> {
      val npsUrl = s"/paye/lifetime-allowance/person/$nino/admin-reference/$psaCheckRef/lookup"

      "[retrieveMpe] should return the expected result when NPS returns an unrecognised error code" in new Test {
        stubGet(
          url = npsUrl,
          response = aResponse().withStatus(IM_A_TEAPOT).withHeader("correlationId", "X-123")
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
          response = okJson("").withHeader("correlationId", "X-123")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Left[_, _]]
        result.swap
          .getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A")))
          .error
          .code mustBe EmptyDataError.code
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

      "[retrieveMpe] should return the expected result when NPS returns an empty json OK response" in new Test {
        stubGet(
          url = npsUrl,
          response = okJson(
            """
              |{
              |}
            """.stripMargin
          ).withHeader("correlationId", "X-123")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Left[_, _]]
        result.swap
          .getOrElse(ErrorWrapper(correlationId, MpeError("N/A", "N/A")))
          .error
          .code mustBe EmptyDataError.code
      }

      val record: ProtectionRecord = ProtectionRecord(
        protectionReference = Some("some-id"),
        `type` = "some-type",
        status = "some-status",
        protectedAmount = Some(1),
        lumpSumAmount = Some(1),
        lumpSumPercentage = Some(1),
        enhancementFactor = Some(0.5),
        pensionCreditLegislation = None
      )

      val recordJsonString: String =
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

      "[retrieveMpe] should return the expected result when NPS returns a valid OK response" in new Test {
        stubGet(
          url = npsUrl,
          response = okJson(recordJsonString).withHeader("correlationId", "X-123")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Right[_, _]]
        result
          .getOrElse(ResponseWrapper(correlationId, ProtectionRecordDetails(Nil)))
          .responseData
          .protectionRecords
          .head mustBe record
      }

      "[retrieveMpe] should handle appropriately when correlation ID is missing for a success" in new Test {
        stubGet(
          url = npsUrl,
          response = okJson(recordJsonString)
        )

        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Right[_, _]]
        result
          .getOrElse(ResponseWrapper(correlationId, ProtectionRecordDetails(Nil)))
          .responseData
          .protectionRecords
          .head mustBe record
      }

      "[retrieveMpe] should handle appropriately when correlation ID is non-matching for a success" in new Test {
        stubGet(
          url = npsUrl,
          response = okJson(recordJsonString).withHeader("correlationId", "nonMatching")
        )

        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a[Right[_, _]]
        result
          .getOrElse(ResponseWrapper(correlationId, ProtectionRecordDetails(Nil)))
          .responseData
          .protectionRecords
          .head mustBe record
      }
    }
  }
}
