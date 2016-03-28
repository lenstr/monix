/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
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

package monix.async

import monix.async.AsyncIterable._
import monix.execution.Scheduler
import scala.collection.{LinearSeq, mutable}
import scala.util.control.NonFatal
import scala.collection.immutable

/** An `AsyncIterable` represents a [[Task]] based asynchronous
  * iterator, generated by [[AsyncIterable]].
  */
sealed trait AsyncIterable[+A] {
  /** Filters the `AsyncIterable` by the given predicate function,
    * returning only those elements that match.
    */
  def filter(p: A => Boolean): AsyncIterable[A] =
    this match {
      case ref @ Next(head, tail) =>
        try { if (p(head)) ref else Wait(tail) }
        catch { case NonFatal(ex) => Error(ex) }
      case NextSeq(head, rest) =>
        try head.filter(p) match {
          case Nil => Wait(rest)
          case filtered => NextSeq(filtered, rest)
        } catch {
          case NonFatal(ex) => Error(ex)
        }
      case Wait(rest) => Wait(rest.map(_.filter(p)))
      case Empty => Empty
      case Error(ex) => Error(ex)
    }

  /** Returns a new iterable by mapping the supplied function
    * over the elements of the source.
    */
  final def map[B](f: A => B): AsyncIterable[B] = {
    println("---------> iterable map")
    this match {
      case Next(head, tail: Task[AsyncIterable[A]]) =>
        try { Next(f(head), tail.map(_.map(f))) }
        catch { case NonFatal(ex) => Error(ex) }
      case NextSeq(head, rest) =>
        try { NextSeq(head.map(f), rest.map(_.map(f))) }
        catch { case NonFatal(ex) => Error(ex) }

      case Wait(rest) => Wait(rest.map(_.map(f)))
      case Empty => Empty
      case Error(ex) => Error(ex)
    }
  }

  /** Applies the function to the elements of the source
    * and concatenates the results.
    */
  final def flatMap[B](f: A => AsyncIterable[B]): AsyncIterable[B] = {
    println("---------> iterable flatMap")
    this match {
      case Next(head, tail) =>
        try { f(head) concatTask tail.map(_.flatMap(f)) }
        catch { case NonFatal(ex) => Error(ex) }

      case NextSeq(list, rest) =>
        try {
          if (list.isEmpty)
            Wait(rest.map(_.flatMap(f)))
          else
            f(list.head) concatTask Task.evalAlways(NextSeq(list.tail, rest).flatMap(f))
        } catch {
          case NonFatal(ex) => Error(ex)
        }

      case Wait(rest) => Wait(rest.map(_.flatMap(f)))
      case Empty => Empty
      case Error(ex) => Error(ex)
    }
  }
  /** If the source is an async iterable generator, then
    * concatenates the generated async iterables.
    */
  final def flatten[B](implicit ev: A <:< AsyncIterable[B]): AsyncIterable[B] =
    flatMap(x => x)

  /** Alias for [[flatMap]]. */
  final def concatMap[B](f: A => AsyncIterable[B]): AsyncIterable[B] =
    flatMap(f)

  /** Alias for [[concat]]. */
  final def concat[B](implicit ev: A <:< AsyncIterable[B]): AsyncIterable[B] =
    flatten

  /** Appends the given iterable to the end of the source,
    * effectively concatenating them.
    */
//  final def ++[B >: A](rhs: AsyncIterable[B]): AsyncIterable[B] =
//    this match {
//      case Wait(task) =>
//        Wait(task.map(_ ++ rhs))
//      case Next(a, lt) =>
//        Next(a, lt.map(_ ++ rhs))
//      case NextSeq(head, lt) =>
//        NextSeq(head, lt.map(_ ++ rhs))
//      case Empty => rhs
//      case Error(ex) => Error(ex)
//    }

  private final def concatTask[B >: A](rhs: Task[AsyncIterable[B]]): AsyncIterable[B] = {
    println("------> iterator concatTask")
    this match {
      case Wait(task) =>
        Wait(task.map(_ concatTask rhs))
      case Next(a, lt) =>
        Next(a, lt.map(_ concatTask rhs))
      case NextSeq(head, lt) =>
        NextSeq(head, lt.map(_ concatTask rhs))
      case Empty => Wait(rhs)
      case Error(ex) => Error(ex)
    }
  }

