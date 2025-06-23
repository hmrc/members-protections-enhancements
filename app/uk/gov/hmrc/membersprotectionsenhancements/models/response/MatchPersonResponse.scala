package uk.gov.hmrc.membersprotectionsenhancements.models.response

import play.api.libs.json.{JsPath, Reads}
import uk.gov.hmrc.membersprotectionsenhancements.utils.MpeReads.matchResult

case class MatchPersonResponse (matchResult: String)

object MatchPersonResponse {
  implicit val reads: Reads[MatchPersonResponse] =
    (JsPath \ "matchResult").read[String](matchResult).map(MatchPersonResponse(_))
}


