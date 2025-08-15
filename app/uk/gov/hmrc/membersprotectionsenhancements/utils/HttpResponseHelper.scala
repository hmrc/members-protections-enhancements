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
import uk.gov.hmrc.membersprotectionsenhancements.models.response.ResponseWrapper
import play.api.http.Status._
import uk.gov.hmrc.http._

trait HttpResponseHelper extends HttpErrorFunctions with Logging {
  protected val classLoggingContext: String

  private def retrieveCorrelationId(response: HttpResponse): String =
    response.header("correlationId").getOrElse("No correlationId")

  implicit def httpReads[Resp: Reads]: HttpReads[Either[ErrorWrapper, ResponseWrapper[Resp]]] =
    (method: String, url: String, response: HttpResponse) => {
      val methodLoggingContext: String = "[httpReads]"
      val logContextString = classLoggingContext + methodLoggingContext

      logger.info(
        s"$logContextString - Attempting to read HTTP response for request with method: $method, and url: $url" +
          s" with correlationId ${retrieveCorrelationId(response)}"
      )

      if (response.status == OK) {
        logger.info(
          s"$logContextString - HTTP response contained success status. Attempting to parse response body" +
            s" with correlationId ${retrieveCorrelationId(response)}"
        )
        jsonValidation[Resp](response.body, retrieveCorrelationId(response))
      } else {
        logger.warn(
          s"$logContextString - HTTP response contained error status: ${response.status}. Attempting to handle error" +
            s" with correlationId ${retrieveCorrelationId(response)}"
        )
        Left(
          handleErrorResponse(httpMethod = method, url = url, response = response)
        )
      }
    }

  protected[utils] def jsonValidation[Resp: Reads](
    body: String,
    correlationId: String
  ): Either[ErrorWrapper, ResponseWrapper[Resp]] = {
    val methodLoggingContext: String = "[jsonValidation]"
    val logContextString = classLoggingContext + methodLoggingContext

    try {
      logger.info(s"$logContextString - Attempting to parse response body string to JSON")
      val responseJson: JsValue = Json.parse(body)

      logger.info(
        s"$logContextString - Successfully parsed response body string to JSON. Validating against expected format"
      )

      responseJson.validate[Resp] match {
        case JsSuccess(value, _) =>
          logger.info(s"$logContextString - Successfully parsed response body JSON to expected format")
          Right[ErrorWrapper, ResponseWrapper[Resp]](ResponseWrapper(correlationId, value)).withLeft
        case JsError(errors) =>
          logger.error(
            message = s"$logContextString - Failed to parse response body JSON to expected format with errors: $errors",
            error = JsResultException(errors)
          )
          Left(ErrorWrapper(correlationId, InternalError))
      }
    } catch {
      case ex: JsonParseException =>
        logger.error(
          message = s"$logContextString - Failed to parse response body string to JSON with error: ${ex.getMessage}",
          error = ex
        )
        Left(ErrorWrapper(correlationId, InternalError))
      case ex: JsonMappingException =>
        logger.error(
          message = s"$logContextString - Failed to parse response body string to JSON with error: ${ex.getMessage}",
          error = ex
        )
        Left(ErrorWrapper(correlationId, InternalError))
    }
  }

  protected[utils] def handleErrorResponse(
    httpMethod: String,
    url: String,
    response: HttpResponse
  ): ErrorWrapper = {
    val methodLoggingContext: String = "[handleErrorResponse]"
    val logContextString = classLoggingContext + methodLoggingContext
    val correlationId = retrieveCorrelationId(response)
    response.status match {
      case BAD_REQUEST =>
        val message = badRequestMessage(httpMethod, url, response.body)
        logger.warn(s"$logContextString[BAD_REQUEST] - $message with correlationId $correlationId")
        ErrorWrapper(correlationId, MpeError("BAD_REQUEST", message))
      case FORBIDDEN =>
        val message = upstreamResponseMessage(httpMethod, url, FORBIDDEN, response.body)
        logger.warn(s"$logContextString - $message with correlationId $correlationId")
        ErrorWrapper(correlationId, MpeError("FORBIDDEN", message))
      case NOT_FOUND =>
        val message = notFoundMessage(httpMethod, url, response.body)
        logger.warn(s"$logContextString[NOT_FOUND] - $message with correlationId $correlationId")
        ErrorWrapper(correlationId, MpeError("NOT_FOUND", message))
      case UNPROCESSABLE_ENTITY if httpMethod == "GET" =>
        val message = upstreamResponseMessage(httpMethod, url, UNPROCESSABLE_ENTITY, response.body)
        logger.warn(s"$logContextString - $message with correlationId $correlationId converting to NOT_FOUND status")
        ErrorWrapper(correlationId, MpeError("NOT_FOUND", message))
      case INTERNAL_SERVER_ERROR =>
        val message = upstreamResponseMessage(httpMethod, url, INTERNAL_SERVER_ERROR, response.body)
        logger.warn(s"$logContextString - $message with correlationId ${retrieveCorrelationId(response)}")
        ErrorWrapper(correlationId, MpeError("INTERNAL_ERROR", message))
      case SERVICE_UNAVAILABLE =>
        val message = upstreamResponseMessage(httpMethod, url, SERVICE_UNAVAILABLE, response.body)
        logger.warn(s"$logContextString - $message with correlationId ${retrieveCorrelationId(response)}")
        ErrorWrapper(correlationId, MpeError("SERVICE_UNAVAILABLE", message))
      case status =>
        logger.error(
          message =
            s"$logContextString - Received an unexpected error status: $status with correlationId ${retrieveCorrelationId(response)}",
          error = new UnrecognisedHttpResponseException(httpMethod, url, response)
        )
        ErrorWrapper(correlationId, UnexpectedStatusError)
    }
  }
}

class UnrecognisedHttpResponseException(method: String, url: String, response: HttpResponse)
    extends Exception(
      s"$method to $url failed with status ${response.status}. Response body: '${response.body}'"
    )