  /** Left associative fold using the function 'f'.
    *
    * On execution the iterable will be traversed from left to right,
    * and the given function will be called with the prior result,
    * accumulating state until the end, when the summary is returned.
    */
  def foldLeftA[S](seed: S)(f: (S,A) => S): Task[S] =
    this match {
      case Empty => Task.now(seed)
      case Error(ex) => Task.error(ex)
      case Wait(next) =>
        next.flatMap(_.foldLeftA(seed)(f))
      case Next(a, next) =>
        try {
          val state = f(seed, a)
          next.flatMap(_.foldLeftA(state)(f))
        } catch {
          case NonFatal(ex) => Task.error(ex)
        }
      case NextSeq(list, next) =>
        try {
          val state = list.foldLeft(seed)(f)
          next.flatMap(_.foldLeftA(state)(f))
        } catch {
          case NonFatal(ex) => Task.error(ex)
        }
    }

  /** Left associative fold with the ability to short-circuit the process.
    *
    * This fold works for as long as the provided function keeps returning `true`
    * as the first member of its result and the streaming isn't completed.
    * If the provided fold function returns a `false` then the folding will
    * stop and the generated result will be the second member
    * of the resulting tuple.
    *
    * @param f is the folding function, returning `(true, state)` if the fold has
    *          to be continued, or `(false, state)` if the fold has to be stopped
    *          and the rest of the values to be ignored.
    */
  def foldWhileA[S](seed: S)(f: (S, A) => (Boolean, S)): Task[S] =
    this match {
      case Empty => Task.now(seed)
      case Error(ex) => Task.error(ex)
      case Wait(next) =>
        next.flatMap(_.foldWhileA(seed)(f))
      case Next(a, next) =>
        try {
          val (continue, state) = f(seed, a)
          if (!continue) Task.now(state) else
            next.flatMap(_.foldWhileA(state)(f))
        } catch {
          case NonFatal(ex) => Task.error(ex)
        }
      case NextSeq(list, next) =>
        try {
          var continue = true
          var state = seed
          val iter = list.iterator

          while (continue && iter.hasNext) {
            val (c,s) = f(state, iter.next())
            state = s
            continue = c
          }

          if (!continue) Task.now(state) else
            next.flatMap(_.foldWhileA(state)(f))
        } catch {
          case NonFatal(ex) => Task.error(ex)
        }
    }

  /** Right associative lazy fold on `AsyncIterable` using the
    * folding function 'f'.
    *
    * This method evaluates `lb` lazily (in some cases it will not be
    * needed), and returns a lazy value. We are using `(A, Eval[B]) =>
    * Eval[B]` to support laziness in a stack-safe way. Chained
    * computation should be performed via .map and .flatMap.
    *
    * For more detailed information about how this method works see the
    * documentation for `Eval[_]`.
    */
  def foldRightA[B](lb: Task[B])(f: (A, Task[B]) => Task[B]): Task[B] =
    this match {
      case Empty => lb
      case Error(ex) => Task.error(ex)
      case Wait(next) =>
        next.flatMap(_.foldRightA(lb)(f))
      case Next(a, next) =>
        f(a, next.flatMap(_.foldRightA(lb)(f)))

      case NextSeq(list, next) =>
        if (list.isEmpty) next.flatMap(_.foldRightA(lb)(f))
        else {
          val a = list.head
          val tail = list.tail
          val rest = Task.now(NextSeq(tail, next))
          f(a, rest.flatMap(_.foldRightA(lb)(f)))
        }
    }

  /** Find the first element matching the predicate, if one exists. */
  def findA(p: A => Boolean): Task[Option[A]] =
    foldWhileA(Option.empty[A])((s,a) => if (p(a)) (false, Some(a)) else (true, s))

  /** Count the total number of elements. */
  def countA: Task[Long] =
    foldLeftA(0L)((acc,_) => acc + 1)

  /** Given a sequence of numbers, calculates a sum. */
  def sumA[B >: A](implicit B: Numeric[B]): Task[B] =
    foldLeftA(B.zero)(B.plus)

  /** Check whether at least one element satisfies the predicate. */
  def existsA(p: A => Boolean): Task[Boolean] =
    foldWhileA(false)((s,a) => if (p(a)) (false, true) else (true, s))

  /** Check whether all elements satisfy the predicate. */
  def forallA(p: A => Boolean): Task[Boolean] =
    foldWhileA(true)((s,a) => if (!p(a)) (false, false) else (true, s))

  /** Aggregates elements in a `List` and preserves order. */
  def toListA: Task[List[A]] = {
    foldLeftA(mutable.ListBuffer.empty[A]) { (acc, a) => acc += a }
      .map(_.toList)
  }

  /** Returns true if there are no elements, false otherwise. */
  def isEmptyA: Task[Boolean] =
    foldWhileA(true)((_,_) => (false, false))

  /** Returns true if there are elements, false otherwise. */
  def nonEmptyA: Task[Boolean] =
    foldWhileA(false)((_,_) => (false, true))

