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

package com.caucho.amqp.server;

import java.io.IOException;

import com.caucho.amqp.common.AmqpReceiverLink;
import com.caucho.amqp.io.AmqpConstants;
import com.caucho.amqp.io.AmqpReader;
import com.caucho.amqp.io.FrameTransfer;
import com.caucho.amqp.io.MessageHeader;
import com.caucho.message.broker.BrokerSender;
import com.caucho.message.broker.SenderSettleHandler;
import com.caucho.util.CurrentTime;
import com.caucho.vfs.TempBuffer;

/**
 * link session management
 */
public class AmqpServerReceiverLink extends AmqpReceiverLink
{
  private final BrokerSender _sender;
  
  public AmqpServerReceiverLink(String name,
                                String address,
                                BrokerSender sender)
  {
    super(name, address);
    
    _sender = sender;
  }

  /**
   * receives a message from the network
   */
  @Override
  protected void onTransfer(FrameTransfer transfer, AmqpReader ain)
    throws IOException
  {
    boolean isSettled = transfer.isSettled();
    
    long desc = ain.peekDescriptor();
    
    MessageHeader header;
    boolean isDurable = false;
    int priority = -1;
    long expireTime = 0;
    
    if (desc == AmqpConstants.ST_MESSAGE_HEADER) {
      header = new MessageHeader();
      header.read(ain);
      
      isDurable = header.isDurable();
      priority = header.getPriority();
      
      long ttl = header.getTimeToLive();
      
      if (ttl >= 0) {
        expireTime = ttl + CurrentTime.getCurrentTime();
      }
    }
    
    int len = ain.getFrameAvailable();
    
    TempBuffer tBuf = TempBuffer.allocate();
    
    ain.read(tBuf.getBuffer(), 0, len);
    
    long xid = 0;
    long mid = _sender.nextMessageId();
    
    SenderSettleHandler handler = null;
    
    if (! isSettled) {
      handler = new MessageSettleHandler(mid);
    }
    
    _sender.message(xid, mid, isDurable, priority, expireTime,
                    tBuf.getBuffer(), 0, len, tBuf, handler);
  }
  
  class MessageSettleHandler implements SenderSettleHandler {
    private final long _deliveryId;
    
    MessageSettleHandler(long deliveryId)
    {
      _deliveryId = deliveryId;
    }
    
    @Override
    public void onAccepted(long mid)
    {
      getSession().onAccepted(_deliveryId);
    }

    @Override
    public void onRejected(long mid, String msg)
    {
      getSession().onRejected(_deliveryId, msg);
    }
  }
}
