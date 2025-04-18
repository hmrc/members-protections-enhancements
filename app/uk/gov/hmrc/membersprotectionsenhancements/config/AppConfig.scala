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
class AppConfig @Inject() (config: Configuration) {

  val appName: String = config.get[String]("appName")

  val loginUrl: String = config.get[String]("urls.login")
  val loginContinueUrl: String = config.get[String]("urls.loginContinue")
  val redirectUrl = s"$loginUrl?continue=http%3A%2F%2Flocalhost%3A6741$loginContinueUrl"
}
