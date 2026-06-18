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

package controllers.validators

import models.request.PensionSchemeMemberRequest
import models.errors.MpeError
import play.api.Logging
import play.api.libs.json._

import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Singleton}

@Singleton
class MembersLookUpValidator @Inject() (implicit val ec: ExecutionContext) extends Logging {

  def validate(
    requestBody: JsValue
  ): Either[MpeError, PensionSchemeMemberRequest] =
    requestBody.validate[PensionSchemeMemberRequest] match {
      case JsSuccess(value, _) =>
        Right(value)
      case JsError(errors) =>
        logger.error(
          s"Request body validation failed",
          JsResultException(errors)
        )
        Left(MpeError("BAD_REQUEST", "Invalid request data"))
    }
}
