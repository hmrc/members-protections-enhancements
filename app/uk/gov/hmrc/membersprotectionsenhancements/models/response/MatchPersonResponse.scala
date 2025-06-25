package uk.gov.hmrc.membersprotectionsenhancements.models.response

import play.api.libs.json.{Format, Reads, Writes, __}
import uk.gov.hmrc.membersprotectionsenhancements.utils.enums.Enums

sealed abstract class MatchPersonResponse

case object `MATCH` extends MatchPersonResponse
case object `NO MATCH` extends MatchPersonResponse

object MatchPersonResponse {
  val enumFormat: Format[MatchPersonResponse] = Enums.format[MatchPersonResponse]
  implicit val reads: Reads[MatchPersonResponse] = (__ \ "matchResult").read[MatchPersonResponse](enumFormat)
  implicit def genericWrites[T <: MatchPersonResponse]: Writes[T] = enumFormat.contramap[T](c => c: MatchPersonResponse)
}


