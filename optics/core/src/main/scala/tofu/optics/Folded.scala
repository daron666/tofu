package tofu.optics

import cats.instances.list._
import cats.syntax.monoid._
import cats.{Applicative, Foldable, Monoid}
import tofu.optics.data.Constant

/** S has some or none occurrences of A
  * and can collect them */
trait PFolded[-S, +T, +A, -B] extends PBase[S, T, A, B] {
  def foldMap[X: Monoid](s: S)(f: A => X): X

  def getAll(s: S): List[A] = foldMap(s)(List(_))

  def as[B1, T1]: PFolded[S, T1, A, B1] = this.asInstanceOf[PFolded[S, T1, A, B1]]
}

object Folded extends MonoOpticCompanion(PFolded) {
  def apply[S] = new FoldedApply[S]

  class FoldedApply[S] {
    type Arb

    def apply[A](fm: Monoid[Arb] => (S, A => Arb) => Arb): Folded[S, A] = new Folded[S, A] {
      def foldMap[X: Monoid](s: S)(f: A => X): X =
        fm(Monoid[X].asInstanceOf[Monoid[Arb]])(s, f.asInstanceOf[A => Arb]).asInstanceOf[X]
    }
  }
}

object PFolded extends OpticCompanion[PFolded] {
  implicit final class TofuFoldedOps[S, T, A, B](private val self: PFolded[S, T, A, B]) extends AnyVal {
    def ++[S1 <: S, T1, A1 >: A, V1](that: PFolded[S1, T1, A1, V1]): PFolded[S1, T1, A1, V1] =
      new PFolded[S1, T1, A1, V1] {
        def foldMap[X: Monoid](s: S1)(f: A1 => X): X = self.foldMap(s)(f) |+| that.foldMap(s)(f)
      }
  }

  def compose[S, T, A, B, U, V](f: PFolded[A, B, U, V], g: PFolded[S, T, A, B]): PFolded[S, T, U, V] =
    new PFolded[S, T, U, V] {
      def foldMap[X: Monoid](s: S)(fux: U => X): X = g.foldMap(s)(f.foldMap(_)(fux))
    }
  final implicit def byFoldable[F[_], A, T, B](implicit F: Foldable[F]): PFolded[F[A], T, A, B] =
    new PFolded[F[A], T, A, B] {
      def foldMap[X: Monoid](fa: F[A])(f: A => X): X = F.foldMap(fa)(f)
    }

  trait Context extends PReduced.Context with PItems.Context with PDowncast.Context {
    override def algebra: Monoid[X]
    def default: X = algebra.empty
    override val functor: Applicative[F] = {
      implicit val alg = algebra
      Applicative[Constant[X, +*]]
    }
  }

  override def toGeneric[S, T, A, B](o: PFolded[S, T, A, B]): Optic[Context, S, T, A, B] =
    new Optic[Context, S, T, A, B] {
      def apply(c: Context)(p: A => Constant[c.X, B]): S => Constant[c.X, T] =
        s => Constant.Impl(o.foldMap(s)(a => p(a).value)(c.algebra))
    }
  override def fromGeneric[S, T, A, B](o: Optic[Context, S, T, A, B]): PFolded[S, T, A, B] =
    new PFolded[S, T, A, B] {
      def foldMap[Y: Monoid](s: S)(f: A => Y): Y =
        o(new Context {
          type X = Y
          override def algebra = Monoid[Y]
        })(a => Constant(f(a)))(s).value
    }
}
