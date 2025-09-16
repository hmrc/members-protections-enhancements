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

package uk.gov.hmrc.membersprotectionsenhancements.models.errors

import play.api.libs.json._
import play.api.libs.functional.syntax.toFunctionalBuilderOps

case class DownstreamErrorResponse(code: String, message: String, errors: Option[Seq[DownstreamErrorResponse]] = None) {
  override def toString: String = if (this == DownstreamErrorResponse.empty) {
    "N/A"
  } else {
    Json.toJson(this)(Json.writes[DownstreamErrorResponse]).toString()
  }
}

object DownstreamErrorResponse {
  val empty: DownstreamErrorResponse = DownstreamErrorResponse("N/A", "N/A")

  val reasonCodeReads: Reads[DownstreamErrorResponse] =
    (__ \ "reason")
      .read[String]
      .and((__ \ "code").read[String])((reason, code) => DownstreamErrorResponse(reason, code))

  private val typeReasonReads: Reads[DownstreamErrorResponse] =
    (__ \ "type")
      .read[String]
      .and((__ \ "reason").read[String])((reason, code) => DownstreamErrorResponse(reason, code))

  private val unprocessableEntitySeqReads: Reads[Seq[DownstreamErrorResponse]] =
    Reads.seq[DownstreamErrorResponse](reasonCodeReads)

  private val internalErrorSeqReads: Reads[Seq[DownstreamErrorResponse]] =
    Reads.seq[DownstreamErrorResponse](typeReasonReads)

  private val badRequestSeqReads: Reads[Seq[DownstreamErrorResponse]] =
    internalErrorSeqReads.orElse(Reads.seq[DownstreamErrorResponse](reasonCodeReads))

  private def multipleErrorReads(
    path: JsPath,
    seqReads: Reads[Seq[DownstreamErrorResponse]]
  ): Reads[DownstreamErrorResponse] =
    path.read[Seq[DownstreamErrorResponse]](seqReads).map {
      case Nil =>
        DownstreamErrorResponse(
          code = "EMPTY_ERRORS_ARRAY",
          message = "Downstream service returned an empty array of errors"
        )
      case head :: Nil => DownstreamErrorResponse(head.code, head.message)
      case errs =>
        DownstreamErrorResponse(
          code = "MULTIPLE_ERRORS",
          message = "An array of multiple errors was returned from the downstream service",
          errors = Some(errs)
        )
    }

  val badRequestErrorReads: Reads[DownstreamErrorResponse] = multipleErrorReads(
    path = __ \ "response" \ "failures",
    seqReads = badRequestSeqReads
  )

  val unprocessableEntityErrorReads: Reads[DownstreamErrorResponse] = multipleErrorReads(
    __ \ "failures",
    unprocessableEntitySeqReads
  )

  val internalErrorReads: Reads[DownstreamErrorResponse] = multipleErrorReads(
    path = __ \ "response" \ "failures",
    seqReads = internalErrorSeqReads
  )
}
