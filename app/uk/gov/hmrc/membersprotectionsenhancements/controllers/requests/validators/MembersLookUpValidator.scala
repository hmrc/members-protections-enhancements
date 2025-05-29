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

package uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.validators

import uk.gov.hmrc.membersprotectionsenhancements.models.errors.MpeError
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.PensionSchemeMemberRequest
import play.api.Logging
import play.api.libs.json._

import scala.collection.Seq
import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Singleton}


@Singleton
class MembersLookUpValidator @Inject()()(implicit val ec: ExecutionContext) extends Logging {

  def validate(requestBody: JsValue): Either[MpeError, PensionSchemeMemberRequest] =
    requestBody.validate[PensionSchemeMemberRequest] match {
      case JsSuccess(value, _) => Right(value)
      case JsError(errors) =>
        val r = errors map {
          case (_: JsPath, Seq(JsonValidationError(Seq(error: String)))) => error
          case _ => "Unknown error"
        }
        Left(MpeError("BAD_REQUEST", "Invalid request data", Some(r.toSeq)))
    }
}
