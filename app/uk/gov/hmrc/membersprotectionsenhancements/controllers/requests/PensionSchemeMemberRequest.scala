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

package uk.gov.hmrc.membersprotectionsenhancements.controllers.requests

import play.api.libs.json._
import uk.gov.hmrc.membersprotectionsenhancements.utils.MpeReads._
import play.api.libs.functional.syntax.toFunctionalBuilderOps

import java.time.LocalDate

case class PensionSchemeMemberRequest(
  firstName: String,
  lastName: String,
  dateOfBirth: LocalDate,
  identifier: String,
  psaCheckRef: String
)

object PensionSchemeMemberRequest {
  implicit val reads: Reads[PensionSchemeMemberRequest] =
    (JsPath \ "firstName")
      .read[String](name)
      .orError(JsPath \ "firstName", "Missing or invalid firstName")
      .and((__ \ "lastName").read[String](name).orError(__ \ "lastName", "Missing or invalid lastName"))
      .and(
        (__ \ "dateOfBirth").read[LocalDate](dateReads).orError(__ \ "dateOfBirth", "Missing or invalid dateOfBirth")
      )
      // TODO: This should probably be 'identifier' and not 'nino' but that would require frontend changes
      .and((__ \ "nino").read[String](identifier).orError(__ \ "identifier", "Missing or invalid nino"))
      .and(
        (__ \ "psaCheckRef").read[String](psaCheckRef).orError(__ \ "psaCheckRef", "Missing or invalid psaCheckRef")
      )(
        PensionSchemeMemberRequest.apply _
      )

  val matchPersonWrites: OWrites[PensionSchemeMemberRequest] = (o: PensionSchemeMemberRequest) =>
    Json.obj(
      "identifier" -> o.identifier,
      "firstForename" -> o.firstName,
      "surname" -> o.lastName,
      "dateOfBirth" -> o.dateOfBirth
    )
}
