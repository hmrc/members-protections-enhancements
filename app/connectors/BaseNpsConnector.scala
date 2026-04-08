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

import controllers.requests.CorrelationId
import com.fasterxml.jackson.core.JsonParseException
import utils.ErrorCodes.REDACTED
import models.response.ResponseWrapper
import utils.HeaderKey.correlationIdKey
import models.errors._
import com.fasterxml.jackson.databind.JsonMappingException
import config.AppConfig
import play.api.Logging
import play.api.libs.json._
import play.api.http.Status._
import uk.gov.hmrc.http.{HttpErrorFunctions, HttpReads, HttpResponse}

import java.util.Base64

abstract class BaseNpsConnector[Resp: Reads] extends HttpErrorFunctions with Logging {
  val config: AppConfig
  val source: ErrorSource

  private def retrieveCorrelationId(response: HttpResponse): CorrelationId = CorrelationId(
    response.header(correlationIdKey).getOrElse("N/A")
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
    responseCorrelationId: CorrelationId
  ): CorrelationId = {
    if (requestCorrelationId.value != responseCorrelationId.value) {
      logger.error(
        message = "Correlation ID was either missing from response, or did not match ID from request. " +
          "Reverting to ID from request for consistency in logs. Be aware of potential ID inconsistencies. " +
          s"Request C-ID: ${requestCorrelationId.value}, Response C-ID: ${responseCorrelationId.value}"
      )
    }

    requestCorrelationId
  }

  implicit def httpReads: HttpReads[ReadsResponse[Resp]] = (method: String, url: String, response: HttpResponse) => {
    val correlationId: CorrelationId = retrieveCorrelationId(response)

    if (response.status == OK) {
      if (method == "GET" && (response.body.isEmpty || response.json == JsObject.empty)) {
        logger.info("HTTP response contained success status with an empty body. Converting to EmptyDataError")
        Left(ErrorWrapper(correlationId, EmptyDataError))
      } else {
        logger.info("HTTP response contained success status with a non-empty body. Attempting to parse response")
        jsonValidation[Resp](response.body, correlationId)
      }
    } else {
      logger.warn(s"HTTP response contained error status: ${response.status}")
      Left(handleErrorResponse(method, url, response, correlationId))
    }
  }

  protected[connectors] def jsonValidation[Rds: Reads](
    body: String,
    correlationId: CorrelationId
  ): ReadsResponse[Rds] =
    try {
      logger.info("")
      logger.info(s"Attempting to parse response body string to JSON")
      val responseJson: JsValue = Json.parse(body)

      logger.info("Successfully parsed response body string to JSON. Validating against expected format")

      responseJson.validate[Rds] match {
        case JsSuccess(value, _) =>
          logger.info("Successfully parsed response body JSON to expected format")
          Right[ErrorWrapper, ResponseWrapper[Rds]](ResponseWrapper(correlationId, value)).withLeft
        case JsError(errors) =>
          logger.error(
            s"Failed to parse response body JSON to expected format",
            JsResultException(errors)
          )
          Left(ErrorWrapper(correlationId, InternalFaultError))
      }
    } catch {
      case ex: JsonParseException =>
        logger.error(
          s"Failed to parse response body string to JSON",
          ex
        )
        Left(ErrorWrapper(correlationId, InternalFaultError))
      case ex: JsonMappingException =>
        logger.error(
          s"Failed to parse response body string to JSON",
          ex
        )
        Left(ErrorWrapper(correlationId, InternalFaultError))
    }

  protected[connectors] def handleErrorResponse(
    httpMethod: String,
    url: String,
    response: HttpResponse,
    correlationId: CorrelationId
  ): ErrorWrapper = {

    logger.info(s"Attempting to check error response status against supported error scenarios")

    errorMap.get(response.status) match {
      case Some(errorCode) =>
        val errorMessage: String = upstreamResponseMessage(
          verbName = httpMethod,
          url = url,
          status = response.status,
          responseBody = REDACTED
        )
        logger.warn(errorMessage)
        ErrorWrapper(correlationId, MpeError(errorCode, errorMessage, source))
      case None =>
        logger.warn(s"Error response status: ${response.status} did not match supported error scenarios")
        ErrorWrapper(correlationId, UnexpectedStatusError)
    }
  }
}

class UnrecognisedHttpResponseException(method: String, url: String, response: HttpResponse)
    extends Exception(
      s"$method to $url failed with unexpected status ${response.status}"
    )
