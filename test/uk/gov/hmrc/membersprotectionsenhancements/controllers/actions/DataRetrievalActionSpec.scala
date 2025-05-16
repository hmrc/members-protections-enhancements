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
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.UserType.PSA
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.{DataRequest, IdentifierRequest, UserDetails}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.IdentifierRequest.AdministratorRequest
import base.SpecBase
import uk.gov.hmrc.auth.core.AffinityGroup

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DataRetrievalActionSpec extends SpecBase with MockitoSugar {

  class Harness extends DataRetrievalActionImpl {
    def callTransform[A](request: IdentifierRequest[A]): Future[DataRequest[A]] = transform(request)
  }

  "Data Retrieval Action" - {

    "should return user details" - {

      "when auth is successful" in {

        val action = new Harness()
        val userDetails = UserDetails(PSA, "A2100001", "id", AffinityGroup.Individual)
        val result = action
          .callTransform(AdministratorRequest.apply(AffinityGroup.Individual, "id", "A2100001", PSA, FakeRequest()))
          .futureValue

        result.userDetails mustBe userDetails
      }
    }
  }
}
