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

import uk.gov.hmrc.membersprotectionsenhancements.models.errors._
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json._
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.CorrelationId
import uk.gov.hmrc.membersprotectionsenhancements.models.response.ResponseWrapper
import play.api.http.Status._
import uk.gov.hmrc.http._

trait HttpResponseHelper extends HttpErrorFunctions { _: Logging =>

  private def retrieveCorrelationId(response: HttpResponse): CorrelationId =
    CorrelationId(response.header("correlationId").getOrElse("No correlationId"))

  implicit def httpReads[Resp: Reads]: HttpReads[Either[ErrorWrapper, ResponseWrapper[Resp]]] =
    (method: String, url: String, response: HttpResponse) => {
      val methodLoggingContext: String = "httpReads"
      val correlationId: CorrelationId = retrieveCorrelationId(response)
      val idLogString: String = correlationIdLogString(correlationId)

      val infoLogger: String => Unit = infoLog(methodLoggingContext, idLogString)
      val warnLogger: (String, Option[Throwable]) => Unit = warnLog(methodLoggingContext, idLogString)

      infoLogger(s"Attempting to read HTTP response with method: $method, and url: $url")

      if (response.status == OK) {
        if (method == "GET" && (response.body.isEmpty || response.json == JsObject.empty)) {
          infoLogger("HTTP response contained success status with an empty body. Converting to EmptyDataError")
          Left(ErrorWrapper(correlationId, EmptyDataError))
        } else {
          infoLogger("HTTP response contained success status with a non-empty body. Attempting to parse response")
          jsonValidation[Resp](response.body, correlationId, Some(methodLoggingContext))
        }
      } else {
        warnLogger(
          s"HTTP response contained error status: ${response.status}. Attempting to handle error",
          None
        )
        Left(
          handleErrorResponse(method, url, response, correlationId, Some(methodLoggingContext))
        )
      }
    }

  protected[utils] def jsonValidation[Resp: Reads](
    body: String,
    correlationId: CorrelationId,
    extraContext: Option[String]
  ): Either[ErrorWrapper, ResponseWrapper[Resp]] = {
    val methodLoggingContext: String = "[jsonValidation]"

    val idLogString: String = correlationIdLogString(correlationId)

    val infoLogger: String => Unit = infoLog(methodLoggingContext, idLogString, extraContext)
    val errorLogger: (String, Option[Throwable]) => Unit = errorLog(methodLoggingContext, idLogString, extraContext)

    try {
      infoLogger(s"Attempting to parse response body string to JSON")
      val responseJson: JsValue = Json.parse(body)

      infoLogger("Successfully parsed response body string to JSON. Validating against expected format")

      responseJson.validate[Resp] match {
        case JsSuccess(value, _) =>
          infoLogger("Successfully parsed response body JSON to expected format")
          Right[ErrorWrapper, ResponseWrapper[Resp]](ResponseWrapper(correlationId, value)).withLeft
        case JsError(errors) =>
          errorLogger(
            s"Failed to parse response body JSON to expected format with errors: $errors",
            Some(JsResultException(errors))
          )
          Left(ErrorWrapper(correlationId, InternalFaultError))
      }
    } catch {
      case ex: JsonParseException =>
        errorLogger(
          s"Failed to parse response body string to JSON with error: ${ex.getMessage}",
          Some(ex)
        )
        Left(ErrorWrapper(correlationId, InternalFaultError))
      case ex: JsonMappingException =>
        errorLogger(
          s"Failed to parse response body string to JSON with error: ${ex.getMessage}",
          Some(ex)
        )
        Left(ErrorWrapper(correlationId, InternalFaultError))
    }
  }

  protected[utils] def handleErrorResponse(
    httpMethod: String,
    url: String,
    response: HttpResponse,
    correlationId: CorrelationId,
    extraContext: Option[String]
  ): ErrorWrapper = {
    val methodLoggingContext: String = "handleErrorResponse"

    val idLogString: String = correlationIdLogString(correlationId)
    val warnLogger: (String, Option[Throwable]) => Unit = warnLog(methodLoggingContext, idLogString, extraContext)
    val errorLogger: (String, Option[Throwable]) => Unit = errorLog(methodLoggingContext, idLogString, extraContext)

    response.status match {
      case BAD_REQUEST =>
        val message = badRequestMessage(httpMethod, url, response.body)
        warnLogger(message, None)
        ErrorWrapper(correlationId, MpeError("BAD_REQUEST", message))
      case FORBIDDEN =>
        val message = upstreamResponseMessage(httpMethod, url, FORBIDDEN, response.body)
        warnLogger(message, None)
        ErrorWrapper(correlationId, MpeError("FORBIDDEN", message))
      case NOT_FOUND =>
        val message = notFoundMessage(httpMethod, url, response.body)
        warnLogger(message, None)
        ErrorWrapper(correlationId, MpeError("NOT_FOUND", message))
      case UNPROCESSABLE_ENTITY if httpMethod == "GET" =>
        val message = upstreamResponseMessage(httpMethod, url, UNPROCESSABLE_ENTITY, response.body)
        warnLogger(message, None)
        ErrorWrapper(correlationId, MpeError("NOT_FOUND", message))
      case INTERNAL_SERVER_ERROR =>
        val message = upstreamResponseMessage(httpMethod, url, INTERNAL_SERVER_ERROR, response.body)
        warnLogger(message, None)
        ErrorWrapper(correlationId, MpeError("INTERNAL_ERROR", message))
      case SERVICE_UNAVAILABLE =>
        val message = upstreamResponseMessage(httpMethod, url, SERVICE_UNAVAILABLE, response.body)
        warnLogger(message, None)
        ErrorWrapper(correlationId, MpeError("SERVICE_UNAVAILABLE", message))
      case status =>
        errorLogger(
          s"Received an unexpected error status: $status",
          Some(new UnrecognisedHttpResponseException(httpMethod, url, response))
        )
        ErrorWrapper(correlationId, UnexpectedStatusError)
    }
  }
}

class UnrecognisedHttpResponseException(method: String, url: String, response: HttpResponse)
    extends Exception(
      s"$method to $url failed with status ${response.status}. Response body: '${response.body}'"
    )
