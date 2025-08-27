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

import uk.gov.hmrc.membersprotectionsenhancements.models.errors._
import cats.data.EitherT
import uk.gov.hmrc.http.HeaderCarrier
import org.mockito.stubbing.OngoingStubbing
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.{CorrelationId, PensionSchemeMemberRequest}
import org.mockito.ArgumentMatchers
import uk.gov.hmrc.membersprotectionsenhancements.models.response._
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import org.mockito.Mockito.when
import base.UnitBaseSpec
import uk.gov.hmrc.membersprotectionsenhancements.connectors.{ConnectorResult, NpsConnector}

import scala.concurrent.{Future, TimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global

import java.time.LocalDate

class MembersLookUpOrchestratorSpec extends UnitBaseSpec {

  trait Test {
    val npsConnector: NpsConnector = mock[NpsConnector]
    val orchestrator: MembersLookUpOrchestrator = new MembersLookUpOrchestrator(npsConnector)

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val correlationId: CorrelationId = "X-123"

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
      res: Future[Either[ErrorWrapper, ResponseWrapper[MatchPersonResponse]]]
    ): OngoingStubbing[ConnectorResult[MatchPersonResponse]] =
      when(
        npsConnector.matchPerson(
          request = ArgumentMatchers.eq(request)
        )(
          hc = ArgumentMatchers.any(),
          ec = ArgumentMatchers.any(),
          correlationId = ArgumentMatchers.any()
        )
      ).thenReturn(EitherT(res))

    def retrieveMpeMock(
      res: Future[Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]]]
    ): OngoingStubbing[ConnectorResult[ProtectionRecordDetails]] =
      when(
        npsConnector.retrieveMpe(
          nino = ArgumentMatchers.eq(request.identifier),
          psaCheckRef = ArgumentMatchers.eq(request.psaCheckRef)
        )(
          hc = ArgumentMatchers.any(),
          ec = ArgumentMatchers.any(),
          correlationId = ArgumentMatchers.any()
        )
      ).thenReturn(EitherT(res))
  }

  "MembersLookUpOrchestrator" -> {
    "checkAndRetrieve" -> {
      "should return the expected result when match person check fails" in new Test {
        matchPersonMock(Future.successful(Left(ErrorWrapper(correlationId, InternalError.copy(source = MatchPerson)))))
        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request).value)

        result mustBe a[Left[_, _]]
        result.swap.getOrElse(ErrorWrapper(correlationId, InvalidBearerTokenError)) mustBe ErrorWrapper(
          correlationId,
          InternalError.copy(source = MatchPerson)
        )
      }

      "should return the expected result when match person check returns NO MATCH" in new Test {
        matchPersonMock(Future.successful(Right(ResponseWrapper(correlationId, `NO MATCH`))))
        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request).value)

        result mustBe a[Left[_, _]]
        result.swap.getOrElse(ErrorWrapper(correlationId, InvalidBearerTokenError)) mustBe ErrorWrapper(
          correlationId,
          NoMatchError
        )
      }

      "should return the expected result when MPE retrieval fails" in new Test {
        matchPersonMock(Future.successful(Right(ResponseWrapper(correlationId, MATCH))))
        retrieveMpeMock(Future.successful(Left(ErrorWrapper(correlationId, UnexpectedStatusError))))
        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request).value)

        result mustBe a[Left[_, _]]
        result.swap.getOrElse(ErrorWrapper(correlationId, InvalidBearerTokenError)) mustBe ErrorWrapper(
          correlationId,
          UnexpectedStatusError
        )
      }

      "should return the expected result when no supported data exists" in new Test {
        matchPersonMock(Future.successful(Right(ResponseWrapper(correlationId, MATCH))))
        retrieveMpeMock(Future.successful(Right(ResponseWrapper(correlationId, ProtectionRecordDetails(Nil)))))
        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request).value)

        result mustBe a[Left[_, _]]
        result.swap.getOrElse(ErrorWrapper(correlationId, InvalidBearerTokenError)) mustBe ErrorWrapper(
          correlationId,
          EmptyDataError
        )
      }

      "should return the EmptyDataError result when no data exists" in new Test {
        matchPersonMock(Future.successful(Right(ResponseWrapper(correlationId, MATCH))))
        retrieveMpeMock(Future.successful(Left(ErrorWrapper(correlationId, EmptyDataError))))
        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request).value)

        result mustBe a[Left[_, _]]
        result.swap.getOrElse(ErrorWrapper(correlationId, InvalidBearerTokenError)) mustBe ErrorWrapper(
          correlationId,
          EmptyDataError
        )
      }

      "should return the expected result when both calls succeeds" in new Test {
        matchPersonMock(Future.successful(Right(ResponseWrapper(correlationId, MATCH))))
        retrieveMpeMock(
          Future.successful(Right(ResponseWrapper(correlationId, ProtectionRecordDetails(Seq(retrieveResponse)))))
        )
        val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request).value)

        result mustBe a[Right[_, _]]
        result.getOrElse(ResponseWrapper(correlationId, ProtectionRecordDetails(Nil))) mustBe ResponseWrapper(
          correlationId,
          ProtectionRecordDetails(Seq(retrieveResponse))
        )
      }

      "should handle appropriately for a fatal error" in new Test {
        matchPersonMock(Future.successful(Right(ResponseWrapper(correlationId, MATCH))))
        retrieveMpeMock(Future.failed(new TimeoutException))
        lazy val result: Either[ErrorWrapper, ResponseWrapper[ProtectionRecordDetails]] =
          await(orchestrator.checkAndRetrieve(request).value)

        assertThrows[TimeoutException](result)
      }
    }
  }
}