  /** Returns the first element in the iterable, as an option. */
  def headA: Task[Option[A]] =
    this match {
      case Wait(next) => next.flatMap(_.headA)
      case Empty => Task.now(None)
      case Error(ex) => Task.error(ex)
      case Next(a, _) => Task.now(Some(a))
      case NextSeq(list, _) => Task.now(list.headOption)
    }

  /** Alias for [[headA]]. */
  def firstA: Task[Option[A]] = headA

  /** Returns a new sequence that will take a maximum of
    * `n` elements from the start of the source sequence.
    */
  def take(n: Int): AsyncIterable[A] =
    if (n <= 0) Empty else this match {
      case Wait(next) => Wait(next.map(_.take(n)))
      case Empty => Empty
      case Error(ex) => Error(ex)
      case Next(a, next) =>
        if (n - 1 > 0)
          Next(a, next.map(_.take(n-1)))
        else
          Next(a, EmptyTask)

      case NextSeq(list, rest) =>
        val length = list.length
        if (length == n)
          NextSeq(list, EmptyTask)
        else if (length < n)
          NextSeq(list, rest.map(_.take(n-length)))
        else
          NextSeq(list.take(n), EmptyTask)
    }

  /** Returns a new sequence that will take elements from
    * the start of the source sequence, for as long as the given
    * function `f` returns `true` and then stop.
    */
  def takeWhile(p: A => Boolean): AsyncIterable[A] =
    this match {
      case Wait(next) => Wait(next.map(_.takeWhile(p)))
      case Empty => Empty
      case Error(ex) => Error(ex)
      case Next(a, next) =>
        try { if (p(a)) Next(a, next.map(_.takeWhile(p))) else Empty }
        catch { case NonFatal(ex) => Error(ex) }
      case NextSeq(list, rest) =>
        try {
          val filtered = list.takeWhile(p)
          if (filtered.length < list.length)
            NextSeq(filtered, EmptyTask)
          else
            NextSeq(filtered, rest.map(_.takeWhile(p)))
        } catch {
          case NonFatal(ex) => Error(ex)
        }
    }

  /** Drops the first `n` elements, from left to right. */
  def drop(n: Int): AsyncIterable[A] =
    if (n <= 0) this else this match {
      case Wait(next) => Wait(next.map(_.drop(n)))
      case Empty => Empty
      case Error(ex) => Error(ex)
      case Next(a, next) => Wait(next.map(_.drop(n-1)))
      case NextSeq(list, rest) =>
        val length = list.length
        if (length == n)
          Wait(rest)
        else if (length > n)
          NextSeq(list.drop(n), rest)
        else
          Wait(rest.map(_.drop(n - length)))
    }

  /** Triggers memoization of the iterable on the first traversal,
    * such that results will get reused on subsequent traversals.
    */
  def memoize: AsyncIterable[A] =
    this match {
      case Wait(next) => Wait(next.memoize.map(_.memoize))
      case ref @ (Empty | Error(_)) => ref
      case Next(a, rest) => Next(a, rest.memoize.map(_.memoize))
      case NextSeq(list, rest) => NextSeq(list, rest.memoize.map(_.memoize))
    }

  /** Materializes the stream and for each element applies
    * the given function.
    *
    * @return a [[CancelableFuture]] that will complete when
    *         the streaming is done and that can also be used to
    *         cancel the streaming.
    */
  def foreach(f: A => Unit)(implicit s: Scheduler): CancelableFuture[Unit] = {
    def loop(task: Task[AsyncIterable[A]]): Task[Unit] = task.flatMap {
      case Next(elem, rest) =>
        try { f(elem); loop(rest) }
        catch { case NonFatal(ex) => s.reportFailure(ex); Task.unit }

      case NextSeq(elems, rest) =>
        try { elems.foreach(f); loop(rest) }
        catch { case NonFatal(ex) => s.reportFailure(ex); Task.unit }

      case Wait(rest) => loop(Task.defer(rest))
      case Empty => Task.unit
      case Error(ex) =>
        s.reportFailure(ex)
        Task.unit
    }

    loop(Task.now(this)).runAsync
  }
}

object AsyncIterable {
  /** Lifts a strict value into an `AsyncIterable` */
  def now[A](a: A): AsyncIterable[A] = Next(a, EmptyTask)

  /** Builder for an [[Error]] state. */
  def error[A](ex: Throwable): AsyncIterable[A] = Error(ex)

  /** Builder for an [[Empty]] state. */
  def empty[A]: AsyncIterable[A] = Empty

  /** Builder for a [[Wait]] iterator state. */
  def wait[A](rest: Task[AsyncIterable[A]]): AsyncIterable[A] = Wait(rest)

