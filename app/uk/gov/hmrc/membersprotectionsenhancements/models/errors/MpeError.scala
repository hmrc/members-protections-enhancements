/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.membersprotectionsenhancements.models.errors

import play.api.libs.json.{Json, OWrites}

sealed case class MpeError(
  code: String,
  message: String,
  source: ErrorSource = Internal
)

object MpeError {
  implicit val writes: OWrites[MpeError] = Json.writes[MpeError]

  implicit def genericWrites[T <: MpeError]: OWrites[T] =
    writes.contramap[T](c => c: MpeError)

}

object UnauthorisedError
    extends MpeError(
      code = "CLIENT_OR_AGENT_NOT_AUTHORISED",
      message = "The client and/or agent is not authorised"
    )

object InvalidBearerTokenError
    extends MpeError(
      code = "UNAUTHORIZED",
      message = "Bearer token is missing or not authorized"
    )

object InternalFaultError
    extends MpeError(
      code = "INTERNAL_SERVER_ERROR",
      message = "An internal server error occurred"
    )

object MissingCorrelationIdError
    extends MpeError(
      code = "MISSING_CORRELATION_ID",
      message = "CorrelationId header could not be found in request"
    )

object NoMatchError
    extends MpeError(
      code = "NO_MATCH",
      message = "Matching API returned NO MATCH result for supplied member details",
      source = MatchPerson
    )

object EmptyDataError
    extends MpeError(
      code = "EMPTY_DATA",
      message = "Retrieve API returned a successful response containing no supported protections or enhancements",
      source = RetrieveMpe
    )

object UnexpectedStatusError
    extends MpeError(
      code = "UNEXPECTED_STATUS_ERROR",
      message = "An unexpected status code was returned from downstream"
    )
