/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import java.io.File
import Relations.Source
import Relations.SourceDependencies
import sbt.internal.util.Relation
import xsbti.api.{ InternalDependency, ExternalDependency, DependencyContext, Source => APISource }
import xsbti.api.DependencyContext._

/**
 * Provides mappings between source files, generated classes (products), and binaries.
 * Dependencies that are tracked include internal: a dependency on a source in the same compilation group (project),
 * external: a dependency on a source in another compilation group (tracked as the name of the class),
 * binary: a dependency on a class or jar file not generated by a source file in any tracked compilation group,
 * inherited: a dependency that resulted from a public template inheriting,
 * direct: any type of dependency, including inheritance.
 */
trait Relations {
  /** All sources _with at least one product_ . */
  def allSources: collection.Set[File]

  /** All products associated with sources. */
  def allProducts: collection.Set[File]

  /** All files that are recorded as a binary dependency of a source file.*/
  def allBinaryDeps: collection.Set[File]

  /** All files in this compilation group (project) that are recorded as a source dependency of a source file in this group.*/
  def allInternalSrcDeps: collection.Set[File]

  /** All files in another compilation group (project) that are recorded as a source dependency of a source file in this group.*/
  def allExternalDeps: collection.Set[String]

  /** Fully qualified names of classes generated from source file `src`. */
  def classNames(src: File): Set[String]

  /** Source files that generated a class with the given fully qualified `name`. This is typically a set containing a single file. */
  def definesClass(name: String): Set[File]

  /** The classes that were generated for source file `src`. */
  def products(src: File): Set[File]
  /** The source files that generated class file `prod`.  This is typically a set containing a single file. */
  def produced(prod: File): Set[File]

  /** The binary dependencies for the source file `src`. */
  def binaryDeps(src: File): Set[File]
  /** The source files that depend on binary file `dep`. */
  def usesBinary(dep: File): Set[File]

  /** Internal source dependencies for `src`.  This includes both direct and inherited dependencies.  */
  def internalSrcDeps(src: File): Set[File]
  /** Internal source files that depend on internal source `dep`.  This includes both direct and inherited dependencies.  */
  def usesInternalSrc(dep: File): Set[File]

  /** External source dependencies that internal source file `src` depends on.  This includes both direct and inherited dependencies.  */
  def externalDeps(src: File): Set[String]
  /** Internal source dependencies that depend on external source file `dep`.  This includes both direct and inherited dependencies.  */
  def usesExternal(dep: String): Set[File]

  private[inc] def usedNames(src: File): Set[String]

  /** Records internal source file `src` as generating class file `prod` with top-level class `name`. */
  @deprecated("Record all products using `addProducts`.", "0.13.8")
  def addProduct(src: File, prod: File, name: String): Relations

  /**
   * Records internal source file `src` as dependending on `dependsOn`. If this dependency is introduced
   * by an inheritance relation, `inherited` is set to true. Note that in this case, the dependency is
   * also registered as a direct dependency.
   */
  @deprecated("Record all external dependencies using `addExternalDeps`.", "0.13.8")
  def addExternalDep(src: File, dependsOn: String, inherited: Boolean): Relations

  /** Records internal source file `src` depending on a dependency binary dependency `dependsOn`.*/
  @deprecated("Record all binary dependencies using `addBinaryDeps`.", "0.13.8")
  def addBinaryDep(src: File, dependsOn: File): Relations

  /**
   * Records internal source file `src` as having direct dependencies on internal source files `directDependsOn`
   * and inheritance dependencies on `inheritedDependsOn`.  Everything in `inheritedDependsOn` must be included in `directDependsOn`;
   * this method does not automatically record direct dependencies like `addExternalDep` does.
   */
  @deprecated("Record all internal dependencies using `addInternalSrcDeps(File, Iterable[InternalDependencies])`.", "0.13.8")
  def addInternalSrcDeps(src: File, directDependsOn: Iterable[File], inheritedDependsOn: Iterable[File]): Relations

  /**
   * Records that the file `src` generates products `products`, has internal dependencies `internalDeps`,
   * has external dependencies `externalDeps` and binary dependencies `binaryDeps`.
   */
  def addSource(
    src: File,
    products: Iterable[(File, String)],
    internalDeps: Iterable[InternalDependency],
    externalDeps: Iterable[ExternalDependency],
    binaryDeps: Iterable[(File, String, Stamp)]
  ): Relations =
    addProducts(src, products).addInternalSrcDeps(src, internalDeps).addExternalDeps(src, externalDeps).addBinaryDeps(src, binaryDeps)

  /**
   * Records all the products `prods` generated by `src`
   */
  private[inc] def addProducts(src: File, prods: Iterable[(File, String)]): Relations

