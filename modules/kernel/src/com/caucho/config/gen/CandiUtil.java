/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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

package com.caucho.config.gen;

import com.caucho.config.inject.CreationalContextImpl;
import com.caucho.config.inject.DecoratorBean;
import com.caucho.config.inject.DelegateProxyBean;
import com.caucho.config.inject.InterceptorBean;
import com.caucho.config.inject.InjectManager;
import com.caucho.util.L10N;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.Decorator;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.Interceptor;
import javax.enterprise.inject.spi.InterceptionType;

/**
 * Utilities
 */
public class CandiUtil {
  private static final L10N L = new L10N(CandiUtil.class);
  private static final Logger log = Logger.getLogger(CandiUtil.class.getName());

  public static final Object []NULL_OBJECT_ARRAY = new Object[0];

  private CandiUtil()
  {
  }
  
  public static Object invoke(Method method, Object bean, Object ...args)
  {
    try {
      return method.invoke(bean, args);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException)
        throw (RuntimeException) e.getCause();
      else
        throw new RuntimeException(method.getName() + ": " + e, e.getCause());
    } catch (Exception e) {
      throw new RuntimeException(method.getName() + ": " + e, e);
    }
  }

  public static int []createInterceptors(InjectManager manager,
                                         ArrayList<Interceptor<?>> beans,
                                         int []indexList,
                                         InterceptionType type,
                                         Annotation ...bindings)
  {
    List<Interceptor<?>> interceptors;

    if (bindings != null && bindings.length > 0) {
      interceptors = manager.resolveInterceptors(type, bindings);
    }
    else
      interceptors = new ArrayList<Interceptor<?>>();

    int offset = 0;

    if (indexList == null) {
      indexList = new int[interceptors.size()];
    }
    else {
      offset = indexList.length;

      int[] newIndexList = new int[indexList.length + interceptors.size()];
      System.arraycopy(indexList, 0, newIndexList, 0, indexList.length);
      indexList = newIndexList;
    }

    for (int i = 0; i < interceptors.size(); i++) {
      Interceptor<?> interceptor = interceptors.get(i);

      int index = beans.indexOf(interceptor);
      if (index >= 0)
        indexList[offset + i] = index;
      else {
        indexList[offset + i] = beans.size();
        beans.add(interceptor);
      }
    }

    return indexList;
  }

  public static Method []createMethods(ArrayList<Interceptor<?>> beans,
                                       InterceptionType type,
                                       int []indexChain)
  {
    Method []methods = new Method[indexChain.length];

    for (int i = 0; i < indexChain.length; i++) {
      int index = indexChain[i];

      // XXX:
      Method method = ((InterceptorBean<?>) beans.get(index)).getMethod(type);

      if (method == null)
        throw new IllegalStateException(L.l("'{0}' is an unknown interception method in '{1}'",
                                           type, beans.get(index)));

      method.setAccessible(true);
      
      methods[i] = method;
    }

    return methods;
  }

  public static Method []createDecoratorMethods(List<Decorator<?>> decorators,
                                                String methodName,
                                                Class<?> ...paramTypes)
  {
    Method []methods = new Method[decorators.size()];
    
    for (int i = 0; i < decorators.size(); i++) {
      Decorator<?> decorator = decorators.get(i);
      Class<?> beanClass = decorator.getBeanClass();
      
      try {
        methods[decorators.size() - i - 1] = beanClass.getMethod(methodName, paramTypes);
        methods[decorators.size() - i - 1].setAccessible(true);
      } catch (Exception e) {
        log.log(Level.FINEST, e.toString(), e);
      }
    }

    return methods;
  }

  public static Method getMethod(Class<?> cl,
                                 String methodName,
                                 Class<?> paramTypes[])
    throws Exception
  {
    Method method = null;
    Exception firstException = null;

    do {
      try {
        method = cl.getDeclaredMethod(methodName, paramTypes);
      } catch (Exception e) {
        if (firstException == null)
          firstException = e;

        cl = cl.getSuperclass();
      }
    } while (method == null && cl != null);

    if (method == null)
      throw firstException;

    method.setAccessible(true);

    return method;
  }

  public static Object generateDelegate(List<Decorator<?>> beans,
                                        Object tail)
  {
    InjectManager webBeans = InjectManager.create();

    Bean<?> parentBean = null;
    CreationalContext env = webBeans.createCreationalContext(parentBean);

    for (int i = beans.size() - 1; i >= 0; i--) {
      Decorator<?> bean = beans.get(i);

      Object instance = webBeans.getReference(bean, bean.getBeanClass(), env);

      // XXX:
      // bean.setDelegate(instance, tail);

      tail = instance;
    }

    return tail;
  }

  public static Object []generateProxyDelegate(InjectManager manager,
                                               List<Decorator<?>> beans,
                                               Object delegateProxy,
                                               CreationalContextImpl<?> parentEnv)
  {
    Object []instances = new Object[beans.size()];

    CreationalContext<Object> proxyEnv
      = new CreationalContextImpl<Object>(DelegateProxyBean.BEAN, parentEnv);
    
    proxyEnv.push(delegateProxy);
    
    for (int i = 0; i < beans.size(); i++) {
      Decorator<?> bean = beans.get(i);
      
      /*
      Object instance = CreationalContextImpl.findWithNull(parentEnv, bean);
      
      if (instance == null) {
        CreationalContext<?> env = new CreationalContextImpl(bean, parentEnv);

        instance = manager.getReference(bean, bean.getBeanClass(), env);
      }
      else if (instance == CreationalContextImpl.NULL) {
        instance = null; // XXX: error?
      }
      */
      
      CreationalContext<?> env = new CreationalContextImpl(bean, proxyEnv);
      
      Object instance = manager.getReference(bean, bean.getBeanClass(), env);

      // XXX:
      InjectionPoint ip = getDelegate(bean);

      if (ip.getMember() instanceof Field) {
        Field field = (Field) ip.getMember();
        field.setAccessible(true);
      
        try {
          field.set(instance, delegateProxy);
        } catch (Exception e) {
          throw new InjectionException(e);
        }
      } else if (ip.getMember() instanceof Method) {
        Method method = (Method) ip.getMember();
        method.setAccessible(true);
      
        try {
          method.invoke(instance, delegateProxy);
        } catch (Exception e) {
          throw new InjectionException(e);
        }
      }
      
      /*
      DecoratorBean<?> decoratorBean = (DecoratorBean<?>) bean;
      decoratorBean.setDelegate(instance, proxy);
      */

      instances[beans.size() - i - 1] = instance;
      
      if (parentEnv instanceof CreationalContextImpl<?>) {
        // InjectionPoint ip = decoratorBean.getDelegateInjectionPoint();
      
        ((CreationalContextImpl<?>) parentEnv).setInjectionPoint(ip);
      }
    }

    return instances;
  }
  
  private static InjectionPoint getDelegate(Decorator<?> bean)
  {
    if (bean instanceof DecoratorBean)
      return ((DecoratorBean) bean).getDelegateInjectionPoint();

    for (InjectionPoint ip : bean.getInjectionPoints()) {
      if (ip.isDelegate())
        return ip;
    }
    
    throw new IllegalStateException(String.valueOf(bean));
  }

  public static int nextDelegate(Object []beans,
                                 Method []methods,
                                 int index)
  {
    for (index--; index >= 0; index--) {
      if (methods[index] != null) {
        return index;
      }
    }

    return index;
  }

  public static int nextDelegate(Object []beans,
                                 Class<?> []apis,
                                 int index)
  {
    for (index--; index >= 0; index--) {
      for (Class<?> api : apis) {
        if (api.isAssignableFrom(beans[index].getClass()))
          return index;
      }
    }

    return index;
  }
}