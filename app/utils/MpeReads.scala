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

package utils

import play.api.libs.json._
import play.api.libs.json.Reads.pattern

import scala.util.Try

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object MpeReads {

  def name(implicit reads: Reads[String]): Reads[String] =
    pattern(
      regex = """^[a-zA-Z\-' ]{1,35}+$""".r,
      error = "error.name"
    )

  def identifier(implicit reads: Reads[String]): Reads[String] =
    pattern(
      regex = (
        "^((([ACEHJLMOPRSWXY][A-CEGHJ-NPR-TW-Z]|B[A-CEHJ-NPR-TW-Z]|G[ACEGHJ-NPR-TW-Z]|[KT]" +
          "[A-CEGHJ-MPR-TW-Z]|N[A-CEGHJL-NPR-SW-Z]|Z[A-CEGHJ-NPR-TW-Y])[0-9]{6})[A-D]?|([0-9]{2}[A-Z]{1}[0-9]{5}))$"
      ).r,
      error = "error.identifier"
    )

  def psaCheckRef(implicit reads: Reads[String]): Reads[String] =
    pattern(
      regex = """^PSA[0-9]{8}[A-Z]$""".r,
      error = "error.psaCheckRef"
    )

  def matchResult(implicit reads: Reads[String]): Reads[String] =
    pattern(
      regex = "^(MATCH|NO MATCH)$".r,
      error = "error.matchResult"
    )

  private val datePattern = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def dateReads: Reads[LocalDate] =
    (json: JsValue) => Try(JsSuccess(LocalDate.parse(json.as[String], datePattern), JsPath)).getOrElse(JsError())

  implicit class ReadsWithError[T](reads: Reads[T]) {
    def orError(jsPath: JsPath, msg: String): Reads[T] =
      reads.orElse((_: JsValue) => JsError(jsPath, msg))
  }
}
