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

import uk.gov.hmrc.membersprotectionsenhancements.models.errors.MpeError
import play.api.http.Status._
import uk.gov.hmrc.http._

trait HttpResponseHelper extends HttpErrorFunctions with Logging {

  implicit val httpResponseReads: HttpReads[HttpResponse] = (method: String, url: String, response: HttpResponse) =>
    response

  def handleErrorResponse(httpMethod: String, url: String)(response: HttpResponse): MpeError =
    response.status match {
      case BAD_REQUEST =>
        val message = badRequestMessage(httpMethod, url, response.body)
        logger.warn(s"[HttpResponseHelper][handleErrorResponse][BAD_REQUEST] - $message")
        MpeError("BAD_REQUEST", message)
      case NOT_FOUND =>
        val message = notFoundMessage(httpMethod, url, response.body)
        logger.warn(s"[HttpResponseHelper][handleErrorResponse][NOT_FOUND] - $message")
        MpeError("NOT_FOUND", message)
      case status if is4xx(status) =>
        val message = upstreamResponseMessage(httpMethod, url, status, response.body)
        logger.warn(s"[HttpResponseHelper][handleErrorResponse] - $message")
        MpeError("FORBIDDEN", message)
      case status if is5xx(status) =>
        val message = upstreamResponseMessage(httpMethod, url, status, response.body)
        logger.warn(s"[HttpResponseHelper][handleErrorResponse] - $message")
        MpeError("INTERNAL_ERROR", message)
      case _ =>
        throw new UnrecognisedHttpResponseException(httpMethod, url, response)
    }

}

class UnrecognisedHttpResponseException(method: String, url: String, response: HttpResponse)
    extends Exception(s"$method to $url failed with status ${response.status}. Response body: '${response.body}'")
