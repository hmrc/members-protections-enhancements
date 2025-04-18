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

package uk.gov.hmrc.membersprotectionsenhancements.generators

import play.api.mvc.Request
import models.PensionSchemeId.{PsaId, PspId}
import org.scalacheck.Gen
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.IdentifierRequest
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.IdentifierRequest.{
  AdministratorRequest,
  PractitionerRequest
}

trait ModelGenerators extends Generators {

  val psaIdGen: Gen[PsaId] = nonEmptyString.map(PsaId)
  val pspIdGen: Gen[PspId] = nonEmptyString.map(PspId)

  def administratorRequestGen[A](request: Request[A]): Gen[AdministratorRequest[A]] =
    for {
      userId <- nonEmptyString
      psaId <- psaIdGen
    } yield AdministratorRequest(userId, request, psaId)

  def practitionerRequestGen[A](request: Request[A]): Gen[PractitionerRequest[A]] =
    for {
      userId <- nonEmptyString
      psaId <- pspIdGen
    } yield PractitionerRequest(userId, request, psaId)

  def identifierRequestGen[A](request: Request[A]): Gen[IdentifierRequest[A]] =
    Gen.oneOf(administratorRequestGen[A](request), practitionerRequestGen[A](request))

}
