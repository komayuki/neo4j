/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compatibility.v3_3

import java.lang.reflect.Constructor

import org.neo4j.cypher.internal.compatibility.v3_3.SemanticTableConverter.ExpressionMapping3To4
import org.neo4j.cypher.internal.frontend.v3_3.ast.{Expression => ExpressionV3_3}
import org.neo4j.cypher.internal.frontend.v3_3.{SemanticDirection => SemanticDirectionV3_3, ast => astV3_3, symbols => symbolsV3_3, InputPosition => InputPositionV3_3}
import org.neo4j.cypher.internal.frontend.{v3_3 => frontendV3_3}
import org.neo4j.cypher.internal.ir.v3_3.{IdName => IdNameV3_3}
import org.neo4j.cypher.internal.ir.v3_4.{PlannerQuery, IdName => IdNameV3_4}
import org.neo4j.cypher.internal.ir.{v3_3 => irV3_3, v3_4 => irV3_4}
import org.neo4j.cypher.internal.util.v3_4.Rewritable.{DuplicatableProduct, RewritableAny}
import org.neo4j.cypher.internal.util.v3_4.{symbols => symbolsV3_4, _}
import org.neo4j.cypher.internal.util.{v3_4 => utilV3_4}
import org.neo4j.cypher.internal.v3_3.logical.plans.{LogicalPlan => LogicalPlanV3_3}
import org.neo4j.cypher.internal.v3_3.logical.{plans => plansV3_3}
import org.neo4j.cypher.internal.v3_4.expressions.{Expression => ExpressionV3_4}
import org.neo4j.cypher.internal.v3_4.logical.plans.{LogicalPlan => LogicalPlanV3_4}
import org.neo4j.cypher.internal.v3_4.logical.{plans => plansV3_4}
import org.neo4j.cypher.internal.v3_4.{expressions => expressionsV3_4}
import org.neo4j.cypher.internal.compiler.{v3_3 => compilerV3_3}

import scala.collection.mutable
import scala.collection.mutable.{HashMap => MutableHashMap}
import scala.util.{Failure, Success, Try}

object LogicalPlanConverter {

  type MutableExpressionMapping3To4 = mutable.Map[(ExpressionV3_3, InputPositionV3_3), ExpressionV3_4]

