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

import play.api.http.HeaderNames.AUTHORIZATION
import config.AppConfig
import cats.data.EitherT
import models.errors.ErrorSource.RetrieveMpe
import models.response.{MpeResponse, ProtectionRecordDetails}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import utils.HeaderKey.{correlationIdKey, govUkOriginatorIdKey, ENVIRONMENT}
import models.errors.{ErrorSource, MpeError}

import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Singleton}
import java.net.URI

@Singleton
class RetrieveMpeNpsConnector @Inject() (val config: AppConfig, val http: HttpClientV2)
    extends BaseNpsConnector[ProtectionRecordDetails] {
  override val source: ErrorSource = RetrieveMpe
  def retrieveMpe(nino: String, psaCheckRef: String, correlationId: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): ConnectorResult[ProtectionRecordDetails] = {
    val retrieveUrl = s"${config.retrieveUrl}/$nino/admin-reference/$psaCheckRef/lookup"
    EitherT(
      http
        .get(URI.create(retrieveUrl).toURL)
        .setHeader(
          (correlationIdKey, correlationId),
          (govUkOriginatorIdKey, config.govUkOriginatorId),
          (AUTHORIZATION, s"Basic ${config.authorizationToken}"),
          (ENVIRONMENT, config.npsEnv)
        )
        .execute[Either[MpeError, MpeResponse[ProtectionRecordDetails]]]
    )
  }
}
