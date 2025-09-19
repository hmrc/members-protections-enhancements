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

import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.PensionSchemeMemberRequest.matchPersonWrites
import play.api.http.ContentTypes
import play.api.http.HeaderNames.{AUTHORIZATION, CONTENT_TYPE}
import cats.data.EitherT
import uk.gov.hmrc.membersprotectionsenhancements.utils.HeaderKey.{correlationIdKey, govUkOriginatorIdKey, ENVIRONMENT}
import play.api.libs.json.Json
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.{CorrelationId, PensionSchemeMemberRequest}
import uk.gov.hmrc.membersprotectionsenhancements.config.AppConfig
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.membersprotectionsenhancements.models.response.{MatchPersonResponse, ResponseWrapper}
import uk.gov.hmrc.membersprotectionsenhancements.utils.Logging
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{ErrorSource, ErrorWrapper, MatchPerson}
import play.api.http.Status._
import uk.gov.hmrc.http._

import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Singleton}
import java.net.URI

@Singleton
class MatchPersonNpsConnector @Inject() (val config: AppConfig, val http: HttpClientV2)
    extends BaseNpsConnector[MatchPersonResponse]
    with Logging {

  override val source: ErrorSource = MatchPerson

  def matchPerson(
    request: PensionSchemeMemberRequest
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    correlationId: CorrelationId
  ): ConnectorResult[MatchPersonResponse] = {
    val matchIndividualAccountUrl: String = config.matchUrl

    val methodLoggingContext: String = "matchPerson"
    def idLogString(correlationId: CorrelationId): String = correlationIdLogString(correlationId = correlationId)

    def infoLogger(correlationId: CorrelationId): String => Unit = infoLog(
      secondaryContext = methodLoggingContext,
      dataLog = idLogString(correlationId)
    )

    def warnLogger(correlationId: CorrelationId): (String, Option[Throwable]) => Unit = warnLog(
      secondaryContext = methodLoggingContext,
      dataLog = idLogString(correlationId)
    )

    infoLogger(correlationId)("Attempting to match supplied member details")

    EitherT(
      http
        .post(URI.create(matchIndividualAccountUrl).toURL)
        .withBody(Json.toJson(request)(matchPersonWrites))
        .setHeader(
          (correlationIdKey, correlationId.value),
          (govUkOriginatorIdKey, config.matchPersonGovUkOriginatorId),
          (AUTHORIZATION, authorization()),
          (CONTENT_TYPE, ContentTypes.JSON),
          (ENVIRONMENT, config.npsEnv)
        )
        .execute[Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]]]
    ).bimap(
      err => {
        val resultCorrelationId = checkIdsMatch(correlationId, err.correlationId, Some(methodLoggingContext))

        warnLogger(resultCorrelationId)(
          s"Match attempt failed to complete with error: ${err.error}",
          None
        )
        err.copy(correlationId = resultCorrelationId)
      },
      resp => {
        val resultCorrelationId = checkIdsMatch(correlationId, resp.correlationId, Some(methodLoggingContext))
        infoLogger(resultCorrelationId)(
          s"Request to match supplied member details completed successfully with result: ${resp.responseData}"
        )
        resp.copy(correlationId = resultCorrelationId)
      }
    )
  }

  override protected[connectors] val errorMap: Map[Int, String] = Map(
    BAD_REQUEST -> "BAD_REQUEST",
    FORBIDDEN -> "FORBIDDEN",
    NOT_FOUND -> "NOT_FOUND",
    INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR",
    SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
  )
}
