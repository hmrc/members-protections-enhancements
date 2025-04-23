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
import play.api.Play.materializer
import play.api.inject.bind
import uk.gov.hmrc.membersprotectionsenhancements.config.AppConfig
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import org.mockito.Mockito.reset
import base.SpecBase
import uk.gov.hmrc.auth.core.AuthConnector
import play.api.Application
import play.api.libs.json.{JsValue, Json}
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import uk.gov.hmrc.http.HeaderCarrier

class MembersDetailsControllerSpec extends SpecBase {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  private val fakeRequest = FakeRequest("POST", "/members-protections-enhancements/submit")
  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val mockAppConfig: AppConfig = mock[AppConfig]

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockAppConfig)
  }

  val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false)
    .overrides(bind[AppConfig].toInstance(mockAppConfig),
      bind[AuthConnector].toInstance(mockAuthConnector))
    .build()

  private val controller = application.injector.instanceOf[MembersDetailsController]

  "Member Details Controller" - {
    "should return 200 for a valid members data" in {

      val json: JsValue = Json.parse(
        """
          |{
          |    "firstName": "Naren",
          |    "lastName": "Vijay",
          |    "dateOfBirth": "2024-12-31",
          |    "nino": "QQ123456C",
          |    "psaCheckRef":"PSA12345678A"
          |}""".stripMargin)

      val response = Json.parse(
        """{
          |"statusCode": "200",
          |"message": "search successful, member details exists"
          |}""".stripMargin)
      val postRequest = fakeRequest.withBody(json)
      val result = controller.submitAndRetrieveMembersPensionSchemes(postRequest)
      status(result) mustBe OK
      contentAsJson(result) mustBe response
    }

    "should return 404 when no results found" in {

      val json: JsValue = Json.parse(
        """
          |{
          |    "firstName": "Naren",
          |    "lastName": "lastname",
          |    "dateOfBirth": "2024-12-31",
          |    "nino": "QQ123456C",
          |    "psaCheckRef":"PSA12345678A"
          |}""".stripMargin)

      val response = Json.parse(
        """{
          |"statusCode": "404",
          |"message": "search failed, no details found with the member details provided"
          |}""".stripMargin)

      val postRequest = fakeRequest.withBody(json)
      val result = controller.submitAndRetrieveMembersPensionSchemes(postRequest)
      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe response
    }

    "should return 400 when invalid or insufficient data submitted" in {

      val json: JsValue = Json.parse(
        """
          |{
          |    "firstName": "Naren",
          |    "lastName": "lastname",
          |    "dateOfBirth": "2024-12-31",
          |    "nino": "QQ123456C"
          |}""".stripMargin)

      val response = Json.parse(
        s"""{
           |"statusCode": "400",
           |"message": "Invalid json format (/psaCheckRef,List(JsonValidationError(List(error.path.missing),List())))"
           |}""".stripMargin)

      val postRequest = fakeRequest.withBody(json)
      val result = controller.submitAndRetrieveMembersPensionSchemes(postRequest)
      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe response
    }
  }

}
