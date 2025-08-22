/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.membersprotectionsenhancements.controllers

import uk.gov.hmrc.membersprotectionsenhancements.utils.Logging
import uk.gov.hmrc.membersprotectionsenhancements.controllers.actions.IdentifierAction
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.membersprotectionsenhancements.orchestrators.MembersLookUpOrchestrator
import cats.data.EitherT
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.validators.MembersLookUpValidator
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.{Inject, Singleton}

@Singleton
class MembersLookUpController @Inject() (
  cc: ControllerComponents,
  identify: IdentifierAction,
  orchestrator: MembersLookUpOrchestrator,
  validator: MembersLookUpValidator
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {
  val classLoggingContext: String = "MembersLookUpController"

  def checkAndRetrieve: Action[JsValue] = identify.async(parse.json) { request =>
    val methodLoggingContext: String = "checkAndRetrieve"
    val fullLoggingContext: String = s"[$classLoggingContext][$methodLoggingContext]"

    logger.info(
      s"$fullLoggingContext - Received authenticated request to perform check and retrieve for supplied member details"
    )

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
    implicit val correlationId: String = request.headers.get("correlationId").getOrElse("No correlationId")

    val result =
      for {
        validatedRequest <- EitherT.fromEither[Future](validator.validate(request.body))
        response <- orchestrator.checkAndRetrieve(validatedRequest)
      } yield {
        logger.info(s"$fullLoggingContext - Success response received with correlationId ${response.correlationId}")

        Ok(Json.toJson(response.responseData)).withHeaders("correlationId" -> response.correlationId)
      }

    result.leftMap { errorWrapper =>
      logger.warn(
        s"$fullLoggingContext - Error response received: $errorWrapper with correlationId ${errorWrapper.correlationId}"
      )
      val errorResponse = errorWrapper.error.code match {
        case "BAD_REQUEST" => BadRequest(Json.toJson(errorWrapper.error))
        case "NOT_FOUND" | "NO_MATCH" | "EMPTY_DATA" => NotFound(Json.toJson(errorWrapper.error))
        case "FORBIDDEN" => Forbidden(Json.toJson(errorWrapper.error))
        case _ => InternalServerError(Json.toJson(errorWrapper.error))
      }
      errorResponse.withHeaders("correlationId" -> errorWrapper.correlationId)
    }.merge
  }
}
