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
import play.api.libs.json.Json
import uk.gov.hmrc.membersprotectionsenhancements.config.AppConfig
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.membersprotectionsenhancements.models.response.{MatchPersonResponse, ProtectionRecordDetails}
import uk.gov.hmrc.membersprotectionsenhancements.utils.HttpResponseHelper
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{MatchPerson, MpeError, RetrieveMpe}
import uk.gov.hmrc.http._
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.PensionSchemeMemberRequest
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.PensionSchemeMemberRequest.matchPersonWrites
import uk.gov.hmrc.membersprotectionsenhancements.utils.HeaderKey.{correlationIdKey, govUkOriginatorIdKey}

import scala.concurrent.ExecutionContext
import javax.inject.{Inject, Singleton}
import java.net.URI

@Singleton
class NpsConnector @Inject() (val config: AppConfig, val http: HttpClientV2) extends HttpResponseHelper {
  protected val classLoggingContext: String = "NpsConnector"

  def matchPerson(request: PensionSchemeMemberRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): ConnectorResult[MatchPersonResponse] = {
    val methodLoggingContext: String = "matchPerson"
    val fullContext: String = s"[$classLoggingContext][$methodLoggingContext]"
    val matchIndividualAccountUrl: String = config.matchUrl

    logger.info(s"$fullContext - Received request to check for a matching individual")

    EitherT(
      http
        .post(URI.create(matchIndividualAccountUrl).toURL)
        .withBody(Json.toJson(request)(matchPersonWrites))
        .setHeader(
          (correlationIdKey, "TODO"), //TODO: Populate with an actual correlation ID
          (govUkOriginatorIdKey, config.matchPersonGovUkOriginatorId)
        )
        .execute[Either[MpeError, MatchPersonResponse]]
    ).bimap(
      err => {
        logger.warn(
          s"$fullContext - Request to check for a matching individual failed with error code: ${err.code}"
        )
        err.copy(source = MatchPerson)
      },
      resp => {
        logger.info(s"$fullContext - Request to check for a matching individual completed successfully")
        resp
      }
    )
  }


  def retrieveMpe(nino: String, psaCheckRef: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): ConnectorResult[ProtectionRecordDetails] = {
    val methodLoggingContext: String = "retrieve"
    val fullContext: String = s"[$classLoggingContext][$methodLoggingContext]"

    val retrieveUrl = s"${config.retrieveUrl}/$nino/admin-reference/$psaCheckRef/lookup"

    logger.info(s"$fullContext - Received request to retrieve member's protections and enhancements")

    EitherT(
      http
        .get(URI.create(retrieveUrl).toURL)
        .setHeader(
          (correlationIdKey, "TODO"), //TODO: Populate with an actual correlation ID
          (govUkOriginatorIdKey, config.retrieveMpeGovUkOriginatorId)
        )
        .execute[Either[MpeError, ProtectionRecordDetails]]
    ).bimap(
      err => {
        logger.warn(
          s"$fullContext - Request to retrieve protections and enhancements failed with error code: ${err.code}"
        )
        err.copy(source = RetrieveMpe)
      },
      resp => {
        logger.info(s"$fullContext - Request to retrieve protections and enhancements completed successfully")
        resp
      }
    )
  }
}