  /** Builds a [[Next]] iterator state. */
  def next[A](head: A, rest: Task[AsyncIterable[A]]): AsyncIterable[A] =
    Next(head, rest)

  /** Builds a [[Next]] iterator state. */
  def nextSeq[A](headSeq: LinearSeq[A], rest: Task[AsyncIterable[A]]): AsyncIterable[A] =
    NextSeq(headSeq, rest)

  /** Lifts a strict value into an `AsyncIterable` */
  def evalAlways[A](a: => A): AsyncIterable[A] =
    Wait(Task.evalAlways(try Next(a, EmptyTask) catch { case NonFatal(ex) => Error(ex) }))

  /** Lifts a strict value into an `AsyncIterable` and
    * memoizes the result for subsequent executions.
    */
  def evalOnce[A](a: => A): AsyncIterable[A] =
    Wait(Task.evalOnce(try Next(a, EmptyTask) catch { case NonFatal(ex) => Error(ex) }))

  /** Promote a non-strict value representing a AsyncIterable
    * to an AsyncIterable of the same type.
    */
  def defer[A](fa: => AsyncIterable[A]): Wait[A] =
    Wait(Task.defer(Task.evalAlways(fa)))

  /** Generages a range between `from` (inclusive) and `until` (exclusive),
    * with `step` as the increment.
    */
  def range(from: Long, until: Long, step: Long = 1L): AsyncIterable[Long] = {
    def loop(cursor: Long): AsyncIterable[Long] = {
      val isInRange = (step > 0 && cursor < until) || (step < 0 && cursor > until)
      val nextCursor = cursor + step
      if (!isInRange) Empty else Next(cursor, Task.evalAlways(loop(nextCursor)))
    }

    Wait(Task.evalAlways(loop(from)))
  }

  def fuckingLoop(x: Long): Task[Long] = {
    Task.now(x).map(_ + 1).flatMap(fuckingLoop)
  }

  /** Converts any sequence into an async iterable.
    *
    * Because the list is a linear sequence that's known
    * (but not necessarily strict), we'll just return
    * a strict state.
    */
  def fromList[A](list: immutable.LinearSeq[A], batchSize: Int): AsyncIterable[A] =
    NextSeq[A](list, EmptyTask)

  /** Converts an iterable into an async iterator. */
  def fromIterable[A](iterable: Iterable[A], batchSize: Int): AsyncIterable[A] =
    Wait(Task.now(iterable).flatMap { iter => fromIterator(iter.iterator, batchSize) })

  /** Converts an iterator into an async iterator. */
  def fromIterator[A](iterator: Iterator[A], batchSize: Int): Task[AsyncIterable[A]] =
    Task.now(iterator).flatMap { iterator =>
      try {
        val buffer = mutable.ListBuffer.empty[A]
        var processed = 0
        while (processed < batchSize && iterator.hasNext) {
          buffer += iterator.next()
          processed += 1
        }

        val result = if (processed == 0) EmptyTask else
          Task.evalAlways(NextSeq(buffer.toList, fromIterator(iterator, batchSize)))

        println(s"---------> iterator (${buffer.headOption.getOrElse(0)})")
        result
      } catch {
        case NonFatal(ex) => Task.now(Error(ex))
      }
    }

  /** A state of the [[AsyncIterable]] representing a deferred iterator. */
  final case class Wait[+A](next: Task[AsyncIterable[A]]) extends AsyncIterable[A]

  /** A state of the [[AsyncIterable]] representing a head/tail decomposition.
    *
    * @param head is the next element to be processed
    * @param rest is the next state in the sequence
    */
  final case class Next[+A](head: A, rest: Task[AsyncIterable[A]]) extends AsyncIterable[A]

  /** A state of the [[AsyncIterable]] representing a head/tail decomposition.
    *
    * Like [[Next]] except that the head is a strict sequence
    * of elements that don't need asynchronous execution.
    * Meant for doing buffering.
    *
    * @param headSeq is a sequence of the next elements to be processed, can be empty
    * @param rest is the next state in the sequence
    */
  case class NextSeq[+A](headSeq: LinearSeq[A], rest: Task[AsyncIterable[A]]) extends AsyncIterable[A]

  /** Represents an error state in the iterator.
    *
    * This is a final state. When this state is received, the data-source
    * should have been canceled already.
    *
    * @param ex is an error that was thrown.
    */
  case class Error(ex: Throwable) extends AsyncIterable[Nothing]

  /** Represents an empty iterator.
    *
    * Received as a final state in the iteration process.
    * When this state is received, the data-source should have
    * been canceled already.
    */
  case object Empty extends AsyncIterable[Nothing]

  // Reusable instances
  private[async] final val EmptyTask = Task.now(Empty)
}