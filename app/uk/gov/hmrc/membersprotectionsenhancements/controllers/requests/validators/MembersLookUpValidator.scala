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

import play.api.libs.json._
import uk.gov.hmrc.membersprotectionsenhancements.controllers.requests.{CorrelationId, PensionSchemeMemberRequest}
import uk.gov.hmrc.membersprotectionsenhancements.utils.Logging
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.{ErrorWrapper, MpeError}

import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Singleton}

@Singleton
class MembersLookUpValidator @Inject() (implicit val ec: ExecutionContext) extends Logging {

  def validate(
    requestBody: JsValue
  )(implicit correlationId: CorrelationId): Either[ErrorWrapper, PensionSchemeMemberRequest] = {
    val methodLoggingContext: String = "validate"

    val idLogString: String = correlationIdLogString(correlationId)
    val infoLogger: String => Unit = infoLog(methodLoggingContext, idLogString)

    infoLogger("Attempting to validate supplied request body")

    requestBody.validate[PensionSchemeMemberRequest] match {
      case JsSuccess(value, _) =>
        infoLogger("Request body validation completed successfully")
        Right(value)
      case JsError(errors) =>
        logger.errorWithException(
          secondaryContext = methodLoggingContext,
          message = s"Request body validation failed",
          ex = JsResultException(errors),
          dataLog = idLogString
        )
        Left(ErrorWrapper(correlationId, MpeError("BAD_REQUEST", "Invalid request data")))
    }
  }
}
