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

package uk.gov.hmrc.membersprotectionsenhancements.connectors

import play.api.libs.json.{JsError, JsResultException, JsSuccess}
import play.shaded.ahc.io.netty.handler.codec.http.HttpMethod
import uk.gov.hmrc.membersprotectionsenhancements.config.AppConfig
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.membersprotectionsenhancements.models.response.ProtectionRecordDetails
import uk.gov.hmrc.membersprotectionsenhancements.utils.HttpResponseHelper
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.MpeError
import play.api.http.Status.OK
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.{Inject, Singleton}
import java.net.URI

@Singleton
class NpsConnector @Inject()(val config: AppConfig, val http: HttpClientV2) extends HttpResponseHelper {

//  def search()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
//
//    //    val searchUrl = s"${config.npsBase}/${config.matchUrl}"
//    //    http
//    //      .post(URI.create(searchUrl).toURL)
//    //      .withBody(Json.toJson(nino))
//    //      .execute[HttpResponse]
//
//    Future.successful(HttpResponse(Status.OK))
//  }

  def retrieve(
                nino: String, psaCheckRef: String
              )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[MpeError, ProtectionRecordDetails]] = {

    val retrieveUrl = s"${config.retrieveUrl}/$nino/admin-reference/$psaCheckRef/lookup"
    http
      .get(URI.create(retrieveUrl).toURL)
      .execute[HttpResponse].map { response =>
        response.status match {
          case OK =>
            response.json.validate[ProtectionRecordDetails] match {
              case JsSuccess(value, _) => Right(value)
              case JsError(errors) => throw JsResultException(errors)
            }
          case _ =>
            logger.error(response.body)
            Left(handleErrorResponse(HttpMethod.GET.toString, retrieveUrl)(response))
        }
      }
  }
}
