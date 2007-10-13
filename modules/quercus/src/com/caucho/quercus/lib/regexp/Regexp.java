/*
 * Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
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

package com.caucho.quercus.lib.regexp;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.logging.*;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.StringBuilderValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.UnicodeBuilderValue;
import com.caucho.util.*;

public class Regexp {
  private static final Logger log
    = Logger.getLogger(Regexp.class.getName());
  
  private static final L10N L = new L10N(Regexp.class);
  
  public static final int FAIL = -1;
  public static final int SUCCESS = 0;

  StringValue _pattern;
  StringValue _subject;
  
  RegexpNode _prog;
  boolean _ignoreCase;
  boolean _isGlobal;

  int _first;
  int _start;

  StringCharCursor _stringCursor;

  int _nLoop;
  int []_loopCount;
  int []_loopTail;

  int _nGroup;
  int []_groupStart; // possibly not matching
  int _match;
  int _lexeme;

  CharBuffer _cb;
  
  // optim stuff
  CharBuffer _prefix; // initial string
  int _minLength; // minimum length possible for this regexp

  CharCursor _lastCursor;
  int _lastIndex;

  StringValue []_groupNames;
  
  boolean _isUnicode;
  boolean _isPHP5String;
  
  boolean _isUTF8;
  boolean _isEval;
  
  GroupState _groupState = new GroupState();
  
  Stack<RegexpNode> _parentLoopRestStack = new Stack<RegexpNode>();
  
  public Regexp(Env env, StringValue rawRegexp)
    throws IllegalRegexpException
  {
    if (rawRegexp.length() < 2) {
      throw new IllegalStateException(L.l(
          "Can't find delimiters in regexp '{0}'.",
          rawRegexp));
    }

    char delim = rawRegexp.charAt(0);

    if (delim == '{')
      delim = '}';
    else if (delim == '[')
      delim = ']';
    else if (delim == '(')
      delim = ')';
    else if (delim == '<')
      delim = '>';
    else if (delim == '\\' || Character.isLetterOrDigit(delim)) {
      throw new IllegalStateException(L.l(
          "Delimiter {0} in regexp '{1}' must not be backslash or alphanumeric.",
          String.valueOf(delim),
          rawRegexp));
    }

    int tail = rawRegexp.lastIndexOf(delim);

    if (tail <= 0)
      throw new IllegalStateException(L.l(
          "Can't find second {0} in regexp '{1}'.",
          String.valueOf(delim),
          rawRegexp));

    StringValue sflags = rawRegexp.substring(tail);
    StringValue pattern = rawRegexp.substring(1, tail); 
    
    int flags = 0;
    
    for (int i = 0; sflags != null && i < sflags.length(); i++) {
      switch (sflags.charAt(i)) {
        case 'm': flags |= Regcomp.MULTILINE; break;
        case 's': flags |= Regcomp.SINGLE_LINE; break;
        case 'i': flags |= Regcomp.IGNORE_CASE; break;
        case 'x': flags |= Regcomp.IGNORE_WS; break;
        case 'g': flags |= Regcomp.GLOBAL; break;
        
        case 'A': flags |= Regcomp.ANCHORED; break;
        case 'D': flags |= Regcomp.END_ONLY; break;
        case 'U': flags |= Regcomp.UNGREEDY; break;
        case 'X': flags |= Regcomp.STRICT; break;
        
        case 'u': _isUTF8 = true; break;
        case 'e': _isEval = true; break;
      }
    }

    _pattern = pattern;
    
    /*
    if (pattern.isUnicode())
      _pattern = pattern;
    else if (pattern.isPHP5String())
      _pattern = pattern;
    else if (_isUTF8)
      _pattern = pattern.toUnicodeValue(env, "UTF-8");
    else
      _pattern = pattern.toUnicodeValue(env);
    */

    Regcomp comp = new Regcomp(flags);
    _prog = comp.parse(new PeekString(_pattern));

    compile(env, _prog, comp);
  }

  protected Regexp(Env env, RegexpNode prog, Regcomp comp)
  {
    _prog = prog;
    
    compile(env, _prog, comp);
  }
  
  private Regexp()
  {
  }

  public StringValue getPattern()
  {
    return _pattern;
  }
  
  public boolean isUTF8()
  {
    return _isUTF8;
  }
  
  public boolean isEval()
  {
    return _isEval;
  }

  private void compile(Env env, RegexpNode prog, Regcomp comp)
  {
    _ignoreCase = (comp._flags & Regcomp.IGNORE_CASE) != 0;
    _isGlobal = (comp._flags & Regcomp.GLOBAL) != 0;

    /*
    if (_ignoreCase)
      RegOptim.ignoreCase(prog);

    if (! _ignoreCase)
      RegOptim.eliminateBacktrack(prog, null);
    */

    _minLength = prog.minLength();
    _prefix = new CharBuffer(prog.prefix());

    //this._prog = RegOptim.linkLoops(prog);

    _nGroup = comp._maxGroup;
    _nLoop = comp._nLoop;
    
    _groupStart = new int[_nGroup + 1];
    for (int i = 0; i < _groupStart.length; i++) {
      _groupStart[i] = -1;
    }

    _loopCount = new int[_nLoop];
    _loopTail = new int[_nLoop];
    _cb = new CharBuffer();
    _stringCursor = new StringCharCursor("");
    
    _groupNames = new StringValue[_nGroup + 1];
    for (Map.Entry<Integer,StringValue> entry : comp._groupNameMap.entrySet()) {
      StringValue groupName = entry.getValue();

      if (_isUnicode) {
      }
      else if (_isUTF8)
        groupName.toBinaryValue(env, "UTF-8");
      else
        groupName.toBinaryValue(env);

      _groupNames[entry.getKey().intValue()] = groupName;
    }
  }

  public void init(Env env, StringValue subject)
  {
    _isUnicode = subject.isUnicode();
    _isPHP5String = subject.isPHP5String();

    _subject = subject;
    
    if (_isUnicode) {
    }
    else if (_isUTF8) {
      try {
        String s = new String(subject.toBytes(), "UTF-8");

        _subject = new UnicodeBuilderValue(s);
      }
      catch (UnsupportedEncodingException e) {
        log.log(Level.FINE, e.getMessage(), e);
        env.error(e);
      }
    }
    else
      _subject = subject.toUnicodeValue(env);

    _stringCursor.init(_subject);
    
    _lastIndex = 0;
    
    for (int i = 0; i < _groupStart.length; i++) {
      _groupStart[i] = -1;
    }
    
    for (int i = 0; i < _loopTail.length; i++) {
      _loopTail[i] = -1;
    }
  }
  
  public boolean isGlobal() { return _isGlobal; }
  public boolean ignoreCase() { return _ignoreCase; }

  /**
   * Tries to match the program.
   *
   * @return the tail of the match
   */
  /*
  private int match(RegexpNode prog, CharCursor cursor)
  {
    return prog.match(cursor, this);
  }
  */
  
  void setGroupState(GroupState newState)
  {
    newState.free(_groupState);
    
    _groupState = newState;
  }
  
  void freeGroupState(GroupState oldState)
  {
    _groupState.free(oldState);
  }
  
  GroupState copyGroupState()
  {
    return _groupState.copy();
  }

  public StringValue getGroupName(int i)
  {
    return _groupNames[i];
  }
  
  public int getBegin(int i)
  {
    if (_groupState.size() < 2 * i)
      return 0;
    else
      return _groupState.get(2 * i);
  }

  public int getEnd(int i)
  {
    if (_groupState.size() < 2 * i + 1)
      return 0;
    else
      return _groupState.get(2 * i + 1);
  }

  public int length() { return _groupState.size() / 2; }
  
  public Regexp clone()
  {
    Regexp regexp = new Regexp();
    
    regexp._pattern = _pattern;
    regexp._prog = _prog;
    
    regexp._ignoreCase = _ignoreCase;
    regexp._isGlobal = _isGlobal;

    regexp._minLength = _minLength;
    regexp._prefix = _prefix;

    regexp._nGroup = _nGroup;
    regexp._nLoop = _nLoop;
    regexp._groupStart = new int[_nGroup + 1];
    regexp._loopCount = new int[_nLoop];
    regexp._loopTail = new int[_nLoop];
    regexp._cb = new CharBuffer();
    regexp._stringCursor = new StringCharCursor("");
    regexp._groupState = new GroupState();
    
    regexp._groupNames = _groupNames;

    regexp._isUnicode = _isUnicode;
    regexp._isUTF8 = _isUTF8;
    regexp._isEval = _isEval;
    
    return regexp;
  }
  
  public String toString()
  {
    return "Regexp[" + _pattern + "]";
  }
}