  private class LogicalPlanRewriter(val expressionMap: MutableExpressionMapping3To4 = new mutable.HashMap[(ExpressionV3_3, InputPositionV3_3), ExpressionV3_4],
                                    val isImportant: ExpressionV3_3 => Boolean = _ => true)
    extends RewriterWithArgs {

    override def apply(v1: (AnyRef, Seq[AnyRef])): AnyRef = rewriter.apply(v1)

    private val rewriter: RewriterWithArgs = bottomUpWithArgs { before =>
      val rewritten = RewriterWithArgs.lift {
        case (plan: plansV3_3.Argument, children: Seq[AnyRef]) =>
          plansV3_4.Argument(children.head.asInstanceOf[Set[IdNameV3_4]])(new PlannerQueryWrapper(plan.solved))
        case (plan: plansV3_3.SingleRow, _) =>
          plansV3_4.Argument()(new PlannerQueryWrapper(plan.solved))
        case (_: plansV3_3.ProduceResult, children: Seq[AnyRef]) =>
          plansV3_4.ProduceResult(source = children(1).asInstanceOf[LogicalPlanV3_4],
            columns = children(0).asInstanceOf[Seq[String]])
        case (plan: plansV3_3.TriadicSelection, children: Seq[AnyRef]) =>
          plansV3_4.TriadicSelection(left = children(1).asInstanceOf[LogicalPlanV3_4],
            right = children(5).asInstanceOf[LogicalPlanV3_4],
            positivePredicate = children(0).asInstanceOf[Boolean],
            sourceId = children(2).asInstanceOf[IdNameV3_4],
            seenId = children(3).asInstanceOf[IdNameV3_4],
            targetId = children(4).asInstanceOf[IdNameV3_4])(new PlannerQueryWrapper(plan.solved))
        case (plan: plansV3_3.LogicalPlan, children: Seq[AnyRef]) =>
          convertVersion("v3_3", "v3_4", plan, children, new PlannerQueryWrapper(plan.solved), classOf[PlannerQuery])

        case (inp: astV3_3.InvalidNodePattern, children: Seq[AnyRef]) =>
          new expressionsV3_4.InvalidNodePattern(children.head.asInstanceOf[Option[expressionsV3_4.Variable]].get)(helpers.as3_4(inp.position))
        case (mp: astV3_3.MapProjection, children: Seq[AnyRef]) =>
          expressionsV3_4.MapProjection(children(0).asInstanceOf[expressionsV3_4.Variable],
            children(1).asInstanceOf[Seq[expressionsV3_4.MapProjectionElement]])(helpers.as3_4(mp.position))

        case (item@(_: compilerV3_3.ast.PrefixSeekRangeWrapper |
                    _: compilerV3_3.ast.InequalitySeekRangeWrapper |
                    _: compilerV3_3.ast.NestedPlanExpression |
                    _: compilerV3_3.ast.ResolvedFunctionInvocation), children: Seq[AnyRef]) =>
          convertVersion("compiler.v3_3.ast", "v3_4.logical.plans", item, children, helpers.as3_4(item.asInstanceOf[astV3_3.ASTNode].position), classOf[InputPosition])
        case (item@(_: astV3_3.rewriters.DesugaredMapProjection | _: astV3_3.ProcedureResultItem | _: plansV3_3.ResolvedCall), children: Seq[AnyRef]) =>
          convertVersion("v3_3", "v3_4", item, children, helpers.as3_4(item.asInstanceOf[astV3_3.ASTNode].position), classOf[InputPosition])
        case (expressionV3_3: astV3_3.ASTNode, children: Seq[AnyRef]) =>
          convertVersion("frontend.v3_3.ast", "v3_4.expressions", expressionV3_3, children, helpers.as3_4(expressionV3_3.position), classOf[InputPosition])
        case (IdNameV3_3(name), _) => IdNameV3_4(name)

        case (symbolsV3_3.CTAny, _) => symbolsV3_4.CTAny
        case (symbolsV3_3.CTBoolean, _) => symbolsV3_4.CTBoolean
        case (symbolsV3_3.CTFloat, _) => symbolsV3_4.CTFloat
        case (symbolsV3_3.CTGeometry, _) => symbolsV3_4.CTGeometry
        case (symbolsV3_3.CTGraphRef, _) => symbolsV3_4.CTGraphRef
        case (symbolsV3_3.CTInteger, _) => symbolsV3_4.CTInteger
        case (symbolsV3_3.ListType(_), children: Seq[AnyRef]) => symbolsV3_4.CTList(children.head.asInstanceOf[symbolsV3_4.CypherType])
        case (symbolsV3_3.CTMap, _) => symbolsV3_4.CTMap
        case (symbolsV3_3.CTNode, _) => symbolsV3_4.CTNode
        case (symbolsV3_3.CTNumber, _) => symbolsV3_4.CTNumber
        case (symbolsV3_3.CTPath, _) => symbolsV3_4.CTPath
        case (symbolsV3_3.CTPoint, _) => symbolsV3_4.CTPoint
        case (symbolsV3_3.CTRelationship, _) => symbolsV3_4.CTRelationship
        case (symbolsV3_3.CTString, _) => symbolsV3_4.CTString

        case (SemanticDirectionV3_3.BOTH, _) => expressionsV3_4.SemanticDirection.BOTH
        case (SemanticDirectionV3_3.INCOMING, _) => expressionsV3_4.SemanticDirection.INCOMING
        case (SemanticDirectionV3_3.OUTGOING, _) => expressionsV3_4.SemanticDirection.OUTGOING

        case (irV3_3.SimplePatternLength, _) => irV3_4.SimplePatternLength

        case (plansV3_3.IncludeTies, _) => plansV3_4.IncludeTies
        case (plansV3_3.DoNotIncludeTies, _) => plansV3_4.DoNotIncludeTies

        case (irV3_3.HasHeaders, _) => irV3_4.HasHeaders
        case (irV3_3.NoHeaders, _) => irV3_4.NoHeaders

        case (plansV3_3.ExpandAll, _) => plansV3_4.ExpandAll
        case (plansV3_3.ExpandInto, _) => plansV3_4.ExpandInto

        case (_: frontendV3_3.ExhaustiveShortestPathForbiddenException, _) => new utilV3_4.ExhaustiveShortestPathForbiddenException

        case (spp: irV3_3.ShortestPathPattern, children: Seq[AnyRef]) =>
          val sp3_4 = convertASTNode[expressionsV3_4.ShortestPaths](spp.expr, expressionMap, isImportant)
          irV3_4.ShortestPathPattern(children(0).asInstanceOf[Option[IdNameV3_4]], children(1).asInstanceOf[irV3_4.PatternRelationship], children(2).asInstanceOf[Boolean])(sp3_4)
        case (astV3_3.NilPathStep, _) => expressionsV3_4.NilPathStep
        case (item@(_: astV3_3.PathStep | _: astV3_3.NameToken[_]), children: Seq[AnyRef]) =>
          convertVersion("frontend.v3_3.ast", "v3_4.expressions", item, children)
        case (nameId: frontendV3_3.NameId, children: Seq[AnyRef]) =>
          convertVersion("frontend.v3_3", "util.v3_4", nameId, children)
        case (frontendV3_3.helpers.Fby(head, tail), children: Seq[AnyRef]) => utilV3_4.Fby(children(0), children(1).asInstanceOf[utilV3_4.NonEmptyList[_]])
        case (frontendV3_3.helpers.Last(head), children: Seq[AnyRef]) => utilV3_4.Last(children(0))
        case (item@(_: plansV3_3.CypherValue |
                    _: plansV3_3.QualifiedName |
                    _: plansV3_3.FieldSignature |
                    _: plansV3_3.ProcedureAccessMode |
                    _: plansV3_3.ProcedureSignature |
                    _: plansV3_3.UserFunctionSignature |
                    _: plansV3_3.QueryExpression[_] |
                    _: plansV3_3.SeekableArgs |
                    _: irV3_3.PatternRelationship |
                    _: irV3_3.VarPatternLength |
                    _: plansV3_3.ColumnOrder), children: Seq[AnyRef]) =>
          convertVersion("v3_3", "v3_4", item, children)
        case (item: frontendV3_3.Bound[_], children: Seq[AnyRef]) =>
          convertVersion("frontend.v3_3", "v3_4.logical.plans", item, children)
        case (item: compilerV3_3.SeekRange[_], children: Seq[AnyRef]) =>
          convertVersion("compiler.v3_3", "v3_4.logical.plans", item, children)

        case (_: List[_], children: Seq[AnyRef]) => children.toList
        case (_: Seq[_], children: Seq[AnyRef]) => children.toIndexedSeq
        case (_: Set[_], children: Seq[AnyRef]) => children.toSet
        case (_: Map[_, _], children: Seq[AnyRef]) => Map(children.map(_.asInstanceOf[(_, _)]): _*)
        case (None, _) => None
        case (p: Product, children: Seq[AnyRef]) => new DuplicatableProduct(p).copyConstructor.invoke(p, children: _*)
      }.apply(before)
      before._1 match {
        case plan: LogicalPlanV3_3 =>
          try {
            rewritten.asInstanceOf[LogicalPlanV3_4].setIdTo(helpers.as3_4(plan.assignedId))
          } catch {
            case (e:frontendV3_3.InternalException) =>
              // ProcedureOrSchema plans have no assigned IDs. That's ok.
          }
        // Save Mapping from 3.3 expression to 3.4 expression
        case e: ExpressionV3_3 if isImportant(e) => expressionMap += (((e, e.position), rewritten.asInstanceOf[ExpressionV3_4]))
        case _ =>
      }
      rewritten
    }
  }

