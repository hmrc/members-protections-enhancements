/*
 * Copyright 2026 HM Revenue & Customs
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

package orchestrators
import models.response.MatchPersonResponse.{`NO MATCH`, MATCH}
import connectors.{ConnectorResult, MatchPersonNpsConnector, RetrieveMpeNpsConnector}
import cats.data.EitherT
import models.errors.ErrorSource.MatchPerson
import models.response._
import uk.gov.hmrc.http.HeaderCarrier
import org.mockito.stubbing.OngoingStubbing
import models.request.PensionSchemeMemberRequest
import org.mockito.ArgumentMatchers
import models.errors._
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import org.mockito.Mockito.when
import base.UnitBaseSpec
import orchestrators.MembersLookUpOrchestrator

import scala.concurrent.{Future, TimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global

import java.time.LocalDate

class MembersLookUpOrchestratorSpec extends UnitBaseSpec {

  trait Test {
    val matchConnector: MatchPersonNpsConnector = mock[MatchPersonNpsConnector]
    val retrieveConnector: RetrieveMpeNpsConnector = mock[RetrieveMpeNpsConnector]
    val orchestrator: MembersLookUpOrchestrator = new MembersLookUpOrchestrator(matchConnector, retrieveConnector)

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val correlationId = "X-123"

    val request: PensionSchemeMemberRequest = PensionSchemeMemberRequest(
      firstName = "Paul",
      lastName = "Smith",
      dateOfBirth = LocalDate.of(2024, 12, 31),
      identifier = "AA123456C",
      psaCheckRef = "PSA12345678A"
    )

    val retrieveResponse: ProtectionRecord = ProtectionRecord(
      protectionReference = Some("some-id"),
      `type` = "some-type",
      status = "some-status",
      protectedAmount = Some(1),
      lumpSumAmount = Some(1),
      lumpSumPercentage = Some(1),
      enhancementFactor = Some(0.5),
      pensionCreditLegislation = None
    )

    def matchPersonMock(
      res: Future[Either[MpeError, MpeResponse[MatchPersonResponse]]]
    ): OngoingStubbing[ConnectorResult[MatchPersonResponse]] =
      when(
        matchConnector.matchPerson(
          request = ArgumentMatchers.eq(request),
          correlationId = ArgumentMatchers.any()
        )(
          hc = ArgumentMatchers.any(),
          ec = ArgumentMatchers.any()
        )
      ).thenReturn(EitherT(res))

    def retrieveMpeMock(
      res: Future[Either[MpeError, MpeResponse[ProtectionRecordDetails]]]
    ): OngoingStubbing[ConnectorResult[ProtectionRecordDetails]] =
      when(
        retrieveConnector.retrieveMpe(
          nino = ArgumentMatchers.eq(request.identifier),
          psaCheckRef = ArgumentMatchers.eq(request.psaCheckRef),
          correlationId = ArgumentMatchers.any()
        )(
          hc = ArgumentMatchers.any(),
          ec = ArgumentMatchers.any()
        )
      ).thenReturn(EitherT(res))
  }

  "MembersLookUpOrchestrator" -> {
    "checkAndRetrieve" -> {
      "should return the expected result when match person check fails" in new Test {
        matchPersonMock(
          Future.successful(Left(InternalFaultError.copy(source = MatchPerson)))
        )
        val result: Either[MpeError, MpeResponse[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request, correlationId).value)

        result mustBe a[Left[_, _]]
        result.swap.getOrElse(InvalidBearerTokenError) mustBe InternalFaultError.copy(source = MatchPerson)
      }

      "should return the expected result when match person check returns NO MATCH" in new Test {
        matchPersonMock(Future.successful(Right(MpeResponse(`NO MATCH`))))
        val result: Either[MpeError, MpeResponse[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request, correlationId).value)

        result mustBe a[Left[_, _]]
        result.swap.getOrElse(InvalidBearerTokenError) mustBe NoMatchError
      }

      "should return the expected result when MPE retrieval fails" in new Test {
        matchPersonMock(Future.successful(Right(MpeResponse(MATCH))))
        retrieveMpeMock(Future.successful(Left(UnexpectedStatusError)))
        val result: Either[MpeError, MpeResponse[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request, correlationId).value)

        result mustBe a[Left[_, _]]
        result.swap.getOrElse(InvalidBearerTokenError) mustBe UnexpectedStatusError
      }

      "should return the expected result when no supported data exists" in new Test {
        matchPersonMock(Future.successful(Right(MpeResponse(MATCH))))
        retrieveMpeMock(Future.successful(Right(MpeResponse(ProtectionRecordDetails(Nil)))))
        val result: Either[MpeError, MpeResponse[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request, correlationId).value)

        result mustBe a[Left[_, _]]
        result.swap.getOrElse(InvalidBearerTokenError) mustBe EmptyDataError
      }

      "should return the EmptyDataError result when no data exists" in new Test {
        matchPersonMock(Future.successful(Right(MpeResponse(MATCH))))
        retrieveMpeMock(Future.successful(Left(EmptyDataError)))
        val result: Either[MpeError, MpeResponse[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request, correlationId).value)

        result mustBe a[Left[_, _]]
        result.swap.getOrElse(InvalidBearerTokenError) mustBe EmptyDataError
      }

      "should return the expected result when both calls succeeds" in new Test {
        matchPersonMock(Future.successful(Right(MpeResponse(MATCH))))
        retrieveMpeMock(
          Future.successful(Right(MpeResponse(ProtectionRecordDetails(Seq(retrieveResponse)))))
        )
        val result: Either[MpeError, MpeResponse[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request, correlationId).value)

        result mustBe a[Right[_, _]]
        result.getOrElse(MpeResponse(ProtectionRecordDetails(Nil))) mustBe MpeResponse(
          ProtectionRecordDetails(Seq(retrieveResponse))
        )
      }

      "should handle appropriately for a fatal error" in new Test {
        matchPersonMock(Future.successful(Right(MpeResponse(MATCH))))
        retrieveMpeMock(Future.failed(new TimeoutException))
        lazy val result: Either[MpeError, MpeResponse[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request, correlationId).value)

        assertThrows[TimeoutException](result)
      }
    }
  }
}
