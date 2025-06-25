package uk.gov.hmrc.membersprotectionsenhancements.models.errors

import play.api.libs.json.Writes
import uk.gov.hmrc.membersprotectionsenhancements.utils.enums.Enums

sealed trait ErrorSource

case object MatchPerson extends ErrorSource
case object RetrieveMpe extends ErrorSource
case object Internal extends ErrorSource

object ErrorSource {
  val writes: Writes[ErrorSource] = Enums.writes[ErrorSource]
  implicit def genericWrites[T <: ErrorSource]: Writes[T] = writes.contramap[T](c => c: ErrorSource)
}
