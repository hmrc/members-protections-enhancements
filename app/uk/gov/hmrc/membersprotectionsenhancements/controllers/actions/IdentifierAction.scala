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

package uk.gov.hmrc.membersprotectionsenhancements.controllers.actions

import play.api.mvc._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{affinityGroup, authorisedEnrolments, internalId}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests._
import uk.gov.hmrc.membersprotectionsenhancements.config.Constants
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import play.api.mvc.Results.{InternalServerError, Unauthorized}
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.IdentifierRequest._
import uk.gov.hmrc.membersprotectionsenhancements.utils.{HeaderKey, IdGenerator, Logging}
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{
  InternalError,
  InvalidBearerTokenError,
  UnauthorisedError
}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[IdentifierActionImpl])
trait IdentifierAction extends ActionBuilder[IdentifierRequest, AnyContent]

@Singleton
class IdentifierActionImpl @Inject() (
  override val authConnector: AuthConnector,
  idGenerator: IdGenerator,
  playBodyParsers: BodyParsers.Default
)(implicit override val executionContext: ExecutionContext)
    extends IdentifierAction
    with AuthorisedFunctions
    with Logging {

  override def parser: BodyParser[AnyContent] = playBodyParsers

  private[actions] def handleWithCorrelationId[A](
    request: Request[A],
    extraContext: String
  )(block: RequestWithCorrelationId[A] => Future[Result]): Future[Result] = {
    val methodLoggingContext: String = "handleWithCorrelationId"

    val infoLogger: String => Unit = infoLog(
      secondaryContext = methodLoggingContext,
      extraContext = Some(extraContext)
    )

    val warnLogger: (String, Option[Throwable]) => Unit = warnLog(
      secondaryContext = methodLoggingContext,
      extraContext = Some(extraContext)
    )

    infoLogger("Attempting to retrieve Correlation ID from request headers")

    val correlationId = request.headers
      .get(HeaderKey.correlationIdKey)
      .fold {
        warnLogger("Correlation ID was missing from request headers. Generating new ID for request", None)
        idGenerator.getCorrelationId
      } { id =>
        infoLogger("Correlation ID was successfully retrieved from request headers")
        id
      }

    block(RequestWithCorrelationId(request, CorrelationId(correlationId)))
  }

  override def invokeBlock[A](request: Request[A],
                              block: IdentifierRequest[A] => Future[Result]): Future[Result] = {
    val methodLoggingContext: String = "invokeBlock"

    logger.info(methodLoggingContext, "Attempting to complete authorisation for request")

    handleWithCorrelationId(request, methodLoggingContext) { req =>
      val idLogString = correlationIdLogString(req.correlationId)

      val infoLogger: String => Unit = infoLog(
        secondaryContext = methodLoggingContext,
        dataLog = idLogString
      )

      val warnLogger: (String, Option[Throwable]) => Unit = warnLog(
        secondaryContext = methodLoggingContext,
        dataLog = idLogString
      )

      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(req)

      authorised(Enrolment(Constants.psaEnrolmentKey).or(Enrolment(Constants.pspEnrolmentKey)))
        .retrieve(internalId.and(affinityGroup).and(authorisedEnrolments)) {
          case Some(internalId) ~ Some(affGroup) ~ IsPSA(psaId) =>
            infoLogger("Authorisation completed successfully for PSA user")
            block(AdministratorRequest(affGroup, internalId, psaId.value, UserType.PSA, req))
          case Some(internalId) ~ Some(affGroup) ~ IsPSP(pspId) =>
            infoLogger("Authorisation completed successfully for PSP user")
            block(PractitionerRequest(affGroup, internalId, pspId.value, UserType.PSP, req))
          case _ =>
            val err: UnauthorizedException = new UnauthorizedException(
              message = "Unable to retrieve user details or type from authorisation response"
            )
            warnLogger("Authorisation completed successfully, but could not retrieve user details or type", Some(err))
            Future.failed(err)
        }
        .recoverWith {
          case err: MissingBearerToken =>
            warnLogger("Authorisation bearer token could not be found", Some(err))
            Future.successful(Unauthorized(Json.toJson(InvalidBearerTokenError)))
          case err: AuthorisationException =>
            warnLogger(s"An authorisation error occurred", Some(err))
            Future.successful(Unauthorized(Json.toJson(UnauthorisedError)))
          case err =>
            errorLog(methodLoggingContext, idLogString)("An unexpected error occurred", Some(err))
            Future.successful(InternalServerError(Json.toJson(InternalError)))
        }
    }
  }

  private object IsPSA {
    def unapply(enrolments: Enrolments): Option[EnrolmentIdentifier] =
      enrolments.enrolments
        .find(_.key == Constants.psaEnrolmentKey)
        .flatMap(_.getIdentifier(Constants.psaId))
  }

  private object IsPSP {
    def unapply(enrolments: Enrolments): Option[EnrolmentIdentifier] =
      enrolments.enrolments
        .find(_.key == Constants.pspEnrolmentKey)
        .flatMap(_.getIdentifier(Constants.pspId))
  }

}
