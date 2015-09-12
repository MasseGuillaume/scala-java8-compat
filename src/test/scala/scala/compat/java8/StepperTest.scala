package scala.compat.java8

import org.junit.Test
import org.junit.Assert._

import java.util.Spliterator

import collectionImpl._


class IncStepperA(private val size0: Long) extends NextStepper[Int] {
  if (size0 < 0) throw new IllegalArgumentException("Size must be >= 0L")
  private var i = 0L
  def characteristics = Stepper.Sized | Stepper.SubSized | Stepper.Ordered
  def knownSize = math.max(0L, size0 - i)
  def hasStep = i < size0
  def nextStep() = { i += 1; (i - 1).toInt }
  def substep() = if ((knownSize - i) <= 1) null else {
    val sub = new IncStepperA(i + (size0 - i)/2)
    sub.i = i
    i = sub.size0
    sub
  }
  def typedPrecisely = this
}

class IncStepperB(private val size0: Long) extends TryStepper[Int] {
  if (size0 < 0) throw new IllegalArgumentException("Size must be >= 0L")
  protected var myCache: Int = 0
  private var i = 0L
  def characteristics = Stepper.Sized | Stepper.SubSized | Stepper.Ordered
  def knownSize = math.max(0L, size0 - i)
  def tryStep(f: Int => Unit): Boolean = if (i >= size0) false else { f(i.toInt); i += 1; true }
  def substep() = if ((knownSize - i) <= 1) null else {
    val sub = new IncStepperB(size0 - (size0 - i)/2)
    sub.i = i
    i = sub.size0
    sub
  }
  def typedPrecisely = this
}

class IncSpliterator(private val size0: Long) extends Spliterator.OfInt {
  private var i = 0L
  def characteristics() = Stepper.Sized | Stepper.SubSized | Stepper.Ordered
  def estimateSize() = math.max(0L, size0 - i)
  def tryAdvance(f: java.util.function.IntConsumer): Boolean = if (i >= size0) false else { f.accept(i.toInt); i += 1; true }
  def trySplit(): Spliterator.OfInt = if (i+1 >= size0) null else {
    val sub = new IncSpliterator(size0 - (size0 - i)/2)
    sub.i = i
    i = sub.size0
    sub
  }
  override def forEachRemaining(f: java.util.function.IntConsumer) { while (i < size0) { f.accept(i.toInt); i += 1 } }
}

class MappingStepper[@specialized (Double, Int, Long) A, @specialized(Double, Int, Long) B](underlying: Stepper[A], mapping: A => B) extends Stepper[B] {
  def characteristics = underlying.characteristics
  def knownSize = underlying.knownSize
  def hasStep = underlying.hasStep
  def nextStep() = mapping(underlying.nextStep())
  def tryStep(f: B => Unit): Boolean = underlying.tryStep(a => f(mapping(a)))
  override def foreach(f: B => Unit) { underlying.foreach(a => f(mapping(a))) }
  def substep() = {
    val undersub = underlying.substep()
    if (undersub == null) null
    else new MappingStepper(undersub, mapping)
  }
  def typedPrecisely = this
  def spliterator: Spliterator[B] = new MappingSpliterator[A, B](underlying.spliterator, mapping)
}

