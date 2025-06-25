package uk.gov.hmrc.membersprotectionsenhancements.models.response

import play.api.libs.json.{Json, OWrites}

case class MatchAndRetrieveResult(matchResult: MatchPersonResponse, protectionRecords: Option[Seq[ProtectionRecord]])

object MatchAndRetrieveResult {
  implicit val writes: OWrites[MatchAndRetrieveResult] = Json.writes[MatchAndRetrieveResult]
}
