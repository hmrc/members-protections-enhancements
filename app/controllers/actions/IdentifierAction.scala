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

package controllers.actions

import utils.HeaderKey
import play.api.mvc._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import controllers.requests._
import config.Constants
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{affinityGroup, authorisedEnrolments, internalId}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import play.api.mvc.Results.{InternalServerError, Unauthorized}
import models.errors._
import controllers.requests.IdentifierRequest._
import uk.gov.hmrc.auth.core._
import play.api.Logging
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[IdentifierActionImpl])
trait IdentifierAction extends ActionBuilder[IdentifierRequest, AnyContent]

@Singleton
class IdentifierActionImpl @Inject() (
  override val authConnector: AuthConnector,
  playBodyParsers: BodyParsers.Default
)(implicit override val executionContext: ExecutionContext)
    extends IdentifierAction
    with AuthorisedFunctions
    with Logging {

  override def parser: BodyParser[AnyContent] = playBodyParsers

  private[actions] def handleWithCorrelationId[A](
    request: Request[A]
  )(block: RequestWithCorrelationId[A] => Future[Result]): Future[Result] = {

    logger.info("Attempting to retrieve Correlation ID from request headers")

    lazy val result = request.headers
      .get(HeaderKey.correlationIdKey)
      .fold {
        logger.error("Correlation ID was missing from request headers")
        Future.successful(InternalServerError(Json.toJson(MissingCorrelationIdError)))
      } { id =>
        logger.info("Correlation ID was successfully retrieved from request headers")
        block(RequestWithCorrelationId(request, CorrelationId(id)))
      }

    result
  }

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] = {
    logger.info("invokeBlock - Attempting to complete authorisation for request")

    handleWithCorrelationId(request) { req =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(req)

      authorised(Enrolment(Constants.psaEnrolmentKey).or(Enrolment(Constants.pspEnrolmentKey)))
        .retrieve(internalId.and(affinityGroup).and(authorisedEnrolments)) {
          case Some(internalId) ~ Some(affGroup) ~ IsPSA(psaId) =>
            logger.info("Authorisation completed successfully for PSA user")
            block(AdministratorRequest(affGroup, internalId, psaId.value, UserType.PSA, req))
          case Some(internalId) ~ Some(affGroup) ~ IsPSP(pspId) =>
            logger.info("Authorisation completed successfully for PSP user")
            block(PractitionerRequest(affGroup, internalId, pspId.value, UserType.PSP, req))
          case _ =>
            val err: UnauthorizedException = new UnauthorizedException(
              message = "Unable to retrieve user details or type from authorisation response"
            )
            logger.warn("Authorisation completed successfully, but could not retrieve user details or type", err)
            Future.failed(err)
        }
        .recoverWith {
          case _: MissingBearerToken =>
            logger.warn("Authorisation bearer token could not be found")
            Future.successful(Unauthorized(Json.toJson(InvalidBearerTokenError)))
          case _: AuthorisationException =>
            logger.warn(s"An authorisation error occurred")
            Future.successful(Unauthorized(Json.toJson(UnauthorisedError)))
          case _: UnauthorizedException =>
            logger.error("An unexpected authorisation error occurred")
            Future.successful(InternalServerError(Json.toJson(InternalFaultError)))
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