  /**
   * Records all the internal source dependencies `deps` of `src`
   */
  private[inc] def addInternalSrcDeps(src: File, deps: Iterable[InternalDependency]): Relations

  /**
   * Records all the external dependencies `deps` of `src`
   */
  private[inc] def addExternalDeps(src: File, deps: Iterable[ExternalDependency]): Relations

  /**
   * Records all the binary dependencies `deps` of `src`
   */
  private[inc] def addBinaryDeps(src: File, deps: Iterable[(File, String, Stamp)]): Relations

  private[inc] def addUsedName(src: File, name: String): Relations

  /** Concatenates the two relations. Acts naively, i.e., doesn't internalize external deps on added files. */
  def ++(o: Relations): Relations

  /** Drops all dependency mappings a->b where a is in `sources`. Acts naively, i.e., doesn't externalize internal deps on removed files. */
  def --(sources: Iterable[File]): Relations

  @deprecated("OK to remove in 0.14", "0.13.1")
  def groupBy[K](f: (File => K)): Map[K, Relations]

  /** The relation between internal sources and generated class files. */
  def srcProd: Relation[File, File]

  /** The dependency relation between internal sources and binaries. */
  def binaryDep: Relation[File, File]

  /** The dependency relation between internal sources.  This includes both direct and inherited dependencies.*/
  def internalSrcDep: Relation[File, File]

  /** The dependency relation between internal and external sources.  This includes both direct and inherited dependencies.*/
  def externalDep: Relation[File, String]

  /** All the internal dependencies */
  private[inc] def internalDependencies: InternalDependencies

  /** All the external dependencies */
  private[inc] def externalDependencies: ExternalDependencies

  /**
   * The source dependency relation between source files introduced by member reference.
   *
   * NOTE: All inheritance dependencies are included in this relation because in order to
   * inherit from a member you have to refer to it. If you check documentation of `inheritance`
   * you'll see that there's small oddity related to traits being the first parent of a
   * class/trait that results in additional parents being introduced due to normalization.
   * This relation properly accounts for that so the invariant that `memberRef` is a superset
   * of `inheritance` is preserved.
   */
  private[inc] def memberRef: SourceDependencies

  /**
   * The source dependency relation between source files introduced by inheritance.
   * The dependency by inheritance is introduced when a template (class or trait) mentions
   * a given type in a parent position.
   *
   * NOTE: Due to an oddity in how Scala's type checker works there's one unexpected dependency
   * on a class being introduced. An example illustrates the best the problem. Let's consider
   * the following structure:
   *
   * trait A extends B
   * trait B extends C
   * trait C extends D
   * class D
   *
   * We are interested in dependencies by inheritance of `A`. One would expect it to be just `B`
   * but the answer is `B` and `D`. The reason is because Scala's type checker performs a certain
   * normalization so the first parent of a type is a class. Therefore the example above is normalized
   * to the following form:
   *
   * trait A extends D with B
   * trait B extends D with C
   * trait C extends D
   * class D
   *
   * Therefore if you inherit from a trait you'll get an additional dependency on a class that is
   * resolved transitively. You should not rely on this behavior, though.
   *
   */
  private[inc] def inheritance: SourceDependencies

  /** The dependency relations between sources.  These include both direct and inherited dependencies.*/
  def direct: Source

  /** The inheritance dependency relations between sources.*/
  def publicInherited: Source

  /** The relation between a source file and the fully qualified names of classes generated from it.*/
  def classes: Relation[File, String]

  /**
   * Flag which indicates whether given Relations object supports operations needed by name hashing algorithm.
   *
   * At the moment the list includes the following operations:
   *
   *   - memberRef: SourceDependencies
   *   - inheritance: SourceDependencies
   *
   * The `memberRef` and `inheritance` implement a new style source dependency tracking. When this flag is
   * enabled access to `direct` and `publicInherited` relations is illegal and will cause runtime exception
   * being thrown. That is done as an optimization that prevents from storing two overlapping sets of
   * dependencies.
   *
   * Conversely, when `nameHashing` flag is disabled access to `memberRef` and `inheritance`
   * relations is illegal and will cause runtime exception being thrown.
   */
  private[inc] def nameHashing: Boolean
  /**
   * Relation between source files and _unqualified_ term and type names used in given source file.
   */
  private[inc] def names: Relation[File, String]

  /**
   * Lists of all the pairs (header, relation) that sbt knows of.
   * Used by TextAnalysisFormat to persist relations.
   * This cannot be stored as a Map because the order is important.
   */
  private[inc] def allRelations: List[(String, Relation[File, _])]
}

object Relations {

