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

package base

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.scalatest.concurrent.ScalaFutures
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.membersprotectionsenhancements.controllers.actions.FakePsaIdentifierAction
import play.api.mvc.BodyParsers
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import org.scalatest.wordspec.AnyWordSpec
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.matchers.must.Matchers
import play.api.Application
import org.scalatest.time.{Millis, Span}

import scala.jdk.CollectionConverters.MapHasAsJava
import scala.reflect.ClassTag

abstract class ItBaseSpec
    extends AnyWordSpec
    with WireMockSupport
    with HttpClientV2Support
    with ScalaFutures
    with Matchers
    with GuiceOneServerPerSuite {

  val parsers: BodyParsers.Default = app.injector.instanceOf[BodyParsers.Default]

  val fakePsaIdentifierAction: FakePsaIdentifierAction = new FakePsaIdentifierAction(parsers)

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(500, Millis)), interval = scaled(Span(50, Millis)))

  implicit val queryParamsToJava: Map[String, String] => java.util.Map[String, StringValuePattern] = _.map {
    case (k, v) =>
      k -> equalTo(v)
  }.asJava

  protected def applicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "auditing.enabled" -> false,
        "metric.enabled" -> false
      )

  protected def injected[A: ClassTag](implicit app: Application): A = app.injector.instanceOf[A]

  def stubGet(url: String, response: ResponseDefinitionBuilder): StubMapping =
    wireMockServer.stubFor(
      get(urlEqualTo(url))
        .willReturn(response)
    )

  def stubGet(url: String, queryParams: Map[String, String], response: ResponseDefinitionBuilder): StubMapping =
    wireMockServer.stubFor(
      get(urlPathTemplate(url))
        .withQueryParams(queryParams)
        .willReturn(response)
    )

  def stubPost(url: String, requestBody: String, response: ResponseDefinitionBuilder): StubMapping =
    wireMockServer.stubFor(
      post(urlEqualTo(url))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(equalTo(requestBody))
        .willReturn(response)
    )
}
