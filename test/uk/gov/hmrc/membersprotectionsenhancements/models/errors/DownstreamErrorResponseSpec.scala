package uk.gov.hmrc.membersprotectionsenhancements.models.errors

import base.UnitBaseSpec
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.DownstreamErrorResponse.{badRequestErrorReads, internalErrorReads, reasonCodeReads, unprocessableEntityErrorReads}

class DownstreamErrorResponseSpec extends UnitBaseSpec {
  "toString" - {
    "should write an empty downstream error to an empty string" in {
      DownstreamErrorResponse.empty.toString mustBe "N/A"
    }

    "should write a non empty downstream error to JSON string" in {
      val errsString: String = DownstreamErrorResponse(
        code = "A CODE",
        message = "A REASON"
      ).toString

      Json.parse(errsString) mustBe Json.parse(
        """
          |{
          | "code": "A CODE",
          | "reason": "A REASON"
          |}
        """.stripMargin
      )
    }

    "should write multiple non empty downstream errors to JSON string" in {
      val errsString: String = DownstreamErrorResponse(
        code = "A CODE",
        message = "A REASON",
        errors = Some(Seq(DownstreamErrorResponse(code = "ANOTHER CODE", message = "ANOTHER REASON")))
      ).toString

      Json.parse(errsString) mustBe Json.parse(
        """
          |{
          | "code": "A CODE",
          | "reason": "A REASON",
          | "errors": [
          |   {
          |     "code": "ANOTHER CODE",
          |     "reason": "ANOTHER REASON"
          |   }
          | ]
          |
          |}
        """.stripMargin
      )
    }
  }

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
    "should read from an empty array of errors" in {
      val json =
        """
          |{
          | "response": {
          |   "failures": []
          | }
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](badRequestErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse(
        code = "EMPTY_ERRORS_ARRAY",
        message = "Downstream service returned an empty array of errors"
      )
    }

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
        message = "An array of multiple errors was returned from the downstream service",
        errors = Some(Seq(
          DownstreamErrorResponse(
            code = "a type",
            message = "a reason"
          ),
          DownstreamErrorResponse(
            code = "another type",
            message = "another reason"
          )
        ))
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
        message = "An array of multiple errors was returned from the downstream service",
        errors = Some(Seq(
          DownstreamErrorResponse(
            code = "a type",
            message = "a reason"
          ),
          DownstreamErrorResponse(
            code = "another type",
            message = "another reason"
          )
        ))
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

  "unprocessableEntityErrorReads" - {
    "should read from an empty array of errors" in {
      val json =
        """
          |{
          | "failures": []
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](unprocessableEntityErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse(
        code = "EMPTY_ERRORS_ARRAY",
        message = "Downstream service returned an empty array of errors"
      )
    }

    "should read from valid JSON" in {
      val json =
        """
          |{
          | "failures": [
          |   {
          |     "reason": "a type",
          |     "code": "a reason"
          |   }
          | ]
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](unprocessableEntityErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse("a type", "a reason")
    }

    "should read from valid JSON when multiple errors exist" in {
      val json =
        """
          |{
          | "failures": [
          |   {
          |     "reason": "a type",
          |     "code": "a reason"
          |   },
          |   {
          |     "reason": "another type",
          |     "code": "another reason"
          |   }
          | ]
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](unprocessableEntityErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse(
        code = "MULTIPLE_ERRORS",
        message = "An array of multiple errors was returned from the downstream service",
        errors = Some(Seq(
          DownstreamErrorResponse(
            code = "a type",
            message = "a reason"
          ),
          DownstreamErrorResponse(
            code = "another type",
            message = "another reason"
          )
        ))
      )
    }

    "should not read from invalid JSON" in {
      val json =
        """
          |{
          | "failures": 1
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](unprocessableEntityErrorReads)
      result mustBe a [JsError]
    }
  }

  "internalErrorReads" - {
    "should read from an empty array of errors" in {
      val json =
        """
          |{
          | "response": {
          |   "failures": []
          | }
          |}
        """.stripMargin

      val result = Json.parse(json).validate[DownstreamErrorResponse](internalErrorReads)
      result mustBe a [JsSuccess[_]]
      result.get mustBe DownstreamErrorResponse(
        code = "EMPTY_ERRORS_ARRAY",
        message = "Downstream service returned an empty array of errors"
      )
    }

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
        message = "An array of multiple errors was returned from the downstream service",
        errors = Some(Seq(
          DownstreamErrorResponse(
            code = "a type",
            message = "a reason"
          ),
          DownstreamErrorResponse(
            code = "another type",
            message = "another reason"
          )
        )
      ))
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
