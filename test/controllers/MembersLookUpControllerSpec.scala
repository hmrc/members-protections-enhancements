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

package controllers

import play.api.test.FakeRequest
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.mvc.{AnyContentAsEmpty, Result}
import controllers.MembersLookUpController
import controllers.actions.IdentifierAction
import models.response.{ProtectionRecord, ProtectionRecordDetails}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import org.mockito.Mockito.reset
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Play.materializer
import play.api.inject.bind
import config.AppConfig
import base.ItBaseSpec
import uk.gov.hmrc.auth.core.AuthConnector
import play.api.Application
import play.api.libs.json.{JsValue, Json}
import play.api.http.Status._
import uk.gov.hmrc.http.HeaderCarrier
import models.errors.{EmptyDataError, NoMatchError}
import org.scalatestplus.mockito.MockitoSugar.mock

import scala.concurrent.Future

class MembersLookUpControllerSpec extends ItBaseSpec {

  trait Test {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
      method = "POST",
      path = "/members-protections-enhancements/check-and-retrieve"
    )

    val nino: String = "AA123456C"
    val psaCheckRef: String = "PSA12345678A"

    val requestJson: JsValue = Json.parse(
      """
        |{
        |    "firstName": "John",
        |    "lastName": "Smith",
        |    "dateOfBirth": "2024-12-31",
        |    "nino": "AA123456C",
        |    "psaCheckRef":"PSA12345678A"
        |}
      """.stripMargin
    )

    val downstreamRequestJson: String = Json
      .parse(
        """
        |{
        | "identifier": "AA123456C",
        | "firstForename": "John",
        | "surname": "Smith",
        | "dateOfBirth": "2024-12-31"
        |}
      """.stripMargin
      )
      .toString()

    val matchResponseJson: String =
      """
        |{
        | "matchResult": "MATCH"
        |}
      """.stripMargin

    val noMatchResponseJson: String =
      """
        |{
        | "matchResult": "NO MATCH"
        |}
      """.stripMargin

    val retrieveResponseJson: String =
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

    val responseModel: ProtectionRecordDetails = ProtectionRecordDetails(
      Seq(
        ProtectionRecord(
          protectionReference = Some("some-id"),
          `type` = "some-type",
          status = "some-status",
          protectedAmount = Some(1),
          lumpSumAmount = Some(1),
          lumpSumPercentage = Some(1),
          enhancementFactor = Some(0.5),
          pensionCreditLegislation = None
        )
      )
    )

    val retrieveUrl: String = s"/paye/lifetime-allowance/person/$nino/admin-reference/$psaCheckRef/lookup"
    val matchUrl: String = "/paye/individual/match"

    val application: Application = new GuiceApplicationBuilder()
      .configure(
        "microservice.services.nps.port" -> wireMockPort,
        "urls.npsContext" -> ""
      )
      .overrides(
        bind[IdentifierAction].toInstance(fakePsaIdentifierAction)
      )
      .build()

    val controller: MembersLookUpController = application.injector.instanceOf[MembersLookUpController]

