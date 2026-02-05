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

package base

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import play.api.mvc.BodyParsers
import play.api.inject.bind
import play.api.libs.ws.WSClient
import controllers.actions.{IdentifierAction, _}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues, TryValues}
import play.api.Application

import scala.reflect.ClassTag

import java.net.URLEncoder

trait UnitBaseSpec
    extends AnyFreeSpec
    with Matchers
    with TryValues
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach
    with GuiceOneServerPerSuite {

  val parsers: BodyParsers.Default = app.injector.instanceOf[BodyParsers.Default]

  private val fakePsaIdentifierAction: FakePsaIdentifierAction = new FakePsaIdentifierAction(parsers)

  protected def applicationBuilder(): GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        bind[IdentifierAction].toInstance(fakePsaIdentifierAction)
      )

  def runningApplication(block: Application => Unit): Unit =
    running(_ => applicationBuilder())(block)

  def urlEncode(input: String): String = URLEncoder.encode(input, "utf-8")

  lazy val client: WSClient = app.injector.instanceOf[WSClient]
}
