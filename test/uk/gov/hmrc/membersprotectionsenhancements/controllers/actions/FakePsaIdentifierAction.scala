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

import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.UserType.PSA
import play.api.mvc._
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.{
  CorrelationId,
  IdentifierRequest,
  RequestWithCorrelationId
}
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.IdentifierRequest.AdministratorRequest

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class FakePsaIdentifierAction @Inject() (bodyParsers: BodyParsers.Default) extends IdentifierAction {
  override def parser: BodyParser[AnyContent] = bodyParsers
  override protected def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] =
    block(
      AdministratorRequest(
        affGroup = AffinityGroup.Individual,
        userId = "id",
        psaId = "A2100001",
        psrUserType = PSA,
        request = RequestWithCorrelationId(request, CorrelationId("someId"))
      )
    )

}