    def setupStubs(
      downstreamRequestBody: String,
      matchStatus: Int,
      matchResponse: String,
      retrieveStatus: Int,
      retrieveResponse: String,
      withRetrieveStub: Boolean
    ): StubMapping = {
      def stubMatch: StubMapping = stubPost(
        url = matchUrl,
        requestBody = downstreamRequestBody,
        response = aResponse.withStatus(matchStatus).withBody(matchResponse)
      )

      if (withRetrieveStub) {
        stubMatch
        stubGet(
          url = retrieveUrl,
          response = aResponse.withStatus(retrieveStatus).withBody(retrieveResponse)
        )
      } else {
        stubMatch
      }
    }
  }

  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockAppConfig: AppConfig = mock[AppConfig]

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockAppConfig)
  }

  "MemberLookUpController" when {
    "checkAndRetrieve" should {
      "[checkAndRetrieve] return 200 for a valid members data and match" in new Test {
        setupStubs(
          downstreamRequestBody = downstreamRequestJson,
          matchStatus = OK,
          matchResponse = matchResponseJson,
          retrieveStatus = OK,
          retrieveResponse = retrieveResponseJson,
          withRetrieveStub = true
        )

        val postRequest: FakeRequest[JsValue] = fakeRequest.withBody(requestJson)
        val result: Future[Result] = controller.checkAndRetrieve(postRequest)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(responseModel)
      }

      "[checkAndRetrieve] return 404 for a no match" in new Test {
        setupStubs(
          downstreamRequestBody = downstreamRequestJson,
          matchStatus = OK,
          matchResponse = noMatchResponseJson,
          retrieveStatus = IM_A_TEAPOT,
          retrieveResponse = "N/A",
          withRetrieveStub = false
        )

        val postRequest: FakeRequest[JsValue] = fakeRequest.withBody(requestJson)
        val result: Future[Result] = controller.checkAndRetrieve(postRequest)

        status(result) mustBe NOT_FOUND
        contentAsJson(result) mustBe Json.toJson(NoMatchError)
      }

      "[checkAndRetrieve] return 404 for an empty protectionRecords response" in new Test {
        setupStubs(
          downstreamRequestBody = downstreamRequestJson,
          matchStatus = OK,
          matchResponse = matchResponseJson,
          retrieveStatus = OK,
          retrieveResponse = """{"protectionRecords": []}""",
          withRetrieveStub = true
        )

        val postRequest: FakeRequest[JsValue] = fakeRequest.withBody(requestJson)
        val result: Future[Result] = controller.checkAndRetrieve(postRequest)

        status(result) mustBe NOT_FOUND
        contentAsJson(result) mustBe Json.toJson(EmptyDataError)
      }

      "[checkAndRetrieve] return 404 for no response body" in new Test {
        setupStubs(
          downstreamRequestBody = downstreamRequestJson,
          matchStatus = OK,
          matchResponse = matchResponseJson,
          retrieveStatus = OK,
          retrieveResponse = "",
          withRetrieveStub = true
        )

        val postRequest: FakeRequest[JsValue] = fakeRequest.withBody(requestJson)
        val result: Future[Result] = controller.checkAndRetrieve(postRequest)

        status(result) mustBe NOT_FOUND
        contentAsJson(result) mustBe Json.toJson(EmptyDataError)
      }

      "[checkAndRetrieve] return 404 for an empty json response" in new Test {
        setupStubs(
          downstreamRequestBody = downstreamRequestJson,
          matchStatus = OK,
          matchResponse = matchResponseJson,
          retrieveStatus = OK,
          retrieveResponse = """{}""",
          withRetrieveStub = true
        )

        val postRequest: FakeRequest[JsValue] = fakeRequest.withBody(requestJson)
        val result: Future[Result] = controller.checkAndRetrieve(postRequest)

        status(result) mustBe NOT_FOUND
        contentAsJson(result) mustBe Json.toJson(EmptyDataError)
      }

      "[checkAndRetrieve] should handle validation failures" in new Test {
        setupStubs(
          downstreamRequestBody = downstreamRequestJson,
          matchStatus = OK,
          matchResponse = "N/A",
          retrieveStatus = OK,
          retrieveResponse = "N/A",
          withRetrieveStub = false
        )

        val postRequest: FakeRequest[JsValue] = fakeRequest.withBody(
          Json.parse(
            """
            |{
            | "firstName": "Naren",
            | "lastName": "Vijay",
            | "nino": "AA123456C",
            | "psaCheckRef":"PSA12345678A"
            |}
          """.stripMargin
          )
        )

        val result: Future[Result] = controller.checkAndRetrieve(postRequest)

        status(result) mustBe BAD_REQUEST
      }

      def handleMatchErrors(errorStatus: Int, errorCode: String, expectedStatus: Int): Unit =
        s"[checkAndRetrieve] handle appropriately when match API returns error status: $errorStatus" in new Test {
          setupStubs(
            downstreamRequestBody = downstreamRequestJson,
            matchStatus = errorStatus,
            matchResponse = "N/A",
            retrieveStatus = IM_A_TEAPOT,
            retrieveResponse = "N/A",
            withRetrieveStub = false
          )

          val postRequest: FakeRequest[JsValue] = fakeRequest.withBody(requestJson)
          val result: Future[Result] = controller.checkAndRetrieve(postRequest)

          status(result) mustBe expectedStatus
          val content: String = contentAsJson(result).toString()
          content must include(errorCode)

          if (errorCode == "UNEXPECTED_STATUS_ERROR") {
            content must include("Internal")
          } else {
            content must include("MatchPerson")
          }
        }

      val errorCases: Seq[(Int, String, Int)] = Seq(
        (BAD_REQUEST, "BAD_REQUEST", BAD_REQUEST),
        (FORBIDDEN, "FORBIDDEN", FORBIDDEN),
        (INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", INTERNAL_SERVER_ERROR),
        (SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", INTERNAL_SERVER_ERROR),
        (IM_A_TEAPOT, "UNEXPECTED_STATUS_ERROR", INTERNAL_SERVER_ERROR)
      )

      errorCases.foreach(errorCase => handleMatchErrors(errorCase._1, errorCase._2, errorCase._3))

      def handleRetrieveErrors(errorStatus: Int, errorCode: String, expectedStatus: Int): Unit =
        s"[checkAndRetrieve] handle appropriately when retrieve API returns error status: $errorStatus" in new Test {
          setupStubs(
            downstreamRequestBody = downstreamRequestJson,
            matchStatus = OK,
            matchResponse = matchResponseJson,
            retrieveStatus = errorStatus,
            retrieveResponse = "N/A",
            withRetrieveStub = true
          )

          val postRequest: FakeRequest[JsValue] = fakeRequest.withBody(requestJson)
          val result: Future[Result] = controller.checkAndRetrieve(postRequest)

          status(result) mustBe expectedStatus
          val content: String = contentAsJson(result).toString()
          content must include(errorCode)
          if (errorCode == "UNEXPECTED_STATUS_ERROR") {
            content must include("Internal")
          } else {
            content must include("RetrieveMpe")
          }
        }

      val retrieveErrorCases: Seq[(Int, String, Int)] = Seq(
        (BAD_REQUEST, "BAD_REQUEST", BAD_REQUEST),
        (FORBIDDEN, "FORBIDDEN", FORBIDDEN),
        (NOT_FOUND, "NOT_FOUND", NOT_FOUND),
        (UNPROCESSABLE_ENTITY, "NOT_FOUND", NOT_FOUND),
        (INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", INTERNAL_SERVER_ERROR),
        (SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", INTERNAL_SERVER_ERROR),
        (IM_A_TEAPOT, "UNEXPECTED_STATUS_ERROR", INTERNAL_SERVER_ERROR)
      )

      retrieveErrorCases.foreach(errorCase => handleRetrieveErrors(errorCase._1, errorCase._2, errorCase._3))
    }
  }
}
