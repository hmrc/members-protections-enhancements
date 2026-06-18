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

package controllers

import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import cats.data.EitherT
import orchestrators.MembersLookUpOrchestrator
import utils.ErrorCodes._
import controllers.actions.IdentifierAction
import controllers.validators.MembersLookUpValidator
import utils.HeaderKey.correlationIdKey
import play.api.Logging
import play.api.libs.json._

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
    val requestCorrelationId: String = request.correlationId
    val result =
      for {
        validatedRequest <- EitherT.fromEither[Future](validator.validate(request.body))
        response <- orchestrator.checkAndRetrieve(validatedRequest, requestCorrelationId)
      } yield Ok(Json.toJson(response.responseData)).withHeaders(correlationIdKey -> requestCorrelationId)

    result.leftMap { error =>
      val errorResponse = error.code match {
        case BAD_REQUEST_ERROR => BadRequest(Json.toJson(error))
        case NOT_FOUND_ERROR | NO_MATCH_ERROR | EMPTY_DATA_ERROR => NotFound(Json.toJson(error))
        case FORBIDDEN_ERROR => Forbidden(Json.toJson(error))
        case _ => InternalServerError(Json.toJson(error))
      }
      errorResponse.withHeaders(correlationIdKey -> requestCorrelationId)
    }.merge
  }
}
