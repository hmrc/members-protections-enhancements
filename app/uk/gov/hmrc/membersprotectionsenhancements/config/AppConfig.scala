/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.membersprotectionsenhancements.config
import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject() (configuration: Configuration) {

  private def loadConfig(key: String): String = configuration.get[String](key)

  val appName: String = loadConfig("appName")

  private lazy val npsBase: String = configuration.get[Service]("microservice.services.nps")

  private lazy val npsContext: String = loadConfig("urls.npsContext")
  lazy val matchUrl: String = npsBase + npsContext + s"/${loadConfig("urls.match")}"
  lazy val retrieveUrl: String = npsBase + npsContext + s"/${loadConfig("urls.retrieve")}"

  lazy val matchPersonGovUkOriginatorId: String = loadConfig("govUkOriginatorId.matchPerson")
  lazy val retrieveMpeGovUkOriginatorId: String = loadConfig("govUkOriginatorId.retrieveMpe")
}
