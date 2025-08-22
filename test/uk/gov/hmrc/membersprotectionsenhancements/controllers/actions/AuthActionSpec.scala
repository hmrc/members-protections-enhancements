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

import play.api.test.FakeRequest
import uk.gov.hmrc.membersprotectionsenhancements.utils.IdGenerator
import play.api.test.Helpers._
import play.api.mvc._
import com.google.inject.Inject
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.membersprotectionsenhancements.config.AppConfig
import uk.gov.hmrc.auth.core.authorise.Predicate
import base.UnitBaseSpec
import uk.gov.hmrc.auth.core._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class AuthActionUnitSpec extends UnitBaseSpec {

  class Harness(authAction: IdentifierAction) {
    def onPageLoad(): Action[AnyContent] = authAction(_ => Results.Ok)
  }

  "Auth Action" - {

    "must throw unauthorised error" - {

      "when the user has no bearer token" in {

        val application = applicationBuilder().build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]

          val authAction = new IdentifierActionImpl(
            new FakeFailingAuthConnector(new MissingBearerToken),
            new IdGenerator,
            bodyParsers
          )
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe UNAUTHORIZED

        }
      }

      "the user's bearer token has expired" in {

        val application = applicationBuilder().build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          application.injector.instanceOf[AppConfig]

          val authAction = new IdentifierActionImpl(
            new FakeFailingAuthConnector(new BearerTokenExpired),
            new IdGenerator,
            bodyParsers
          )
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe UNAUTHORIZED

        }
      }

      "the user doesn't have sufficient enrolments" in {
        val application = applicationBuilder().build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          application.injector.instanceOf[AppConfig]

          val authAction = new IdentifierActionImpl(
            new FakeFailingAuthConnector(new InsufficientEnrolments),
            new IdGenerator,
            bodyParsers
          )
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe UNAUTHORIZED
        }
      }

      "the user used an unaccepted auth provider" in {
        val application = applicationBuilder().build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          application.injector.instanceOf[AppConfig]

          val authAction = new IdentifierActionImpl(
            new FakeFailingAuthConnector(new UnsupportedAuthProvider),
            new IdGenerator,
            bodyParsers
          )
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe UNAUTHORIZED
        }
      }

      "the user has an unsupported affinity group" in {

        val application = applicationBuilder().build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          application.injector.instanceOf[AppConfig]

          val authAction = new IdentifierActionImpl(
            new FakeFailingAuthConnector(new UnsupportedAffinityGroup),
            new IdGenerator,
            bodyParsers
          )
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe UNAUTHORIZED
        }
      }

      "the user has an unsupported credential role" in {

        val application = applicationBuilder().build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          application.injector.instanceOf[AppConfig]

          val authAction = new IdentifierActionImpl(
            new FakeFailingAuthConnector(new UnsupportedCredentialRole),
            new IdGenerator,
            bodyParsers
          )

          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe UNAUTHORIZED
        }
      }
    }
  }
}

class FakeFailingAuthConnector @Inject() (exceptionToReturn: Throwable) extends AuthConnector {
  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[A] =
    Future.failed(exceptionToReturn)
}
