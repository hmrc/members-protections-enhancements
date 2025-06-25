package uk.gov.hmrc.membersprotectionsenhancements.models.errors

import base.UnitBaseSpec
import play.api.libs.json.{JsString, Json}

class ErrorSourceSpec extends UnitBaseSpec {
  "ErrorSource" -> {
    "should correctly write any values" in {
      Json.toJson(MatchPerson) mustBe JsString("MatchPerson")
      Json.toJson(RetrieveMpe) mustBe JsString("RetrieveMpe")
      Json.toJson(Internal) mustBe JsString("Internal")
    }
  }
}
