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

package uk.gov.hmrc.membersprotectionsenhancements.orchestrators

import uk.gov.hmrc.membersprotectionsenhancements.models.errors.MpeError
import cats.data.EitherT
import uk.gov.hmrc.membersprotectionsenhancements.connectors.NpsConnector
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.PensionSchemeMemberRequest
import uk.gov.hmrc.membersprotectionsenhancements.models.response.{`NO MATCH`, MATCH, MatchAndRetrieveResult}

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.{Inject, Singleton}

@Singleton
class MembersLookUpOrchestrator @Inject()(npsConnector: NpsConnector)
                                         (implicit val ec: ExecutionContext) extends Logging {
  val classLoggingContext: String = "MembersLookUpOrchestrator"

  def checkAndRetrieve(request: PensionSchemeMemberRequest)
                      (implicit hc: HeaderCarrier): EitherT[Future, MpeError, MatchAndRetrieveResult] = {
    val methodLoggingContext: String = "checkAndRetrieve"
    val fullLoggingContext: String = s"[$classLoggingContext][$methodLoggingContext]"

    logger.info(s"$fullLoggingContext - Received request to perform check and retrieve for supplied member details")

    val result: EitherT[Future, MpeError, MatchAndRetrieveResult] = npsConnector
      .matchPerson(request)
      .flatMap {
        case MATCH =>
          logger.info(s"$fullLoggingContext - Supplied member details successfully matched. Proceeding to retrieval")

          npsConnector.retrieveMpe(request.identifier, request.psaCheckRef).subflatMap { details =>
            logger.info(s"$fullLoggingContext - Successfully retrieved protections and enhancements data")
            Right(MatchAndRetrieveResult(MATCH, Some(details.protectionRecords)))
          }
        case `NO MATCH` =>
          logger.warn(s"$fullLoggingContext - No match was found for the supplied member details")
          EitherT.right(Future.successful(MatchAndRetrieveResult(`NO MATCH`, None)))
      }

    result.leftMap { err =>
      logger.warn(s"$fullLoggingContext - An error occurred with source: ${err.source}")
      err
    }
  }
}
