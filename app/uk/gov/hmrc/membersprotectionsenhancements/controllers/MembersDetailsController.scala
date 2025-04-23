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

package uk.gov.hmrc.membersprotectionsenhancements.controllers

import uk.gov.hmrc.membersprotectionsenhancements.controllers.actions.IdentifierAction
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.membersprotectionsenhancements.models.PensionSchemeMemberDetails
import play.api.libs.json._
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.{Inject, Singleton}

@Singleton
class MembersDetailsController @Inject()(
  cc:               ControllerComponents,
  identify:         IdentifierAction
)(implicit ec:      ExecutionContext)
    extends BackendController(cc) {

  def submitAndRetrieveMembersPensionSchemes: Action[JsValue] = Action.async(parse.json) { request =>
    HeaderCarrierConverter.fromRequest(request)
    
//    val response = orchestrators.submitAndRetrieveMembersPensionSchemes(request.rawQueryString)
//    response.map(body => Ok(Json.toJson(body)))
    request.body.validate[PensionSchemeMemberDetails] match {
          case JsSuccess(value, _) =>
            if(value.lastName.equalsIgnoreCase("lastname")){
              val response = Json.parse(
                """{
                  |"statusCode": "404",
                  |"message": "search failed, no details found with the member details provided"
                  |}""".stripMargin)

              Future.successful(NotFound(response))
            }
            else {
              val response = Json.parse(
                """{
                  |"statusCode": "200",
                  |"message": "search successful, member details exists"
                  |}""".stripMargin)

              Future.successful(Ok(response))
            }

          case JsError(Seq(e)) =>
            val response = Json.parse(
              s"""{
                |"statusCode": "400",
                |"message": "Invalid json format ${e.toString()}"
                |}""".stripMargin)

            Future.successful(BadRequest(response))
    }
  }
}
