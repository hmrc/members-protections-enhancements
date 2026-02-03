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

package models.response

import base.UnitBaseSpec
import models.response.{ProtectionRecord, ProtectionRecordDetails}
import play.api.libs.json._

class ProtectionRecordDetailsSpec extends UnitBaseSpec {
  val testModel: ProtectionRecordDetails = ProtectionRecordDetails(
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

  val emptyJson: JsValue = Json.parse(
    """
      |{
      | "protectionRecords": [
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

    "return a JsSuccess when reading an empty ProtectionRecordDetails JSON" in {
      val result = emptyJson.validate[ProtectionRecordDetails]
      result mustBe a[JsSuccess[_]]
      result.get mustBe ProtectionRecordDetails(Seq.empty)
    }
  }
}
