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

package uk.gov.hmrc.membersprotectionsenhancements.connectors

import uk.gov.hmrc.membersprotectionsenhancements.models.errors.ErrorSource.Internal
import base.UnitBaseSpec
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.membersprotectionsenhancements.config.AppConfig
import uk.gov.hmrc.membersprotectionsenhancements.models.response.ResponseWrapper
import uk.gov.hmrc.membersprotectionsenhancements.utils.Logging
import uk.gov.hmrc.membersprotectionsenhancements.models.errors._
import play.api.http.Status._
import uk.gov.hmrc.http._

class BaseNpsConnectorSpec extends UnitBaseSpec {

  protected case class DummyClass(field: String)

  protected object DummyClass {
    implicit val reads: Reads[DummyClass] = Json.reads[DummyClass]
  }

  private object TestObject extends BaseNpsConnector[DummyClass] with Logging {
    override val errorMap: Map[Int, String] = Map(
      IM_A_TEAPOT -> "TEAPOT_TIME"
    )

    override val config: AppConfig = mock[AppConfig]
    override val source: ErrorSource = Internal
  }

  private val dummyUrl: String = "some-url"
  private val correlationId = "X-123"

  "handleErrorResponse" -> {
    "[handleErrorResponse] handle appropriately for supported error status" in {
      val dummyResponse = HttpResponse(IM_A_TEAPOT, "{}")
      lazy val testResult: ErrorWrapper = TestObject.handleErrorResponse(
        httpMethod = "get",
        url = dummyUrl,
        response = dummyResponse,
        correlationId = correlationId,
        extraContext = None
      )
      testResult.error.code mustBe "TEAPOT_TIME"
    }

    "[handleErrorResponse] handle appropriately for unsupported error status" in {
      val dummyResponse = HttpResponse(IM_A_TEAPOT + 1, "{}")
      lazy val testResult: ErrorWrapper = TestObject.handleErrorResponse(
        httpMethod = "get",
        url = dummyUrl,
        response = dummyResponse,
        correlationId = correlationId,
        extraContext = None
      )
      testResult.error.code mustBe "UNEXPECTED_STATUS_ERROR"
    }
  }

  "jsonValidation" -> {
    "[jsonValidation] when provided with non-valid JSON should return an error" in {
      TestObject.jsonValidation[DummyClass]("", correlationId, None) mustBe Left(
        ErrorWrapper(correlationId, InternalFaultError)
      )
      TestObject.jsonValidation[DummyClass]("""{"field"}""", correlationId, None) mustBe Left(
        ErrorWrapper(correlationId, InternalFaultError)
      )
    }

    "[jsonValidation] when provided with valid JSON which breaks validation rules should return an error" in {
      lazy val res: Either[ErrorWrapper, ResponseWrapper[DummyClass]] = TestObject.jsonValidation[DummyClass](
        """
          |{
          | "field": 2
          |}
        """.stripMargin,
        correlationId,
        None
      )

      res mustBe a[Left[_, _]]
      res mustBe Left(ErrorWrapper(correlationId, InternalFaultError))
    }

    "[jsonValidation] when provided with valid JSON should return expected data model" in {
      val res: Either[ErrorWrapper, ResponseWrapper[DummyClass]] = TestObject.jsonValidation[DummyClass](
        """
          |{
          | "field": "value"
          |}
        """.stripMargin,
        correlationId,
        Some("loggingContext")
      )

      res mustBe a[Right[_, _]]
      res.getOrElse(ResponseWrapper(correlationId, DummyClass("N/A"))) mustBe ResponseWrapper(
        correlationId,
        DummyClass("value")
      )
    }
  }

  "httpReads" -> {
    "[httpReads] should return an error for an expected error status" in {
      val result: Either[ErrorWrapper, ResponseWrapper[DummyClass]] = TestObject.httpReads
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(IM_A_TEAPOT, "")
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(ErrorWrapper(correlationId, InternalFaultError)).error.code mustBe "TEAPOT_TIME"
    }

    "[httpReads] should handle appropriately for an unexpected status code" in {
      val result: Either[ErrorWrapper, ResponseWrapper[DummyClass]] = TestObject.httpReads
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(IM_A_TEAPOT + 1, "")
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(ErrorWrapper(correlationId, InternalFaultError)).error.code mustBe "UNEXPECTED_STATUS_ERROR"
    }

    "[httpReads] should handle appropriately for a success" in {
      val result: Either[ErrorWrapper, ResponseWrapper[DummyClass]] = TestObject.httpReads
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(OK, """{"field": "value"}""")
        )

      result mustBe a[Right[_, _]]
      result.getOrElse(ResponseWrapper(correlationId, DummyClass("N/A"))).responseData.field mustBe "value"
    }

    "[httpReads] of GET method should handle appropriately for a success with no response body" in {
      val result: Either[ErrorWrapper, ResponseWrapper[DummyClass]] = TestObject.httpReads
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(OK)
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(ErrorWrapper(correlationId, InternalFaultError)).error.code mustBe EmptyDataError.code
    }

    "[httpReads] of GET method should handle appropriately for a success with empty json response" in {
      val result: Either[ErrorWrapper, ResponseWrapper[DummyClass]] = TestObject.httpReads
        .read(
          method = "GET",
          url = dummyUrl,
          response = HttpResponse(OK, """{}""")
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(ErrorWrapper(correlationId, InternalFaultError)).error.code mustBe EmptyDataError.code
    }

    "[httpReads] of POST method should handle appropriately for a success with no response body" in {
      val result: Either[ErrorWrapper, ResponseWrapper[DummyClass]] = TestObject.httpReads
        .read(
          method = "POST",
          url = dummyUrl,
          response = HttpResponse(OK)
        )

      result mustBe a[Left[_, _]]
      result.swap.getOrElse(ErrorWrapper(correlationId, InternalFaultError)).error.code mustBe "INTERNAL_SERVER_ERROR"
    }
  }

}