class MappingSpliterator[A, B](private val underlying: Spliterator[A], mapping: A => B) extends Spliterator[B] {
  def characteristics = underlying.characteristics
  def estimateSize() = underlying.estimateSize()
  def tryAdvance(f: java.util.function.Consumer[_ >: B]): Boolean = underlying.tryAdvance(new java.util.function.Consumer[A]{ def accept(a: A) { f.accept(mapping(a)) } })
  def trySplit(): Spliterator[B] = {
    val undersplit = underlying.trySplit()
    if (undersplit == null) null
    else new MappingSpliterator(undersplit, mapping)
  }
}
class IntToGenericSpliterator[A](private val underlying: Spliterator.OfInt, mapping: Int => A) extends Spliterator[A] {
  def characteristics = underlying.characteristics
  def estimateSize() = underlying.estimateSize()
  def tryAdvance(f: java.util.function.Consumer[_ >: A]): Boolean = underlying.tryAdvance(new java.util.function.IntConsumer{ def accept(a: Int) { f.accept(mapping(a)) } })
  def trySplit(): Spliterator[A] = {
    val undersplit = underlying.trySplit()
    if (undersplit == null) null
    else new IntToGenericSpliterator[A](undersplit, mapping)
  }
}
class IntToDoubleSpliterator(private val underlying: Spliterator.OfInt, mapping: Int => Double) extends Spliterator.OfDouble {
  def characteristics = underlying.characteristics
  def estimateSize() = underlying.estimateSize()
  def tryAdvance(f: java.util.function.DoubleConsumer): Boolean = underlying.tryAdvance(new java.util.function.IntConsumer{ def accept(a: Int) { f.accept(mapping(a)) } })
  def trySplit(): Spliterator.OfDouble = {
    val undersplit = underlying.trySplit()
    if (undersplit == null) null
    else new IntToDoubleSpliterator(undersplit, mapping)
  }
}
class IntToLongSpliterator(private val underlying: Spliterator.OfInt, mapping: Int => Long) extends Spliterator.OfLong {
  def characteristics = underlying.characteristics
  def estimateSize() = underlying.estimateSize()
  def tryAdvance(f: java.util.function.LongConsumer): Boolean = underlying.tryAdvance(new java.util.function.IntConsumer{ def accept(a: Int) { f.accept(mapping(a)) } })
  def trySplit(): Spliterator.OfLong = {
    val undersplit = underlying.trySplit()
    if (undersplit == null) null
    else new IntToLongSpliterator(undersplit, mapping)
  }
}


class StepperTest {
  def subs[Z, A, CC <: Stepper[A]](zero: Z)(s: Stepper[A])(f: Stepper[A] => Z, op: (Z, Z) => Z): Z = {
    val ss = s.substep()
    if (ss == null) op(zero, f(s))
    else {
      val left = subs(zero)(ss)(f, op)
      subs(left)(s)(f, op)
    }
  }

  val sizes = Vector(0, 1, 2, 4, 15, 17, 2512)
  def sources: Vector[(Int, Stepper[Int])] = sizes.flatMap(i => Vector(
    i -> new IncStepperA(i),
    i -> new IncStepperB(i),
    i -> Stepper.ofSpliterator(new IncSpliterator(i)),
    i -> new MappingStepper[Int,Int](new IncStepperA(i), x => x),
    i -> new MappingStepper[Long, Int](Stepper.ofSpliterator(new IntToLongSpliterator(new IncSpliterator(i), _.toLong)), _.toInt),
    i -> new MappingStepper[Double, Int](Stepper.ofSpliterator(new IntToDoubleSpliterator(new IncSpliterator(i), _.toDouble)), _.toInt),
    i -> new MappingStepper[String, Int](Stepper.ofSpliterator(new IntToGenericSpliterator[String](new IncSpliterator(i), _.toString)), _.toInt)
  ))

  @Test
  def count_only() {
    sources.foreach{ case (i, s) => assertEquals(i, s.count) }
    sources.foreach{ case (i, s) => assertEquals(i, subs(0)(s)(_.count.toInt, _ + _)) }
  }

  @Test
  def count_conditionally() {
    sources.foreach{ case (i, s) => assertEquals((0 until i).count(_ % 3 == 0), s.count(_ % 3 == 0)) }
    sources.foreach{ case (i, s) => assertEquals((0 until i).count(_ % 3 == 0), subs(0)(s)(_.count(_ % 3 == 0).toInt, _ + _)) }
  }

  @Test
  def existence() {
    sources.foreach{ case (i, s) => assert(i > 0 == s.exists(_ >= 0)) }
    sources.foreach{ case (i, s) => assert(i > 16 == s.exists(_ % 17 == 16)) }
    sources.foreach{ case (i, s) => assert(i > 0 == subs(false)(s)(_.exists(_ >= 0), _ || _)) }
    sources.foreach{ case (i, s) => assert(i > 16 == subs(false)(s)(_.exists(_ % 17 == 16), _ || _)) }
  }

