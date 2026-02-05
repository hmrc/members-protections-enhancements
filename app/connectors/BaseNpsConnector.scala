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

package connectors

import utils.Logging
import controllers.requests.CorrelationId
import com.fasterxml.jackson.core.JsonParseException
import models.response.ResponseWrapper
import play.api.libs.json._
import models.errors._
import com.fasterxml.jackson.databind.JsonMappingException
import config.AppConfig
import play.api.http.Status._
import uk.gov.hmrc.http.{HttpErrorFunctions, HttpReads, HttpResponse}

import java.util.Base64

abstract class BaseNpsConnector[Resp: Reads] extends HttpErrorFunctions { this: Logging =>
  val config: AppConfig
  val source: ErrorSource

  private def retrieveCorrelationId(response: HttpResponse): CorrelationId = CorrelationId(
    response.header("correlationId").getOrElse("N/A")
  )

  protected[connectors] def authorization(): String = {
    val clientId = config.npsClientId
    val secret = config.npsSecret

    val encoded = Base64.getEncoder.encodeToString(s"$clientId:$secret".getBytes("UTF-8"))
    s"Basic $encoded"
  }

  private type ReadsResponse[Wrapped] = Either[ErrorWrapper, ResponseWrapper[Wrapped]]

  protected[connectors] val errorMap: Map[Int, String]

  protected[connectors] def checkIdsMatch(
    requestCorrelationId: CorrelationId,
    responseCorrelationId: CorrelationId,
    extraLoggingContext: Option[String]
  ): CorrelationId = {
    if (requestCorrelationId.value != responseCorrelationId.value) {
      logger.error(
        secondaryContext = "checkIdsMatch",
        message = "Correlation ID was either missing from response, or did not match ID from request. " +
          "Reverting to ID from request for consistency in logs. Be aware of potential ID inconsistencies. " +
          s"Request C-ID: ${requestCorrelationId.value}, Response C-ID: ${responseCorrelationId.value}",
        extraContext = extraLoggingContext
      )
    }

    requestCorrelationId
  }

  implicit def httpReads: HttpReads[ReadsResponse[Resp]] = (method: String, url: String, response: HttpResponse) => {
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
        s"HTTP response contained error status: ${response.status}",
        None
      )
      Left(handleErrorResponse(method, url, response, correlationId, Some(methodLoggingContext)))
    }
  }

  protected[connectors] def jsonValidation[Rds: Reads](
    body: String,
    correlationId: CorrelationId,
    extraContext: Option[String]
  ): ReadsResponse[Rds] = {
    val methodLoggingContext: String = "[jsonValidation]"

    val idLogString: String = correlationIdLogString(correlationId)

    val infoLogger: String => Unit = infoLog(methodLoggingContext, idLogString, extraContext)
    val errorLogger: (String, Option[Throwable]) => Unit = errorLog(methodLoggingContext, idLogString, extraContext)

    try {
      infoLogger(s"Attempting to parse response body string to JSON")
      val responseJson: JsValue = Json.parse(body)

      infoLogger("Successfully parsed response body string to JSON. Validating against expected format")

      responseJson.validate[Rds] match {
        case JsSuccess(value, _) =>
          infoLogger("Successfully parsed response body JSON to expected format")
          Right[ErrorWrapper, ResponseWrapper[Rds]](ResponseWrapper(correlationId, value)).withLeft
        case JsError(errors) =>
          errorLogger(
            s"Failed to parse response body JSON to expected format",
            Some(JsResultException(errors))
          )
          Left(ErrorWrapper(correlationId, InternalFaultError))
      }
    } catch {
      case ex: JsonParseException =>
        errorLogger(
          s"Failed to parse response body string to JSON",
          Some(ex)
        )
        Left(ErrorWrapper(correlationId, InternalFaultError))
      case ex: JsonMappingException =>
        errorLogger(
          s"Failed to parse response body string to JSON",
          Some(ex)
        )
        Left(ErrorWrapper(correlationId, InternalFaultError))
    }
  }

  protected[connectors] def handleErrorResponse(
    httpMethod: String,
    url: String,
    response: HttpResponse,
    correlationId: CorrelationId,
    extraContext: Option[String]
  ): ErrorWrapper = {
    val methodLoggingContext: String = "handleErrorResponse"

    val idLogString: String = correlationIdLogString(correlationId)
    val infoLogger: String => Unit = infoLog(methodLoggingContext, idLogString, extraContext)
    val warnLogger: (String, Option[Throwable]) => Unit = warnLog(methodLoggingContext, idLogString, extraContext)
    val errorLogger: (String, Option[Throwable]) => Unit = errorLog(methodLoggingContext, idLogString, extraContext)

    infoLogger(s"Attempting to check error response status against supported error scenarios")

    errorMap.get(response.status) match {
      case Some(errorCode) =>
        val errorMessage: String = upstreamResponseMessage(
          verbName = httpMethod,
          url = url,
          status = response.status,
          responseBody = "REDACTED"
        )
        warnLogger(errorMessage, None)
        ErrorWrapper(correlationId, MpeError(errorCode, errorMessage, source))
      case None =>
        errorLogger(
          s"Error response status: ${response.status} did not match supported error scenarios",
          Some(new UnrecognisedHttpResponseException(httpMethod, url, response))
        )
        ErrorWrapper(correlationId, UnexpectedStatusError)
    }
  }
}

class UnrecognisedHttpResponseException(method: String, url: String, response: HttpResponse)
    extends Exception(
      s"$method to $url failed with unexpected status ${response.status}"
    )
