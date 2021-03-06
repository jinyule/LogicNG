///////////////////////////////////////////////////////////////////////////
//                   __                _      _   ________               //
//                  / /   ____  ____ _(_)____/ | / / ____/               //
//                 / /   / __ \/ __ `/ / ___/  |/ / / __                 //
//                / /___/ /_/ / /_/ / / /__/ /|  / /_/ /                 //
//               /_____/\____/\__, /_/\___/_/ |_/\____/                  //
//                           /____/                                      //
//                                                                       //
//               The Next Generation Logic Library                       //
//                                                                       //
///////////////////////////////////////////////////////////////////////////
//                                                                       //
//  Copyright 2015-20xx Christoph Zengler                                //
//                                                                       //
//  Licensed under the Apache License, Version 2.0 (the "License");      //
//  you may not use this file except in compliance with the License.     //
//  You may obtain a copy of the License at                              //
//                                                                       //
//  http://www.apache.org/licenses/LICENSE-2.0                           //
//                                                                       //
//  Unless required by applicable law or agreed to in writing, software  //
//  distributed under the License is distributed on an "AS IS" BASIS,    //
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or      //
//  implied.  See the License for the specific language governing        //
//  permissions and limitations under the License.                       //
//                                                                       //
///////////////////////////////////////////////////////////////////////////

package org.logicng.functions;

import org.junit.Assert;
import org.junit.Test;
import org.logicng.formulas.CType;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Literal;
import org.logicng.formulas.PBConstraint;
import org.logicng.formulas.Variable;
import org.logicng.io.parsers.ParserException;
import org.logicng.io.parsers.PropositionalParser;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Unit tests for {@link LiteralProfileFunction}.
 * @version 1.0
 * @since 1.0
 */
public class LiteralProfileTest {

  private final FormulaFactory f = new FormulaFactory();
  private final FormulaFactory f2 = new FormulaFactory();

  private final LiteralProfileFunction litProfile = new LiteralProfileFunction();

  private PBConstraint pb1;
  private PBConstraint pb2;
  private PBConstraint cc1;
  private PBConstraint cc2;
  private PBConstraint amo1;
  private PBConstraint amo2;
  private PBConstraint exo1;
  private PBConstraint exo2;

  public LiteralProfileTest() {
    final Variable[] lits1 = new Variable[]{f.variable("a")};
    final List<Literal> lits2 = Arrays.asList(f2.variable("a"), f.literal("b", false), f.variable("c"));
    final List<Variable> litsCC2 = Arrays.asList(f.variable("a"), f2.variable("b"), f.variable("c"));
    final int[] coeffs1 = new int[]{3};
    final List<Integer> coeffs2 = Arrays.asList(3, -2, 7);
    this.pb1 = f.pbc(CType.LE, 2, lits1, coeffs1);
    this.pb2 = f.pbc(CType.LE, 8, lits2, coeffs2);
    this.cc1 = f.cc(CType.LT, 1, lits1);
    this.cc2 = f.cc(CType.GE, 2, litsCC2);
    this.amo1 = f.amo(lits1);
    this.amo2 = f.amo(litsCC2);
    this.exo1 = f.exo(lits1);
    this.exo2 = f.exo(litsCC2);
  }

  @Test
  public void testConstants() {
    Assert.assertEquals(new HashMap<>(), f.verum().apply(litProfile, true));
    Assert.assertEquals(new HashMap<>(), f.verum().apply(litProfile, false));
    Assert.assertEquals(new HashMap<>(), f.falsum().apply(litProfile, true));
    Assert.assertEquals(new HashMap<>(), f.falsum().apply(litProfile, false));
  }

  @Test
  public void testLiterals() {
    final Map<Literal, Integer> expectedPos = new HashMap<>();
    expectedPos.put(f2.variable("a"), 1);
    final Map<Literal, Integer> expectedNeg = new HashMap<>();
    expectedNeg.put(f2.literal("a", false), 1);
    Assert.assertEquals(expectedPos, f.literal("a", true).apply(litProfile, true));
    Assert.assertEquals(expectedPos, f.literal("a", true).apply(litProfile, false));
    Assert.assertEquals(expectedNeg, f.literal("a", false).apply(litProfile, true));
    Assert.assertEquals(expectedNeg, f.literal("a", false).apply(litProfile, false));
  }

