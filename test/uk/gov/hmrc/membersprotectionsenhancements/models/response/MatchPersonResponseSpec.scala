/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.membersprotectionsenhancements.models.response

import base.UnitBaseSpec
import play.api.libs.json._

class MatchPersonResponseSpec extends UnitBaseSpec {
  "MatchPersonResponse" -> {
    "reads" -> {
      "[reads] should return the expected object for valid JSON" in {
        val json: String =
          """
          |{
          | "matchResult": "MATCH"
          |}
        """.stripMargin

        val result = Json.parse(json).validate[MatchPersonResponse]
        result mustBe a[JsSuccess[_]]
        result.get mustBe MATCH
      }

      "[reads] should return the expected object for valid JSON with no match" in {
        val json: String =
          """
            |{
            | "matchResult": "NO MATCH"
            |}
        """.stripMargin

        val result = Json.parse(json).validate[MatchPersonResponse]
        result mustBe a[JsSuccess[_]]
        result.get mustBe `NO MATCH`
      }

      "[reads] should return an error for valid JSON which breaks validation rules" in {
        val json: String =
          """
            |{
            | "matchResult": "BEEP"
            |}
        """.stripMargin

        val result = Json.parse(json).validate[MatchPersonResponse]
        result mustBe a[JsError]
      }

      "[reads] should return an error for invalid JSON" in {
        val json: String =
          """
            |{
            | "matchResult": 2
            |}
        """.stripMargin

        val result = Json.parse(json).validate[MatchPersonResponse]
        result mustBe a[JsError]
      }
    }

    "writes" -> {
      "[writes] should return the expected object" in {
        Json.toJson(`MATCH`) mustBe JsString("MATCH")
        Json.toJson(`NO MATCH`) mustBe JsString("NO MATCH")
      }
    }
  }

}
