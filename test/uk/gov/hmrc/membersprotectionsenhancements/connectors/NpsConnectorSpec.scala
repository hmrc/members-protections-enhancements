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

import com.github.tomakehurst.wiremock.client.WireMock
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.MpeError
import play.api.inject.guice.GuiceApplicationBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import base.ItBaseSpec
import play.api.Application
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.membersprotectionsenhancements.models.response.{ProtectionRecord, ProtectionRecordDetails}

import scala.concurrent.ExecutionContext.Implicits.global

class NpsConnectorSpec extends ItBaseSpec {

  private val nino = "AA123456C"
  private val psaCheckRef = "PSA12345678A"
  private val retrieveUrl = s"/mpe-nps-stub/paye/lifetime-allowance/person/$nino/admin-reference/$psaCheckRef/lookup"

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val application: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.nps.port" -> wireMockPort)
    .build()

  val connector: NpsConnector = application.injector.instanceOf[NpsConnector]

  "retrieve" should {
    "return valid response with status 200 for a valid submission" in {

      val response: String =
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

      val resModel: ProtectionRecordDetails = ProtectionRecordDetails(
        Seq(
          ProtectionRecord(
            protectionReference = Some("some-id"),
            `type` = "some-type",
            status = "some-status",
            protectedAmount = Some(1),
            lumpSumAmount = Some(1),
            lumpSumPercentage = Some(1),
            enhancementFactor = Some(0.5)
          )
        )
      )

      stubGet(retrieveUrl, ok(response))

      whenReady(connector.retrieve(nino, psaCheckRef)) { (result: Either[MpeError, ProtectionRecordDetails]) =>
        WireMock.verify(
          getRequestedFor(
            urlEqualTo(retrieveUrl)
          )
        )
        result mustBe Right(resModel)
      }
    }
    "return MpeError wrapped BAD_REQUEST for BAD_REQUEST response" in {

      val errorResponse = MpeError(
        "BAD_REQUEST",
        "GET of 'http://localhost:6001/mpe-nps-stub/paye/lifetime-allowance/person/AA123456C/admin-reference/PSA12345678A/lookup' returned 400 (Bad Request). Response body ''",
        None
      )

      stubGet(retrieveUrl, badRequest())

      whenReady(connector.retrieve(nino, psaCheckRef)) { (result: Either[MpeError, ProtectionRecordDetails]) =>
        WireMock.verify(
          getRequestedFor(
            urlEqualTo(retrieveUrl)
          )
        )
        result mustBe Left(errorResponse)
      }
    }

    "return MpeError wrapped NOT_FOUND for NOT_FOUND response" in {

      val errorResponse = MpeError(
        "NOT_FOUND",
        "GET of 'http://localhost:6001/mpe-nps-stub/paye/lifetime-allowance/person/AA123456C/admin-reference/PSA12345678A/lookup' returned 404 (Not Found). Response body: ''"
      )
      stubGet(retrieveUrl, notFound())

      whenReady(connector.retrieve(nino, psaCheckRef)) { (result: Either[MpeError, ProtectionRecordDetails]) =>
        WireMock.verify(
          getRequestedFor(
            urlEqualTo(retrieveUrl)
          )
        )
        result mustBe Left(errorResponse)
      }
    }

    "return MpeError wrapped FORBIDDEN for any 4xx response" in {

      val errorResponse = MpeError(
        "FORBIDDEN",
        "GET of 'http://localhost:6001/mpe-nps-stub/paye/lifetime-allowance/person/AA123456C/admin-reference/PSA12345678A/lookup' returned 403. Response body: ''"
      )
      stubGet(retrieveUrl, forbidden())

      whenReady(connector.retrieve(nino, psaCheckRef)) { (result: Either[MpeError, ProtectionRecordDetails]) =>
        WireMock.verify(
          getRequestedFor(
            urlEqualTo(retrieveUrl)
          )
        )
        result mustBe Left(errorResponse)
      }
    }

    "return MpeError wrapped INTERNAL_ERROR for any 5xx response" in {

      val errorResponse = MpeError(
        "INTERNAL_ERROR",
        "GET of 'http://localhost:6001/mpe-nps-stub/paye/lifetime-allowance/person/AA123456C/admin-reference/PSA12345678A/lookup' returned 500. Response body: ''"
      )
      stubGet(retrieveUrl, serverError())

      whenReady(connector.retrieve(nino, psaCheckRef)) { (result: Either[MpeError, ProtectionRecordDetails]) =>
        WireMock.verify(
          getRequestedFor(
            urlEqualTo(retrieveUrl)
          )
        )
        result mustBe Left(errorResponse)
      }
    }
  }
}