  /**
   * Represents all the relations that sbt knows of along with a way to recreate each
   * of their elements from their string representation.
   */
  private[inc] val existingRelations = {
    val string2File: String => File = new File(_)
    List(
      ("products", string2File),
      ("binary dependencies", string2File),
      ("direct source dependencies", string2File),
      ("direct external dependencies", identity[String] _),
      ("public inherited source dependencies", string2File),
      ("public inherited external dependencies", identity[String] _),
      ("member reference internal dependencies", string2File),
      ("member reference external dependencies", identity[String] _),
      ("inheritance internal dependencies", string2File),
      ("inheritance external dependencies", identity[String] _),
      ("class names", identity[String] _),
      ("used names", identity[String] _)
    )
  }
  /**
   * Reconstructs a Relations from a list of Relation
   * The order in which the relations are read matters and is defined by `existingRelations`.
   */
  def construct(nameHashing: Boolean, relations: List[Relation[_, _]]) =
    relations match {
      case p :: bin :: di :: de :: pii :: pie :: mri :: mre :: ii :: ie :: cn :: un :: Nil =>
        val srcProd = p.asInstanceOf[Relation[File, File]]
        val binaryDep = bin.asInstanceOf[Relation[File, File]]
        val directSrcDeps = makeSource(di.asInstanceOf[Relation[File, File]], de.asInstanceOf[Relation[File, String]])
        val publicInheritedSrcDeps = makeSource(pii.asInstanceOf[Relation[File, File]], pie.asInstanceOf[Relation[File, String]])
        val memberRefSrcDeps = makeSourceDependencies(mri.asInstanceOf[Relation[File, File]], mre.asInstanceOf[Relation[File, String]])
        val inheritanceSrcDeps = makeSourceDependencies(ii.asInstanceOf[Relation[File, File]], ie.asInstanceOf[Relation[File, String]])
        val classes = cn.asInstanceOf[Relation[File, String]]
        val names = un.asInstanceOf[Relation[File, String]]

        // we don't check for emptiness of publicInherited/inheritance relations because
        // we assume that invariant that says they are subsets of direct/memberRef holds
        assert(nameHashing || (memberRefSrcDeps == emptySourceDependencies), "When name hashing is disabled the `memberRef` relation should be empty.")
        assert(!nameHashing || (directSrcDeps == emptySource), "When name hashing is enabled the `direct` relation should be empty.")

        if (nameHashing) {
          val internal = InternalDependencies(Map(DependencyByMemberRef -> mri.asInstanceOf[Relation[File, File]], DependencyByInheritance -> ii.asInstanceOf[Relation[File, File]]))
          val external = ExternalDependencies(Map(DependencyByMemberRef -> mre.asInstanceOf[Relation[File, String]], DependencyByInheritance -> ie.asInstanceOf[Relation[File, String]]))
          Relations.make(srcProd, binaryDep, internal, external, classes, names)
        } else {
          assert(names.all.isEmpty, s"When `nameHashing` is disabled `names` relation should be empty: $names")
          Relations.make(srcProd, binaryDep, directSrcDeps, publicInheritedSrcDeps, classes)
        }
      case _ => throw new java.io.IOException(s"Expected to read ${existingRelations.length} relations but read ${relations.length}.")
    }

  /** Tracks internal and external source dependencies for a specific dependency type, such as direct or inherited.*/
  final class Source private[sbt] (val internal: Relation[File, File], val external: Relation[File, String]) {
    def addInternal(source: File, dependsOn: Iterable[File]): Source = new Source(internal + (source, dependsOn), external)
    @deprecated("Use addExternal(File, Iterable[String])", "0.13.8")
    def addExternal(source: File, dependsOn: String): Source = new Source(internal, external + (source, dependsOn))
    def addExternal(source: File, dependsOn: Iterable[String]): Source = new Source(internal, external + (source, dependsOn))
    /** Drops all dependency mappings from `sources`. Acts naively, i.e., doesn't externalize internal deps on removed files.*/
    def --(sources: Iterable[File]): Source = new Source(internal -- sources, external -- sources)
    def ++(o: Source): Source = new Source(internal ++ o.internal, external ++ o.external)

    @deprecated("Broken implementation. OK to remove in 0.14", "0.13.1")
    def groupBySource[K](f: File => K): Map[K, Source] = {

      val i = internal.groupBy { case (a, b) => f(a) }
      val e = external.groupBy { case (a, b) => f(a) }
      val pairs = for (k <- i.keySet ++ e.keySet) yield (k, new Source(getOrEmpty(i, k), getOrEmpty(e, k)))
      pairs.toMap
    }

    override def equals(other: Any) = other match {
      case o: Source => internal == o.internal && external == o.external
      case _         => false
    }

