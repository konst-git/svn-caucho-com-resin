/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.php.expr;

import java.io.IOException;

import java.util.HashSet;

import com.caucho.java.JavaWriter;

import com.caucho.php.env.Env;
import com.caucho.php.env.NullValue;
import com.caucho.php.env.Value;

import com.caucho.php.gen.PhpWriter;

import com.caucho.php.program.AnalyzeInfo;
import com.caucho.php.program.ExprStatement;

/**
 * Represents unsetting a PHP variable
 */
public class UnsetVarExpr extends Expr {
  private final AbstractVarExpr _var;

  public UnsetVarExpr(AbstractVarExpr var)
  {
    _var = var;
  }

  /**
   * Evaluates the expression.
   *
   * @param env the calling environment.
   *
   * @return the expression value.
   */
  public Value eval(Env env)
    throws Throwable
  {
    _var.evalUnset(env);

    return NullValue.NULL;
  }

  //
  // Java code generation
  //

  /**
   * Analyze the expression
   */
  public void analyze(AnalyzeInfo info)
  {
    _var.analyzeUnset(info);
  }

  /**
   * Generates code to evaluate the expression
   *
   * @param out the writer to the Java source code.
   */
  public void generate(PhpWriter out)
    throws IOException
  {
    _var.generateUnset(out);
  }
  
  public int hashCode()
  {
    return _var.hashCode();
  }

  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (getClass() != o.getClass())
      return false;

    UnsetVarExpr expr = (UnsetVarExpr) o;

    return _var == expr._var;
  }
  
  public String toString()
  {
    return "unset(" + _var + ")";
  }
}

