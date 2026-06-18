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

package orchestrators

import models.response.MatchPersonResponse._
import connectors.{ConnectorResult, MatchPersonNpsConnector, RetrieveMpeNpsConnector}
import cats.data.EitherT
import models.response.{MpeResponse, ProtectionRecordDetails}
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import models.request.PensionSchemeMemberRequest
import models.errors.{EmptyDataError, MpeError, NoMatchError}

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.{Inject, Singleton}

@Singleton
class MembersLookUpOrchestrator @Inject() (
  matchPersonConnector: MatchPersonNpsConnector,
  retrieveMpeConnector: RetrieveMpeNpsConnector
)(implicit val ec: ExecutionContext)
    extends Logging {

  def checkAndRetrieve(
    request: PensionSchemeMemberRequest,
    correlationId: String
  )(implicit hc: HeaderCarrier): ConnectorResult[ProtectionRecordDetails] =
    matchPersonConnector.matchPerson(request, correlationId).flatMap {
      case MpeResponse(MATCH) =>
        retrieveMpeConnector.retrieveMpe(request.identifier, request.psaCheckRef, correlationId).subflatMap {
          case MpeResponse(ProtectionRecordDetails(data)) if data.isEmpty =>
            logger.warn("No protection record details were returned")
            Left(EmptyDataError)
          case details =>
            Right(details)
        }
      case MpeResponse(`NO MATCH`) =>
        logger.warn(s"Could not match supplied member details")
        EitherT[Future, MpeError, MpeResponse[ProtectionRecordDetails]](
          Future.successful(Left(NoMatchError))
        )
    }
}
