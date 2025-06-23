package uk.gov.hmrc.membersprotectionsenhancements.models.errors

import base.UnitBaseSpec
import play.api.libs.json.Json

class MpeErrorSpec extends UnitBaseSpec {
  "MpeError" -> {
    "writes should return the expected JSON" in {
      MpeError.writes.writes(MpeError("CODE", "Message")) mustBe Json.parse(
        """
          |{
          | "code": "CODE",
          | "message": "Message"
          |}
        """.stripMargin
      )
    }

    "genericWrites should return the expected JSON" in {
      MpeError.genericWrites.writes(InternalError)mustBe Json.parse(
        """
          |{
          | "code": "INTERNAL_SERVER_ERROR",
          | "message": "An internal server error occurred"
          |}
        """.stripMargin
      )
    }
  }

}
