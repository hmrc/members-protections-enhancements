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

package orchestrators

import cats.data.EitherT
import connectors.{ConnectorResult, MatchPersonNpsConnector, RetrieveMpeNpsConnector}
import controllers.requests.{CorrelationId, PensionSchemeMemberRequest}
import models.errors.{EmptyDataError, ErrorWrapper, NoMatchError}
import models.response.MatchPersonResponse.*
import models.response.{ProtectionRecordDetails, ResponseWrapper}
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MembersLookUpOrchestrator @Inject() (
  matchPersonConnector: MatchPersonNpsConnector,
  retrieveMpeConnector: RetrieveMpeNpsConnector
)(implicit val ec: ExecutionContext)
    extends Logging {

  def checkAndRetrieve(
    request: PensionSchemeMemberRequest,
    correlationId: CorrelationId
  )(implicit hc: HeaderCarrier): ConnectorResult[ProtectionRecordDetails] = {

    logger.info(s"Attempting to match supplied member details (Correlation ID: $correlationId.value)")

    val result: ConnectorResult[ProtectionRecordDetails] =
      matchPersonConnector.matchPerson(request, correlationId).flatMap {
        case ResponseWrapper(responseCorrelationId, MATCH) =>
          logger.info(s"$responseCorrelationId - Successfully matched supplied member details")
          doRetrieval(request)(hc, responseCorrelationId)
        case ResponseWrapper(responseCorrelationId, `NO MATCH`) =>
          logger.warn(s"$responseCorrelationId - Could not match supplied member details")
          EitherT[Future, ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]](
            Future.successful(
              Left[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]](
                ErrorWrapper(responseCorrelationId, NoMatchError)
              )
            )
          )
      }

    result.leftMap { err =>
      logger.warn(
        s"An error occurred with code: ${err.error.code}, and source: ${err.error.source} (Correction ID: ${err.correlationId.value})"
      )
      err
    }
  }

  private def doRetrieval(request: PensionSchemeMemberRequest)(implicit
    headerCarrier: HeaderCarrier,
    correlationId: CorrelationId
  ): ConnectorResult[ProtectionRecordDetails] = {

    logger.info(s"Attempting to retrieve member's protection record details (Correlation ID: ${correlationId.value})")

    retrieveMpeConnector.retrieveMpe(request.identifier, request.psaCheckRef, correlationId).subflatMap {
      case ResponseWrapper(responseCorrelationId, ProtectionRecordDetails(data)) if data.isEmpty =>
        logger.warn("No protection record details were returned")
        Left(ErrorWrapper(responseCorrelationId, EmptyDataError))
      case details =>
        logger
          .info(
            s"Successfully retrieved member's protection record details (Correlation ID: ${details.correlationId.value})"
          )
        Right(details)
    }
  }
}