    override def hashCode = (internal, external).hashCode
  }

  /** Tracks internal and external source dependencies for a specific dependency type, such as direct or inherited.*/
  private[inc] final class SourceDependencies(val internal: Relation[File, File], val external: Relation[File, String]) {
    def addInternal(source: File, dependsOn: Iterable[File]): SourceDependencies = new SourceDependencies(internal + (source, dependsOn), external)
    @deprecated("Use addExternal(File, Iterable[String])", "0.13.8")
    def addExternal(source: File, dependsOn: String): SourceDependencies = new SourceDependencies(internal, external + (source, dependsOn))
    def addExternal(source: File, dependsOn: Iterable[String]): SourceDependencies = new SourceDependencies(internal, external + (source, dependsOn))
    /** Drops all dependency mappings from `sources`. Acts naively, i.e., doesn't externalize internal deps on removed files.*/
    def --(sources: Iterable[File]): SourceDependencies = new SourceDependencies(internal -- sources, external -- sources)
    def ++(o: SourceDependencies): SourceDependencies = new SourceDependencies(internal ++ o.internal, external ++ o.external)

    override def equals(other: Any) = other match {
      case o: SourceDependencies => internal == o.internal && external == o.external
      case _                     => false
    }

    override def hashCode = (internal, external).hashCode
  }

  private[sbt] def getOrEmpty[A, B, K](m: Map[K, Relation[A, B]], k: K): Relation[A, B] = m.getOrElse(k, Relation.empty)

  private[this] lazy val e = Relation.empty[File, File]
  private[this] lazy val estr = Relation.empty[File, String]
  private[this] lazy val es = new Source(e, estr)

  def emptySource: Source = es
  private[inc] lazy val emptySourceDependencies: SourceDependencies = new SourceDependencies(e, estr)
  def empty: Relations = empty(nameHashing = IncOptions.nameHashingDefault)
  private[inc] def empty(nameHashing: Boolean): Relations =
    if (nameHashing)
      new MRelationsNameHashing(e, e, InternalDependencies.empty, ExternalDependencies.empty, estr, estr)
    else
      new MRelationsDefaultImpl(e, e, es, es, estr)

  def make(srcProd: Relation[File, File], binaryDep: Relation[File, File], direct: Source, publicInherited: Source, classes: Relation[File, String]): Relations =
    new MRelationsDefaultImpl(srcProd, binaryDep, direct = direct, publicInherited = publicInherited, classes)

  private[inc] def make(srcProd: Relation[File, File], binaryDep: Relation[File, File],
    internalDependencies: InternalDependencies, externalDependencies: ExternalDependencies,
    classes: Relation[File, String], names: Relation[File, String]): Relations =
    new MRelationsNameHashing(srcProd, binaryDep, internalDependencies = internalDependencies, externalDependencies = externalDependencies, classes, names)
  def makeSource(internal: Relation[File, File], external: Relation[File, String]): Source = new Source(internal, external)
  private[inc] def makeSourceDependencies(internal: Relation[File, File], external: Relation[File, String]): SourceDependencies = new SourceDependencies(internal, external)
}

private object DependencyCollection {
  /**
   * Combine `m1` and `m2` such that the result contains all the dependencies they represent.
   * `m1` is expected to be smaller than `m2`.
   */
  def joinMaps[T](m1: Map[DependencyContext, Relation[File, T]], m2: Map[DependencyContext, Relation[File, T]]) =
    m1.foldLeft(m2) { case (tmp, (key, values)) => tmp.updated(key, tmp.getOrElse(key, Relation.empty) ++ values) }
}

private object InternalDependencies {
  /**
   * Constructs an empty `InteralDependencies`
   */
  def empty = InternalDependencies(Map.empty)
}

private case class InternalDependencies(dependencies: Map[DependencyContext, Relation[File, File]]) {
  /**
   * Adds `dep` to the dependencies
   */
  def +(dep: InternalDependency): InternalDependencies =
    InternalDependencies(dependencies.updated(dep.context, dependencies.getOrElse(dep.context, Relation.empty) + (dep.sourceFile, dep.targetFile)))

  /**
   * Adds all `deps` to the dependencies
   */
  def ++(deps: Iterable[InternalDependency]): InternalDependencies = deps.foldLeft(this)(_ + _)
  def ++(deps: InternalDependencies): InternalDependencies = InternalDependencies(DependencyCollection.joinMaps(dependencies, deps.dependencies))

  /**
   * Removes all dependencies from `sources` to another file from the dependencies
   */
  def --(sources: Iterable[File]): InternalDependencies = InternalDependencies(dependencies.mapValues(_ -- sources).filter(_._2.size > 0))
}

