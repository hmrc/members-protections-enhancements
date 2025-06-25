package uk.gov.hmrc.membersprotectionsenhancements.models.response

import play.api.libs.json.{JsPath, Reads}
import uk.gov.hmrc.membersprotectionsenhancements.utils.MpeReads.matchResult

sealed abstract class MatchPersonResponse(val value: String)

case object Match extends MatchPersonResponse("MATCH")
case object NoMatch extends MatchPersonResponse("NO MATCH")

object MatchPersonResponse {
  implicit val reads: Reads[MatchPersonResponse] =
    (JsPath \ "matchResult").read[String](matchResult).map(str => if(str == "MATCH") Match else NoMatch)
}


