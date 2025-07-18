/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.membersprotectionsenhancements.utils

import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{InternalError, MpeError}
import base.UnitBaseSpec
import play.api.libs.json.{Json, Reads}
import play.api.http.Status._
import uk.gov.hmrc.http._

class HttpResponseHelperSpec extends UnitBaseSpec {

  private object TestObject extends HttpResponseHelper {
    override val classLoggingContext: String = ""
  }

  protected case class DummyClass(field: String)

  protected object DummyClass {
    implicit val reads: Reads[DummyClass] = Json.reads[DummyClass]
  }

  private val dummyUrl: String = "some-url"

  "handleErrorResponse" -> {
    def handleErrorScenario(status: Int, method: String, expectedErrorCode: String): Unit =
      s"[handleErrorResponse] should handle appropriately for method: $method, and status: $status" in {
        val dummyResponse = HttpResponse(status, "{}")
        lazy val testResult: MpeError = TestObject.handleErrorResponse(method, dummyUrl, dummyResponse)
        testResult.code mustBe expectedErrorCode
      }

    val testScenarios: Seq[(Int, String, String)] = Seq(
      (BAD_REQUEST, "GET", "BAD_REQUEST"),
      (FORBIDDEN, "GET", "FORBIDDEN"),
      (NOT_FOUND, "GET", "NOT_FOUND"),
      (NOT_FOUND, "POST", "UNEXPECTED_STATUS_ERROR"),
      (UNPROCESSABLE_ENTITY, "GET", "UNPROCESSABLE_ENTITY"),
      (UNPROCESSABLE_ENTITY, "PUT", "UNEXPECTED_STATUS_ERROR"),
      (INTERNAL_SERVER_ERROR, "GET", "INTERNAL_ERROR"),
      (SERVICE_UNAVAILABLE, "GET", "SERVICE_UNAVAILABLE"),
      (IM_A_TEAPOT, "GET", "UNEXPECTED_STATUS_ERROR")
    )

    testScenarios.foreach(scenario => handleErrorScenario(scenario._1, scenario._2, scenario._3))
  }

  "jsonValidation" -> {
    "[jsonValidation] when provided with non-valid JSON should return an error" in {
      TestObject.jsonValidation[DummyClass]("") mustBe Left(InternalError)
      TestObject.jsonValidation[DummyClass]("""{"field"}""") mustBe Left(InternalError)
    }

    "[jsonValidation] when provided with valid JSON which breaks validation rules should return an error" in {
      lazy val res: Either[MpeError, DummyClass] = TestObject.jsonValidation[DummyClass](
        """
          |{
          | "field": 2
          |}
        """.stripMargin
      )

      res mustBe a[Left[_, _]]
      res mustBe Left(InternalError)
    }

    "[jsonValidation] when provided with valid JSON should return expected data model" in {
      val res: Either[MpeError, DummyClass] = TestObject.jsonValidation[DummyClass](
        """
          |{
          | "field": "value"
          |}
        """.stripMargin
      )

      res mustBe a[Right[_, _]]
      res.getOrElse(DummyClass("N/A")) mustBe DummyClass("value")
    }
  }

  "httpReads" -> {
    "[httpReads] should return an error for an expected error status" in {
      val result: Either[MpeError, DummyClass] = TestObject
        .httpReads[DummyClass]
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(BAD_REQUEST, "")
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(InternalError).code mustBe "BAD_REQUEST"
    }

    "[httpReads] should handle appropriately for an unexpected status code" in {
      val result: Either[MpeError, DummyClass] = TestObject
        .httpReads[DummyClass]
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(IM_A_TEAPOT, "")
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(InternalError).code mustBe "UNEXPECTED_STATUS_ERROR"
    }

    "[httpReads] should handle appropriately for a success" in {
      val result: Either[MpeError, DummyClass] = TestObject
        .httpReads[DummyClass]
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(OK, """{"field": "value"}""")
        )

      result mustBe a[Right[_, _]]
      result.getOrElse(DummyClass("N/A")).field mustBe "value"
    }
  }

}
