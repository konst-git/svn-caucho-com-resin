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

package com.caucho.cloud.loadbalance;

import java.util.ArrayList;

import com.caucho.cloud.topology.CloudPod;
import com.caucho.config.ConfigException;
import com.caucho.network.balance.ClientSocketFactory;
import com.caucho.server.cluster.ServletService;
import com.caucho.util.L10N;

/**
 * LoadBalanceService distributes requests across a group of clients.
 */
public class LoadBalanceBuilder
{
  private static final L10N L = new L10N(LoadBalanceBuilder.class);
  
  private LoadBalanceStrategy _strategy = LoadBalanceStrategy.ADAPTIVE;
  private String _meterCategory = null;
  
  private long _idleTimeout;
  
  private ArrayList<ClientSocketFactory> _clientList 
    = new ArrayList<ClientSocketFactory>();
  
  private String _cluster;
  private int _port;
  
  /**
   * Sets the load balance strategy.
   */
  public void setStrategy(LoadBalanceStrategy strategy)
  {
    if (strategy == null)
      throw new NullPointerException();
    
    _strategy = strategy;
  }

  /**
   * The load balance strategy.
   */
  public LoadBalanceStrategy getStrategy()
  {
    return _strategy;
  }
  
  /**
   * The request-sticky generator
   */
  public void setStickyRequestHashGenerator(StickyRequestHashGenerator gen)
  {
    
  }
  
  
  public void setIdleTimeout(long timeout)
  {
    _idleTimeout = timeout;
  }
  
  public long getIdleTimeout()
  {
    return _idleTimeout;
  }
  
  /**
   * The statistics meter category.
   */
  public void setMeterCategory(String category)
  {
    _meterCategory = category;
  }
  
  /**
   * The statistics meter category.
   */
  public String getMeterCategory()
  {
    return _meterCategory;
  }
  
  public void addAddress(String address)
  {
    ClientSocketFactory client = createClientSocketFactory(address);
    
    addClient(client);
  }
  
  /**
   * Adds a client pool factory.
   */
  public void addClient(ClientSocketFactory client)
  {
    client.init();
    client.start();
    
    _clientList.add(client);
  }
  
  public ArrayList<ClientSocketFactory> getClientList()
  {
    return _clientList;
  }
  
  /**
   * Sets the target cluster by id.
   */
  public void setTargetCluster(String clusterId)
  {
    throw new IllegalStateException(L.l("{0}: setTargetCluster requires Resin Professional.",
                                        this));
  }
  /**
   * Sets the target cluster by id.
   */
  public void setTargetPort(int port)
  {
    throw new IllegalStateException(L.l("{0}: setTargetPort requires Resin Professional.",
                                        this));
  }
  
  /**
   * Sets the target cluster by CloudPod id.
   */
  public void setTargetCluster(CloudPod pod)
  {
    throw new IllegalStateException(L.l("{0}: setTargetCluster requires Resin Professional.",
                                        this));
  }
  
  /**
   * Returns the load balance manager.
   */
  public LoadBalanceManager create()
  {
    ClientSocketFactory socketFactory = null;
    if (getClientList().size() > 0)
      socketFactory = getClientList().get(0);

    return new SingleLoadBalanceManager(socketFactory, getMeterCategory());
  }

  protected ClientSocketFactory createClientSocketFactory(String address)
  {
    int p = address.lastIndexOf(':');
    int q = address.lastIndexOf(']');

    if (p < 0 && q < p)
      throw new ConfigException(L.l("'{0}' is an invalid address because it does not specify the port.",
                                    address));

    String host = address.substring(0, p);
    int port = Integer.parseInt(address.substring(p + 1));

    ServletService server = ServletService.getCurrent();

    boolean isSecure = false;

    ClientSocketFactory factory
      = new ClientSocketFactory(server.getServerId(),
                                address,
                                getMeterCategory(),
                                address,
                                host,
                                port,
                                isSecure);
    
    if (_idleTimeout > 0)
      factory.setLoadBalanceIdleTime(_idleTimeout);
    
    return factory;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
