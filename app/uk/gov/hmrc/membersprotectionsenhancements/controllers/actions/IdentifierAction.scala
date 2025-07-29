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
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.{IdentifierRequest, UserType}
import uk.gov.hmrc.membersprotectionsenhancements.config.{AppConfig, Constants}
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import play.api.mvc.Results.{InternalServerError, Unauthorized}
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.IdentifierRequest.{
  AdministratorRequest,
  PractitionerRequest
}
import uk.gov.hmrc.membersprotectionsenhancements.utils.Logging
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{
  InternalError,
  InvalidBearerTokenError,
  UnauthorisedError
}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AuthenticatedIdentifierAction])
trait IdentifierAction extends ActionBuilder[IdentifierRequest, AnyContent]

@Singleton
class AuthenticatedIdentifierAction @Inject() (
  override val authConnector: AuthConnector,
  config: AppConfig,
  playBodyParsers: BodyParsers.Default
)(implicit override val executionContext: ExecutionContext)
    extends IdentifierAction
    with AuthorisedFunctions
    with Logging {

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    authorised(Enrolment(Constants.psaEnrolmentKey).or(Enrolment(Constants.pspEnrolmentKey)))
      .retrieve(internalId.and(affinityGroup).and(authorisedEnrolments)) {

        case Some(internalId) ~ Some(affGroup) ~ IsPSA(psaId) =>
          block(AdministratorRequest(affGroup, internalId, psaId.value, UserType.PSA, request))
        case Some(internalId) ~ Some(affGroup) ~ IsPSP(pspId) =>
          block(PractitionerRequest(affGroup, internalId, pspId.value, UserType.PSP, request))
        case _ =>
          Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
      }
      .recoverWith {
        case e: MissingBearerToken =>
          logger.warn(
            s"[AuthenticatedIdentifierAction][invokeBlock - MissingBearerToken] An unexpected error occurred: ${e.printStackTrace()}"
          )
          Future.successful(Unauthorized(Json.toJson(InvalidBearerTokenError)))
        case e: AuthorisationException =>
          logger.warn(
            s"[AuthenticatedIdentifierAction][invokeBlock - AuthorisationException] An unexpected error occurred: ${e.printStackTrace()}"
          )
          Future.successful(Unauthorized(Json.toJson(UnauthorisedError)))
        case error =>
          logger.warn(s"[AuthenticatedIdentifierAction][invokeBlock - authorised] An unexpected error occurred: $error")
          Future.successful(InternalServerError(Json.toJson(InternalError)))
      }
  }

  override def parser: BodyParser[AnyContent] = playBodyParsers

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
