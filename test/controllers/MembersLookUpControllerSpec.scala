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

package controllers

import base.UnitBaseSpec
import cats.data.EitherT
import controllers.actions.FakePsaIdentifierAction
import controllers.requests.PensionSchemeMemberRequest
import controllers.requests.validators.MembersLookUpValidator
import models.errors.{ErrorWrapper, MpeError}
import models.response.{ProtectionRecord, ProtectionRecordDetails, ResponseWrapper}
import orchestrators.MembersLookUpOrchestrator
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers.shouldBe
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import utils.ErrorCodes.*

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MembersLookUpControllerSpec extends UnitBaseSpec {

  private lazy val mockMembersLookUpOrchestrator: MembersLookUpOrchestrator = mock[MembersLookUpOrchestrator]
  private lazy val mockMembersLookUpValidator: MembersLookUpValidator = mock[MembersLookUpValidator]

  private val controller = new MembersLookUpController(
    stubMessagesControllerComponents(),
    new FakePsaIdentifierAction(parsers),
    mockMembersLookUpOrchestrator,
    mockMembersLookUpValidator
  )

  val requestJson: JsValue = Json.parse(
    """
      |{
      |    "firstName": "John",
      |    "lastName": "Smith",
      |    "dateOfBirth": "2024-12-31",
      |    "nino": "AA123456C",
      |    "psaCheckRef":"PSA12345678A"
      |}
    """.stripMargin
  )

  val responseModel: ProtectionRecordDetails = ProtectionRecordDetails(
    Seq(
      ProtectionRecord(
        protectionReference = Some("some-id"),
        `type` = "some-type",
        status = "some-status",
        protectedAmount = Some(1),
        lumpSumAmount = Some(1),
        lumpSumPercentage = Some(1),
        enhancementFactor = Some(0.5),
        pensionCreditLegislation = None
      )
    )
  )

  val requestObject: PensionSchemeMemberRequest = PensionSchemeMemberRequest(
    firstName = "John",
    lastName = "Smith",
    dateOfBirth = LocalDate.of(2024, 12, 31),
    identifier = "AA123456C",
    psaCheckRef = "PSA12345678A"
  )

  val correlationId = "X-123"

  "POST /" - {
    "return 200" in {

      when(mockMembersLookUpOrchestrator.checkAndRetrieve(any())(any(), any()))
        .thenReturn(EitherT(Future.successful(Right(ResponseWrapper(correlationId, responseModel)))))

      when(mockMembersLookUpValidator.validate(any())(any()))
        .thenReturn(Right(requestObject))

      val request = FakeRequest(
        method = "POST",
        path = "/members-protections-enhancements/check-and-retrieve"
      ).withBody(requestJson)

      val response = controller.checkAndRetrieve(request)
      status(response) shouldBe OK
    }

    "return error" - {
      def serviceErrors(error: String, expectedStatus: Int): Unit =
        s"with code $error from backend" in {

          when(mockMembersLookUpOrchestrator.checkAndRetrieve(any())(any(), any()))
            .thenReturn(EitherT(Future.successful(Left(ErrorWrapper(correlationId, MpeError(error, "error message"))))))

          when(mockMembersLookUpValidator.validate(any())(any()))
            .thenReturn(Right(requestObject))

          val request = FakeRequest(
            method = "POST",
            path = "/members-protections-enhancements/check-and-retrieve"
          ).withBody(requestJson)

          val response = controller.checkAndRetrieve(request)
          status(response) shouldBe expectedStatus
        }

      val input = Seq(
        (BAD_REQUEST_ERROR, BAD_REQUEST),
        (NOT_FOUND_ERROR, NOT_FOUND),
        (EMPTY_DATA_ERROR, NOT_FOUND),
        (NO_MATCH_ERROR, NOT_FOUND),
        (FORBIDDEN_ERROR, FORBIDDEN),
        (INTERNAL_ERROR, INTERNAL_SERVER_ERROR)
      )

      input.foreach(args => (serviceErrors _).tupled(args))
    }
  }
}
