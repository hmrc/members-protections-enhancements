package uk.gov.hmrc.membersprotectionsenhancements.models.errors

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}

case class DownstreamErrorResponse(code: String,
                                   reason: String,
                                   errors: Seq[DownstreamErrorResponse] = Nil)

object DownstreamErrorResponse {
  val reasonCodeReads: Reads[DownstreamErrorResponse] = (
    (__ \ "reason").read[String] and
      (__ \ "code").read[String]
    )((reason, code) => DownstreamErrorResponse(reason, code))

  private val typeReasonReads: Reads[DownstreamErrorResponse] = (
    (__ \ "type").read[String] and
      (__ \ "reason").read[String]
    )((reason, code) => DownstreamErrorResponse(reason, code))

  private val internalErrorSeqReads: Reads[Seq[DownstreamErrorResponse]] =
    Reads.seq[DownstreamErrorResponse](typeReasonReads)

  private val badRequestSeqReads: Reads[Seq[DownstreamErrorResponse]] =
   internalErrorSeqReads orElse Reads.seq[DownstreamErrorResponse](reasonCodeReads)

  private def multipleErrorReads(seqReads: Reads[Seq[DownstreamErrorResponse]]): Reads[DownstreamErrorResponse] = {
    (__ \ "response" \ "failures").read[Seq[DownstreamErrorResponse]](seqReads).map {
      case head :: Nil => DownstreamErrorResponse(head.code, head.reason)
      case errs => DownstreamErrorResponse(
        code = "MULTIPLE_ERRORS",
        reason = "An array of multiple errors was returned from the downstream service",
        errors = errs
      )
    }
  }

  val badRequestErrorReads: Reads[DownstreamErrorResponse] = multipleErrorReads(badRequestSeqReads)

  val internalErrorReads: Reads[DownstreamErrorResponse] = multipleErrorReads(internalErrorSeqReads)
}