  def convertLogicalPlan[T <: LogicalPlanV3_4](logicalPlan: LogicalPlanV3_3,
                                               isImportant: ExpressionV3_3 => Boolean = _ => true): (LogicalPlanV3_4, ExpressionMapping3To4) = {
    val rewriter = new LogicalPlanRewriter(isImportant = isImportant)
    val planV3_4 = new RewritableAny[LogicalPlanV3_3](logicalPlan).rewrite(rewriter, Seq.empty).asInstanceOf[T]
    (planV3_4, rewriter.expressionMap.toMap)
  }

  private[v3_3] def convertExpression[T <: ExpressionV3_4](expression: ExpressionV3_3): T = {
    new RewritableAny[ExpressionV3_3](expression).rewrite(new LogicalPlanRewriter, Seq.empty).asInstanceOf[T]
  }

  private def convertASTNode[T <: utilV3_4.ASTNode](ast: astV3_3.ASTNode,
                                                    expressionMap: MutableExpressionMapping3To4,
                                                    isImportant: ExpressionV3_3 => Boolean): T = {
    new RewritableAny[astV3_3.ASTNode](ast).rewrite(new LogicalPlanRewriter(expressionMap, isImportant), Seq.empty).asInstanceOf[T]
  }

  private val constructors = new ThreadLocal[MutableHashMap[(String, String, String), Constructor[_]]]() {
    override def initialValue: MutableHashMap[(String, String, String), Constructor[_]] =
      new MutableHashMap[(String, String, String), Constructor[_]]
  }

  private def getConstructor(classNameV3_3: String, oldPackage: String, newPackage: String): Constructor[_] = {
    constructors.get.getOrElseUpdate((classNameV3_3, oldPackage, newPackage), {
      assert(classNameV3_3.contains(oldPackage), s"wrong 3.3 package name given. $classNameV3_3 does not contain $oldPackage")
      val classNameV3_4 = classNameV3_3.replace(oldPackage, newPackage)
      Try(Class.forName(classNameV3_4)).map(_.getConstructors.head) match {
        case Success(c) => c
        case Failure(e: ClassNotFoundException) => throw new InternalException(
          s"Failed trying to rewrite $classNameV3_3 - 3.4 class not found ($classNameV3_4)", e)
        case Failure(e: NoSuchElementException) => throw new InternalException(
          s"Failed trying to rewrite $classNameV3_3 - this class does not have a constructor", e)
      }
    })
  }

  private def convertVersion(oldPackage: String, newPackage: String, thing: AnyRef, children: Seq[AnyRef], extraArg: AnyRef = null, assignableClazzForArg: Class[_] = null): AnyRef = {
    val thingClass = thing.getClass
    val classNameV3_3 = thingClass.getName
    val constructor = getConstructor(classNameV3_3, oldPackage, newPackage)

    val params = constructor.getParameterTypes
    val args = children.toVector
    val ctorArgs = if (params.length == args.length + 1 && params.last.isAssignableFrom(assignableClazzForArg)) args :+ extraArg else args

    Try(constructor.newInstance(ctorArgs: _*).asInstanceOf[AnyRef]) match {
      case Success(i) => i
      case Failure(e) =>
        throw new IllegalArgumentException(s"Could not construct ${thingClass.getSimpleName} with arguments ${ctorArgs.toList}", e)
    }
  }
}
