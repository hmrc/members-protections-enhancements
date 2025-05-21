package uk.gov.hmrc.membersprotectionsenhancements.models.response

import base.SpecBase
import play.api.libs.json.{JsError, JsObject, JsSuccess, JsValue, Json}

class ProtectionRecordDetailsSpec extends SpecBase {
  val testModel: ProtectionRecordDetails = ProtectionRecordDetails(Seq(
    ProtectionRecord(
      protectionReference = Some("some-id"),
      `type` = "some-type",
      status = "some-status",
      protectedAmount = Some(1),
      lumpSumAmount = Some(1),
      lumpSumPercentage = Some(1),
      enhancementFactor = Some(0.5)
    )
  ))

  val testJson: JsValue = Json.parse(
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



  "writes" -> {
    "should return the expected JSON" in {
      Json.toJson(testModel) mustBe testJson
    }
  }

  "reads" -> {
    "return a JsError when reading from invalid JSON" in {
      JsObject.empty.validate[ProtectionRecordDetails] mustBe a[JsError]
    }

    "return a JsSuccess when reading from valid JSON" in {
      val result = testJson.validate[ProtectionRecordDetails]
      result mustBe a[JsSuccess[_]]
      result.get mustBe testModel
    }
  }
}
