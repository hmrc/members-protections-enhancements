package uk.gov.hmrc.membersprotectionsenhancements.models.errors

import base.UnitBaseSpec
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.DownstreamErrorResponse.{badRequestErrorReads, internalErrorReads, reasonCodeReads}

class DownstreamErrorResponseSpec extends UnitBaseSpec {
  "reasonCodeReads" - {
    "should read from valid JSON" in {
      val json =
        """
          |{
          | "reason": "a reason",
          | "code": "a code"
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](reasonCodeReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse("a reason", "a code")
    }

    "should not read from invalid JSON" in {
      val json =
        """
          |{
          | "reason": 1
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](reasonCodeReads)
      result mustBe a [JsError]
    }
  }

  "badRequestErrorReads" - {
    "should read from valid JSON in reason-code format" in {
      val json =
        """
          |{
          | "response": {
          |   "failures": [
          |     {
          |       "reason": "a reason",
          |       "code": "a code"
          |     }
          |   ]
          | }
          |}
        """.stripMargin

      println((Json.parse(json) \ "response" \ "failures").validate[Seq[JsValue]])

      val result = Json.parse(json).validate[DownstreamErrorResponse](badRequestErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse("a reason", "a code")
    }

    "should read from valid JSON in type-reason format" in {
      val json =
        """
          |{
          | "response": {
          |   "failures": [
          |     {
          |       "type": "a type",
          |       "reason": "a reason"
          |     }
          |   ]
          | }
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](badRequestErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse("a type", "a reason")
    }

    "should read from valid JSON when multiple errors exist in type-reason format" in {
      val json =
        """
          |{
          | "response": {
          |   "failures": [
          |     {
          |       "type": "a type",
          |       "reason": "a reason"
          |     },
          |     {
          |       "type": "another type",
          |       "reason": "another reason"
          |     }
          |   ]
          | }
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](badRequestErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse(
        code = "MULTIPLE_ERRORS",
        reason = "An array of multiple errors was returned from the downstream service",
        errors = Seq(
          DownstreamErrorResponse(
            code = "a type",
            reason = "a reason"
          ),
          DownstreamErrorResponse(
            code = "another type",
            reason = "another reason"
          )
        )
      )
    }

    "should read from valid JSON when multiple errors exist in reason-code format" in {
      val json =
        """
          |{
          | "response": {
          |   "failures": [
          |     {
          |       "reason": "a type",
          |       "code": "a reason"
          |     },
          |     {
          |       "reason": "another type",
          |       "code": "another reason"
          |     }
          |   ]
          | }
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](badRequestErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse(
        code = "MULTIPLE_ERRORS",
        reason = "An array of multiple errors was returned from the downstream service",
        errors = Seq(
          DownstreamErrorResponse(
            code = "a type",
            reason = "a reason"
          ),
          DownstreamErrorResponse(
            code = "another type",
            reason = "another reason"
          )
        )
      )
    }

    "should not read a mixed format" in {
      val json =
        """
          |{
          | "response": {
          |   "failures": [
          |     {
          |       "reason": "a type",
          |       "code": "a reason"
          |     },
          |     {
          |       "type": "another type",
          |       "reason": "another reason"
          |     }
          |   ]
          | }
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](badRequestErrorReads)
      result mustBe a [JsError]
    }

    "should not read from invalid JSON" in {
      val json =
        """
          |{
          | "response": 1
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](badRequestErrorReads)
      result mustBe a [JsError]
    }
  }

  "internalErrorReads" - {
    "should read from valid JSON" in {
      val json =
        """
          |{
          | "response": {
          |   "failures": [
          |     {
          |       "type": "a type",
          |       "reason": "a reason"
          |     }
          |   ]
          | }
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](internalErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse("a type", "a reason")
    }

    "should read from valid JSON when multiple errors exist" in {
      val json =
        """
          |{
          | "response": {
          |   "failures": [
          |     {
          |       "type": "a type",
          |       "reason": "a reason"
          |     },
          |     {
          |       "type": "another type",
          |       "reason": "another reason"
          |     }
          |   ]
          | }
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](internalErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse(
        code = "MULTIPLE_ERRORS",
        reason = "An array of multiple errors was returned from the downstream service",
        errors = Seq(
          DownstreamErrorResponse(
            code = "a type",
            reason = "a reason"
          ),
          DownstreamErrorResponse(
            code = "another type",
            reason = "another reason"
          )
        )
      )
    }

    "should not read from invalid JSON" in {
      val json =
        """
          |{
          | "response": 1
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](internalErrorReads)
      result mustBe a [JsError]
    }
  }
}
