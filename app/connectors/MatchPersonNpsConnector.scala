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

package connectors

import play.api.http.ContentTypes
import play.api.http.HeaderNames.{AUTHORIZATION, CONTENT_TYPE}
import config.AppConfig
import cats.data.EitherT
import models.errors.ErrorSource.MatchPerson
import models.response.{MatchPersonResponse, MpeResponse}
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import models.request.PensionSchemeMemberRequest
import utils.HeaderKey.{correlationIdKey, govUkOriginatorIdKey, ENVIRONMENT}
import models.request.PensionSchemeMemberRequest.matchPersonWrites
import models.errors.{ErrorSource, MpeError}
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue

import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Singleton}
import java.net.URI

@Singleton
class MatchPersonNpsConnector @Inject() (val config: AppConfig, val http: HttpClientV2)
    extends BaseNpsConnector[MatchPersonResponse] {

  override val source: ErrorSource = MatchPerson

  def matchPerson(
    request: PensionSchemeMemberRequest,
    correlationId: String
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): ConnectorResult[MatchPersonResponse] =
    EitherT(
      http
        .post(URI.create(config.matchUrl).toURL)
        .withBody(Json.toJson(request)(matchPersonWrites))
        .setHeader(
          (correlationIdKey, correlationId),
          (govUkOriginatorIdKey, config.govUkOriginatorId),
          (AUTHORIZATION, s"Basic ${config.authorizationToken}"),
          (CONTENT_TYPE, ContentTypes.JSON),
          (ENVIRONMENT, config.npsEnv)
        )
        .execute[Either[MpeError, MpeResponse[MatchPersonResponse]]]
    )
}
