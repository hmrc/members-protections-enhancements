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

package uk.gov.hmrc.membersprotectionsenhancements.models.errors

import base.UnitBaseSpec
import play.api.libs.json.Json

class MpeErrorSpec extends UnitBaseSpec {
  "MpeError" -> {
    "writes should return the expected JSON" in {
      MpeError.writes.writes(MpeError("CODE", "Message", reasons = Some(Seq("reason")))) mustBe Json.parse(
        """
          |{
          | "code": "CODE",
          | "message": "Message",
          | "source": "Internal",
          | "reasons": ["reason"]
          |}
        """.stripMargin
      )
    }

    "genericWrites should return the expected JSON" in {
      MpeError.genericWrites.writes(InternalError) mustBe Json.parse(
        """
          |{
          | "code": "INTERNAL_SERVER_ERROR",
          | "message": "An internal server error occurred",
          | "source": "Internal"
          |}
        """.stripMargin
      )
    }
  }

}
