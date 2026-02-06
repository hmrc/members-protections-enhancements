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

import utils.Logging
import play.api.mvc.{Action, ControllerComponents}
import cats.data.EitherT
import orchestrators.MembersLookUpOrchestrator
import controllers.actions.IdentifierAction
import play.api.libs.json._
import controllers.requests.validators.MembersLookUpValidator
import controllers.requests.CorrelationId
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

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
    val methodLoggingContext: String = "checkAndRetrieve"

    implicit val requestCorrelationId: CorrelationId = request.getCorrelationId

    def idLogString(correlationId: CorrelationId): String = correlationIdLogString(correlationId, Some("authenticated"))

    def infoLogger(correlationId: CorrelationId): String => Unit = infoLog(
      methodLoggingContext,
      idLogString(correlationId)
    )

    def warnLogger(correlationId: CorrelationId): (String, Option[Throwable]) => Unit = warnLog(
      methodLoggingContext,
      idLogString(correlationId)
    )

    infoLogger(requestCorrelationId)("Attempting to check for, and retrieve member's protection record details")

    val result =
      for {
        validatedRequest <- EitherT.fromEither[Future](validator.validate(request.body))
        response <- orchestrator.checkAndRetrieve(validatedRequest)
      } yield {
        infoLogger(response.correlationId)("Successfully retrieved member's protection record details")
        Ok(Json.toJson(response.responseData)).withHeaders("correlationId" -> response.correlationId.value)
      }

    result.leftMap { errorWrapper =>
      warnLogger(errorWrapper.correlationId)(
        "An error occurred while attempting to check for, and retrieve member's protection record details" +
          s"with code: ${errorWrapper.error.code}, " +
          s"message: ${errorWrapper.error.message}, " +
          s"and source: ${errorWrapper.error.source}",
        None
      )

      val errorResponse = errorWrapper.error.code match {
        case "BAD_REQUEST" => BadRequest(Json.toJson(errorWrapper.error))
        case "NOT_FOUND" | "NO_MATCH" | "EMPTY_DATA" => NotFound(Json.toJson(errorWrapper.error))
        case "FORBIDDEN" => Forbidden(Json.toJson(errorWrapper.error))
        case _ => InternalServerError(Json.toJson(errorWrapper.error))
      }

      errorResponse.withHeaders("correlationId" -> errorWrapper.correlationId.value)
    }.merge
  }
}
