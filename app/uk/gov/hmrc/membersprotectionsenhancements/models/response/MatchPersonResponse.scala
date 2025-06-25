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

package uk.gov.hmrc.membersprotectionsenhancements.models.response

import uk.gov.hmrc.membersprotectionsenhancements.utils.enums.Enums
import play.api.libs.json._

sealed abstract class MatchPersonResponse

case object `MATCH` extends MatchPersonResponse
case object `NO MATCH` extends MatchPersonResponse

object MatchPersonResponse {
  private val enumFormat: Format[MatchPersonResponse] = Enums.format[MatchPersonResponse]
  implicit val reads: Reads[MatchPersonResponse] = (__ \ "matchResult").read[MatchPersonResponse](enumFormat)
  implicit def genericWrites[T <: MatchPersonResponse]: Writes[T] = enumFormat.contramap[T](c => c: MatchPersonResponse)
}