  @Test
  def finding() {
    for (k <- 0 until 100) {
      (sources zip sources).foreach{ case ((i,s), (j,t)) =>
        val x = util.Random.nextInt(math.min(i,j)+3)
        val a = s.find(_ == x)
        val b = subs(None: Option[Int])(t)(_.find(_ == x), _ orElse _)
        assertEquals(a, b)
        assertEquals(a.isDefined, x < math.min(i,j))
      }      
    }
  }

  @Test
  def folding() {
    sources.foreach{ case (i,s) => assertEquals((0 until i).mkString, s.fold("")(_ + _.toString)) }
    sources.foreach{ case (i,s) => assertEquals((0 until i).mkString, subs("")(s)(_.fold("")(_ + _.toString), _ + _)) }
    sources.foreach{ case (i,s) => assertEquals((0 until i).map(_.toDouble).sum, s.fold(0.0)(_ + _), 1e-10) }
    sources.foreach{ case (i,s) => assertEquals((0 until i).map(_.toDouble).sum, subs(0.0)(s)(_.fold(0.0)(_ + _), _ + _), 1e-10) }
  }

  @Test
  def foldingUntil() {
    def expected(i: Int) = (0 until i).scan(0)(_ + _).dropWhile(_ < 6*i).headOption.getOrElse((0 until i).sum)
    sources.foreach{ case (i,s) => assertEquals(expected(i), s.foldTo(0)(_ + _)(_ >= 6*i)) }
    sources.foreach{ case (_,s) => assertEquals(-1, s.foldTo(-1)(_ * _)(_ => true)) }
    sources.foreach{ case (i,s) =>
      val ss = s.substep
      val x = s.foldTo( if (ss == null) 0 else ss.foldTo(0)(_ + _)(_ >= 6*i) )(_ + _)(_ >= 6*i)
      assertEquals(expected(i), x)
    }
  }

  @Test
  def foreaching() {
    sources.foreach{ case (i,s) =>
      val clq = new java.util.concurrent.ConcurrentLinkedQueue[String]
      s.foreach( clq add _.toString )
      assertEquals((0 until i).map(_.toString).toSet, Iterator.continually(if (!clq.isEmpty) Some(clq.poll) else None).takeWhile(_.isDefined).toSet.flatten)
    }
    sources.foreach{ case (i,s) =>
      val clq = new java.util.concurrent.ConcurrentLinkedQueue[String]
      subs(())(s)(_.foreach( clq add _.toString ), (_, _) => ())
      assertEquals((0 until i).map(_.toString).toSet, Iterator.continually(if (!clq.isEmpty) Some(clq.poll) else None).takeWhile(_.isDefined).toSet.flatten)
    }
  }

  @Test
  def reducing() {
    sources.foreach{ case (i,s) => 
      if (i==0) assertEquals(s.hasStep, false)
      else assertEquals((0 until i).sum, s.reduce(_ + _))
    }
    sources.foreach{ case (i,s) =>
      assertEquals((0 until i).sum, subs(0)(s)(x => if (!x.hasStep) 0 else x.reduce(_ + _), _ + _))
    }
  }

  @Test
  def iterating() {
    sources.foreach{ case (i, s) => assert(Iterator.range(0,i) sameElements s.iterator) }
  }

  @Test
  def spliterating() {
    sources.foreach{ case (i,s) => 
      var sum = 0
      s.spliterator.forEachRemaining(new java.util.function.Consumer[Int]{ def accept(i: Int) { sum += i } })
      assertEquals(sum, (0 until i).sum)
    }
    sources.foreach{ case (i,s) => 
      val sum = subs(0)(s)(x => { var sm = 0; x.spliterator.forEachRemaining(new java.util.function.Consumer[Int]{ def accept(i: Int) { sm += i } }); sm }, _ + _)
      assertEquals(sum, (0 until i).sum)
    }
  }
}

