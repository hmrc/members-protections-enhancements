package uk.gov.hmrc.membersprotectionsenhancements.models.response

import base.UnitBaseSpec
import play.api.libs.json.{JsError, JsString, JsSuccess, Json}

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
