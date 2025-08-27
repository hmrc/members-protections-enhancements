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
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.UserDetails
import uk.gov.hmrc.membersprotectionsenhancements.config.{AppConfig, Constants}
import play.api.mvc.Results.Ok
import uk.gov.hmrc.membersprotectionsenhancements.utils.IdGenerator
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{
  InternalError,
  InvalidBearerTokenError,
  UnauthorisedError
}
import play.api.test.Helpers._
import org.mockito.Mockito.when
import base.UnitBaseSpec
import uk.gov.hmrc.auth.core._
import play.api.libs.json.Json
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import org.mockito.ArgumentMatchers.any
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.IdentifierRequest.{
  AdministratorRequest,
  PractitionerRequest
}

import scala.concurrent.{ExecutionContext, Future}

class IdentifierActionSpec extends UnitBaseSpec with StubPlayBodyParsersFactory {
  private val dummyIdGenerator = new IdGenerator {
    override val getCorrelationId: String = "generatedId"
  }

  def authAction = new IdentifierActionImpl(
    authConnector = mockAuthConnector,
    idGenerator = dummyIdGenerator,
    playBodyParsers = bodyParsers
  )(ExecutionContext.global)

  class Handler {
    def run: Action[AnyContent] = authAction { request =>
      request match {
        case AdministratorRequest(_, correlationId, UserDetails(psrUserType, psrUserId, userId, affinityGroup)) =>
          Ok(
            Json.obj(
              "psrUserType" -> psrUserType,
              "userId" -> userId,
              "psaId" -> psrUserId,
              "affinityGroup" -> affinityGroup,
              "correlationId" -> correlationId.value
            )
          )

        case PractitionerRequest(_, correlationId, UserDetails(psrUserType, psrUserId, userId, affinityGroup)) =>
          Ok(
            Json.obj(
              "psrUserType" -> psrUserType,
              "userId" -> userId,
              "pspId" -> psrUserId,
              "affinityGroup" -> affinityGroup,
              "correlationId" -> correlationId.value
            )
          )
      }
    }
  }

  def handler: Handler = new Handler()

  def authResult(
    affinityGroup: Option[AffinityGroup],
    internalId: Option[String],
    enrolments: Enrolment*
  ): Option[String] ~ Option[AffinityGroup] ~ Enrolments =
    internalId.and(affinityGroup).and(Enrolments(enrolments.toSet))

  val psaEnrolment: Enrolment = Enrolment(
    key = Constants.psaEnrolmentKey,
    identifiers = Seq(EnrolmentIdentifier(key = Constants.psaId, value = "A2100001")),
    state = "Activated"
  )

  val pspEnrolment: Enrolment = Enrolment(
    key = Constants.pspEnrolmentKey,
    identifiers = Seq(EnrolmentIdentifier(key = Constants.pspId, value = "21000002")),
    state = "Activated"
  )

  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val bodyParsers: BodyParsers.Default = mock[BodyParsers.Default]

  def setAuthValue(value: Option[String] ~ Option[AffinityGroup] ~ Enrolments): Unit =
    setAuthValue(Future.successful(value))

  def setAuthValue[A](value: Future[A]): Unit =
    when(mockAuthConnector.authorise[A](any(), any())(any(), any()))
      .thenReturn(value)

  "AuthenticateIdentifierAction" - {
    "return an unauthorised result" - {
      "when any unhandled exception occurs" in runningApplication { _ =>
        setAuthValue(Future.failed(new RuntimeException("Authorise predicate fails")))
        val result = handler.run(FakeRequest())
        redirectLocation(result) mustBe None
        contentAsJson(result) mustBe Json.toJson(InternalError)
      }

      "when authorise fails to match predicate" in runningApplication { _ =>
        setAuthValue(Future.failed(new AuthorisationException("Authorise predicate fails") {}))
        val result = handler.run(FakeRequest())
        redirectLocation(result) mustBe None
        contentAsJson(result) mustBe Json.toJson(UnauthorisedError)
      }

      "when authorise fails due to invalid or no bearer token" in runningApplication { _ =>
        setAuthValue(Future.failed(new MissingBearerToken("No Bearer token") {}))
        val result = handler.run(FakeRequest())
        redirectLocation(result) mustBe None
        contentAsJson(result) mustBe Json.toJson(InvalidBearerTokenError)
      }

      "when user does not have an Internal Id" in runningApplication { _ =>
        setAuthValue(authResult(Some(AffinityGroup.Individual), None, psaEnrolment))
        val result = handler.run(FakeRequest())
        redirectLocation(result) mustBe None
      }

      "when user does not have an AffinityGroup" in runningApplication { _ =>
        setAuthValue(authResult(None, Some("id"), psaEnrolment))
        val result = handler.run(FakeRequest())
        redirectLocation(result) mustBe None
      }

      "when user does not have psa or psp enrolment" in runningApplication { _ =>
        setAuthValue(authResult(Some(AffinityGroup.Individual), Some("internalId")))
        val result = handler.run(FakeRequest())
        redirectLocation(result) mustBe None
      }
    }

    "return an IdentifierRequest" - {
      "User has a psa enrolment" in runningApplication { _ =>
        setAuthValue(authResult(Some(AffinityGroup.Individual), Some("internalId"), psaEnrolment))

        val result = handler.run(FakeRequest().withHeaders("correlationId" -> "anId"))

        status(result) mustBe OK
        (contentAsJson(result) \ "psaId").asOpt[String] mustBe Some("A2100001")
        (contentAsJson(result) \ "pspId").asOpt[String] mustBe None
        (contentAsJson(result) \ "userId").asOpt[String] mustBe Some("internalId")
        (contentAsJson(result) \ "correlationId").asOpt[String] mustBe Some("anId")
      }

      "User has a psp enrolment" in runningApplication { _ =>
        setAuthValue(authResult(Some(AffinityGroup.Individual), Some("internalId"), pspEnrolment))

        val result = handler.run(FakeRequest().withHeaders("correlationId" -> "anId"))

        status(result) mustBe OK
        (contentAsJson(result) \ "psaId").asOpt[String] mustBe None
        (contentAsJson(result) \ "pspId").asOpt[String] mustBe Some("21000002")
        (contentAsJson(result) \ "userId").asOpt[String] mustBe Some("internalId")
        (contentAsJson(result) \ "correlationId").asOpt[String] mustBe Some("anId")
      }

      "must generate correlationId when it does not exist in request" in runningApplication { _ =>
        setAuthValue(authResult(Some(AffinityGroup.Individual), Some("internalId"), pspEnrolment))

        val result = handler.run(FakeRequest())

        status(result) mustBe OK
        (contentAsJson(result) \ "psaId").asOpt[String] mustBe None
        (contentAsJson(result) \ "pspId").asOpt[String] mustBe Some("21000002")
        (contentAsJson(result) \ "userId").asOpt[String] mustBe Some("internalId")
        (contentAsJson(result) \ "correlationId").asOpt[String] mustBe Some("generatedId")
      }
    }
  }
}
