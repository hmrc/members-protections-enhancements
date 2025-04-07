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

import play.api.test.{FakeRequest, StubPlayBodyParsersFactory}
import play.api.mvc.{Action, AnyContent, BodyParsers}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.membersprotectionsenhancements.config.{AppConfig, Constants}
import play.api.mvc.Results.Ok
import play.api.test.Helpers._
import org.mockito.Mockito.when
import base.SpecBase
import uk.gov.hmrc.auth.core._
import play.api.Application
import play.api.libs.json.Json
import org.mockito.ArgumentMatchers.any
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.IdentifierRequest.{AdministratorRequest, PractitionerRequest}

import scala.concurrent.{ExecutionContext, Future}

class AuthenticatedIdentifierActionSpec extends SpecBase with StubPlayBodyParsersFactory {

  def authAction(appConfig: AppConfig) =
    new AuthenticatedIdentifierAction(
      mockAuthConnector,
      appConfig,
      bodyParsers)(ExecutionContext.global)

  class Handler(appConfig: AppConfig) {
    def run: Action[AnyContent] = authAction(appConfig) { request =>
      request match {
        case AdministratorRequest(userId, _, psaId) =>
          Ok(Json.obj("userId" -> userId, "psaId" -> psaId.value))

        case PractitionerRequest(userId, _, pspId) =>
          Ok(Json.obj("userId" -> userId, "pspId" -> pspId.value))
      }
    }
  }

  def appConfig(implicit app: Application): AppConfig = injected[AppConfig]

  def handler(implicit app: Application): Handler = new Handler(appConfig)

  def authResult(internalId: Option[String], enrolments: Enrolment*) =
    new~(internalId, Enrolments(enrolments.toSet))

  val psaEnrolment: Enrolment =
    Enrolment(Constants.psaEnrolmentKey, Seq(EnrolmentIdentifier(Constants.psaId, "A2100001")), "Activated")

  val pspEnrolment: Enrolment =
    Enrolment(Constants.pspEnrolmentKey, Seq(EnrolmentIdentifier(Constants.pspId, "21000002")), "Activated")

  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val bodyParsers: BodyParsers.Default = mock[BodyParsers.Default]

  def setAuthValue(value: Option[String] ~ Enrolments): Unit =
    setAuthValue(Future.successful(value))

  def setAuthValue[A](value: Future[A]): Unit =
    when(mockAuthConnector.authorise[A](any(), any())(any(), any()))
      .thenReturn(value)

  "AuthenticateIdentifierAction" - {
    "return an unauthorised result" - {
      "when user is not signed in" in runningApplication { implicit app =>
        setAuthValue(Future.failed(new NoActiveSession("No user signed in") {}))
        val result = handler.run(FakeRequest())
        redirectLocation(result) mustBe None
      }

      "when authorise fails to match predicate" in runningApplication { implicit app =>
        setAuthValue(Future.failed(new AuthorisationException("Authorise predicate fails") {}))
        val result = handler.run(FakeRequest())
        redirectLocation(result) mustBe None
      }

      "when user does not have an Internal Id" in runningApplication { implicit app =>
        setAuthValue(authResult(None, psaEnrolment))
        val result = handler.run(FakeRequest())
        redirectLocation(result) mustBe None
      }

      "when user does not have psa or psp enrolment" in runningApplication {
        implicit app =>
          setAuthValue(authResult(Some("internalId")))
          val result = handler.run(FakeRequest())
          redirectLocation(result) mustBe None
      }
    }

    "return an IdentifierRequest" - {
      "User has a psa enrolment and has a valid session" in runningApplication { implicit app =>
        setAuthValue(authResult(Some("internalId"), psaEnrolment))

        val result = handler.run(FakeRequest())

        status(result) mustBe OK
        (contentAsJson(result) \ "psaId").asOpt[String] mustBe Some("A2100001")
        (contentAsJson(result) \ "pspId").asOpt[String] mustBe None
        (contentAsJson(result) \ "userId").asOpt[String] mustBe Some("internalId")
      }

      "User has a psp enrolment and has a valid session" in runningApplication { implicit app =>
        setAuthValue(authResult(Some("internalId"), pspEnrolment))

        val result = handler.run(FakeRequest())

        status(result) mustBe OK
        (contentAsJson(result) \ "psaId").asOpt[String] mustBe None
        (contentAsJson(result) \ "pspId").asOpt[String] mustBe Some("21000002")
        (contentAsJson(result) \ "userId").asOpt[String] mustBe Some("internalId")
      }
    }
  }
}

