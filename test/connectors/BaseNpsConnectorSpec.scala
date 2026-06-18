/*
 * Copyright 2026 HM Revenue & Customs
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

package connectors

import config.AppConfig
import base.UnitBaseSpec
import utils.ErrorCodes.BAD_REQUEST_ERROR
import models.errors.ErrorSource.Internal
import models.response.MpeResponse
import models.errors._
import play.api.Logging
import play.api.libs.json.{Json, Reads}
import play.api.http.Status._
import uk.gov.hmrc.http._

class BaseNpsConnectorSpec extends UnitBaseSpec {

  protected case class DummyClass(field: String)

  protected object DummyClass {
    implicit val reads: Reads[DummyClass] = Json.reads[DummyClass]
  }

  private object TestObject extends BaseNpsConnector[DummyClass] with Logging {
    override val config: AppConfig = mock[AppConfig]
    override val source: ErrorSource = Internal
  }

  private val dummyUrl: String = "some-url"

  "handleErrorResponse" -> {
    "[handleErrorResponse] handle appropriately for supported error status" in {
      val dummyResponse = HttpResponse(BAD_REQUEST, "{}")
      lazy val testResult: MpeError = TestObject.handleErrorResponse(
        httpMethod = "get",
        url = dummyUrl,
        response = dummyResponse
      )
      testResult.code mustBe BAD_REQUEST_ERROR
    }

    "[handleErrorResponse] handle appropriately for unsupported error status" in {
      val dummyResponse = HttpResponse(BAD_REQUEST + 1, "{}")
      lazy val testResult = TestObject.handleErrorResponse(
        httpMethod = "get",
        url = dummyUrl,
        response = dummyResponse
      )
      testResult.code mustBe "UNEXPECTED_STATUS_ERROR"
    }
  }

  "jsonValidation" -> {
    "[jsonValidation] when provided with non-valid JSON should return an error" in {
      TestObject.jsonValidation[DummyClass]("") mustBe Left(InternalFaultError)
      TestObject.jsonValidation[DummyClass]("""{"field"}""") mustBe Left(InternalFaultError)
    }

    "[jsonValidation] when provided with valid JSON which breaks validation rules should return an error" in {
      lazy val res: Either[MpeError, MpeResponse[DummyClass]] = TestObject.jsonValidation[DummyClass](
        """
          |{
          | "field": 2
          |}
        """.stripMargin
      )

      res mustBe a[Left[_, _]]
      res mustBe Left(InternalFaultError)
    }

    "[jsonValidation] when provided with valid JSON should return expected data model" in {
      val res: Either[MpeError, MpeResponse[DummyClass]] = TestObject.jsonValidation[DummyClass](
        """
          |{
          | "field": "value"
          |}
        """.stripMargin
      )

      res mustBe a[Right[_, _]]
      res.getOrElse(MpeResponse(DummyClass("N/A"))) mustBe MpeResponse(DummyClass("value"))
    }
  }

  "httpReads" -> {
    "[httpReads] should return an error for an expected error status" in {
      val result: Either[MpeError, MpeResponse[DummyClass]] = TestObject.httpReads
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(BAD_REQUEST, "")
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(InternalFaultError).code mustBe BAD_REQUEST_ERROR
    }

    "[httpReads] should handle appropriately for an unexpected status code" in {
      val result: Either[MpeError, MpeResponse[DummyClass]] = TestObject.httpReads
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(BAD_REQUEST + 1, "")
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(InternalFaultError).code mustBe "UNEXPECTED_STATUS_ERROR"
    }

    "[httpReads] should handle appropriately for a success" in {
      val result: Either[MpeError, MpeResponse[DummyClass]] = TestObject.httpReads
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(OK, """{"field": "value"}""")
        )

      result mustBe a[Right[_, _]]
      result.getOrElse(MpeResponse(DummyClass("N/A"))).responseData.field mustBe "value"
    }

    "[httpReads] of GET method should handle appropriately for a success with no response body" in {
      val result: Either[MpeError, MpeResponse[DummyClass]] = TestObject.httpReads
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(OK)
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(InternalFaultError).code mustBe EmptyDataError.code
    }

    "[httpReads] of GET method should handle appropriately for a success with empty json response" in {
      val result: Either[MpeError, MpeResponse[DummyClass]] = TestObject.httpReads
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(OK, """{}""")
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(InternalFaultError).code mustBe EmptyDataError.code
    }

    "[httpReads] of POST method should handle appropriately for a success with no response body" in {
      val result: Either[MpeError, MpeResponse[DummyClass]] = TestObject.httpReads
        .read(
          method = "POST",
          url = dummyUrl,
          response = HttpResponse(OK)
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(InternalFaultError).code mustBe "INTERNAL_SERVER_ERROR"
    }
  }

}
