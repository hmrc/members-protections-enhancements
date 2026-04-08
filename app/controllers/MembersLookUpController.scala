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

package controllers

import play.api.mvc.{Action, ControllerComponents}
import cats.data.EitherT
import orchestrators.MembersLookUpOrchestrator
import utils.ErrorCodes._
import controllers.actions.IdentifierAction
import controllers.requests.CorrelationId
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import play.api.Logging
import play.api.libs.json._
import utils.HeaderKey.correlationIdKey
import controllers.requests.validators.MembersLookUpValidator

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

  def checkAndRetrieve: Action[JsValue] = identify.async(parse.json) { implicit request =>

    val requestCorrelationId: CorrelationId = request.getCorrelationId

    logger.info(s"$requestCorrelationId - Attempting to check for, and retrieve member's protection record details")

    val result =
      for {
        validatedRequest <- EitherT.fromEither[Future](validator.validate(request.body, requestCorrelationId))
        response <- orchestrator.checkAndRetrieve(validatedRequest, requestCorrelationId)
      } yield {
        logger.info(
          s"Successfully retrieved member's protection record details (Correlation ID: ${response.correlationId.value})"
        )
        Ok(Json.toJson(response.responseData)).withHeaders(correlationIdKey -> response.correlationId.value)
      }

    result.leftMap { errorWrapper =>
      logger.warn(
        s"An error occurred while attempting to check for, and retrieve member's protection record details (Correlation ID: ${errorWrapper.correlationId.value})" +
          s"with code: ${errorWrapper.error.code}, " +
          s"message: ${errorWrapper.error.message}, " +
          s"and source: ${errorWrapper.error.source}"
      )

      val errorResponse = errorWrapper.error.code match {
        case BAD_REQUEST_ERROR => BadRequest(Json.toJson(errorWrapper.error))
        case NOT_FOUND_ERROR | NO_MATCH_ERROR | EMPTY_DATA_ERROR => NotFound(Json.toJson(errorWrapper.error))
        case FORBIDDEN_ERROR => Forbidden(Json.toJson(errorWrapper.error))
        case _ => InternalServerError(Json.toJson(errorWrapper.error))
      }

      errorResponse.withHeaders(correlationIdKey -> errorWrapper.correlationId.value)
    }.merge
  }
}