private object ExternalDependencies {
  /**
   * Constructs an empty `ExternalDependencies`
   */
  def empty = ExternalDependencies(Map.empty)
}

private case class ExternalDependencies(dependencies: Map[DependencyContext, Relation[File, String]]) {
  /**
   * Adds `dep` to the dependencies
   */
  def +(dep: ExternalDependency): ExternalDependencies = ExternalDependencies(dependencies.updated(dep.context, dependencies.getOrElse(dep.context, Relation.empty) + (dep.sourceFile, dep.targetClassName)))

  /**
   * Adds all `deps` to the dependencies
   */
  def ++(deps: Iterable[ExternalDependency]): ExternalDependencies = deps.foldLeft(this)(_ + _)
  def ++(deps: ExternalDependencies): ExternalDependencies = ExternalDependencies(DependencyCollection.joinMaps(dependencies, deps.dependencies))

  /**
   * Removes all dependencies from `sources` to another file from the dependencies
   */
  def --(sources: Iterable[File]): ExternalDependencies = ExternalDependencies(dependencies.mapValues(_ -- sources).filter(_._2.size > 0))
}

/**
 * An abstract class that contains common functionality inherited by two implementations of Relations trait.
 *
 * A little note why we have two different implementations of Relations trait. This is needed for the time
 * being when we are slowly migrating to the new invalidation algorithm called "name hashing" which requires
 * some subtle changes to dependency tracking. For some time we plan to keep both algorithms side-by-side
 * and have a runtime switch which allows to pick one. So we need logic for both old and new dependency
 * tracking to be available. That's exactly what two subclasses of MRelationsCommon implement. Once name
 * hashing is proven to be stable and reliable we'll phase out the old algorithm and the old dependency tracking
 * logic.
 *
 * `srcProd` is a relation between a source file and a product: (source, product).
 * Note that some source files may not have a product and will not be included in this relation.
 *
 * `binaryDeps` is a relation between a source file and a binary dependency: (source, binary dependency).
 *   This only includes dependencies on classes and jars that do not have a corresponding source/API to track instead.
 *   A class or jar with a corresponding source should only be tracked in one of the source dependency relations.
 *
 * `classes` is a relation between a source file and its generated fully-qualified class names.
 */
private abstract class MRelationsCommon(val srcProd: Relation[File, File], val binaryDep: Relation[File, File],
  val classes: Relation[File, String]) extends Relations {
  def allSources: collection.Set[File] = srcProd._1s

  def allProducts: collection.Set[File] = srcProd._2s
  def allBinaryDeps: collection.Set[File] = binaryDep._2s
  def allInternalSrcDeps: collection.Set[File] = internalSrcDep._2s
  def allExternalDeps: collection.Set[String] = externalDep._2s

  def classNames(src: File): Set[String] = classes.forward(src)
  def definesClass(name: String): Set[File] = classes.reverse(name)

  def products(src: File): Set[File] = srcProd.forward(src)
  def produced(prod: File): Set[File] = srcProd.reverse(prod)

  def binaryDeps(src: File): Set[File] = binaryDep.forward(src)
  def usesBinary(dep: File): Set[File] = binaryDep.reverse(dep)

  def internalSrcDeps(src: File): Set[File] = internalSrcDep.forward(src)
  def usesInternalSrc(dep: File): Set[File] = internalSrcDep.reverse(dep)

  def externalDeps(src: File): Set[String] = externalDep.forward(src)
  def usesExternal(dep: String): Set[File] = externalDep.reverse(dep)

  def usedNames(src: File): Set[String] = names.forward(src)

  /** Making large Relations a little readable. */
  private val userDir = sys.props("user.dir").stripSuffix("/") + "/"
  private def nocwd(s: String) = s stripPrefix userDir
  private def line_s(kv: (Any, Any)) = "    " + nocwd("" + kv._1) + " -> " + nocwd("" + kv._2) + "\n"
  protected def relation_s(r: Relation[_, _]) = (
    if (r.forwardMap.isEmpty) "Relation [ ]"
    else (r.all.toSeq.map(line_s).sorted) mkString ("Relation [\n", "", "]")
  )
}

/**
 * This class implements Relations trait with support for tracking of `direct` and `publicInherited` source
 * dependencies. Therefore this class preserves the "old" (from sbt 0.13.0) dependency tracking logic and it's
 * a default implementation.
 *
 * `direct` defines relations for dependencies between internal and external source dependencies.  It includes all types of
 *    dependencies, including inheritance.
 *
 * `publicInherited` defines relations for internal and external source dependencies, only including dependencies
 *    introduced by inheritance.
 *
 */
