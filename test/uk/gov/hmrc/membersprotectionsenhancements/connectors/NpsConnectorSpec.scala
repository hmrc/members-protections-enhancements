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
import play.api.http.Status.IM_A_TEAPOT
import play.api.libs.json.JsObject
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.PensionSchemeMemberRequest
import uk.gov.hmrc.membersprotectionsenhancements.models.response.{MatchPersonResponse, ProtectionRecord, ProtectionRecordDetails}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

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
        nino = "AA123456C",
        psaCheckRef = "PSA12345678A"
      )

      "[matchPerson] should return the expected result when NPS returns a recognised error code" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = badRequest()
        )

        val result: Either[MpeError, MatchPersonResponse] = await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a [Left[_, _]]
        result.swap.getOrElse(MpeError("N/A", "N/A")).code mustBe "BAD_REQUEST"
      }

      "[matchPerson] should return the expected result when NPS returns a unrecognised error code" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = aResponse().withStatus(IM_A_TEAPOT)
        )

        val result: Either[MpeError, MatchPersonResponse] = await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a [Left[_, _]]
        result.swap.getOrElse(MpeError("N/A", "N/A")).code mustBe "UNEXPECTED_STATUS_ERROR"
      }

      "[matchPerson] should return the expected result when NPS returns an unparsable OK response" in new Test {
        stubPost(
          url = npsUrl,
          requestBody = PensionSchemeMemberRequest.matchPersonWrites.writes(request).toString(),
          response = okJson(JsObject.empty.toString())
        )

        val result: Either[MpeError, MatchPersonResponse] = await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a [Left[_, _]]
        result.swap.getOrElse(MpeError("N/A", "N/A")).code mustBe "INTERNAL_SERVER_ERROR"
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

        val result: Either[MpeError, MatchPersonResponse] = await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a [Left[_, _]]
        result.swap.getOrElse(MpeError("N/A", "N/A")).code mustBe "INTERNAL_SERVER_ERROR"
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

        val result: Either[MpeError, MatchPersonResponse] = await(connector.matchPerson(request).value)

        WireMock.verify(postRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a [Right[_, _]]
        result.getOrElse(MatchPersonResponse("N/A")).matchResult mustBe "MATCH"
      }
    }

    "retrieveMpe" -> {
      val npsUrl = s"/paye/lifetime-allowance/person/$nino/admin-reference/$psaCheckRef/lookup"

      "[retrieveMpe] should return the expected result when NPS returns a recognised error code" in new Test {
        stubGet(
          url = npsUrl,
          response = badRequest()
        )

        val result: Either[MpeError, ProtectionRecordDetails] = await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a [Left[_, _]]
        result.swap.getOrElse(MpeError("N/A", "N/A")).code mustBe "BAD_REQUEST"
      }

      "[retrieveMpe] should return the expected result when NPS returns an unrecognised error code" in new Test {
        stubGet(
          url = npsUrl,
          response = aResponse().withStatus(IM_A_TEAPOT)
        )

        val result: Either[MpeError, ProtectionRecordDetails] = await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a [Left[_, _]]
        result.swap.getOrElse(MpeError("N/A", "N/A")).code mustBe "UNEXPECTED_STATUS_ERROR"
      }

      "[retrieveMpe] should return the expected result when NPS returns an unparsable OK response" in new Test {
        stubGet(
          url = npsUrl,
          response = okJson("")
        )

        val result: Either[MpeError, ProtectionRecordDetails] = await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a [Left[_, _]]
        result.swap.getOrElse(MpeError("N/A", "N/A")).code mustBe "INTERNAL_SERVER_ERROR"
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

        val result: Either[MpeError, ProtectionRecordDetails] = await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a [Left[_, _]]
        result.swap.getOrElse(MpeError("N/A", "N/A")).code mustBe "INTERNAL_SERVER_ERROR"
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

        val result: Either[MpeError, ProtectionRecordDetails] = await(connector.retrieveMpe(nino, psaCheckRef).value)

        WireMock.verify(getRequestedFor(urlEqualTo(npsUrl)))

        result mustBe a [Right[_, _]]
        result.getOrElse(ProtectionRecordDetails(Nil)).protectionRecords.head mustBe ProtectionRecord(
          protectionReference = Some("some-id"),
          `type` = "some-type",
          status = "some-status",
          protectedAmount = Some(1),
          lumpSumAmount = Some(1),
          lumpSumPercentage = Some(1),
          enhancementFactor = Some(0.5)
        )
      }
    }
  }


/*

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
  }*/
}