  @Test
  public void testPBC() {
    final SortedMap<Literal, Integer> exp1 = new TreeMap<>();
    exp1.put(f.variable("a"), 1);
    final SortedMap<Literal, Integer> exp2 = new TreeMap<>();
    exp2.put(f.variable("a"), 1);
    exp2.put(f.literal("b", false), 1);
    exp2.put(f.variable("c"), 1);
    final SortedMap<Literal, Integer> exp2CC = new TreeMap<>();
    exp2CC.put(f.variable("a"), 1);
    exp2CC.put(f.variable("b"), 1);
    exp2CC.put(f.variable("c"), 1);

    Assert.assertEquals(exp1, pb1.apply(litProfile, true));
    Assert.assertEquals(exp2, pb2.apply(litProfile, true));
    Assert.assertEquals(exp1, cc1.apply(litProfile, true));
    Assert.assertEquals(exp2CC, cc2.apply(litProfile, true));
    Assert.assertEquals(exp1, amo1.apply(litProfile, true));
    Assert.assertEquals(exp2CC, amo2.apply(litProfile, true));
    Assert.assertEquals(exp1, exo1.apply(litProfile, true));
    Assert.assertEquals(exp2CC, exo2.apply(litProfile, true));

    Assert.assertEquals(exp1, pb1.apply(litProfile, false));
    Assert.assertEquals(exp2, pb2.apply(litProfile, false));
    Assert.assertEquals(exp1, cc1.apply(litProfile, false));
    Assert.assertEquals(exp2CC, cc2.apply(litProfile, false));
    Assert.assertEquals(exp1, amo1.apply(litProfile, false));
    Assert.assertEquals(exp2CC, amo2.apply(litProfile, false));
    Assert.assertEquals(exp1, exo1.apply(litProfile, false));
    Assert.assertEquals(exp2CC, exo2.apply(litProfile, false));
  }

  @Test
  public void testNot() throws ParserException {
    final Map<Literal, Integer> expected = new HashMap<>();
    expected.put(f2.variable("a"), 2);
    expected.put(f2.variable("b"), 1);
    expected.put(f2.variable("c"), 1);
    expected.put(f2.literal("b", false), 1);
    expected.put(f2.literal("c", false), 2);
    final PropositionalParser p = new PropositionalParser(f);
    final Formula formula = p.parse("~(a & (b | c) & ((~b | ~c) => (~c & a)))");
    Assert.assertEquals(expected, formula.apply(litProfile, true));
    Assert.assertEquals(expected, formula.apply(litProfile, false));
  }

  @Test
  public void testBinaryOperator() throws ParserException {
    final Map<Literal, Integer> expected = new HashMap<>();
    expected.put(f2.variable("a"), 2);
    expected.put(f2.variable("b"), 1);
    expected.put(f2.variable("c"), 1);
    expected.put(f2.literal("b", false), 1);
    expected.put(f2.literal("c", false), 2);
    final PropositionalParser p = new PropositionalParser(f);
    final Formula impl = p.parse("(a & (b | c) & (~b | ~c)) => (~c & a)");
    final Formula equiv = p.parse("(a & (b | c) & (~b | ~c)) <=> (~c & a)");
    Assert.assertEquals(expected, impl.apply(litProfile, true));
    Assert.assertEquals(expected, impl.apply(litProfile, false));
    Assert.assertEquals(expected, equiv.apply(litProfile, true));
    Assert.assertEquals(expected, equiv.apply(litProfile, false));
  }

  @Test
  public void testNAryOperator() throws ParserException {
    final Map<Literal, Integer> expected = new HashMap<>();
    expected.put(f2.variable("a"), 2);
    expected.put(f2.variable("b"), 1);
    expected.put(f2.variable("c"), 1);
    expected.put(f2.literal("b", false), 1);
    expected.put(f2.literal("c", false), 2);
    final PropositionalParser p = new PropositionalParser(f);
    final Formula formula = p.parse("a & (b | c) & (~b | ~c) | (~c & a)");
    Assert.assertEquals(expected, formula.apply(litProfile, true));
    Assert.assertEquals(expected, formula.apply(litProfile, false));
  }
}
