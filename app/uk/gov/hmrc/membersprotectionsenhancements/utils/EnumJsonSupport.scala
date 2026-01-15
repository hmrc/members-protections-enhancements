/*
 * Copyright 2026 HM Revenue & Customs
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

trait EnumJsonSupport[E <: scala.reflect.Enum] {
  lazy val readsValues: Set[E]
  val readsPath: String = ""
  lazy val jsPath: JsPath = JsPath(readsPath.split("/").map(KeyPathNode(_)).toList)
  private def readsMap: Map[String, E] = readsValues.map(value => value.toString -> value).toMap

  implicit lazy val enumWrites: Writes[E] = (o: E) => JsString(o.toString)
  implicit lazy val enumReads: Reads[E] = jsPath.read[String].collect(JsonValidationError(""))(readsMap)
}
