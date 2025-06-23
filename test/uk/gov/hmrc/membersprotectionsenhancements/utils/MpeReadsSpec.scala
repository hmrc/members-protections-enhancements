package uk.gov.hmrc.membersprotectionsenhancements.utils

import base.UnitBaseSpec
import MpeReads.{ReadsWithError, dateReads, matchResult, name, nino, psaCheckRef}
import play.api.libs.json.{JsError, JsNumber, JsPath, JsString, JsSuccess}

import java.time.LocalDate

class MpeReadsSpec extends UnitBaseSpec {
  "name" -> {
    "should read correctly for a valid name string" in {
      name.reads(JsString("Name")) mustBe JsSuccess("Name")
    }

    "should read an error for a invalid name string" in {
      name.reads(JsNumber(1)) mustBe a [JsError]
    }
  }

  "nino" -> {
    "should read correctly for a valid nino string" in {
      nino.reads(JsString("AA111111A")) mustBe JsSuccess("AA111111A")
    }

    "should read an error for a invalid nino string" in {
      nino.reads(JsString("not valid")) mustBe a [JsError]
    }
  }

  "psaCheckRef" -> {
    "should read correctly for a valid check ref string" in {
      psaCheckRef.reads(JsString("PSA11111111A")) mustBe JsSuccess("PSA11111111A")
    }

    "should read an error for a invalid check ref string" in {
      psaCheckRef.reads(JsString("invalid")) mustBe a [JsError]
    }
  }

  "matchResult" -> {
    "should read correctly for a valid match result string" in {
      matchResult.reads(JsString("MATCH")) mustBe JsSuccess("MATCH")
    }

    "should read an error for a invalid match result string" in {
      matchResult.reads(JsString("invalid")) mustBe a [JsError]
    }
  }

  "dateReads" -> {
    "should read correctly for a valid date string" in {
      dateReads.reads(JsString("1990-11-11")) mustBe JsSuccess(LocalDate.of(1990, 11, 11))
    }

    "should read an error for a invalid date string" in {
      dateReads.reads(JsString("")) mustBe a [JsError]
    }
  }

  "orError" -> {
    "return the expected error" in {
      val reads = new ReadsWithError(dateReads).orError(JsPath.apply(Nil), "")
      reads.reads(JsString("")) mustBe JsError(JsPath.apply(Nil), "")
    }
  }
}
