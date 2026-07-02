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
import utils.ErrorCodes._
import models.response.MpeResponse
import utils.HeaderKey.correlationIdKey
import models.errors._
import play.api.Logging
import play.api.libs.json._
import play.api.http.Status._
import uk.gov.hmrc.http.{HttpErrorFunctions, HttpReads, HttpResponse}

abstract class BaseNpsConnector[Resp: Reads] extends HttpErrorFunctions with Logging {
  val config: AppConfig
  val source: ErrorSource

  private type ReadsResponse[A] = Either[MpeError, MpeResponse[A]]

  implicit def httpReads: HttpReads[ReadsResponse[Resp]] = (method: String, url: String, response: HttpResponse) => {
    response.header(correlationIdKey).getOrElse("N/A")

    if (response.status == OK) {
      if (method == "GET" && (response.body.isEmpty || response.json == JsObject.empty)) {
        Left(EmptyDataError)
      } else {
        jsonValidation[Resp](response.body)
      }
    } else {
      logger.warn(s"HTTP response contained error status: ${response.status}")
      Left(handleErrorResponse(method, url, response))
    }
  }

  protected[connectors] def jsonValidation[Rds: Reads](
    body: String
  ): ReadsResponse[Rds] =
    try
      Json.parse(body).validate[Rds] match {
        case JsSuccess(value, _) =>
          Right[MpeError, MpeResponse[Rds]](MpeResponse(value)).withLeft
        case JsError(errors) =>
          logger.error(
            s"Failed to parse response body JSON to expected format",
            JsResultException(errors)
          )
          Left(InternalFaultError)
      }
    catch {
      case ex: Throwable =>
        logger.error(
          s"Failed to parse response body string to JSON",
          ex
        )
        Left(InternalFaultError)
    }

  protected[connectors] def handleErrorResponse(
    httpMethod: String,
    url: String,
    response: HttpResponse
  ): MpeError = {
    val errorMap: Map[Int, String] = Map(
      BAD_REQUEST -> BAD_REQUEST_ERROR,
      FORBIDDEN -> FORBIDDEN_ERROR,
      NOT_FOUND -> NOT_FOUND_ERROR,
      INTERNAL_SERVER_ERROR -> INTERNAL_ERROR,
      SERVICE_UNAVAILABLE -> SERVICE_UNAVAILABLE_ERROR,
      UNPROCESSABLE_ENTITY -> NOT_FOUND_ERROR
    )
    errorMap.get(response.status) match {
      case Some(errorCode) =>
        val errorMessage: String = upstreamResponseMessage(
          verbName = httpMethod,
          url = url,
          status = response.status,
          responseBody = "REDACTED"
        )
        logger.warn(errorMessage)
        MpeError(errorCode, errorMessage, source)
      case None =>
        logger.warn(s"Error response status: ${response.status} did not match supported error scenarios")
        UnexpectedStatusError
    }
  }
}
