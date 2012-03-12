/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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

package com.caucho.amqp.common;

import java.io.IOException;

import com.caucho.amqp.io.AmqpConstants;
import com.caucho.amqp.io.AmqpError;
import com.caucho.amqp.io.AmqpReader;
import com.caucho.amqp.io.FrameAttach;
import com.caucho.amqp.io.FrameFlow;
import com.caucho.amqp.io.FrameTransfer;
import com.caucho.amqp.io.MessageHeader;
import com.caucho.amqp.io.FrameAttach.Role;
import com.caucho.util.CurrentTime;
import com.caucho.vfs.TempBuffer;

/**
 * link session management
 */
abstract public class AmqpLink
{
  private final String _name;
  private final String _address;
  
  private int _incomingHandle = -1;
  private int _outgoingHandle = -1;
  
  private AmqpSession _session;
  //private MessageSettleHandler _settleHandler;
  
  public AmqpLink(String name, String address)
  {
    _name = name;
    _address = address;
  }
  
  public String getName()
  {
    return _name;
  }

  public String getAddress()
  {
    return _address;
  }

  abstract public Role getRole();

  public AmqpSession getSession()
  {
    return _session;
  }
  
  void setSession(AmqpSession session)
  {
    if (_session != null)
      throw new IllegalStateException();
    
    _session = session;
  }

  public int getIncomingHandle()
  {
    return _incomingHandle;
  }
  
  public void setIncomingHandle(int handle)
  {
    _incomingHandle = handle;
  }
  
  public int getOutgoingHandle()
  {
    return _outgoingHandle;
  }
  
  public void setOutgoingHandle(int handle)
  {
    _outgoingHandle = handle;
  }
  
  //
  // message transfer
  //

  /**
   * Message receivers implement this method to receive a
   * message fragment from the network.
   */
  protected void onTransfer(FrameTransfer transfer, AmqpReader ain)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  //
  // disposition
  //

  /**
   * @param messageId
   */
  public void onAccepted(long xid, long messageId)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * @param messageId
   */
  public void onRejected(long xid, long messageId, String message)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * @param xid
   * @param messageId
   * @param error
   */
  public void onRejected(long xid, long messageId, AmqpError error)
  {
    // TODO Auto-generated method stub
    
  }

  /**
   * @param messageId
   */
  public void onReleased(long xid, long messageId)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  public void onModified(long xid,
                       long mid, 
                       boolean isFailed, 
                       boolean isUndeliverableHere)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * @param mid
   */
  public void accepted(long mid)
  {
    // TODO Auto-generated method stub
    
  }

  /**
   * @param mid
   * @param errorMessage
   */
  public void rejected(long mid, String errorMessage)
  {
    // TODO Auto-generated method stub
    
  }

  /**
   * @param mid
   */
  public void released(long mid)
  {
    // TODO Auto-generated method stub
    
  }

  /**
   * @param flow
   */
  public void onFlow(FrameFlow flow)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getName() + "]";
  }
}
