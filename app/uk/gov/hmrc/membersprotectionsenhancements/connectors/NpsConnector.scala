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
import play.api.http.HeaderNames.AUTHORIZATION
import cats.data.EitherT
import uk.gov.hmrc.membersprotectionsenhancements.utils.HeaderKey.{correlationIdKey, govUkOriginatorIdKey}
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.PensionSchemeMemberRequest
import uk.gov.hmrc.membersprotectionsenhancements.config.AppConfig
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.membersprotectionsenhancements.models.response.{
  MatchPersonResponse,
  ProtectionRecordDetails,
  ResponseWrapper
}
import uk.gov.hmrc.membersprotectionsenhancements.utils.HttpResponseHelper
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{ErrorWrapper, MatchPerson, RetrieveMpe}

import scala.concurrent.ExecutionContext

import java.util.Base64
import javax.inject.{Inject, Singleton}
import java.net.URI

@Singleton
class NpsConnector @Inject() (val config: AppConfig, val http: HttpClientV2) extends HttpResponseHelper {
  protected val classLoggingContext: String = "NpsConnector"

  private def authorization(): String = {
    val clientId = config.npsClientId
    val secret = config.npsSecret

    val encoded = Base64.getEncoder.encodeToString(s"$clientId:$secret".getBytes("UTF-8"))
    s"Bearer $encoded"
  }

  def matchPerson(
    request: PensionSchemeMemberRequest
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    correlationId: String
  ): ConnectorResult[ResponseWrapper[MatchPersonResponse]] = {
    val methodLoggingContext: String = "matchPerson"
    val fullContext: String = s"[$classLoggingContext][$methodLoggingContext]"
    val matchIndividualAccountUrl: String = config.matchUrl

    logger.info(s"$fullContext - Received request to check for a matching individual with correlationId $correlationId")

    EitherT(
      http
        .post(URI.create(matchIndividualAccountUrl).toURL)
        .withBody(Json.toJson(request)(matchPersonWrites))
        .setHeader(
          (correlationIdKey, correlationId),
          (govUkOriginatorIdKey, config.matchPersonGovUkOriginatorId),
          (AUTHORIZATION, authorization())
        )
        .execute[Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]]]
    ).bimap(
      err => {
        logger.warn(
          s"$fullContext - Request to check for a matching individual" +
            s" with correlationId ${err.correlationId} failed with error code: ${err.error.code}"
        )
        err.copy(error = err.error.copy(source = MatchPerson))
      },
      resp => {
        logger.info(
          s"$fullContext - Request to check for a matching individual completed successfully with correlationId ${resp.correlationId}"
        )
        resp
      }
    )
  }

  def retrieveMpe(nino: String, psaCheckRef: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    correlationId: String
  ): ConnectorResult[ResponseWrapper[ProtectionRecordDetails]] = {
    val methodLoggingContext: String = "retrieve"
    val fullContext: String = s"[$classLoggingContext][$methodLoggingContext]"

    val retrieveUrl = s"${config.retrieveUrl}/$nino/admin-reference/$psaCheckRef/lookup"

    logger.info(
      s"$fullContext - Received request to retrieve member's protections and enhancements with correlationId $correlationId"
    )

    EitherT(
      http
        .get(URI.create(retrieveUrl).toURL)
        .setHeader(
          (correlationIdKey, correlationId),
          (govUkOriginatorIdKey, config.retrieveMpeGovUkOriginatorId),
          (AUTHORIZATION, authorization())
        )
        .execute[Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]]]
    ).bimap(
      err => {
        logger.warn(
          s"$fullContext - Request to retrieve protections and enhancements" +
            s" with correlationId ${err.correlationId} failed with error code: ${err.error.code}"
        )
        err.copy(error = err.error.copy(source = RetrieveMpe))
      },
      resp => {
        logger.info(
          s"$fullContext - Request to retrieve protections and enhancements completed successfully with correlationId ${resp.correlationId}"
        )
        resp
      }
    )
  }
}
