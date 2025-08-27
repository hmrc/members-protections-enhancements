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

import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{EmptyDataError, ErrorWrapper, NoMatchError}
import cats.data.EitherT
import uk.gov.hmrc.membersprotectionsenhancements.connectors.{ConnectorResult, NpsConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.{CorrelationId, PensionSchemeMemberRequest}
import uk.gov.hmrc.membersprotectionsenhancements.models.response._
import uk.gov.hmrc.membersprotectionsenhancements.utils.Logging

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.{Inject, Singleton}

@Singleton
class MembersLookUpOrchestrator @Inject() (npsConnector: NpsConnector)
                                          (implicit val ec: ExecutionContext) extends Logging {

  def checkAndRetrieve(request: PensionSchemeMemberRequest)
                      (implicit hc: HeaderCarrier, correlationId: CorrelationId): ConnectorResult[ProtectionRecordDetails] = {
    val methodLoggingContext: String = "checkAndRetrieve"

    def idLogString(correlationId: CorrelationId): String = correlationIdLogString(
      correlationId = correlationId,
      requestContext = Some("authenticated, and validated")
    )

    def infoLogger(correlationId: CorrelationId): String => Unit = infoLog(
      secondaryContext = methodLoggingContext,
      dataLog = idLogString(correlationId)
    )

    def warnLogger(correlationId: CorrelationId): (String, Option[Throwable]) => Unit = warnLog(
      secondaryContext = methodLoggingContext,
      dataLog = idLogString(correlationId)
    )

    infoLogger(correlationId)("Attempting to match supplied member details")

    val result: ConnectorResult[ProtectionRecordDetails] = npsConnector.matchPerson(request).flatMap {
      case ResponseWrapper(responseCorrelationId, MATCH) =>
        infoLogger(responseCorrelationId)("Successfully matched supplied member details")
        doRetrieval(request, Some(methodLoggingContext))(hc, responseCorrelationId)
      case ResponseWrapper(responseCorrelationId, `NO MATCH`) =>
        warnLogger(responseCorrelationId)("Could not match supplied member details", None)
        EitherT[Future, ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]](
          Future.successful(Left[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]](
            ErrorWrapper(responseCorrelationId, NoMatchError)
          ))
        )
    }

    result.leftMap { err =>
      warnLogger(err.correlationId)(
        s"An error occurred with code: ${err.error.code}, and source: ${err.error.source}",
        None
      )
      err
    }
  }

  private def doRetrieval(request: PensionSchemeMemberRequest, extraContext: Some[String])
                         (implicit headerCarrier: HeaderCarrier,
                          correlationId: CorrelationId): ConnectorResult[ProtectionRecordDetails] = {
    val doRetrievalLoggingContext: String = "doRetrieval"

    def idLogString(correlationId: CorrelationId): String = correlationIdLogString(correlationId = correlationId)

    def retrieveInfoLogger(correlationId: CorrelationId): String => Unit = infoLog(
      secondaryContext = doRetrievalLoggingContext,
      dataLog = idLogString(correlationId),
      extraContext = extraContext
    )

    retrieveInfoLogger(correlationId)("Attempting to retrieve member's protection record details")

    npsConnector.retrieveMpe(request.identifier, request.psaCheckRef).subflatMap {
      case ResponseWrapper(responseCorrelationId, ProtectionRecordDetails(data)) if data.isEmpty =>
        logger.warn(
          secondaryContext = doRetrievalLoggingContext,
          message = "No protection record details were returned",
          dataLog = idLogString(responseCorrelationId),
          extraContext = extraContext
        )
        Left(ErrorWrapper(responseCorrelationId, EmptyDataError))
      case details =>
        retrieveInfoLogger(details.correlationId)("Successfully retrieved member's protection record details")
        Right(details)
    }
  }
}
