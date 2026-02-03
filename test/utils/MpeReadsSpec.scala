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

package utils

import base.UnitBaseSpec
import play.api.libs.json._
import utils.MpeReads._

import java.time.LocalDate

class MpeReadsSpec extends UnitBaseSpec {
  "name" -> {
    "should read correctly for a valid name string" in {
      name.reads(JsString("Name")) mustBe JsSuccess("Name")
    }

    "should read an error for a invalid name string" in {
      name.reads(JsNumber(1)) mustBe a[JsError]
    }
  }

  "identifier" -> {
    "should read correctly for a valid nino string" in {
      identifier.reads(JsString("AA111111A")) mustBe JsSuccess("AA111111A")
    }

    "should read correctly for a valid TRN string" in {
      identifier.reads(JsString("99L99999")) mustBe JsSuccess("99L99999")
    }

    "should read an error for a invalid nino string" in {
      identifier.reads(JsString("not valid")) mustBe a[JsError]
    }
  }

  "psaCheckRef" -> {
    "should read correctly for a valid check ref string" in {
      psaCheckRef.reads(JsString("PSA11111111A")) mustBe JsSuccess("PSA11111111A")
    }

    "should read an error for a invalid check ref string" in {
      psaCheckRef.reads(JsString("invalid")) mustBe a[JsError]
    }
  }

  "matchResult" -> {
    "should read correctly for a valid match result string" in {
      matchResult.reads(JsString("MATCH")) mustBe JsSuccess("MATCH")
    }

    "should read an error for a invalid match result string" in {
      matchResult.reads(JsString("invalid")) mustBe a[JsError]
    }
  }

  "dateReads" -> {
    "should read correctly for a valid date string" in {
      dateReads.reads(JsString("1990-11-11")) mustBe JsSuccess(LocalDate.of(1990, 11, 11))
    }

    "should read an error for a invalid date string" in {
      dateReads.reads(JsString("")) mustBe a[JsError]
    }
  }

  "orError" -> {
    "return the expected error" in {
      val reads = new ReadsWithError(dateReads).orError(JsPath.apply(Nil), "")
      reads.reads(JsString("")) mustBe JsError(JsPath.apply(Nil), "")
    }
  }
}
