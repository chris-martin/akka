package akka.streams

import scala.language.implicitConversions
import rx.async.api.Producer

sealed trait Operation[I, O] {
  def andThen[O2](next: Operation[O, O2]): Operation[I, O2] = AndThen(this, next)
}

// TODO: still needed?
sealed trait StatefulOperation[I, O, Z] extends Operation[I, O] {
  def initState: Z
}

case class Consume[I](producer: Producer[I]) extends Operation[Nothing, I]

/** A noop operation */
case class Identity[I]() extends Operation[I, I]
case class Map[I, O](f: I ⇒ O) extends Operation[I, O]
case class FlatMap[I, O](f: I ⇒ Producer[O]) extends Operation[I, O]
case class FlatMapNested[I, O](operation: Operation[I, O]) extends Operation[Producer[I], O]

case class Foreach[I](f: I ⇒ Unit) extends Operation[I, Unit]

case class Filter[I](pred: I ⇒ Boolean) extends Operation[I, I]

case class Span[I](pred: I ⇒ Boolean) extends Operation[I, Producer[I]]
/** Extracts head element and tail stream from a stream */
case class HeadTail[I]() extends Operation[I, (I, Producer[I])]
case class Fold[I, Z](z: Z, acc: (Z, I) ⇒ Z) extends StatefulOperation[I, Z, Z] {
  def initState: Z = z
}
case class AndThen[I1, I2, O](first: Operation[I1, I2], second: Operation[I2, O]) extends Operation[I1, O]

sealed trait FoldResult[Z, O]
object FoldResult {
  case class Continue[Z, O](state: Z) extends FoldResult[Z, O]
  case class Emit[Z, O](value: O, nextSeed: Z) extends FoldResult[Z, O]
}
case class FoldUntil[I, O, Z](seed: Z, acc: (Z, I) ⇒ FoldResult[Z, O]) extends StatefulOperation[I, O, Z] {
  def initState: Z = seed
}

case class Produce[O](iterator: Iterable[O]) extends Operation[O, O]

object Operations {
  def Op[I] = Identity[I]()

  implicit def consumeProducer[I](producer: Producer[I]) =
    new AddOps[Nothing, I](Consume(producer))

  implicit class AddOps[I, O](val op: Operation[I, O]) extends AnyVal {
    def next[O2](next: Operation[O, O2]): Operation[I, O2] = op.andThen(next)

    def map[O2](f: O ⇒ O2): Operation[I, O2] = next(Map(f))
    def flatMap[O2](f: O ⇒ Producer[O2]): Operation[I, O2] = next(FlatMap(f))

    def foreach(f: O ⇒ Unit): Operation[I, Unit] = next(Foreach(f))

    def filter(pred: O ⇒ Boolean): Operation[I, O] = next(Filter(pred))

    def fold[Z](z: Z)(acc: (Z, O) ⇒ Z): Operation[I, Z] = next(Fold(z, acc))
    def foldUntil[Z, O2](seed: Z)(acc: (Z, O) ⇒ FoldResult[Z, O2]): Operation[I, O2] = next(FoldUntil(seed, acc))

    def span(pred: O ⇒ Boolean): Operation[I, Producer[O]] = next(Span(pred))
    def headTail: Operation[I, (O, Producer[O])] = next(HeadTail[O]())
  }
  implicit class AddProducerOps[I, O](val op: Operation[I, Producer[O]]) extends AnyVal {
    def flatMapNested[O2](operation: Operation[O, O2]): Operation[I, O2] = op.andThen(FlatMapNested(operation))
  }
}