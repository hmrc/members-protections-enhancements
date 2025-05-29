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

package uk.gov.hmrc.membersprotectionsenhancements.controllers

import play.api.test.FakeRequest
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.MpeError
import uk.gov.hmrc.membersprotectionsenhancements.controllers.actions.{
  DataRetrievalAction,
  FakeDataRetrievalAction,
  IdentifierAction
}
import play.api.inject.bind
import uk.gov.hmrc.membersprotectionsenhancements.config.AppConfig
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import org.mockito.Mockito.reset
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Play.materializer
import base.ItBaseSpec
import uk.gov.hmrc.auth.core.AuthConnector
import play.api.Application
import play.api.libs.json.{JsValue, Json}
import play.api.http.Status._
import uk.gov.hmrc.http.HeaderCarrier

class MembersLookUpControllerSpec extends ItBaseSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  private val fakeRequest = FakeRequest("POST", "/members-protections-enhancements/check-and-retrieve")
  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val mockAppConfig: AppConfig = mock[AppConfig]

  private val nino = "QQ123456C"
  private val psaCheckRef = "PSA12345678A"
  private val retrieveUrl = s"/mpe-nps-stub/paye/lifetime-allowance/person/$nino/admin-reference/$psaCheckRef/lookup"

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockAppConfig)
  }

  val application: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.nps.port" -> wireMockPort)
    .overrides(
      bind[IdentifierAction].toInstance(fakePsaIdentifierAction),
      bind[DataRetrievalAction].toInstance(new FakeDataRetrievalAction)
    )
    .build()

  private val controller = application.injector.instanceOf[MembersLookUpController]

  val requestJson: JsValue = Json.parse("""
                                   |{
                                   |    "firstName": "Naren",
                                   |    "lastName": "Vijay",
                                   |    "dateOfBirth": "2024-12-31",
                                   |    "nino": "QQ123456C",
                                   |    "psaCheckRef":"PSA12345678A"
                                   |}""".stripMargin)

  "MemberLookUpController" should {
    "should return 200 for a valid members data" in {

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

      stubGet(retrieveUrl, ok(response))

      val postRequest = fakeRequest.withBody(requestJson)
      val result = controller.checkAndRetrieve(postRequest)
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse(response)
    }

    "should return 404 when no results found" in {

      val response = MpeError(
        "NOT_FOUND",
        "GET of 'http://localhost:6001/mpe-nps-stub/paye/lifetime-allowance/person/QQ123456C/admin-reference/PSA12345678A/lookup' returned 404 (Not Found). Response body: ''"
      )

      stubGet(retrieveUrl, notFound())

      val postRequest = fakeRequest.withBody(requestJson)
      val result = controller.checkAndRetrieve(postRequest)
      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.toJson(response)
    }

    "should return 400 when invalid or insufficient data submitted" in {

      val json: JsValue = Json.parse("""
          |{
          |    "firstName": "Naren",
          |    "lastName": "lastname",
          |    "dateOfBirth": "2024-12-31",
          |    "nino": "QQ123456C"
          |}""".stripMargin)

      val response = MpeError("BAD_REQUEST", "Invalid request data", Some(Seq("Missing or invalid psaCheckRef")))

      val postRequest = fakeRequest.withBody(json)
      val result = controller.checkAndRetrieve(postRequest)
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.toJson(response)
    }

    "should return 403 for an UNPROCESSED error" in {

      val response = MpeError(
        "FORBIDDEN",
        "GET of 'http://localhost:6001/mpe-nps-stub/paye/lifetime-allowance/person/QQ123456C/admin-reference/PSA12345678A/lookup' returned 403. Response body: ''"
      )

      stubGet(retrieveUrl, forbidden())

      val postRequest = fakeRequest.withBody(requestJson)
      val result = controller.checkAndRetrieve(postRequest)
      status(result) mustBe FORBIDDEN
      contentAsJson(result) mustBe Json.toJson(response)
    }

    "should return 500 for any server error" in {

      val response = MpeError(
        "INTERNAL_ERROR",
        "GET of 'http://localhost:6001/mpe-nps-stub/paye/lifetime-allowance/person/QQ123456C/admin-reference/PSA12345678A/lookup' returned 500. Response body: ''"
      )

      stubGet(retrieveUrl, serverError())

      val postRequest = fakeRequest.withBody(requestJson)
      val result = controller.checkAndRetrieve(postRequest)
      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.toJson(response)
    }
  }

}