private class MRelationsDefaultImpl(srcProd: Relation[File, File], binaryDep: Relation[File, File],
  // direct should include everything in inherited
  val direct: Source, val publicInherited: Source,
  classes: Relation[File, String]) extends MRelationsCommon(srcProd, binaryDep, classes) {
  def internalSrcDep: Relation[File, File] = direct.internal
  def externalDep: Relation[File, String] = direct.external

  def nameHashing: Boolean = false

  def memberRef: SourceDependencies =
    throw new UnsupportedOperationException("The `memberRef` source dependencies relation is not supported " +
      "when `nameHashing` flag is disabled.")
  def inheritance: SourceDependencies =
    throw new UnsupportedOperationException("The `memberRef` source dependencies relation is not supported " +
      "when `nameHashing` flag is disabled.")

  def addProduct(src: File, prod: File, name: String): Relations =
    new MRelationsDefaultImpl(srcProd + (src, prod), binaryDep, direct = direct,
      publicInherited = publicInherited, classes + (src, name))

  def addProducts(src: File, products: Iterable[(File, String)]): Relations =
    new MRelationsDefaultImpl(srcProd ++ products.map(p => (src, p._1)), binaryDep, direct = direct,
      publicInherited = publicInherited, classes ++ products.map(p => (src, p._2)))

  def addInternalSrcDeps(src: File, deps: Iterable[InternalDependency]) = {
    val depsByInheritance = deps.collect { case i: InternalDependency if i.context == DependencyByInheritance => i.targetFile }

    val newD = direct.addInternal(src, deps.map(_.targetFile))
    val newI = publicInherited.addInternal(src, depsByInheritance)

    new MRelationsDefaultImpl(srcProd, binaryDep, direct = newD, publicInherited = newI, classes)
  }

  def addInternalSrcDeps(src: File, directDependsOn: Iterable[File], inheritedDependsOn: Iterable[File]): Relations = {
    val directDeps = directDependsOn.map(d => new InternalDependency(src, d, DependencyByMemberRef))
    val inheritedDeps = inheritedDependsOn.map(d => new InternalDependency(src, d, DependencyByInheritance))
    addInternalSrcDeps(src, directDeps ++ inheritedDeps)
  }

  def addExternalDeps(src: File, deps: Iterable[ExternalDependency]) = {
    val depsByInheritance = deps.collect { case e: ExternalDependency if e.context == DependencyByInheritance => e.targetClassName }

    val newD = direct.addExternal(src, deps.map(_.targetClassName))
    val newI = publicInherited.addExternal(src, depsByInheritance)

    new MRelationsDefaultImpl(srcProd, binaryDep, direct = newD, publicInherited = newI, classes)
  }

  def addExternalDep(src: File, dependsOn: String, inherited: Boolean): Relations = {
    val newI = if (inherited) publicInherited.addExternal(src, dependsOn :: Nil) else publicInherited
    val newD = direct.addExternal(src, dependsOn :: Nil)
    new MRelationsDefaultImpl(srcProd, binaryDep, direct = newD, publicInherited = newI, classes)
  }

  def addBinaryDeps(src: File, deps: Iterable[(File, String, Stamp)]) =
    new MRelationsDefaultImpl(srcProd, binaryDep + (src, deps.map(_._1)), direct, publicInherited, classes)

  def addBinaryDep(src: File, dependsOn: File): Relations =
    new MRelationsDefaultImpl(srcProd, binaryDep + (src, dependsOn), direct = direct,
      publicInherited = publicInherited, classes)

  def names: Relation[File, String] =
    throw new UnsupportedOperationException("Tracking of used names is not supported " +
      "when `nameHashing` is disabled.")

  def addUsedName(src: File, name: String): Relations =
    throw new UnsupportedOperationException("Tracking of used names is not supported " +
      "when `nameHashing` is disabled.")

  override def externalDependencies: ExternalDependencies = ExternalDependencies(Map(DependencyByMemberRef -> direct.external, DependencyByInheritance -> publicInherited.external))
  override def internalDependencies: InternalDependencies = InternalDependencies(Map(DependencyByMemberRef -> direct.internal, DependencyByInheritance -> publicInherited.internal))

  def ++(o: Relations): Relations = {
    if (nameHashing != o.nameHashing)
      throw new UnsupportedOperationException("The `++` operation is not supported for relations " +
        "with different values of `nameHashing` flag.")
    new MRelationsDefaultImpl(srcProd ++ o.srcProd, binaryDep ++ o.binaryDep, direct ++ o.direct,
      publicInherited ++ o.publicInherited, classes ++ o.classes)
  }
  def --(sources: Iterable[File]) =
    new MRelationsDefaultImpl(srcProd -- sources, binaryDep -- sources, direct = direct -- sources,
      publicInherited = publicInherited -- sources, classes -- sources)

  @deprecated("Broken implementation. OK to remove in 0.14", "0.13.1")
  def groupBy[K](f: File => K): Map[K, Relations] =
    {
      type MapRel[T] = Map[K, Relation[File, T]]
      def outerJoin(srcProdMap: MapRel[File], binaryDepMap: MapRel[File], direct: Map[K, Source],
        inherited: Map[K, Source], classesMap: MapRel[String],
        namesMap: MapRel[String]): Map[K, Relations] =
        {
          def kRelations(k: K): Relations = {
            def get[T](m: Map[K, Relation[File, T]]) = Relations.getOrEmpty(m, k)
            def getSrc(m: Map[K, Source]): Source = m.getOrElse(k, Relations.emptySource)
            def getSrcDeps(m: Map[K, SourceDependencies]): SourceDependencies =
              m.getOrElse(k, Relations.emptySourceDependencies)
            new MRelationsDefaultImpl(get(srcProdMap), get(binaryDepMap), getSrc(direct), getSrc(inherited),
              get(classesMap))
          }
          val keys = (srcProdMap.keySet ++ binaryDepMap.keySet ++ direct.keySet ++ inherited.keySet ++ classesMap.keySet).toList
          Map(keys.map((k: K) => (k, kRelations(k))): _*)
        }

      def f1[B](item: (File, B)): K = f(item._1)

      outerJoin(srcProd.groupBy(f1), binaryDep.groupBy(f1), direct.groupBySource(f),
        publicInherited.groupBySource(f), classes.groupBy(f1), names.groupBy(f1))
    }

  override def equals(other: Any) = other match {
    case o: MRelationsDefaultImpl =>
      srcProd == o.srcProd && binaryDep == o.binaryDep && direct == o.direct &&
        publicInherited == o.publicInherited && classes == o.classes
    case _ => false
  }

  def allRelations = {
    val rels = List(
      srcProd,
      binaryDep,
      direct.internal,
      direct.external,
      publicInherited.internal,
      publicInherited.external,
      Relations.emptySourceDependencies.internal, // Default implementation doesn't provide memberRef source deps
      Relations.emptySourceDependencies.external, // Default implementation doesn't provide memberRef source deps
      Relations.emptySourceDependencies.internal, // Default implementation doesn't provide inheritance source deps
      Relations.emptySourceDependencies.external, // Default implementation doesn't provide inheritance source deps
      classes,
      Relation.empty[File, String]
    ) // Default implementation doesn't provide used names relation
    Relations.existingRelations map (_._1) zip rels
  }

  override def hashCode = (srcProd :: binaryDep :: direct :: publicInherited :: classes :: Nil).hashCode

  override def toString = (
    """
	  |Relations:
	  |  products: %s
	  |  bin deps: %s
	  |  src deps: %s
	  |  ext deps: %s
	  |  class names: %s
	  """.trim.stripMargin.format(List(srcProd, binaryDep, internalSrcDep, externalDep, classes) map relation_s: _*)
  )
}

