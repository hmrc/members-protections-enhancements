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

import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Singleton}

@Singleton
class MembersLookUpValidator @Inject() (implicit val ec: ExecutionContext, correlationId: String) extends Logging {
  val classLoggingContext: String = "MembersLookUpValidator"

  def validate(requestBody: JsValue): Either[MpeError, PensionSchemeMemberRequest] = {
    val methodLoggingContext: String = "validate"
    val fullLoggingContext = s"[$classLoggingContext][$methodLoggingContext]"

    logger.info(s"$fullLoggingContext - Attempting to validate supplied request body with correlationId $correlationId")

    requestBody.validate[PensionSchemeMemberRequest] match {
      case JsSuccess(value, _) =>
        logger.info(
          s"$fullLoggingContext - Request body validation completed successfully with correlationId $correlationId"
        )
        Right(value)
      case JsError(errors) =>
        val r = errors.map(t => t._2.foldLeft("")((x, y) => x + y.message))

        logger.error(
          s"$fullLoggingContext - Request body validation with correlationId $correlationId failed with errors: $r"
        )
        Left(MpeError("BAD_REQUEST", "Invalid request data", Some(r.toSeq)))
    }
  }
}
