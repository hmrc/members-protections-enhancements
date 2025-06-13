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

package uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.validators

import uk.gov.hmrc.membersprotectionsenhancements.models.errors.MpeError
import base.UnitBaseSpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.PensionSchemeMemberRequest

import scala.concurrent.ExecutionContext.Implicits.global

import java.time.LocalDate

class MembersDetailsValidatorSpec extends UnitBaseSpec {

  val validator = new MembersLookUpValidator()

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

  "MembersDetailsValidator" - {
    "return a valid model" in {
      validator.validate(json) mustBe Right(model)
    }

    "return an error for invalid data" in {
      val invalidJson: JsValue = Json.parse("""
          |{
          |    "firstName": "Naren",
          |    "lastName": "Vijay",
          |    "nino": "AA123456C",
          |    "psaCheckRef":"PSA12345678A"
          |}""".stripMargin)

      validator.validate(invalidJson) mustBe
        Left(MpeError("BAD_REQUEST", "Invalid request data", Some(List("Missing or invalid dateOfBirth"))))
    }

    "return multiple errors" in {
      val invalidJson: JsValue = Json.parse("""
          |{
          |    "firstName": "Naren",
          |    "lastName": "Vijay",
          |    "dateOfBirth": "20-01-2012",
          |    "nino": "QQ1 234 56C",
          |    "psaCheckRef":"PSA12345678A"
          |}""".stripMargin)

      validator.validate(invalidJson) mustBe
        Left(
          MpeError(
            "BAD_REQUEST",
            "Invalid request data",
            Some(List("Missing or invalid dateOfBirth", "Missing or invalid nino"))
          )
        )
    }
  }
}
