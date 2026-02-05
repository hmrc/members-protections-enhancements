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

import cats.data.EitherT
import config.AppConfig
import controllers.requests.CorrelationId
import models.errors.ErrorSource.RetrieveMpe
import models.errors.{ErrorSource, ErrorWrapper}
import models.response.{ProtectionRecordDetails, ResponseWrapper}
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.Status.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import utils.HeaderKey.{ENVIRONMENT, correlationIdKey, govUkOriginatorIdKey}
import utils.Logging

import java.net.URI
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class RetrieveMpeNpsConnector @Inject() (val config: AppConfig, val http: HttpClientV2)
    extends BaseNpsConnector[ProtectionRecordDetails]
    with Logging {

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
          (govUkOriginatorIdKey, config.govUkOriginatorId),
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

  override protected[connectors] val errorMap: Map[Int, String] = Map(
    BAD_REQUEST -> "BAD_REQUEST",
    FORBIDDEN -> "FORBIDDEN",
    NOT_FOUND -> "NOT_FOUND",
    UNPROCESSABLE_ENTITY -> "NOT_FOUND",
    INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR",
    SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
  )
}
