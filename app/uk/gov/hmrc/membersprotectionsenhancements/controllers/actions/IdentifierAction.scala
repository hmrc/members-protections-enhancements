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
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.IdentifierRequest
import uk.gov.hmrc.membersprotectionsenhancements.config.{AppConfig, Constants}
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import play.api.mvc.Results._
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.IdentifierRequest.{
  AdministratorRequest,
  PractitionerRequest
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
    with AuthorisedFunctions {

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised(Enrolment(Constants.psaEnrolmentKey).or(Enrolment(Constants.pspEnrolmentKey)))
      .retrieve(Retrievals.internalId.and(Retrievals.authorisedEnrolments)) {

        case Some(internalId) ~ IsPSA(psaId) => block(AdministratorRequest(internalId, request, psaId.value))
        case Some(internalId) ~ IsPSP(pspId) => block(PractitionerRequest(internalId, request, pspId.value))
        case Some(_) ~ _ =>
          Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
        case _ =>
          Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
      }
      .recoverWith { case _ =>
        Future.successful(BadRequest(""))
      }
  }

  override def parser: BodyParser[AnyContent] = playBodyParsers

  object IsPSA {
    def unapply(enrolments: Enrolments): Option[EnrolmentIdentifier] =
      enrolments.enrolments
        .find(_.key == Constants.psaEnrolmentKey)
        .flatMap(_.getIdentifier(Constants.psaId))
  }

  object IsPSP {
    def unapply(enrolments: Enrolments): Option[EnrolmentIdentifier] =
      enrolments.enrolments
        .find(_.key == Constants.pspEnrolmentKey)
        .flatMap(_.getIdentifier(Constants.pspId))
  }
}
