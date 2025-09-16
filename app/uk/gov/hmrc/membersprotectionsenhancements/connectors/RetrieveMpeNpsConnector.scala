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

import cats.data.EitherT
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.Status._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.membersprotectionsenhancements.config.AppConfig
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.CorrelationId
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.DownstreamErrorResponse.{badRequestErrorReads, internalErrorReads, reasonCodeReads, unprocessableEntityErrorReads}
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{ErrorSource, ErrorWrapper, RetrieveMpe}
import uk.gov.hmrc.membersprotectionsenhancements.models.response.{ProtectionRecordDetails, ResponseWrapper}
import uk.gov.hmrc.membersprotectionsenhancements.utils.HeaderKey.{ENVIRONMENT, correlationIdKey, govUkOriginatorIdKey}
import uk.gov.hmrc.membersprotectionsenhancements.utils.Logging

import java.net.URI
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class RetrieveMpeNpsConnector @Inject()(val config: AppConfig, val http: HttpClientV2)
  extends BaseNpsConnector[ProtectionRecordDetails] with Logging {

  override val source: ErrorSource = RetrieveMpe

  def retrieveMpe(nino: String, psaCheckRef: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    correlationId: CorrelationId
  ): ConnectorResult[ProtectionRecordDetails] = {
    val retrieveUrl = s"${config.retrieveUrl}/$nino/admin-reference/$psaCheckRef/lookup"

    val methodLoggingContext: String = "retrieveMpe"
    def idLogString(correlationId: CorrelationId): String = correlationIdLogString(correlationId = correlationId)

    def infoLogger(correlationId: CorrelationId): String => Unit = infoLog(
      secondaryContext = methodLoggingContext,
      dataLog = idLogString(correlationId)
    )

    def warnLogger(correlationId: CorrelationId): (String, Option[Throwable]) => Unit = warnLog(
      secondaryContext = methodLoggingContext,
      dataLog = idLogString(correlationId)
    )

    infoLogger(correlationId)("Attempting to retrieve supplied member's protection record details")

    EitherT(
      http
        .get(URI.create(retrieveUrl).toURL)
        .setHeader(
          (correlationIdKey, correlationId.value),
          (govUkOriginatorIdKey, config.retrieveMpeGovUkOriginatorId),
          (AUTHORIZATION, authorization()),
          (ENVIRONMENT, config.npsEnv)
        )
        .execute[Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]]]
    ).bimap(
      err => {
        val resultCorrelationId: CorrelationId = checkIdsMatch(
          requestCorrelationId = correlationId,
          responseCorrelationId = err.correlationId,
          extraLoggingContext = Some(methodLoggingContext)
        )

        warnLogger(resultCorrelationId)(
          s"Request to retrieve supplied member's protection record details failed with error: ${err.error}",
          None
        )
        err.copy(correlationId = resultCorrelationId)
      },
      resp => {
        val resultCorrelationId = checkIdsMatch(correlationId, resp.correlationId, Some(methodLoggingContext))
        infoLogger(resultCorrelationId)(
          "Request to retrieve supplied member's protection record details completed successfully"
        )
        resp.copy(correlationId = resultCorrelationId)
      }
    )
  }

  override protected[connectors] val errorMap: Map[Int, (String, ErrorValidation)] = Map(
    BAD_REQUEST -> ("BAD_REQUEST", jsonErrorValidation(badRequestErrorReads)),
    FORBIDDEN -> ("FORBIDDEN", jsonErrorValidation(reasonCodeReads)),
    NOT_FOUND -> ("NOT_FOUND", notFoundErrorValidation),
    UNPROCESSABLE_ENTITY -> ("NOT_FOUND", jsonErrorValidation(unprocessableEntityErrorReads)),
    INTERNAL_SERVER_ERROR -> ("INTERNAL_ERROR", jsonErrorValidation(internalErrorReads)),
    SERVICE_UNAVAILABLE -> ("SERVICE_UNAVAILABLE", jsonErrorValidation(internalErrorReads))
  )
}
