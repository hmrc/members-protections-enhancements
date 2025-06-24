package uk.gov.hmrc.membersprotectionsenhancements

import cats.data.EitherT
import uk.gov.hmrc.membersprotectionsenhancements.models.errors.MpeError

import scala.concurrent.Future

package object connectors {
  type ConnectorResult[Resp] = EitherT[Future, MpeError, Resp]
}
