package uk.gov.hmrc.membersprotectionsenhancements.models.response

import play.api.libs.json.{Json, OFormat}

case class ProtectionRecordDetails(protectionRecords: Seq[ProtectionRecord])

object ProtectionRecordDetails {
  implicit val format: OFormat[ProtectionRecordDetails] = Json.format[ProtectionRecordDetails]
}
