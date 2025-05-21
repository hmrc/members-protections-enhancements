package uk.gov.hmrc.membersprotectionsenhancements.models.response

import play.api.libs.json.{Json, OFormat}

case class ProtectionRecord(protectionReference: Option[String],
                            `type`: String,
                            status: String,
                            protectedAmount: Option[Int],
                            lumpSumAmount: Option[Int],
                            lumpSumPercentage: Option[Int],
                            enhancementFactor: Option[Double])

object ProtectionRecord {
  implicit val format: OFormat[ProtectionRecord] = Json.format[ProtectionRecord]
}
