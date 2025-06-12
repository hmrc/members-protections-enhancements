/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.membersprotectionsenhancements.orchestrators

import uk.gov.hmrc.membersprotectionsenhancements.models.errors.MpeError
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.PensionSchemeMemberRequest
import uk.gov.hmrc.membersprotectionsenhancements.models.response.{ProtectionRecord, ProtectionRecordDetails}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import org.mockito.Mockito.when
import base.UnitBaseSpec
import uk.gov.hmrc.membersprotectionsenhancements.connectors.NpsConnector

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import java.time.LocalDate

class MembersLookUpOrchestratorSpec extends UnitBaseSpec {

  val npsConnector: NpsConnector = mock[NpsConnector]
  val orchestrator = new MembersLookUpOrchestrator(npsConnector)
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val request: PensionSchemeMemberRequest =
    PensionSchemeMemberRequest("Naren", "Vijay", LocalDate.of(2024, 12, 31), "AA123456C", "PSA12345678A")

  val resModel: ProtectionRecordDetails = ProtectionRecordDetails(
    Seq(
      ProtectionRecord(
        protectionReference = Some("some-id"),
        `type` = "some-type",
        status = "some-status",
        protectedAmount = Some(1),
        lumpSumAmount = Some(1),
        lumpSumPercentage = Some(1),
        enhancementFactor = Some(0.5)
      )
    )
  )

  "MembersLookUpOrchestrator" - {
    "return a valid response model for a valid request" in {

      when(npsConnector.retrieve(request.nino, request.psaCheckRef)).thenReturn(Future.successful(Right(resModel)))
      val result = await(orchestrator.checkAndRetrieve(request))
      result mustBe Right(resModel)
    }

    "return an error for invalid data" in {

      val error = MpeError("NOT_FOUND", "No data found")

      when(npsConnector.retrieve(request.nino, request.psaCheckRef)).thenReturn(Future.successful(Left(error)))
      val result = await(orchestrator.checkAndRetrieve(request))
      result mustBe Left(error)
    }
  }
}
