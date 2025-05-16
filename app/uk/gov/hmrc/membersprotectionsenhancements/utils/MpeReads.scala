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

package uk.gov.hmrc.membersprotectionsenhancements.utils

import play.api.libs.json._
import play.api.libs.json.Reads.pattern

import scala.util.Try

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object MpeReads {

   def name(implicit reads: Reads[String]): Reads[String] =
    pattern(
      """^[a-zA-Z\-' ]{1,35}+$""".r,
      "error.name"
    )
  def nino(implicit reads: Reads[String]): Reads[String] =
    pattern(
      """[A-Za-z]{2}[0-9]{6}[A-Za-z]{1}""".r,
      "error.email"
    )
  def psaCheckRef(implicit reads: Reads[String]): Reads[String] =
    pattern(
      """[A-Za-z]{3}[0-9]{8}[A-Za-z]{1}""".r,
      "error.email"
    )

  private val datePattern = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def dateReads: Reads[LocalDate] =
    (json: JsValue) => Try(JsSuccess(LocalDate.parse(json.as[String], datePattern), JsPath)).getOrElse(JsError())

  implicit class ReadsWithError[T](reads: Reads[T]) {
    def orError(msg: String): Reads[T] = {
      reads.orElse((json: JsValue) => JsError(__, msg))
    }
  }
}