/**
 * This class implements Relations trait with support for tracking of `memberRef` and `inheritance` source
 * dependencies. Therefore this class implements the new (compared to sbt 0.13.0) dependency tracking logic
 * needed by the name hashing invalidation algorithm.
 */
private class MRelationsNameHashing(srcProd: Relation[File, File], binaryDep: Relation[File, File],
  val internalDependencies: InternalDependencies,
  val externalDependencies: ExternalDependencies,
  classes: Relation[File, String],
  val names: Relation[File, String]) extends MRelationsCommon(srcProd, binaryDep, classes) {
  def direct: Source =
    throw new UnsupportedOperationException("The `direct` source dependencies relation is not supported " +
      "when `nameHashing` flag is disabled.")
  def publicInherited: Source =
    throw new UnsupportedOperationException("The `publicInherited` source dependencies relation is not supported " +
      "when `nameHashing` flag is disabled.")

  val nameHashing: Boolean = true

  def internalSrcDep: Relation[File, File] = memberRef.internal
  def externalDep: Relation[File, String] = memberRef.external

  def addProduct(src: File, prod: File, name: String): Relations =
    new MRelationsNameHashing(srcProd + (src, prod), binaryDep, internalDependencies = internalDependencies,
      externalDependencies = externalDependencies, classes + (src, name), names = names)

  def addProducts(src: File, products: Iterable[(File, String)]): Relations =
    new MRelationsNameHashing(srcProd ++ products.map(p => (src, p._1)), binaryDep,
      internalDependencies = internalDependencies, externalDependencies = externalDependencies,
      classes ++ products.map(p => (src, p._2)), names = names)

  def addInternalSrcDeps(src: File, deps: Iterable[InternalDependency]) =
    new MRelationsNameHashing(srcProd, binaryDep, internalDependencies = internalDependencies ++ deps,
      externalDependencies = externalDependencies, classes, names)

  def addInternalSrcDeps(src: File, dependsOn: Iterable[File], inherited: Iterable[File]): Relations = {
    val memberRefDeps = dependsOn.map(new InternalDependency(src, _, DependencyByMemberRef))
    val inheritedDeps = inherited.map(new InternalDependency(src, _, DependencyByInheritance))
    addInternalSrcDeps(src, memberRefDeps ++ inheritedDeps)
  }

  def addExternalDeps(src: File, deps: Iterable[ExternalDependency]) =
    new MRelationsNameHashing(srcProd, binaryDep, internalDependencies = internalDependencies,
      externalDependencies = externalDependencies ++ deps, classes, names)

  def addExternalDep(src: File, dependsOn: String, inherited: Boolean): Relations =
    throw new UnsupportedOperationException("This method is not supported when `nameHashing` flag is enabled.")

  def addBinaryDeps(src: File, deps: Iterable[(File, String, Stamp)]) =
    new MRelationsNameHashing(srcProd, binaryDep + (src, deps.map(_._1)), internalDependencies = internalDependencies,
      externalDependencies = externalDependencies, classes, names)

  def addBinaryDep(src: File, dependsOn: File): Relations =
    new MRelationsNameHashing(srcProd, binaryDep + (src, dependsOn), internalDependencies = internalDependencies,
      externalDependencies = externalDependencies, classes, names = names)

  def addUsedName(src: File, name: String): Relations =
    new MRelationsNameHashing(srcProd, binaryDep, internalDependencies = internalDependencies,
      externalDependencies = externalDependencies, classes, names = names + (src, name))

  override def inheritance: SourceDependencies =
    new SourceDependencies(internalDependencies.dependencies.getOrElse(DependencyByInheritance, Relation.empty), externalDependencies.dependencies.getOrElse(DependencyByInheritance, Relation.empty))
  override def memberRef: SourceDependencies =
    new SourceDependencies(internalDependencies.dependencies.getOrElse(DependencyByMemberRef, Relation.empty), externalDependencies.dependencies.getOrElse(DependencyByMemberRef, Relation.empty))

  def ++(o: Relations): Relations = {
    if (!o.nameHashing)
      throw new UnsupportedOperationException("The `++` operation is not supported for relations " +
        "with different values of `nameHashing` flag.")
    new MRelationsNameHashing(srcProd ++ o.srcProd, binaryDep ++ o.binaryDep,
      internalDependencies = internalDependencies ++ o.internalDependencies, externalDependencies = externalDependencies ++ o.externalDependencies,
      classes ++ o.classes, names = names ++ o.names)
  }
  def --(sources: Iterable[File]) =
    new MRelationsNameHashing(srcProd -- sources, binaryDep -- sources,
      internalDependencies = internalDependencies -- sources, externalDependencies = externalDependencies -- sources, classes -- sources,
      names = names -- sources)

  def groupBy[K](f: File => K): Map[K, Relations] = {
    throw new UnsupportedOperationException("Merging of Analyses that have" +
      "`relations.nameHashing` set to `true` is not supported.")
  }

  override def equals(other: Any) = other match {
    case o: MRelationsNameHashing =>
      srcProd == o.srcProd && binaryDep == o.binaryDep && memberRef == o.memberRef &&
        inheritance == o.inheritance && classes == o.classes
    case _ => false
  }

  def allRelations = {
    val rels = List(
      srcProd,
      binaryDep,
      Relations.emptySource.internal, // NameHashing doesn't provide direct dependencies
      Relations.emptySource.external, // NameHashing doesn't provide direct dependencies
      Relations.emptySource.internal, // NameHashing doesn't provide public inherited dependencies
      Relations.emptySource.external, // NameHashing doesn't provide public inherited dependencies
      memberRef.internal,
      memberRef.external,
      inheritance.internal,
      inheritance.external,
      classes,
      names
    )
    Relations.existingRelations map (_._1) zip rels
  }

  override def hashCode = (srcProd :: binaryDep :: memberRef :: inheritance :: classes :: Nil).hashCode

  override def toString = (
    """
	  |Relations (with name hashing enabled):
	  |  products: %s
	  |  bin deps: %s
	  |  src deps: %s
	  |  ext deps: %s
	  |  class names: %s
	  |  used names: %s
	  """.trim.stripMargin.format(List(srcProd, binaryDep, internalSrcDep, externalDep, classes, names) map relation_s: _*)
  )

}
