/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.membersprotectionsenhancements.controllers.requests

import base.UnitBaseSpec
import play.api.libs.json.{JsResultException, JsValue, Json}

import java.time.LocalDate

class PensionSchemeMemberRequestSpec extends UnitBaseSpec {

  val json: JsValue = Json.parse("""
      |{
      |    "firstName": "Naren",
      |    "lastName": "Vijay",
      |    "dateOfBirth": "2024-12-31",
      |    "nino": "AA123456C",
      |    "psaCheckRef":"PSA12345678A"
      |}""".stripMargin)

  val model: PensionSchemeMemberRequest =
    PensionSchemeMemberRequest("Naren", "Vijay", LocalDate.of(2024, 12, 31), "AA123456C", "PSA12345678A")

  "PensionSchemeMemberRequest" - {
    "return a valid model" in {
      json.as[PensionSchemeMemberRequest] mustBe model
    }

    "return a error for invalid or missing date" in {
      val invalidJson: JsValue = Json.parse("""
          |{
          |    "firstName": "Naren",
          |    "lastName": "Vijay",
          |    "nino": "QQ 12 34 56 C",
          |    "psaCheckRef":"PSA12345678A"
          |}""".stripMargin)

      intercept[JsResultException] {
        invalidJson.as[PensionSchemeMemberRequest]
      }
    }

    "return a error for invalid firstName" in {
      val invalidJson: JsValue = Json.parse("""
          |{
          |    "firstName": "NarenNarenNarenNarenNarenNarenNarenNarenNarenNarenNarenNarenNarenNaren",
          |    "lastName": "Vijay",
          |    "dateOfBirth": "2024-12-31",
          |    "nino": "AA123456C",
          |    "psaCheckRef":"PSA12345678A"
          |}""".stripMargin)

      intercept[JsResultException] {
        invalidJson.as[PensionSchemeMemberRequest]
      }
    }

    "return a error for invalid date format" in {
      val invalidJson: JsValue = Json.parse("""
          |{
          |    "firstName": "Naren",
          |    "lastName": "Vijay",
          |    "dateOfBirth": "20-01-2012",
          |    "nino": "QQ123456C",
          |    "psaCheckRef":"PSA12345678A"
          |}""".stripMargin)

      intercept[JsResultException] {
        invalidJson.as[PensionSchemeMemberRequest]
      }
    }
  }
}
